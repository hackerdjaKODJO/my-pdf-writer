package com.hackerdjakodjo.mypdfwriter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CustomTemplatePathsTest {

    @Test
    fun sanitize_acceptsPortableImageFileNames() {
        val accepted = listOf(
            "tpl-4f0c2a.png",
            "photo_01.jpg",
            "scan-2.JPEG",
            "background.webp",
        )
        accepted.forEach { name ->
            assertEquals(name, CustomTemplatePaths.sanitizeKey(name))
        }
        assertTrue(CustomTemplatePaths.newKey().startsWith(CustomTemplatePaths.FILE_PREFIX))
        assertTrue(CustomTemplatePaths.newKey().endsWith(".png"))
    }

    @Test
    fun sanitize_rejectsAbsoluteTraversalNestedAndUnknownTypes() {
        val rejected = listOf(
            null,
            "",
            "   ",
            ".",
            "..",
            "../escape.png",
            "..\\escape.png",
            "files/templates/a.png",
            "/data/data/com.example/files/templates/a.png",
            "C:\\templates\\a.png",
            "C:/templates/a.png",
            "a.png\u0000",
            ".hidden.png",
            "tpl-a.gif",
            "tpl-a.pdf",
            "tpl-a",
            "tpl-a.png/../b.png",
            "a".repeat(130) + ".png",
        )
        rejected.forEach { name ->
            assertNull("should reject: $name", CustomTemplatePaths.sanitizeKey(name))
        }
    }

    @Test
    fun fileIn_resolvesOnlyInsideTheTemplateDirectory() {
        val root = Files.createTempDirectory("template-paths").toFile()
        try {
            val file = CustomTemplatePaths.fileIn(root, "tpl-a.png")
            assertNotNull(file)
            assertEquals(root.canonicalFile, file?.parentFile)
            assertNull(CustomTemplatePaths.fileIn(root, "../escape.png"))
            assertNull(CustomTemplatePaths.fileIn(root, "/tmp/escape.png"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun names_areTrimmedBoundedAndDerivedWithoutExtensions() {
        assertEquals("강의 배경", CustomTemplatePaths.normalizeName("  강의   배경  "))
        assertEquals(CustomTemplatePaths.DEFAULT_NAME, CustomTemplatePaths.normalizeName("   "))
        assertEquals("biology", CustomTemplatePaths.displayNameFrom("biology.pdf"))
        assertEquals("photo", CustomTemplatePaths.displayNameFrom("/sdcard/photo.png"))
        assertEquals(80, CustomTemplatePaths.normalizeName("가".repeat(100)).length)
        assertFalse(CustomTemplatePaths.extensionOf("tpl-a.PNG").isBlank())
        assertEquals("png", CustomTemplatePaths.extensionOf("tpl-a.PNG"))
    }

    @Test
    fun formats_detectMagicInsteadOfProviderMime() {
        assertEquals(
            CustomTemplateFormat.PNG,
            CustomTemplateFormats.sniff(
                byteArrayOf(
                    0x89.toByte(),
                    0x50.toByte(),
                    0x4E.toByte(),
                    0x47.toByte(),
                    0x0D.toByte(),
                    0x0A.toByte(),
                    0x1A.toByte(),
                    0x0A.toByte(),
                ),
            ),
        )
        assertEquals(
            CustomTemplateFormat.JPEG,
            CustomTemplateFormats.sniff(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())),
        )
        assertEquals(
            CustomTemplateFormat.WEBP,
            "RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray(Charsets.US_ASCII)
                .let(CustomTemplateFormats::sniff),
        )
        assertEquals(
            CustomTemplateFormat.PDF,
            "%PDF-1.7".toByteArray(Charsets.US_ASCII).let(CustomTemplateFormats::sniff),
        )
        assertNull(CustomTemplateFormats.sniff("GIF89a".toByteArray(Charsets.US_ASCII)))
        assertNull(CustomTemplateFormats.sniff(byteArrayOf()))
        assertFalse(CustomTemplateFormats.isImage(CustomTemplateFormat.PDF))
        assertTrue(CustomTemplateFormats.isImage(CustomTemplateFormat.WEBP))
    }

    @Test
    fun keysInPages_ignoresInvalidLegacyValues() {
        val pages = listOf(
            NotePage(customBackground = "tpl-a.png"),
            NotePage(customBackground = "../escape.png"),
            NotePage(customBackground = null),
        )
        assertEquals(setOf("tpl-a.png"), CustomTemplatePaths.keysInPages(pages))
    }
}
