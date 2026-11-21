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
            "databases/diary_v2.db", "files/photos/a.jpg", "files/datastore/settings_v2.preferences_pb", "files/datastore/sync_meta.preferences_pb",
            "shared_prefs/lock.xml", "no_backup/androidx.work.workdb", "cache/x", "lib/libfoo.so",
        ).forEach { File(root, it).apply { parentFile.mkdirs(); writeText("x") } }

        assertTrue(AppDataWiper.wipe(root, keep = setOf("files/datastore/sync_meta.preferences_pb")))

        val left = root.walk().filter { it.isFile }.map { it.relativeTo(root).path }.sorted().toList()
        assertEquals(listOf("files/datastore/sync_meta.preferences_pb", "lib/libfoo.so"), left)

        assertTrue(AppDataWiper.wipe(root))
        assertEquals(listOf("lib/libfoo.so"), root.walk().filter { it.isFile }.map { it.relativeTo(root).path }.toList())
        root.deleteRecursively()
    }
}
