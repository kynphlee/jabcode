package com.jabauth.core.validation

/**
 * Result of a validation operation
 *
 * Encapsulates success/failure state with optional error details.
 *
 * @property isValid true if validation passed
 * @property errorMessage descriptive error if validation failed
 * @property errorCode optional machine-readable error code
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val errorCode: String? = null
) {
    companion object {
        /**
         * Create a successful validation result
         */
        fun success(): ValidationResult = ValidationResult(isValid = true)

        /**
         * Create a failed validation result
         *
         * @param message Human-readable error description
         * @param code Machine-readable error code
         */
        fun failure(message: String, code: String? = null): ValidationResult =
            ValidationResult(isValid = false, errorMessage = message, errorCode = code)
    }
}
