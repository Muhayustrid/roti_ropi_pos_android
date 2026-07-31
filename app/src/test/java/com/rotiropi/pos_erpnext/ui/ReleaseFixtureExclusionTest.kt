package com.rotiropi.pos_erpnext.ui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseFixtureExclusionTest {

    @Test
    fun preview_fixtures_exist_only_in_debug_or_test_sources() {
        val projectRoot = findProjectRoot()
        val previewFiles = Files.walk(projectRoot.resolve("app/src")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().contains("Preview") }
                .collect(Collectors.toList())
        }

        assertTrue("Task 2B must provide at least one debug preview", previewFiles.isNotEmpty())
        assertTrue(previewFiles.all { path ->
            val relative = projectRoot.relativize(path).toString().replace('\\', '/')
            relative.startsWith("app/src/debug/") ||
                relative.startsWith("app/src/test/") ||
                relative.startsWith("app/src/androidTest/")
        })
    }

    @Test
    fun populated_demo_fixtures_never_reach_main_or_release_sources() {
        val projectRoot = findProjectRoot()
        val shippedSources = listOf("app/src/main", "app/src/release").map(projectRoot::resolve)

        shippedSources.forEach { root ->
            Files.walk(root).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                    .forEach { path ->
                        val source = Files.readAllLines(path).joinToString("\n")
                        assertTrue(
                            "$path must not declare a populated demo fixture",
                            !source.contains("demoData = true"),
                        )
                    }
            }
        }

        val releaseDemo = projectRoot.resolve(
            "app/src/release/java/com/rotiropi/pos_erpnext/ui/demo/PosDemoStates.kt"
        )
        assertTrue("Release must supply its own demo stub", Files.exists(releaseDemo))
        assertTrue(
            "Release demo stub must stay unsupported",
            Files.readAllLines(releaseDemo).joinToString("\n").contains("supported = false"),
        )
    }

    private fun findProjectRoot(): Path {
        var path = Paths.get("").toAbsolutePath()
        while (!Files.exists(path.resolve("settings.gradle.kts"))) {
            path = path.parent ?: error("Could not find project root")
        }
        return path
    }
}
