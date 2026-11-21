package com.pass.diary.core.ai

import java.io.File

/** Repo `shared/` as the app's assets (unit tests run with cwd = core/ai); test resources override for fixtures. */
object TestAssets : AssetSource {
    private val shared = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "shared") }.first { File(it, "ai").isDirectory }

    override fun bytes(path: String): ByteArray? = File(shared, path).takeIf { it.exists() }?.readBytes()

    fun resource(path: String): String = javaClass.classLoader!!.getResource(path)!!.readText()
}
