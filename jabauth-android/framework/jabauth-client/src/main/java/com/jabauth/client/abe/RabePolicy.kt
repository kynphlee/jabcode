package com.jabauth.client.abe

/**
 * Policy layer for the native Rabe CP-ABE engine.
 *
 * Faithful Android port of the framework policy stack:
 * - `org.nexus.jabauth.abe.policy.RobustPolicyParser` (recursive-descent parser with
 *   AND/OR/NOT precedence and `name:value` attribute leaves),
 * - `org.nexus.jabauth.abe.policy.PolicyExpression` (the AST + evaluation), and
 * - `org.nexus.jabauth.abe.policy.RabeHumanPolicyRenderer` (fully-parenthesized rabe
 *   HumanPolicy rendering).
 *
 * String policies (e.g. `role:doctor AND dept:cardio`) are the interop unit — they are
 * parsed once and used both for the decrypt-time Java-side policy gate and for rendering
 * the rabe-grammar-valid policy handed to the native KEM. Keeping a single parser
 * guarantees the gate and the native encoding stay in lock-step.
 */

/** A set of user attributes for ABE operations. Port of the framework `AttributeSet`. */
data class RabeAttributeSet(val attributes: Map<String, String>) {

    fun hasAttribute(name: String): Boolean = attributes.containsKey(name)

    fun getAttribute(name: String): String? = attributes[name]

    class Builder {
        private val attributes = HashMap<String, String>()

        fun attribute(name: String, value: String): Builder {
            attributes[name] = value
            return this
        }

        fun build(): RabeAttributeSet = RabeAttributeSet(attributes.toMap())
    }

    companion object {
        fun of(attributes: Map<String, String>): RabeAttributeSet = RabeAttributeSet(attributes.toMap())
        fun builder(): Builder = Builder()
    }
}

/** Base type for policy AST nodes. */
sealed interface PolicyExpression {
    fun evaluate(attributes: RabeAttributeSet): Boolean
}

internal data class AndExpression(val left: PolicyExpression, val right: PolicyExpression) : PolicyExpression {
    override fun evaluate(attributes: RabeAttributeSet): Boolean =
        left.evaluate(attributes) && right.evaluate(attributes)
}

internal data class OrExpression(val left: PolicyExpression, val right: PolicyExpression) : PolicyExpression {
    override fun evaluate(attributes: RabeAttributeSet): Boolean =
        left.evaluate(attributes) || right.evaluate(attributes)
}

internal data class NotExpression(val inner: PolicyExpression) : PolicyExpression {
    override fun evaluate(attributes: RabeAttributeSet): Boolean = !inner.evaluate(attributes)
}

internal data class AttributeExpression(val name: String, val value: String) : PolicyExpression {
    override fun evaluate(attributes: RabeAttributeSet): Boolean = value == attributes.getAttribute(name)
}

/**
 * Recursive-descent policy parser with proper operator precedence and tokenization.
 *
 * Grammar:
 * ```
 *   policy    ::= orExpr
 *   orExpr    ::= andExpr ( "OR" andExpr )*
 *   andExpr   ::= primary ( "AND" primary )*
 *   primary   ::= attribute | "(" policy ")" | "NOT" primary
 *   attribute ::= name ":" value
 * ```
 * Precedence (highest→lowest): parentheses, NOT, AND, OR.
 *
 * Holds no mutable instance state (each parse carries its own [Cursor]), so a single
 * instance is safe to share across threads.
 */
class RobustPolicyParser {

    class PolicyParseException(message: String) : RuntimeException(message)

    private enum class TokenType { ATTRIBUTE, VALUE, COLON, AND, OR, NOT, LPAREN, RPAREN }

    private data class Token(val type: TokenType, val value: String, val position: Int) {
        override fun toString(): String = "$type($value)"
    }

    private class Cursor(val tokens: List<Token>) {
        var position: Int = 0
    }

