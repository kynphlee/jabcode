package com.jabauth.client.abe

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference

/**
 * Exception carrying a native Rabe FFI status code.
 *
 * Never contains secret/key/policy material — only the numeric status and the
 * operation name, mirroring the framework's `RabeNativeException`.
 */
class RabeNativeException(message: String, val statusCode: Int) : RuntimeException(message)

/**
 * JNA binding for the Rabe CP-ABE cdylib.
 *
 * Faithful Android port of the framework `org.nexus.jabauth.abe.encryption.kem.RabeJna`.
 * The one deliberate simplification: on Android, `Native.load("rabe_kem", ...)` finds
 * `librabe_kem.so` in the packaged `jniLibs/<abi>/` automatically, so the JVM's
 * classpath-resource + temp-file extraction fallback is NOT needed and is omitted.
 *
 * Every native entry point returns a status code (0 = OK, non-zero = error). The native
 * side wraps each body in `std::panic::catch_unwind`, so a malformed policy (or any other
 * internal error) is reported as a non-OK code instead of unwinding across the FFI boundary
 * and aborting the process.
 */
internal object RabeKemNative {

    /** JNA interface mirroring the 5 native entry points. */
    internal interface RabeLibrary : Library {
        fun rabe_setup(
            out_pk: PointerByReference, out_pk_len: NativeLongByReference,
            out_msk: PointerByReference, out_msk_len: NativeLongByReference
        ): Int

        fun rabe_keygen(
            pk: Pointer, pk_len: NativeLong,
            msk: Pointer, msk_len: NativeLong,
            attributes_json: String,
            out_sk: PointerByReference, out_sk_len: NativeLongByReference
        ): Int

        fun rabe_kem_enc(
            pk: Pointer, pk_len: NativeLong,
            policy_utf8: String,
            out_ct: PointerByReference, out_ct_len: NativeLongByReference,
            out_key32: ByteArray
        ): Int

        fun rabe_kem_dec(
            pk: Pointer, pk_len: NativeLong,
            sk: Pointer, sk_len: NativeLong,
            policy_utf8: String,
            ct: Pointer, ct_len: NativeLong,
            out_key32: ByteArray
        ): Int

        fun rabe_free(ptr: Pointer, len: NativeLong)
    }

    // Native FFI status codes (mirror of the RC_* constants in native/rabe-kem/src/lib.rs).
    private const val RC_OK = 0
    private const val RC_NULL_OUTPUT = -1
    private const val RC_ERROR = -2
    private const val RC_PANIC = -3

    private val lib: RabeLibrary by lazy { Native.load("rabe_kem", RabeLibrary::class.java) }

    private fun rcMessage(rc: Int): String = when (rc) {
        RC_NULL_OUTPUT -> "null output pointer (rc=$rc)"
        RC_ERROR -> "recoverable native error — e.g. malformed policy or invalid input (rc=$rc)"
        RC_PANIC -> "native panic caught at the FFI boundary (rc=$rc)"
        else -> "unknown native error (rc=$rc)"
    }

    /**
     * Translate a non-OK native status into a clean, catchable exception. Never logs
     * secret/key/policy material — only the numeric status and the operation name.
     */
    private fun checkRc(rc: Int, op: String) {
        if (rc != RC_OK) {
            throw RabeNativeException("rabe $op failed: ${rcMessage(rc)}", rc)
        }
    }

    private fun toByteArrayAndFree(pref: PointerByReference, lenRef: NativeLongByReference): ByteArray {
        val ptr: Pointer? = pref.value
        val len = lenRef.value.toLong()
        if (ptr == null || len <= 0) {
            return ByteArray(0)
        }
        val out = ptr.getByteArray(0, len.toInt())
        lib.rabe_free(ptr, NativeLong(len))
        return out
    }

    /** @return { pk, msk } */
    fun setup(): Array<ByteArray> {
        val pkRef = PointerByReference()
        val mskRef = PointerByReference()
        val pkLen = NativeLongByReference()
        val mskLen = NativeLongByReference()
        val rc = lib.rabe_setup(pkRef, pkLen, mskRef, mskLen)
        checkRc(rc, "setup")
        val pk = toByteArrayAndFree(pkRef, pkLen)
        val msk = toByteArrayAndFree(mskRef, mskLen)
        return arrayOf(pk, msk)
    }

