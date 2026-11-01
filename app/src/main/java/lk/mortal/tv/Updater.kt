package lk.mortal.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

class Updater(private val activity: Activity) {
    data class Release(val code: Int, val name: String, val notes: String, val url: String, val sha256: String, val minSdk: Int)
    fun check(repo: String): Release {
        require(repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")))
        val j = JSONObject(Network.read("https://github.com/$repo/releases/latest/download/update.json", 256 * 1024))
        val release = Release(j.getInt("versionCode"), j.getString("versionName"), j.optString("notes"), j.getString("apkUrl"), j.getString("sha256"), j.getInt("minSdk"))
        require(release.url.startsWith("https://github.com/$repo/releases/download/") && release.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid release metadata" }
        require(release.minSdk <= android.os.Build.VERSION.SDK_INT) { "This update requires a newer Android version" }
        return release
    }
    fun download(release: Release, cancelled: AtomicBoolean, progress: (Int) -> Unit): File {
        require(release.code > BuildConfig.VERSION_CODE) { "Update is not newer" }
        val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
        val partial = File(dir, "download.part"); val file = File(dir, "mortal-tv.apk")
        val connection = Network.open(release.url)
        try {
            val total = connection.contentLengthLong; require(total <= 200L * 1024 * 1024) { "APK too large" }
            val digest = MessageDigest.getInstance("SHA-256"); var count = 0L
            connection.inputStream.use { input -> partial.outputStream().use { out ->
                val buffer = ByteArray(65536)
                while (true) {
                    check(!cancelled.get()) { "Download cancelled" }
                    val n = input.read(buffer); if (n < 0) break
                    count += n; require(count <= 200L * 1024 * 1024) { "APK too large" }
                    digest.update(buffer, 0, n); out.write(buffer, 0, n)
                    progress(if (total > 0) (100 * count / total).toInt().coerceIn(0, 100) else 0)
                }
            } }
            require(digest.digest().joinToString("") { "%02x".format(it) }.equals(release.sha256, true)) { "Checksum mismatch; download discarded" }
            verify(partial, release.code)
            file.delete(); require(partial.renameTo(file)) { "Could not finalize update" }; return file
        } finally { connection.disconnect(); partial.delete() }
    }
    @Suppress("DEPRECATION")
    private fun verify(file: File, code: Int) {
        val pm = activity.packageManager
        val flags = if (android.os.Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = pm.getPackageInfo(activity.packageName, flags)
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: error("Unreadable APK")
        val archiveCode = if (android.os.Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        val currentCerts = if (android.os.Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        val updateCerts = if (android.os.Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners else archive.signatures
        UpdatePolicy.verifyPackage(activity.packageName, archive.packageName.orEmpty(), BuildConfig.VERSION_CODE.toLong(), code.toLong(), archiveCode, currentCerts.orEmpty().map { it.toCharsString() }.toSet(), updateCerts.orEmpty().map { it.toCharsString() }.toSet())
    }
    fun install(file: File, fromPc: Boolean = false) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity).setTitle(if (fromPc) "Allow APK installation" else "Allow app updates").setMessage("Enable 'Allow from this source' for Mortal TV, then return and select " + if (fromPc) "Settings > APK from PC > Install." else "Install downloaded update.")
                .setPositiveButton("Open permission settings") { _, _ -> activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))) }
                .setNegativeButton("Later", null).show(); return
        }
        try { val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file); activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        catch (_: Exception) { AlertDialog.Builder(activity).setMessage("Android could not open its package installer.").setPositiveButton("OK", null).show() }
    }
}
