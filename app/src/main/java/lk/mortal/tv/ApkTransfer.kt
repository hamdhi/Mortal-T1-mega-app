package lk.mortal.tv

import java.io.InputStream
import java.io.OutputStream

object ApkTransfer {
    const val STORAGE_RESERVE = 16L * 1024 * 1024
    fun checkLength(length: Long) { require(length > 0) { "Choose a non-empty APK" } }
    fun checkSpace(length: Long, available: Long) {
        checkLength(length)
        require(available > STORAGE_RESERVE && length <= available - STORAGE_RESERVE) { "Not enough free space on the TV. Remove files or choose a smaller APK." }
    }
    fun copy(input: InputStream, output: OutputStream, length: Long) {
        checkLength(length)
        val buffer = ByteArray(65536); var remaining = length
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            require(count > 0) { "Upload was interrupted. Try again." }
            output.write(buffer, 0, count); remaining -= count
        }
    }
    fun checkSelfUpdate(currentCode: Long, uploadedCode: Long, currentCerts: Set<String>, uploadedCerts: Set<String>) {
        require(uploadedCode >= currentCode) { "This Mortal TV APK is older than the installed version" }
        require(currentCerts.isNotEmpty() && currentCerts == uploadedCerts) { "Mortal TV signing certificate does not match" }
    }
}
