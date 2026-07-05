package com.jabauth.client.abe

/**
 * Parses a CP-ABE access policy from its **infix string form** — the shape the server seals into the
 * `ABE_SEALED` envelope (e.g. `role:inspector AND region:EU`, `(a OR b) AND c`) — into the [ABEPolicy]
 * AST the ABE stage adjudicates.
 *
 * Grammar (monotone — no negation, matching CP-ABE's AND/OR/threshold structure):
 * ```
 *   expr   := term (OR term)*
 *   term   := factor (AND factor)*
 *   factor := '(' expr ')' | attribute
 *   attribute := run of non-space, non-paren chars (colons kept, e.g. role:inspector)
 * ```
 * `AND`/`OR` are case-insensitive keywords. Runs of the same operator flatten into one n-ary node
 * (`a AND b AND c` → one [ABEPolicy.And] of three leaves) so the rendered policy and the adjudication
 * match the readable form. Returns null on any malformed input — a policy we cannot parse is not one we
 * can honestly adjudicate.
 */
object AbePolicyParser {

    fun parse(policy: String?): ABEPolicy? {
        if (policy.isNullOrBlank()) return null
        val tokens = tokenize(policy) ?: return null
        if (tokens.isEmpty()) return null
        val cursor = Cursor(tokens)
        val result = runCatching { parseExpr(cursor) }.getOrNull() ?: return null
        return if (cursor.atEnd) result else null // trailing garbage → reject
    }

    private fun parseExpr(c: Cursor): ABEPolicy {
        val terms = mutableListOf(parseTerm(c))
        while (c.peek().equalsKeyword("OR")) { c.next(); terms += parseTerm(c) }
        return if (terms.size == 1) terms[0] else ABEPolicy.Or(terms)
    }

    private fun parseTerm(c: Cursor): ABEPolicy {
        val factors = mutableListOf(parseFactor(c))
        while (c.peek().equalsKeyword("AND")) { c.next(); factors += parseFactor(c) }
        return if (factors.size == 1) factors[0] else ABEPolicy.And(factors)
    }

    private fun parseFactor(c: Cursor): ABEPolicy {
        val t = c.next() ?: error("unexpected end of policy")
        if (t == "(") {
            val inner = parseExpr(c)
            require(c.next() == ")") { "unbalanced parenthesis" }
            return inner
        }
        require(t != ")" && !t.isKeyword()) { "expected attribute, got '$t'" }
        return ABEPolicy.Leaf(t)
    }

    /** Split into '(' ')' and word tokens; whitespace separates. Returns null on an unclosed structure. */
    private fun tokenize(s: String): List<String>? {
        val out = mutableListOf<String>()
        val word = StringBuilder()
        fun flush() { if (word.isNotEmpty()) { out += word.toString(); word.clear() } }
        for (ch in s) when {
            ch.isWhitespace() -> flush()
            ch == '(' || ch == ')' -> { flush(); out += ch.toString() }
            else -> word.append(ch)
        }
        flush()
        return out
    }

    private fun String?.equalsKeyword(kw: String) = this != null && this.equals(kw, ignoreCase = true)
    private fun String.isKeyword() = equals("AND", true) || equals("OR", true)

    private class Cursor(private val tokens: List<String>) {
        private var i = 0
        val atEnd get() = i >= tokens.size
        fun peek(): String? = tokens.getOrNull(i)
        fun next(): String? = tokens.getOrNull(i)?.also { i++ }
    }
}
