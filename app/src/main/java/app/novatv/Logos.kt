package app.novatv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Channel logos uploaded by the viewer (Settings -> CHANNEL LOGOS).
 * Files live in the app's private storage, one PNG per channel id, so they survive library syncs and updates.
 */
object Logos {
    private var dir: File? = null
    private val cache = HashMap<String, ImageBitmap?>()
    private val main = Handler(Looper.getMainLooper())

    /** Read by composables so they recompose when a logo is added / removed. */
    val version = mutableIntStateOf(0)

    fun init(ctx: Context) {
        if (dir == null) {
            val d = File(ctx.applicationContext.filesDir, "logos")
            d.mkdirs()
            dir = d
        }
    }

    private fun safe(id: String): String = id.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun fileOf(id: String): File? = dir?.let { File(it, safe(id) + ".png") }

    fun has(id: String): Boolean = fileOf(id)?.exists() == true

    fun file(id: String): File? = fileOf(id)?.takeIf { it.exists() }

    /** The uploaded logo for [id], or null. Call from a composable together with [version]. */
    @Synchronized
    fun get(id: String): ImageBitmap? {
        if (cache.containsKey(id)) return cache[id]
        val f = file(id)
        val bmp = try {
            if (f == null) null else BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
        } catch (e: Throwable) {
            null
        }
        cache[id] = bmp
        return bmp
    }

    /** Decode [bytes] (PNG / JPG / WebP), shrink to at most 512 px and store as PNG. Returns false if it is not an image. */
    @Synchronized
    fun save(id: String, bytes: ByteArray): Boolean {
        val target = fileOf(id) ?: return false
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return false
            val longest = maxOf(bmp.width, bmp.height)
            val out = if (longest > 512) {
                val k = 512f / longest
                Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
            } else bmp
            target.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cache.remove(id)
            bump()
            true
        } catch (e: Throwable) {
            false
        }
    }

    @Synchronized
    fun remove(id: String) {
        fileOf(id)?.delete()
        cache.remove(id)
        bump()
    }

    private fun bump() {
        main.post { version.intValue = version.intValue + 1 }
    }
}
