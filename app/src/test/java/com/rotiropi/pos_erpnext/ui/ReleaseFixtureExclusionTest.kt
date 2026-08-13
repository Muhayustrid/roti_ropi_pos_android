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

    /**
     * The demo layout toggle and its fixtures were deleted: Products and Reports had no data
     * source, so the only thing that ever populated them was synthetic. This keeps a demo
     * fixture from reappearing in code that ships, whether or not a `release/` source set
     * exists to stub it out.
     */
    @Test
    fun no_demo_fixture_survives_in_shipped_sources() {
        val projectRoot = findProjectRoot()
        val shippedSources = listOf("app/src/main", "app/src/release")
            .map(projectRoot::resolve)
            .filter(Files::exists)

        assertTrue("app/src/main must exist", shippedSources.isNotEmpty())
        shippedSources.forEach { root ->
            Files.walk(root).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                    .forEach { path ->
                        val source = Files.readAllLines(path).joinToString("\n")
                        listOf("demoData", "PosDemoStates").forEach { marker ->
                            assertTrue(
                                "$path must not reference $marker",
                                !source.contains(marker),
                            )
                        }
                    }
            }
        }
    }

    private fun findProjectRoot(): Path {
        var path = Paths.get("").toAbsolutePath()
        while (!Files.exists(path.resolve("settings.gradle.kts"))) {
            path = path.parent ?: error("Could not find project root")
        }
        return path
    }
}
