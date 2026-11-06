package com.ghostgrey.player

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.CRC32

/** Applies a BPS patch to the player's own base ROM. Nothing copyrighted is bundled in the app. */
object Patcher {
    const val BASE_CRC = 0xDD88761CL
    const val RESULT_CRC = 0xC2D95B0CL
    const val PATCH_ASSET = "GhostGrey03.bps"
    private const val MAX_INPUT = 20_000_000

    class PatchException(message: String) : Exception(message)

    fun romFile(ctx: Context) = File(ctx.filesDir, "ghostgrey.gba")

    fun crc32(b: ByteArray): Long = CRC32().also { it.update(b) }.value

    fun hex(v: Long) = String.format("%08X", v)

    fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_INPUT) throw PatchException("That file is too large to be the base game.")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Verifies the chosen base ROM, patches it, verifies the result, and stores it privately. */
    fun buildGame(ctx: Context, base: ByteArray) {
        val got = crc32(base)
        if (got != BASE_CRC) {
            throw PatchException(
                "This file isn't the base game this hack needs " +
                    "(Pokemon FireRed v1.0, USA).\n\nExpected CRC32 ${hex(BASE_CRC)}, got ${hex(got)}."
            )
        }
        val patch = ctx.assets.open(PATCH_ASSET).use { readLimited2(it) }
        val result = applyBps(base, patch)
        val resCrc = crc32(result)
        if (resCrc != RESULT_CRC) {
            throw PatchException("Patching finished but the result didn't verify (${hex(resCrc)}).")
        }
        val dest = romFile(ctx)
        val tmp = File(ctx.filesDir, "ghostgrey.gba.tmp")
        tmp.writeBytes(result)
        if (!tmp.renameTo(dest)) throw PatchException("Couldn't save the patched game.")
    }

    private fun readLimited2(input: InputStream): ByteArray = input.readBytes()

    fun applyBps(src: ByteArray, patch: ByteArray): ByteArray {
        if (patch.size < 19 || String(patch, 0, 4, Charsets.US_ASCII) != "BPS1") {
            throw PatchException("Bundled patch file is damaged.")
        }
        var pos = 4
        fun vl(): Long {
            var data = 0L
            var shift = 1L
            while (true) {
                val x = patch[pos++].toInt() and 0xFF
                data += (x and 0x7F).toLong() * shift
                if ((x and 0x80) != 0) break
                shift = shift shl 7
                data += shift
            }
            return data
        }

        val srcSize = vl()
        val tgtSize = vl()
        val metaSize = vl()
        pos += metaSize.toInt()
        if (src.size.toLong() != srcSize) throw PatchException("Base ROM has the wrong size.")

        val out = ByteArray(tgtSize.toInt())
        var outPos = 0
        var srcRel = 0
        var tgtRel = 0
        val end = patch.size - 12

        while (pos < end) {
            val data = vl()
            val cmd = (data and 3L).toInt()
            val len = ((data shr 2) + 1).toInt()
            when (cmd) {
                0 -> { // SourceRead
                    System.arraycopy(src, outPos, out, outPos, len)
                    outPos += len
                }
                1 -> { // TargetRead
                    System.arraycopy(patch, pos, out, outPos, len)
                    pos += len
                    outPos += len
                }
                2 -> { // SourceCopy
                    val d = vl()
                    srcRel += (if ((d and 1L) != 0L) -1 else 1) * (d shr 1).toInt()
                    System.arraycopy(src, srcRel, out, outPos, len)
                    srcRel += len
                    outPos += len
                }
                else -> { // TargetCopy (may overlap, copy byte by byte)
                    val d = vl()
                    tgtRel += (if ((d and 1L) != 0L) -1 else 1) * (d shr 1).toInt()
                    repeat(len) { out[outPos++] = out[tgtRel++] }
                }
            }
        }
        return out
    }
}
