package com.pass.diary.core.ai

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * Wiring. Whether "온이 깨우기" is ON is a user setting owned by the app: feature code passes it through
 * [OniLlm.enabled]; the LLM engine exists only when that is true and a model file is installed.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides @Singleton fun assets(@ApplicationContext c: Context): AssetSource = AndroidAssets(c)
    @Provides @Singleton fun crisis(a: AssetSource) = CrisisDetector(a.json("ai/crisis.json")!!.obj)
    @Provides @Singleton fun classifier(a: AssetSource) = EmotionClassifier.load(a)
    @Provides @Singleton fun tags(a: AssetSource) = TagExtractor(a.json("ai/tags.json")!!.obj)
    @Provides @Singleton fun template(a: AssetSource) =
        TemplateReplyEngine(a.json("library/replies.json")!!.arr, a.json("library/questions.json")?.arr)
    @Provides @Singleton fun prompts(a: AssetSource) = LlmPrompts(a.json("ai/llm/prompts.json")!!.obj)
    @Provides @Singleton fun filter(a: AssetSource) = OutputFilter(
        a.json("ai/llm/filters.json")!!.obj,
        people = a.json("ai/tags.json")!!.obj.objects("people").map { listOf(it.str("tag")!!) + it.strings("aliases") },
    )
    @Provides @Singleton fun models(a: AssetSource, @ApplicationContext c: Context) =
        ModelManager(ModelSpec.parse(a.json("ai/llm/models.json")!!.arr), File(c.filesDir, "models"), AndroidDeviceInfo(c))
    @Provides @Singleton fun oniLlm(m: ModelManager, p: LlmPrompts, f: OutputFilter, t: TemplateReplyEngine, @ApplicationContext c: Context) =
        OniLlm(m, p, f, t, c.cacheDir.path)
    @Provides @Singleton fun pipeline(cr: CrisisDetector, cl: EmotionClassifier, tg: TagExtractor, t: TemplateReplyEngine, o: OniLlm) =
        AiPipeline(cr, cl, tg, t) { o.engine() }

    // Interfaces the feature modules depend on
    @Provides fun replyProvider(p: AiPipeline): ReplyProvider = p
    @Provides fun emotionSuggester(p: AiPipeline): EmotionSuggester = p
    @Provides @Singleton fun chat(o: OniLlm, t: TemplateReplyEngine, cr: CrisisDetector): ChatResponder = OniChat({ o.engine() }, t, cr)
    @Provides fun modelState(o: OniLlm): ModelStateProvider = o
}

/**
 * Holds the one loaded LLM (2.6–3.7 GB mapped) so every screen shares it, and is the [ModelStateProvider].
 * [enabled] mirrors 설정 › AI › 대화형 AI (the app's Startup keeps it in step with DataStore).
 */
class OniLlm(
    private val models: ModelManager,
    private val prompts: LlmPrompts,
    private val filter: OutputFilter,
    private val template: TemplateReplyEngine,
    private val cacheDir: String,
) : ModelStateProvider {
    // On by default: a downloaded model is "깨어 있음" until the user turns 대화형 AI off.
    @Volatile var enabled: Boolean = true
        private set

    override fun state() = ModelState(models.specs, models.supported(), models.installed(), enabled, models.wifiRecommended)
    override fun gate(spec: ModelSpec) = models.gate(spec)
    override fun download(spec: ModelSpec) = models.download(spec)
    override fun delete(spec: ModelSpec) { release(); models.delete(spec) }
    override fun setEnabled(on: Boolean) { enabled = on; if (!on) release() }
    private var loaded: Pair<String, LlmReplyEngine>? = null
    private var runtime: LlmRuntime? = null

    @Synchronized fun engine(): LlmReplyEngine? {
        val spec = models.installed()?.takeIf { enabled } ?: return release()
        loaded?.takeIf { it.first == spec.id }?.let { return it.second }
        release()
        // An emulator's "GPU" is a software WebGPU, far slower than its CPU (measured: init 65 s, no token in 3 min).
        val emulator = android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.PRODUCT.startsWith("sdk")
        val rt = LiteRtLmRuntime(models.file(spec).path, cacheDir, tryGpu = !emulator).also { runtime = it }
        return LlmReplyEngine(rt, prompts, filter, template).also { loaded = spec.id to it }
    }

    /** Call when the user turns the model off or deletes it. */
    @Synchronized fun release(): LlmReplyEngine? { runtime?.close(); runtime = null; loaded = null; return null }
}
