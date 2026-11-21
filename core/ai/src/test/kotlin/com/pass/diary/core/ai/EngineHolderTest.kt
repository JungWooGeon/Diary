package com.pass.diary.core.ai

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** LLM engine lifecycle races (review #3), with a fake "native" engine. */
class EngineHolderTest {
    private class FakeEngine(val n: Int) { @Volatile var disposed = false }

    @Test fun initFinishingAfterCloseIsDisposedNotAttached() = runBlocking {
        val initStarted = CountDownLatch(1)
        val releaseInit = CountDownLatch(1)
        val created = Collections.synchronizedList(mutableListOf<FakeEngine>())
        val h = EngineHolder(create = {
            initStarted.countDown(); releaseInit.await(5, TimeUnit.SECONDS); FakeEngine(created.size).also { created += it }
        }, dispose = { it.disposed = true })

        val warm = async(Dispatchers.Default) { runCatching { h.use { } } }
        assertTrue(initStarted.await(5, TimeUnit.SECONDS))
        h.close() // user turns 대화형 AI off while the model is still loading
        releaseInit.countDown()

        assertTrue(warm.await().exceptionOrNull() is LlmClosedException)
        assertTrue(created.single().disposed)
        // closed for good: no new engine is ever made
        assertTrue(runCatching { h.use { } }.exceptionOrNull() is LlmClosedException)
        assertEquals(1, created.size)
    }

    @Test fun closeCancelsGenerationBeforeDisposing() = runBlocking {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val generating = CompletableDeferred<Unit>()
        val disposed = CompletableDeferred<Unit>()
        val engine = FakeEngine(0)
        val h = EngineHolder(create = { engine }, dispose = { events += "dispose"; it.disposed = true; disposed.complete(Unit) })

        val gen = async(Dispatchers.Default) {
            runCatching {
                h.use { e ->
                    generating.complete(Unit)
                    try { awaitCancellation() } finally { events += "generation stopped (engine disposed=${e.disposed})" }
                }
            }
        }
        generating.await()
        h.close()

        assertTrue(gen.await().exceptionOrNull() is LlmClosedException) // an ordinary failure -> library reply
        withTimeout(5_000) { disposed.await() }
        assertEquals(listOf("generation stopped (engine disposed=false)", "dispose"), events.toList())
    }
}
