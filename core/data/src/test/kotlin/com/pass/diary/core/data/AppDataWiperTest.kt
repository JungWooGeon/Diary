package com.pass.diary.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AppDataWiperTest {
    @Test fun wipesAllButKeptPaths() {
        val root = Files.createTempDirectory("data").toFile()
        listOf(
            "databases/diary_v2.db", "files/photos/a.jpg", "files/datastore/settings_v2.preferences_pb", "files/models/gemma.litertlm",
            "shared_prefs/lock.xml", "shared_prefs/oni_model.xml", "no_backup/androidx.work.workdb", "cache/x", "lib/libfoo.so",
        ).forEach { File(root, it).apply { parentFile.mkdirs(); writeText("x") } }

        assertTrue(AppDataWiper.wipe(root, keep = setOf("files/models", "shared_prefs/oni_model.xml")))

        val left = root.walk().filter { it.isFile }.map { it.relativeTo(root).path }.sorted().toList()
        assertEquals(listOf("files/models/gemma.litertlm", "lib/libfoo.so", "shared_prefs/oni_model.xml"), left)

        assertTrue(AppDataWiper.wipe(root))
        assertEquals(listOf("lib/libfoo.so"), root.walk().filter { it.isFile }.map { it.relativeTo(root).path }.toList())
        root.deleteRecursively()
    }
}
