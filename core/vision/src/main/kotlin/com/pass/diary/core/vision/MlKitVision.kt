package com.pass.diary.core.vision

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.OptionalModuleApi
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

@Singleton
class MlKitVision @Inject constructor(@ApplicationContext private val context: Context) : Vision {
    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }
    private val reader by lazy { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) }
    private val segmenter by lazy { SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()) }
    private val scanner by lazy {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(5)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE).build(),
        )
    }

    override suspend fun prepare(tool: VisionTool): Readiness = runCatching {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) return Readiness.UNSUPPORTED
        val apis: List<OptionalModuleApi> = when (tool) {
            VisionTool.PHOTO_TAGS -> listOf(labeler)
            VisionTool.PAPER -> listOf(scanner, reader)
            VisionTool.STICKER -> listOf(segmenter)
        }
        val modules = ModuleInstall.getClient(context)
        if (modules.areModulesAvailable(*apis.toTypedArray()).await().areModulesAvailable()) return Readiness.READY
        // Play downloads it in the background; the next prepare() sees it.
        modules.installModules(ModuleInstallRequest.newBuilder().apply { apis.forEach { addApi(it) } }.build())
        Readiness.PREPARING
    }.getOrElse { Log.w(TAG, "prepare $tool", it); Readiness.UNSUPPORTED }

    override suspend fun labels(image: Uri): List<ImageLabel> =
        labeler.process(InputImage.fromFilePath(context, image)).await().map { ImageLabel(it.text, it.confidence) }

    override suspend fun scanIntent(activity: Activity): IntentSender = scanner.getStartScanIntent(activity).await()

    override fun scannedPages(data: Intent?): List<Uri> =
        GmsDocumentScanningResult.fromActivityResultIntent(data)?.pages?.map { it.imageUri }.orEmpty()

    override suspend fun readText(page: Uri): String = reader.process(InputImage.fromFilePath(context, page)).await().text

    override suspend fun cutout(image: Uri): Bitmap? {
        val src = withContext(Dispatchers.IO) { decode(image, MAX_STICKER_SOURCE) } ?: return null
        val fg = segmenter.process(InputImage.fromBitmap(src, 0)).await().foregroundBitmap ?: return null
        return withContext(Dispatchers.Default) { cropToOpaque(fg) }
    }

    /** Long side ≤ [maxSide], EXIF rotation applied (camera photos are stored sideways). */
    private fun decode(uri: Uri, maxSide: Int): Bitmap? {
        val r = context.contentResolver
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        r.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, b) }
        if (b.outWidth <= 0) return null
        var sample = 1
        while (max(b.outWidth, b.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = r.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val rot = r.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val scale = (maxSide / max(bmp.width, bmp.height).toFloat()).coerceAtMost(1f)
        if (rot == 0 && scale == 1f) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postScale(scale, scale); postRotate(rot.toFloat()) }, true)
    }

    private companion object {
        const val TAG = "MlKitVision"
        const val MAX_STICKER_SOURCE = 1600
    }
}

/** The subject's bounding box (alpha > 0) plus a small margin; null when the mask is empty. */
internal fun cropToOpaque(bmp: Bitmap): Bitmap? {
    val w = bmp.width; val h = bmp.height
    val row = IntArray(w)
    var l = w; var t = h; var r = -1; var b = -1
    for (y in 0 until h) {
        bmp.getPixels(row, 0, w, 0, y, w, 1)
        for (x in 0 until w) if (row[x] ushr 24 != 0) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; b = y }
    }
    if (r < 0) return null
    val m = (max(r - l, b - t) * 0.03f).toInt()
    l = (l - m).coerceAtLeast(0); t = (t - m).coerceAtLeast(0); r = (r + m).coerceAtMost(w - 1); b = (b + m).coerceAtMost(h - 1)
    return Bitmap.createBitmap(bmp, l, t, r - l + 1, b - t + 1)
}

@Singleton
class MlKitHandwriting @Inject constructor() : Handwriting {
    private val model = DigitalInkRecognitionModel.builder(DigitalInkRecognitionModelIdentifier.fromLanguageTag("ko")!!).build()
    private val models = RemoteModelManager.getInstance()
    private val recognizer by lazy { DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build()) }

    /** Measured on the emulator: files under no_backup/com.google.mlkit.downloads after download(). */
    override val modelSizeMb = MODEL_MB

    override suspend fun isDownloaded() = runCatching { models.isModelDownloaded(model).await() }.getOrDefault(false)
    override suspend fun download() { models.download(model, DownloadConditions.Builder().build()).await() }
    override suspend fun delete() { runCatching { models.deleteDownloadedModel(model).await() } }

    override suspend fun recognize(strokes: List<List<InkPoint>>, width: Float, height: Float, preContext: String): String? {
        if (strokes.none { it.isNotEmpty() }) return null
        val ink = Ink.builder().apply {
            strokes.filter { it.isNotEmpty() }.forEach { s -> addStroke(Ink.Stroke.builder().apply { s.forEach { addPoint(Ink.Point.create(it.x, it.y, it.t)) } }.build()) }
        }.build()
        val ctx = RecognitionContext.builder().setPreContext(preContext.takeLast(20)).setWritingArea(WritingArea(width, height)).build()
        return recognizer.recognize(ink, ctx).await().candidates.firstOrNull()?.text
    }

    companion object { const val MODEL_MB = 25 } // ko: 21.3MB fst + 3.8MB tflite + spec (2026-11-22)
}

/** Play services Task -> suspend (kotlinx-coroutines-play-services would be one more dependency for these few lines). */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class VisionModule {
    @Binds abstract fun vision(impl: MlKitVision): Vision
    @Binds abstract fun handwriting(impl: MlKitHandwriting): Handwriting
}
