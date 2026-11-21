package com.pass.diary.feature.report

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Instagram story size. The card is rendered at this size off screen (YearLightScreen), not upscaled. */
internal const val STORY_W = 1080
internal const val STORY_H = 1920

internal fun Bitmap.toStory(): Bitmap = if (width == STORY_W && height == STORY_H) this else Bitmap.createScaledBitmap(this, STORY_W, STORY_H, true)

/** Saves to Pictures/온이 일기 (no permission on API 29+). Returns false on API < 29: the caller shares instead. */
internal suspend fun saveToGallery(ctx: Context, bmp: Bitmap, name: String): Boolean = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false // ponytail: pre-10 would need WRITE_EXTERNAL_STORAGE; share sheet covers it
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/온이 일기")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val r = ctx.contentResolver
    val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
    runCatching {
        r.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        r.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }.onFailure { r.delete(uri, null, null) }.isSuccess
}

/** Share sheet with the PNG (cache/share/, served by the app's FileProvider "<appId>.files"). */
internal suspend fun shareImage(ctx: Context, bmp: Bitmap, name: String) {
    val file = withContext(Dispatchers.IO) {
        File(ctx.cacheDir, "share").apply { mkdirs() }.resolve("$name.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    // ClipData carries the read grant to the chooser too (its image preview), not only to the picked app.
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = android.content.ClipData.newRawUri("", uri)
    ctx.startActivity(Intent.createChooser(send, "올해의 빛 공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
}
