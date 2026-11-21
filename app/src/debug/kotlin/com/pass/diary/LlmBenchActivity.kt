package com.pass.diary

import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.pass.diary.core.ai.LiteRtLmRuntime
import com.pass.diary.core.ai.LlmPrompts
import com.pass.diary.core.ai.LlmRequest
import com.pass.diary.core.ai.ModelManager
import com.pass.diary.core.ai.OniLlm
import com.pass.diary.core.ai.OutputFilter
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Debug only: on-device LLM speed on the 30 eval diaries (tools/model-eval/data/diaries.json), the reply prompt as the app
 * sends it. Logs TTFT, decode tok/s (one streamed chunk ≈ one token), total time, peak RSS (VmHWM), filter pass.
 *
 *   # model in place (debug import, no download):
 *   adb shell run-as com.pass.diary mkdir -p files/models
 *   # (piping a 2.6GB file through `adb exec-in` truncated at ~1.4GB on a Note20 — push to /data/local/tmp, then copy)
 *   adb push gemma-4-E2B-it.litertlm /data/local/tmp/
 *   adb shell run-as com.pass.diary cp /data/local/tmp/gemma-4-E2B-it.litertlm files/models/
 *   adb shell rm /data/local/tmp/gemma-4-E2B-it.litertlm   # then verify size/sha256 against shared/ai/llm/models.json
 *
 *   # measured 2026-11-21, Galaxy Note20 (SD865, 8GB), E2B: GPU init 14s, TTFT p50 1.9s, reply p50 5.0s / p90 5.5s,
 *   #   17 tok/s, peak RSS 2.1GB, no thermal throttling over 30 replies. CPU: TTFT ~8.3s, reply ~12.5s, 13 tok/s, 2.6GB.
 *   adb shell am start -n com.pass.diary/.LlmBenchActivity --es model e2b --ei n 30 [--es backend cpu]
 *   adb logcat -s LlmBench          # results also in files/bench-<model>.json
 */
@AndroidEntryPoint
class LlmBenchActivity : ComponentActivity() {
    @Inject lateinit var models: ModelManager
    @Inject lateinit var prompts: LlmPrompts
    @Inject lateinit var filter: OutputFilter
    @Inject lateinit var oni: OniLlm
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var out: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { setPadding(32, 96, 32, 32); textSize = 12f }
        setContentView(ScrollView(this).apply { addView(out) })
        val id = intent.getStringExtra("model") ?: "e2b"
        val n = intent.getIntExtra("n", 30)
        val gpu = intent.getStringExtra("backend") != "cpu"
        scope.launch { runCatching { run(id, n, gpu) }.onFailure { log("FAILED: $it"); Log.e(TAG, "bench failed", it) } }
    }

    private fun log(line: String) {
        Log.i(TAG, line)
        out.append(line + "\n")
    }

    private suspend fun run(id: String, n: Int, gpu: Boolean) {
        oni.release() // only one model in memory
        val spec = models.specs.first { it.id == id }
        val file = models.file(spec)
        check(file.exists()) { "no model at $file" }
        val diaries = Json.parseToJsonElement(assets.open("diaries.json").bufferedReader().readText()).jsonArray.take(n)
            .map { it.jsonObject["id"]!!.jsonPrimitive.content to it.jsonObject["text"]!!.jsonPrimitive.content }
        log("model=$id file=${file.length() / 1_000_000}MB diaries=${diaries.size} device=${android.os.Build.MODEL}")
        val task = prompts.reply
        val rows = mutableListOf<String>()
        LiteRtLmRuntime(file.path, cacheDir.path, tryGpu = gpu).use { rt ->
            val t0 = System.nanoTime()
            rt.warmUp()
            log("init=${ms(t0)}ms backend=${rt.backend} rss=${rssMb("VmRSS")}MB")
            for ((did, text) in diaries) {
                val start = System.nanoTime()
                var first = 0L
                var chunks = 0
                var last = ""
                withContext(Dispatchers.Default) {
                    rt.generate(LlmRequest(task.system, text, task.sampling, task.fewshot, task.schema)).collect {
                        if (chunks == 0) first = System.nanoTime()
                        chunks++; last = it
                    }
                }
                val total = ms(start)
                val ttft = if (first == 0L) -1 else (first - start) / 1_000_000
                val decodeSec = (total - ttft).coerceAtLeast(1) / 1000.0
                val fails = filter.check(task.filters, last.trim(), text)
                val row = "%s ttft=%dms total=%dms chunks=%d tok/s=%.1f chars=%d filter=%s".format(
                    did, ttft, total, chunks, (chunks - 1).coerceAtLeast(0) / decodeSec, last.length, fails.ifEmpty { "ok" },
                )
                log(row)
                rows += """{"id":"$did","ttftMs":$ttft,"totalMs":$total,"chunks":$chunks,"filter":"${fails.joinToString()}","text":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(last))}}"""
            }
        }
        log("peakRss=${rssMb("VmHWM")}MB")
        File(filesDir, "bench-$id.json").writeText("[\n" + rows.joinToString(",\n") + "\n]")
        log("DONE -> files/bench-$id.json")
    }

    private fun ms(t0: Long) = (System.nanoTime() - t0) / 1_000_000

    /** VmHWM = peak resident set of this process (model pages included). */
    private fun rssMb(key: String) = File("/proc/self/status").readLines().firstOrNull { it.startsWith(key) }
        ?.filter { it.isDigit() }?.toLongOrNull()?.div(1024) ?: -1

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private companion object { const val TAG = "LlmBench" }
}
