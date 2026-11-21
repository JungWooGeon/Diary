package com.pass.diary.core.vision

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri

/** For tests (no Play services in JVM tests): canned answers, every tool [ready]. */
class FakeVision(
    var ready: Readiness = Readiness.READY,
    var labels: List<ImageLabel> = emptyList(),
    var text: String = "",
) : Vision {
    override suspend fun prepare(tool: VisionTool) = ready
    override suspend fun labels(image: Uri) = labels
    override suspend fun scanIntent(activity: Activity): IntentSender = error("no scanner in tests")
    override fun scannedPages(data: Intent?): List<Uri> = emptyList()
    override suspend fun readText(page: Uri) = text
    override suspend fun cutout(image: Uri): Bitmap? = null
}

class FakeHandwriting(var downloaded: Boolean = true, var result: String? = "안녕") : Handwriting {
    override val modelSizeMb = 20
    override suspend fun isDownloaded() = downloaded
    override suspend fun download() { downloaded = true }
    override suspend fun delete() { downloaded = false }
    override suspend fun recognize(strokes: List<List<InkPoint>>, width: Float, height: Float, preContext: String) = result
}
