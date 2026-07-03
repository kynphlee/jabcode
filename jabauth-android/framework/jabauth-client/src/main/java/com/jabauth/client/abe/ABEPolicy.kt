package com.jabauth.client.abe

/**
 * Attribute-Based Encryption (ABE) access policy
 * 
 * Represents a CP-ABE access structure using boolean logic over attributes.
 * 
 * **Examples:**
 * - Simple: `(role:admin)`
 * - AND: `(role:admin AND dept:engineering)`
 * - OR: `(role:admin OR role:manager)`
 * - Threshold: `(2 OF [role:admin, dept:engineering, clearance:secret])`
 * - Nested: `(role:admin AND (dept:engineering OR dept:research))`
 * 
 * **JSON Format:**
 * ```json
 * {
 *   "type": "AND",
 *   "children": [
 *     {"type": "LEAF", "attribute": "role:admin"},
 *     {"type": "LEAF", "attribute": "dept:engineering"}
 *   ]
 * }
 * ```
 */
sealed class ABEPolicy {
    
    /**
     * Leaf node - single attribute requirement
     * 
     * @property attribute Attribute string (e.g., "role:admin")
     */
    data class Leaf(val attribute: String) : ABEPolicy()
    
    /**
     * AND node - all children must be satisfied
     * 
     * @property children Child policies (all must evaluate to true)
     */
    data class And(val children: List<ABEPolicy>) : ABEPolicy()
    
    /**
     * OR node - at least one child must be satisfied
     * 
     * @property children Child policies (at least one must evaluate to true)
     */
    data class Or(val children: List<ABEPolicy>) : ABEPolicy()
    
    /**
     * Threshold node - k-of-n children must be satisfied
     * 
     * @property threshold Minimum number of children that must be satisfied
     * @property children Child policies
     */
    data class Threshold(val threshold: Int, val children: List<ABEPolicy>) : ABEPolicy()
    
    /**
     * Convert policy to human-readable string, e.g. `(role:inspector AND region:EU)`.
     *
     * `final` is load-bearing: the subclasses are `data class`es, and a non-final supertype `toString()`
     * would be shadowed by their generated `toString()` (yielding `And(children=[Leaf(attribute=…)])`).
     * A final implementation here suppresses that generation so every node renders readably.
     */
    final override fun toString(): String {
        return when (this) {
            is Leaf -> attribute
            is And -> "(${children.joinToString(" AND ")})"
            is Or -> "(${children.joinToString(" OR ")})"
            is Threshold -> "($threshold OF [${children.joinToString(", ")}])"
        }
    }
    
    /**
     * Convert policy to JSON string
     */
    fun toJson(): String {
        return when (this) {
            is Leaf -> """{"type":"LEAF","attribute":"$attribute"}"""
            is And -> """{"type":"AND","children":[${children.joinToString(",") { it.toJson() }}]}"""
            is Or -> """{"type":"OR","children":[${children.joinToString(",") { it.toJson() }}]}"""
            is Threshold -> """{"type":"THRESHOLD","threshold":$threshold,"children":[${children.joinToString(",") { it.toJson() }}]}"""
        }
    }
}