    fun parse(policy: String?): PolicyExpression {
        require(!(policy == null || policy.trim().isEmpty())) { "Policy cannot be null or empty" }
        try {
            val cur = Cursor(tokenize(policy))
            val result = parseOrExpression(cur)
            if (cur.position < cur.tokens.size) {
                val unexpected = cur.tokens[cur.position]
                throw PolicyParseException(
                    "Unexpected token '${unexpected.value}' at position ${unexpected.position}"
                )
            }
            return result
        } catch (e: PolicyParseException) {
            throw IllegalArgumentException("Policy parse error: ${e.message}", e)
        }
    }

    private fun parseOrExpression(cur: Cursor): PolicyExpression {
        var left = parseAndExpression(cur)
        while (match(cur, TokenType.OR)) {
            val right = parseAndExpression(cur)
            left = OrExpression(left, right)
        }
        return left
    }

    private fun parseAndExpression(cur: Cursor): PolicyExpression {
        var left = parsePrimary(cur)
        while (match(cur, TokenType.AND)) {
            val right = parsePrimary(cur)
            left = AndExpression(left, right)
        }
        return left
    }

    private fun parsePrimary(cur: Cursor): PolicyExpression {
        if (match(cur, TokenType.NOT)) {
            val inner = parsePrimary(cur)
            return NotExpression(inner)
        }
        if (match(cur, TokenType.LPAREN)) {
            val inner = parseOrExpression(cur)
            if (!match(cur, TokenType.RPAREN)) {
                throw PolicyParseException("Expected ')' but found: ${currentToken(cur)}")
            }
            return inner
        }
        if (peek(cur, TokenType.ATTRIBUTE)) {
            val attrToken = consume(cur, TokenType.ATTRIBUTE)
            if (!match(cur, TokenType.COLON)) {
                throw PolicyParseException("Expected ':' after attribute name")
            }
            val valueToken = consume(cur, TokenType.VALUE)
            return AttributeExpression(attrToken.value, valueToken.value)
        }
        throw PolicyParseException("Expected attribute, NOT, or '(' but found: ${currentToken(cur)}")
    }

    private fun match(cur: Cursor, type: TokenType): Boolean {
        if (peek(cur, type)) {
            cur.position++
            return true
        }
        return false
    }

    private fun peek(cur: Cursor, type: TokenType): Boolean =
        cur.position < cur.tokens.size && cur.tokens[cur.position].type == type

    private fun consume(cur: Cursor, type: TokenType): Token {
        if (!peek(cur, type)) {
            throw PolicyParseException("Expected $type but found: ${currentToken(cur)}")
        }
        return cur.tokens[cur.position++]
    }

    private fun currentToken(cur: Cursor): String =
        if (cur.position >= cur.tokens.size) "end of input" else "'${cur.tokens[cur.position].value}'"

    private fun tokenize(policy: String): List<Token> {
        val result = ArrayList<Token>()
        var pos = 0
        while (pos < policy.length) {
            val c = policy[pos]

            if (c.isWhitespace()) {
                pos++
                continue
            }
            if (c == '(') {
                result.add(Token(TokenType.LPAREN, "(", pos)); pos++; continue
            }
            if (c == ')') {
                result.add(Token(TokenType.RPAREN, ")", pos)); pos++; continue
            }
            if (c == ':') {
                if (result.isEmpty() || result[result.size - 1].type != TokenType.ATTRIBUTE) {
                    throw PolicyParseException(
                        "Unexpected ':' at position $pos (must follow attribute name)"
                    )
                }
                result.add(Token(TokenType.COLON, ":", pos)); pos++; continue
            }
            // Quoted "name:value" pair (rabe HumanPolicy quotes attributes). Unquote so a
            // pre-quoted attribute still matches the holder's unquoted attribute.
            if (c == '"') {
                val contentStart = pos + 1
                var scan = contentStart
                while (scan < policy.length && policy[scan] != '"') scan++
                if (scan >= policy.length) {
                    throw PolicyParseException("Unterminated quoted token starting at position $pos")
                }
                val inner = policy.substring(contentStart, scan)
                emitAttributeColonValue(result, inner, pos)
                pos = scan + 1
                continue
            }
            if (c.isLetter() || c == '_') {
                val start = pos
                while (pos < policy.length &&
                    (policy[pos].isLetterOrDigit() || policy[pos] == '_' || policy[pos] == '-')
                ) {
                    pos++
                }
                val word = policy.substring(start, pos)
                when {
                    word.equals("AND", ignoreCase = true) -> result.add(Token(TokenType.AND, "AND", start))
                    word.equals("OR", ignoreCase = true) -> result.add(Token(TokenType.OR, "OR", start))
                    word.equals("NOT", ignoreCase = true) -> result.add(Token(TokenType.NOT, "NOT", start))
                    else -> {
                        val type = if (result.isNotEmpty() &&
                            result[result.size - 1].type == TokenType.COLON
                        ) TokenType.VALUE else TokenType.ATTRIBUTE
                        if (type == TokenType.ATTRIBUTE && !ATTRIBUTE_PATTERN.matches(word)) {
                            throw PolicyParseException("Invalid attribute name '$word' at position $start")
                        }
                        if (type == TokenType.VALUE && !VALUE_PATTERN.matches(word)) {
                            throw PolicyParseException("Invalid attribute value '$word' at position $start")
                        }
                        result.add(Token(type, word, start))
                    }
                }
                continue
            }
            throw PolicyParseException("Unexpected character '$c' at position $pos")
        }
        return result
    }

