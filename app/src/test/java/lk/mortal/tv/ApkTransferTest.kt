package lk.mortal.tv

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ApkTransferTest {
    @Test fun streamsExactLengthWithoutConsumingNextRequest() {
        val input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)); val output = ByteArrayOutputStream()
        ApkTransfer.copy(input, output, 3)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray()); assertEquals(4, input.read())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsTruncatedUpload() {
        ApkTransfer.copy(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), 2)
    }
    @Test fun rejectsInvalidSizesWithoutAnArtificialCap() {
        listOf(0L, -1L).forEach { length ->
            try { ApkTransfer.checkLength(length); fail("Accepted invalid size") } catch (_: IllegalArgumentException) { }
        }
        ApkTransfer.checkLength(5L * 1024 * 1024 * 1024)
    }
    @Test fun checksActualSpaceWithoutOverflow() {
        ApkTransfer.checkSpace(5L * 1024 * 1024 * 1024, 6L * 1024 * 1024 * 1024)
        listOf(0L, ApkTransfer.STORAGE_RESERVE, 100L * 1024 * 1024).forEach { available ->
            try { ApkTransfer.checkSpace(200L * 1024 * 1024, available); fail("Accepted insufficient space") } catch (_: IllegalArgumentException) { }
        }
        try { ApkTransfer.checkSpace(Long.MAX_VALUE, Long.MAX_VALUE); fail("Overflow bypassed reserve") } catch (_: IllegalArgumentException) { }
    }
    @Test fun selfReinstallAndNewerVersionRequireOriginalSigner() {
        ApkTransfer.checkSelfUpdate(1006, 1006, setOf("original"), setOf("original"))
        ApkTransfer.checkSelfUpdate(1006, 1007, setOf("original"), setOf("original"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsSelfDowngrade() {
        ApkTransfer.checkSelfUpdate(1007, 1006, setOf("original"), setOf("original"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsWrongSelfSigner() {
        ApkTransfer.checkSelfUpdate(1006, 1007, setOf("original"), setOf("other"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsMissingSelfSigner() {
        ApkTransfer.checkSelfUpdate(1006, 1007, emptySet(), emptySet())
    }
    @Test fun validatesChunkBoundariesForLargeFiles() {
        val total = 5L * 1024 * 1024 * 1024
        ApkTransfer.checkChunk(total, 0, 0, ApkTransfer.CHUNK_BYTES)
        ApkTransfer.checkChunk(total, total - 3, total - 3, 3)
    }
    @Test fun rejectsWrongOffsetsAndPartsBeyondTotal() {
        val invalid = listOf(longArrayOf(10, 1, 0, 3), longArrayOf(10, -1, -1, 3), longArrayOf(10, 10, 10, 1), longArrayOf(10, 9, 9, 2), longArrayOf(Long.MAX_VALUE, Long.MAX_VALUE - 1, Long.MAX_VALUE - 1, 2))
        invalid.forEach { p -> try { ApkTransfer.checkChunk(p[0], p[1], p[2], p[3]); fail("Invalid part accepted") } catch (_: IllegalArgumentException) { } }
    }
    @Test fun rejectsEmptyAndOversizedParts() {
        listOf(0L, -1L, ApkTransfer.CHUNK_BYTES + 1).forEach { length ->
            try { ApkTransfer.checkChunk(Long.MAX_VALUE, 0, 0, length); fail("Invalid part size accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
