package lk.mortal.tv

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.InputStream

class ApkInbox(private val context: Context) {
    data class Info(val name: String, val packageName: String, val version: String, val code: Long, val size: Long) {
        fun json() = JSONObject().put("name", name).put("packageName", packageName).put("version", version).put("versionCode", code).put("bytes", size)
    }
    private val directory = File(context.cacheDir, "updates/pc").apply { mkdirs() }
    val file = File(directory, "received.apk")
    private val partial = File(directory, "upload.part")
    private var uploadId: String? = null
    private var uploadLength = 0L
    @Synchronized fun uploadState() = JSONObject().put("uploadId", uploadId ?: JSONObject.NULL).put("offset", if (uploadId != null) partial.length() else 0).put("total", uploadLength)
    @Synchronized fun begin(length: Long): JSONObject {
        ApkTransfer.checkSpace(length, directory.usableSpace)
        require(!file.exists()) { "An APK is already waiting. Delete the uploaded APK before sending another." }
        require(uploadId == null) { "Another upload is in progress. Cancel or delete it before retrying." }
        partial.delete(); uploadId = java.util.UUID.randomUUID().toString(); uploadLength = length
        return uploadState()
    }
    @Synchronized fun append(id: String, offset: Long, input: InputStream, length: Long): Long {
        require(id == uploadId) { "Upload session expired. Start the upload again." }
        ApkTransfer.checkChunk(uploadLength, offset, partial.length(), length)
        ApkTransfer.checkSpace(length, directory.usableSpace)
        try { java.io.FileOutputStream(partial, true).use { ApkTransfer.copy(input, it, length) } }
        catch (e: Exception) { if (partial.exists()) java.io.RandomAccessFile(partial, "rw").use { it.setLength(offset) }; throw e }
        return partial.length()
    }
    @Synchronized fun finish(id: String): Info {
        require(id == uploadId) { "Upload session expired. Start the upload again." }
        require(partial.length() == uploadLength) { "APK upload is incomplete" }
        try {
            val info = inspect(partial)
            require(partial.renameTo(file)) { "Could not save uploaded APK" }
            return info
        } finally { partial.delete(); uploadId = null; uploadLength = 0 }
    }
    @Synchronized fun cancel(id: String) {
        if (id == uploadId) { require(!partial.exists() || partial.delete()) { "Could not cancel upload" }; uploadId = null; uploadLength = 0 }
    }
    fun availableBytes() = (directory.usableSpace - ApkTransfer.STORAGE_RESERVE).coerceAtLeast(0)
    @Synchronized fun pending(): Info? = if (file.isFile) inspect(file) else null
    @Synchronized fun receive(input: InputStream, length: Long): Info {
        ApkTransfer.checkLength(length)
        require(uploadId == null) { "Another upload is in progress" }
        require(!file.exists()) { "An APK is already waiting. Install or remove it in TV Settings > APK from PC first." }
        ApkTransfer.checkSpace(length, directory.usableSpace)
        try {
            partial.outputStream().use { ApkTransfer.copy(input, it, length) }
            val info = inspect(partial)
            require(partial.renameTo(file)) { "Could not save the uploaded APK" }
            return info
        } finally { partial.delete() }
    }
    @Synchronized fun remove() {
        require(!file.exists() || file.delete()) { "Could not remove APK" }
        require(!partial.exists() || partial.delete()) { "Could not remove incomplete upload" }
        uploadId = null; uploadLength = 0
    }
    @Suppress("DEPRECATION")
    private fun inspect(apk: File): Info {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: error("Not a readable APK. Upload a standalone .apk file, not a bundle or ZIP.")
        val app = archive.applicationInfo ?: error("APK application information is missing")
        require(app.minSdkVersion <= Build.VERSION.SDK_INT) { "This APK requires a newer Android version" }
        fun certs(info: PackageInfo) = (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures).orEmpty().map { it.toCharsString() }.toSet()
        val signers = certs(archive); require(signers.isNotEmpty()) { "APK signing information could not be read" }
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        if (archive.packageName == context.packageName) {
            ApkTransfer.checkSelfUpdate(BuildConfig.VERSION_CODE.toLong(), code, certs(pm.getPackageInfo(context.packageName, flags)), signers)
        }
        app.sourceDir = apk.absolutePath; app.publicSourceDir = apk.absolutePath
        val label = runCatching { app.loadLabel(pm).toString() }.getOrDefault(archive.packageName).take(200)
        return Info(label, archive.packageName, archive.versionName.orEmpty().take(100), code, apk.length())
    }
}