    fun keygen(pkIn: ByteArray, mskIn: ByteArray, attributesJson: String): ByteArray {
        // Validate all inputs before crossing the JNA boundary.
        JnaBoundaryValidator.validatePublicKey(pkIn)
        JnaBoundaryValidator.validateMasterKey(mskIn)
        JnaBoundaryValidator.validateAttributesJson(attributesJson)

        // Defensive copies so native code cannot mutate the originals.
        val pk = JnaBoundaryValidator.defensiveCopy(pkIn)
        val msk = JnaBoundaryValidator.defensiveCopy(mskIn)

        val pkPtr = Pointer(Native.malloc(pk.size.toLong()))
        pkPtr.write(0, pk, 0, pk.size)
        val mskPtr = Pointer(Native.malloc(msk.size.toLong()))
        mskPtr.write(0, msk, 0, msk.size)
        try {
            val skRef = PointerByReference()
            val skLen = NativeLongByReference()
            val rc = lib.rabe_keygen(
                pkPtr, NativeLong(pk.size.toLong()),
                mskPtr, NativeLong(msk.size.toLong()),
                attributesJson, skRef, skLen
            )
            checkRc(rc, "keygen")
            val result = toByteArrayAndFree(skRef, skLen)

            JnaBoundaryValidator.validateNativeOutput(result, "rabe_keygen")
            JnaBoundaryValidator.validateUserKey(result)
            return result
        } finally {
            Native.free(Pointer.nativeValue(pkPtr))
            Native.free(Pointer.nativeValue(mskPtr))
        }
    }

    /** @return { key32, kemCt } */
    fun kemEnc(pkIn: ByteArray, policyUtf8: String): Array<ByteArray> {
        JnaBoundaryValidator.validatePublicKey(pkIn)
        JnaBoundaryValidator.validatePolicy(policyUtf8)

        val pk = JnaBoundaryValidator.defensiveCopy(pkIn)

        val pkPtr = Pointer(Native.malloc(pk.size.toLong()))
        pkPtr.write(0, pk, 0, pk.size)
        try {
            val ctRef = PointerByReference()
            val ctLen = NativeLongByReference()
            val key32 = ByteArray(32)
            val rc = lib.rabe_kem_enc(pkPtr, NativeLong(pk.size.toLong()), policyUtf8, ctRef, ctLen, key32)
            // A malformed policy returns a non-OK status here (the native side caught the
            // would-be panic). Throw a clean, catchable exception — the process survives.
            checkRc(rc, "kem_enc")
            val ct = toByteArrayAndFree(ctRef, ctLen)

            JnaBoundaryValidator.validateKemKey(key32)
            JnaBoundaryValidator.validateNativeOutput(ct, "rabe_kem_enc")
            JnaBoundaryValidator.validateCiphertext(ct)
            return arrayOf(key32, ct)
        } finally {
            Native.free(Pointer.nativeValue(pkPtr))
        }
    }

    fun kemDec(pkIn: ByteArray, skIn: ByteArray, policyUtf8: String, ctIn: ByteArray): ByteArray {
        JnaBoundaryValidator.validatePublicKey(pkIn)
        JnaBoundaryValidator.validateUserKey(skIn)
        JnaBoundaryValidator.validatePolicy(policyUtf8)
        JnaBoundaryValidator.validateCiphertext(ctIn)

        val pk = JnaBoundaryValidator.defensiveCopy(pkIn)
        val sk = JnaBoundaryValidator.defensiveCopy(skIn)
        val ct = JnaBoundaryValidator.defensiveCopy(ctIn)

        val pkPtr = Pointer(Native.malloc(pk.size.toLong()))
        pkPtr.write(0, pk, 0, pk.size)
        val skPtr = Pointer(Native.malloc(sk.size.toLong()))
        skPtr.write(0, sk, 0, sk.size)
        val ctPtr = Pointer(Native.malloc(ct.size.toLong()))
        ctPtr.write(0, ct, 0, ct.size)
        try {
            val key32 = ByteArray(32)
            val rc = lib.rabe_kem_dec(
                pkPtr, NativeLong(pk.size.toLong()),
                skPtr, NativeLong(sk.size.toLong()),
                policyUtf8,
                ctPtr, NativeLong(ct.size.toLong()),
                key32
            )
            checkRc(rc, "kem_dec")

            JnaBoundaryValidator.validateKemKey(key32)
            return key32
        } finally {
            Native.free(Pointer.nativeValue(pkPtr))
            Native.free(Pointer.nativeValue(skPtr))
            Native.free(Pointer.nativeValue(ctPtr))
        }
    }
}
