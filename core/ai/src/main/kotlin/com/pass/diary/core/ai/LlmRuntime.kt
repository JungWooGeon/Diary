package com.pass.diary.core.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class Sampling(val temperature: Double, val topK: Int = 40, val topP: Double = 0.95, val maxTokens: Int = 256)

data class LlmRequest(
    val system: String,
    val user: String,
    val sampling: Sampling,
    val fewshot: List<Pair<String, String>> = emptyList(),
    /** JSON schema string -> constrained decoding (LiteRT-LM ResponseFormat.json). */
    val jsonSchema: String? = null,
)

/** On-device LLM. [generate] emits the text so far (cumulative), then completes. Faked in unit tests. */
interface LlmRuntime : AutoCloseable {
    fun generate(request: LlmRequest): Flow<String>
    /** Load the model ahead of the first [generate] (LiteRT-LM init takes ~10 s). */
    suspend fun warmUp() {}
}

/**
 * LiteRT-LM (com.google.ai.edge.litertlm:litertlm-android 0.19.1; API in core/ai/DEPS.md).
 * One Engine per model file (init ~10 s, lazily on first use, off the main thread); one Conversation per request
 * because the sampler is fixed per conversation. One generation at a time.
 */
class LiteRtLmRuntime(
    private val modelPath: String,
    private val cacheDir: String?,
    /** false = CPU only (benchmarks; emulators whose "GPU" is a software WebGPU). */
    private val tryGpu: Boolean = true,
) : LlmRuntime {
    /** "GPU" / "CPU" once loaded. */
    var backend: String? = null
        private set

    private val holder = EngineHolder(create = {
        // GPU first (much faster on recent phones), CPU if the device has no usable OpenCL.
        (if (tryGpu) runCatching { Engine(EngineConfig(modelPath, backend = Backend.GPU(), cacheDir = cacheDir)).also { it.initialize(); backend = "GPU" } }.getOrNull() else null)
            ?: Engine(EngineConfig(modelPath, backend = Backend.CPU(), cacheDir = cacheDir)).also { it.initialize(); backend = "CPU" }
    }, dispose = { it.close() })

    override fun generate(request: LlmRequest): Flow<String> = channelFlow {
        holder.use { engine ->
            val s = request.sampling
            val config = ConversationConfig(
                systemInstruction = Contents.of(request.system),
                initialMessages = request.fewshot.flatMap { (u, a) -> listOf(Message.user(u), Message.model(a)) },
                samplerConfig = SamplerConfig(topK = s.topK, topP = s.topP, temperature = s.temperature.coerceAtLeast(0.0)),
                maxOutputToken = s.maxTokens,
                thinkingConfig = ThinkingConfig(enableThinking = false), // Gemma 4 otherwise "thinks" on short/JSON inputs
                enableResponseFormat = request.jsonSchema != null,
            )
            engine.createConversation(config).use { conv ->
                var acc = ""
                try {
                    conv.sendMessageAsync(request.user, responseFormat = request.jsonSchema?.let { ResponseFormat.json(it) })
                        .collect { m ->
                            val chunk = m.toString()
                            // chunks are documented as streamed pieces; accept both delta and cumulative to be safe
                            acc = if (chunk.startsWith(acc) && acc.isNotEmpty()) chunk else acc + chunk
                            send(acc)
                        }
                } finally {
                    conv.cancelProcess() // no-op when finished; stops native decoding on timeout/cancel/close
                }
            }
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun warmUp() = withContext(Dispatchers.Default) { holder.use { } }

    /** Non-blocking: cancels a running generation, then frees the engine once it has stopped. */
    override fun close() = holder.close()
}

class LlmClosedException : IllegalStateException("LLM runtime closed")

/**
 * The one native engine and its lifecycle. [use] runs under [lock], creating the engine on first use (seconds). [close]
 * marks the holder closed, cancels the running [use] block, then disposes under the same lock — so an engine is never
 * freed mid-generation, and one whose init finishes after [close] is disposed instead of attached.
 */
internal class EngineHolder<E : Any>(
    private val create: () -> E,
    private val dispose: (E) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val lock = Mutex()
    private var engine: E? = null
    @Volatile private var closed = false
    @Volatile private var running: Job? = null

    suspend fun <R> use(block: suspend (E) -> R): R = lock.withLock {
        if (closed) throw LlmClosedException()
        val e = engine ?: create().also { if (closed) { dispose(it); throw LlmClosedException() }; engine = it }
        coroutineScope {
            val job = async { block(e) }
            running = job
            if (closed) job.cancel()
            try {
                job.await()
            } catch (c: CancellationException) {
                ensureActive() // the caller itself was cancelled: propagate that
                throw LlmClosedException() // cancelled by close(): an ordinary failure for the caller (falls back to the library)
            } finally {
                running = null
            }
        }
    }

    fun close() {
        closed = true
        running?.cancel()
        scope.launch { lock.withLock { engine?.let(dispose); engine = null } }
    }
}
