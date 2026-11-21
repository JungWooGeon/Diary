package com.pass.diary.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.pass.diary.core.model.Photo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/** Copies picked photos into app storage (filesDir/photos), long side ≤ 1600px, JPEG q80 (ARCHITECTURE §2.2). */
@Singleton
class PhotoStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir get() = File(context.filesDir, "photos").apply { mkdirs() }

    /** null if the image can't be read. */
    suspend fun import(uri: Uri, entryId: String): Photo? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@runCatching null
            val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
            val (w, h) = scaledSize(decoded.width, decoded.height)
            val m = Matrix().apply {
                postScale(w / decoded.width.toFloat(), h / decoded.height.toFloat())
                postRotate(rotation.toFloat())
            }
            val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
            val id = UUID.randomUUID().toString()
            val file = File(dir, "$id.jpg")
            file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            Photo(id = id, entryId = entryId, localPath = file.absolutePath, w = out.width, h = out.height)
        }.getOrNull()
    }

    fun delete(photo: Photo) {
        File(photo.localPath).takeIf { it.parentFile == dir }?.delete()
    }

    companion object {
        const val MAX_SIDE = 1600
        const val QUALITY = 80
        const val MAX_PER_ENTRY = 5

        /** Largest power of two that keeps the decoded long side ≥ [MAX_SIDE] (saves memory before the exact scale). */
        fun sampleSize(w: Int, h: Int): Int {
            var s = 1
            while (max(w, h) / (s * 2) >= MAX_SIDE) s *= 2
            return s
        }

        fun scaledSize(w: Int, h: Int): Pair<Int, Int> {
            val long = max(w, h)
            if (long <= MAX_SIDE) return w to h
            val f = MAX_SIDE / long.toFloat()
            return (w * f).roundToInt().coerceAtLeast(1) to (h * f).roundToInt().coerceAtLeast(1)
        }
    }
}
