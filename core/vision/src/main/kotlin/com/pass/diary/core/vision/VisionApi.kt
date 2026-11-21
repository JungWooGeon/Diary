package com.pass.diary.core.vision

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri

/** What the editor offers; each needs its own Play services module (downloaded by Play, not shipped in the APK). */
enum class VisionTool { PHOTO_TAGS, PAPER, STICKER }

enum class Readiness {
    READY,
    /** Play services is still downloading the module (just started by [Vision.prepare]): try again in a moment. */
    PREPARING,
    /** No Play services / module not offered on this phone. */
    UNSUPPORTED,
}

data class ImageLabel(val text: String, val confidence: Float)

/** ML Kit on device. Results are suggestions: the UI always asks before anything goes into the entry. */
interface Vision {
    /** Never throws. A missing module starts downloading and reports [Readiness.PREPARING]. */
    suspend fun prepare(tool: VisionTool): Readiness

    /** Image Labeling (English labels); [PhotoTags] turns them into Korean tag chips. */
    suspend fun labels(image: Uri): List<ImageLabel>

    /** Document Scanner UI; launch with StartIntentSenderForResult and read the result with [scannedPages]. */
    suspend fun scanIntent(activity: Activity): IntentSender
    fun scannedPages(data: Intent?): List<Uri>

    /** Text Recognition v2, Korean script. */
    suspend fun readText(page: Uri): String

    /** Subject Segmentation: the people/things only, transparent elsewhere, cropped to them. null = nothing found. */
    suspend fun cutout(image: Uri): Bitmap?
}

data class InkPoint(val x: Float, val y: Float, val t: Long)

/** Digital Ink Recognition, Korean. The model is not in the APK: [download] after the user agrees to [modelSizeMb]. */
interface Handwriting {
    val modelSizeMb: Int
    suspend fun isDownloaded(): Boolean
    /** Throws when it fails (offline, no space). */
    suspend fun download()
    suspend fun delete()
    /** Best candidate for the strokes drawn in a [width]×[height] area; [preContext] = text right before it. */
    suspend fun recognize(strokes: List<List<InkPoint>>, width: Float, height: Float, preContext: String = ""): String?
}
