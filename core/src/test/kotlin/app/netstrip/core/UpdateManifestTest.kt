package app.netstrip.core

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateManifestTest {
    @Test
    fun readsThePublishedVersion() {
        val manifest = parseUpdateManifest(
            """
            {
              "versionCode": 2,
              "versionName": "1.1.0",
              "apkUrl": "https://example.com/netstrip.apk"
            }
            """.trimIndent(),
        )
        assertEquals(2L, manifest.versionCode)
        assertEquals("1.1.0", manifest.versionName)
        assertEquals("https://example.com/netstrip.apk", manifest.apkUrl)
        assertEquals("", manifest.notes)
    }

    @Test
    fun notesKeepLineBreaks() {
        val manifest = parseUpdateManifest(
            """{"versionCode": 7, "versionName": "1.6.0", "apkUrl": "https://example.com/a.apk", "notes": "שורה\nשנייה"}""",
        )
        assertEquals("שורה\nשנייה", manifest.notes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsANonHttpsApk() {
        parseUpdateManifest(
            """{"versionCode": 2, "versionName": "1.1.0", "apkUrl": "http://example.com/a.apk"}""",
        )
    }
}
