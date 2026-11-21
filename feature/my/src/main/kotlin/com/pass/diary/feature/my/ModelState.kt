package com.pass.diary.feature.my

import kotlinx.coroutines.flow.StateFlow

/** One downloadable on-device model card on "온이 깨우기" (shared/ai/llm/models.json). */
data class ModelCard(
    val id: String,
    val title: String,
    val modelName: String,
    val sizeBytes: Long,
    val minRamGb: Int,
    val premium: Boolean,
    val state: ModelState,
)

sealed interface ModelState {
    /** This phone can't run it (RAM below [ModelCard.minRamGb]). */
    data class Unsupported(val deviceRamGb: Int) : ModelState
    data object NotDownloaded : ModelState
    data class Downloading(val downloadedBytes: Long, val etaSec: Long?, val paused: Boolean) : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

/** Seam for workstream D (core:ai ModelManager). The app binds a stub until integration. */
interface ModelStateProvider {
    val models: StateFlow<List<ModelCard>>
    val wifiOnly: StateFlow<Boolean>
    val deviceRamGb: Int
    fun download(id: String)
    fun pause(id: String)
    fun cancel(id: String)
    fun delete(id: String)
    fun setWifiOnly(on: Boolean)
}
