package lk.mortal.tv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.io.File

object Logos {
    private val memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) { override fun sizeOf(key: String, value: Bitmap) = value.byteCount }
    fun load(view: ImageView, url: String) {
        view.tag = url; view.setImageResource(R.drawable.icon)
        if (!Playlist.safeUrl(url)) return
        memory.get(url)?.let { view.setImageBitmap(it); return }
        val cache = File(view.context.cacheDir, "logos")
        Network.io.execute {
            runCatching {
                cache.mkdirs(); val file = File(cache, Playlist.identity(url))
                if (!file.exists()) {
                    val c = Network.open(url)
                    try { c.inputStream.use { input ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= 1024 * 1024); out.write(buffer, 0, n) }
                        file.writeBytes(out.toByteArray())
                    } } finally { c.disconnect() }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, bounds)
                require(bounds.outWidth in 1..12000 && bounds.outHeight in 1..12000)
                val opts = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 160).coerceAtLeast(1) }
                val bitmap = BitmapFactory.decodeFile(file.path, opts) ?: error("Invalid image")
                memory.put(url, bitmap)
                view.post { if (view.tag == url) view.setImageBitmap(bitmap) }
                if (cache.listFiles().orEmpty().sumOf { it.length() } > 32L * 1024 * 1024) cache.listFiles().orEmpty().sortedBy { it.lastModified() }.take(20).forEach { it.delete() }
            }
        }
    }
    fun clearMemory() { memory.evictAll() }
}
