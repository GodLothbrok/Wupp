package com.wa.beastclient.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainViewModelStartupSafetyTest {

    @Test
    fun resolveEffectiveMediaMimeTypePrefersConcreteTypeOverWildcardOrOctetStream() {
        assertEquals("image/png", resolveEffectiveMediaMimeType("*/*", "image/png"))
        assertEquals("video/mp4", resolveEffectiveMediaMimeType("video/*", "video/mp4"))
        assertEquals("application/pdf", resolveEffectiveMediaMimeType("application/octet-stream", "application/pdf"))
        assertEquals("image/jpeg", resolveEffectiveMediaMimeType(null, "image/jpeg"))
        assertEquals("image/*", resolveEffectiveMediaMimeType("image/*", null))
        assertEquals("application/octet-stream", resolveEffectiveMediaMimeType("*/*", null))
        assertEquals("image/gif", resolveEffectiveMediaMimeType("image/gif", "image/jpeg"))
    }

    @Test
    fun mainViewModelDeclaresAllInitReferencedFieldsBeforeInitBlock() {
        val candidates = listOf(
            File("src/main/java/com/wa/beastclient/ui/MainViewModel.kt"),
            File("app/src/main/java/com/wa/beastclient/ui/MainViewModel.kt")
        )
        val sourceFile = candidates.firstOrNull { it.isFile } ?: return
        val lines = sourceFile.readLines()
        val initStartIdx = lines.indexOfFirst { it.trim() == "init {" }
        assertTrue("Expected init block in MainViewModel.kt", initStartIdx >= 0)

        var depth = 0
        var initEndIdx = -1
        for (i in initStartIdx until lines.size) {
            for (ch in lines[i]) {
                if (ch == '{') depth++
                else if (ch == '}') depth--
            }
            if (depth == 0) {
                initEndIdx = i
                break
            }
        }
        assertTrue("Expected matching brace for init block", initEndIdx > initStartIdx)

        val initBody = lines.subList(initStartIdx, initEndIdx + 1).joinToString("\n")
        val afterInitDecls = lines.subList(initEndIdx + 1, lines.size)
            .mapIndexedNotNull { offset, line ->
                val trimmed = line.trimStart()
                if (!line.startsWith("    ") || line.startsWith("        ")) return@mapIndexedNotNull null
                val m = Regex("""^(?:private\s+|internal\s+|protected\s+)?(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\b""").find(trimmed)
                m?.groupValues?.get(1)?.let { name -> name to (initEndIdx + 2 + offset) }
            }
        assertTrue("Expected property declarations after init block in MainViewModel.kt", afterInitDecls.isNotEmpty())
        for ((field, lineNum) in afterInitDecls) {
            val referencedInInit = Regex("""\b$field\b""").containsMatchIn(initBody)
            assertTrue(
                "Field $field (line $lineNum) is referenced in init {} (lines ${initStartIdx + 1}-${initEndIdx + 1}) before it is initialized",
                !referencedInInit
            )
        }
    }
}