    private fun emitAttributeColonValue(result: MutableList<Token>, inner: String, position: Int) {
        val colon = inner.indexOf(':')
        if (colon <= 0 || colon == inner.length - 1) {
            throw PolicyParseException(
                "Quoted token at position $position must be a 'name:value' attribute"
            )
        }
        val name = inner.substring(0, colon)
        val value = inner.substring(colon + 1)
        if (!ATTRIBUTE_PATTERN.matches(name)) {
            throw PolicyParseException(
                "Invalid attribute name '$name' in quoted token at position $position"
            )
        }
        if (!VALUE_PATTERN.matches(value)) {
            throw PolicyParseException(
                "Invalid attribute value '$value' in quoted token at position $position"
            )
        }
        result.add(Token(TokenType.ATTRIBUTE, name, position))
        result.add(Token(TokenType.COLON, ":", position))
        result.add(Token(TokenType.VALUE, value, position))
    }

    companion object {
        private val ATTRIBUTE_PATTERN = Regex("^[a-zA-Z_][a-zA-Z0-9_\\-]*$")
        private val VALUE_PATTERN = Regex("^[a-zA-Z0-9_\\-]+$")
    }
}

/**
 * Renders an ABE policy string into a fully-parenthesized rabe HumanPolicy string.
 *
 * The rabe human-policy grammar has no mixed-precedence production, so a flat policy such
 * as `"A" and "B" or "C"` is rejected. This renderer routes the policy through the canonical
 * [RobustPolicyParser] (the same parser the decrypt-time gate uses, so precedence and
 * semantics match), then serialises the AST with explicit parentheses around every binary
 * node. Attribute leaves are emitted as quoted `"name:value"` terms. rabe HumanPolicy has no
 * negation, so a `NOT` subexpression is rejected with [IllegalArgumentException].
 */
object RabeHumanPolicyRenderer {

    private val PARSER = RobustPolicyParser()

    fun render(policy: String?): String {
        if (policy == null || policy.trim().isEmpty()) {
            return ""
        }
        val ast = PARSER.parse(policy.trim())
        val sb = StringBuilder()
        render(ast, sb)
        return sb.toString()
    }

    private fun render(expr: PolicyExpression, sb: StringBuilder) {
        when (expr) {
            is AttributeExpression -> sb.append('"')
                .append(expr.name.replace("\"", "\\\""))
                .append(':')
                .append(expr.value.replace("\"", "\\\""))
                .append('"')

            is AndExpression -> {
                sb.append('(')
                render(expr.left, sb)
                sb.append(" and ")
                render(expr.right, sb)
                sb.append(')')
            }

            is OrExpression -> {
                sb.append('(')
                render(expr.left, sb)
                sb.append(" or ")
                render(expr.right, sb)
                sb.append(')')
            }

            is NotExpression -> throw IllegalArgumentException(
                "rabe HumanPolicy has no negation operator; NOT cannot be encoded natively"
            )
        }
    }
}
