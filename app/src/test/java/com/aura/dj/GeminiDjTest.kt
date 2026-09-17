package com.aura.dj

import com.aura.music.data.gemini.DjAdjustmentType
import com.aura.music.data.gemini.DjSessionContext
import com.aura.music.data.gemini.GeminiDjService
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeminiDjTest {

    private lateinit var geminiDjService: GeminiDjService

    @Before
    fun setup() {
        geminiDjService = GeminiDjService(OkHttpClient())
    }

    @Test
    fun testParseGeminiResponse_ValidJson() {
        val simulatedResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\n  \"dj_shoutout\": \"¡Cadencia a tope y mente en la carretera!\",\n  \"dj_comment\": \"Metemos 165 a 172 BPM de puro Drum & Bass y Synthwave para que las piernas no sientan el ácido láctico en esa recta.\",\n  \"vibe_tag\": \"🚴‍♂️ 168 BPM | High Cadence\",\n  \"tracks\": [\n    { \"title\": \"Witchcraft\", \"artist\": \"Pendulum\" },\n    { \"title\": \"Solaris\", \"artist\": \"Sub Focus\" },\n    { \"title\": \"Firestarter\", \"artist\": \"The Prodigy\" }\n  ]\n}"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = geminiDjService.parseGeminiResponse(simulatedResponse)

        assertEquals("¡Cadencia a tope y mente en la carretera!", parsed.djShoutout)
        assertTrue(parsed.djComment.contains("165 a 172 BPM"))
        assertEquals("🚴‍♂️ 168 BPM | High Cadence", parsed.vibeTag)
        assertEquals(3, parsed.tracks.size)
        assertEquals("Witchcraft", parsed.tracks[0].title)
        assertEquals("Pendulum", parsed.tracks[0].artist)
    }

    @Test
    fun testParseGeminiResponse_WithMarkdownWrapper() {
        val simulatedResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "```json\n{\n  \"dj_shoutout\": \"¡A romperla en los fierros!\",\n  \"dj_comment\": \"Puro rock pesado de los 80s.\",\n  \"vibe_tag\": \"🔥 150 BPM | Modo Bestia\",\n  \"tracks\": [\n    { \"title\": \"Eye of the Tiger\", \"artist\": \"Survivor\" }\n  ]\n}\n```"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = geminiDjService.parseGeminiResponse(simulatedResponse)

        assertEquals("¡A romperla en los fierros!", parsed.djShoutout)
        assertEquals("🔥 150 BPM | Modo Bestia", parsed.vibeTag)
        assertEquals(1, parsed.tracks.size)
        assertEquals("Eye of the Tiger", parsed.tracks[0].title)
    }

    @Test
    fun testParseGeminiResponse_WithSearchGroundingTextAndParts() {
        val simulatedGroundedResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "Basado en los charts y novedades encontradas en la web:\n"
                      },
                      {
                        "text": "```json\n{\n  \"dj_shoutout\": \"¡Beats frescos del año!\",\n  \"dj_comment\": \"Lo más nuevo de la escena electrónica.\",\n  \"vibe_tag\": \"⚡ Ultra Fresh Beats\",\n  \"tracks\": [\n    { \"title\": \"Starlight\", \"artist\": \"Anyma\" }\n  ]\n}\n```\nDisfruta la mezcla."
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = geminiDjService.parseGeminiResponse(simulatedGroundedResponse)

        assertEquals("¡Beats frescos del año!", parsed.djShoutout)
        assertEquals("⚡ Ultra Fresh Beats", parsed.vibeTag)
        assertEquals(1, parsed.tracks.size)
        assertEquals("Starlight", parsed.tracks[0].title)
        assertEquals("Anyma", parsed.tracks[0].artist)
    }

    @Test
    fun testDjSessionContext_RecordPlayedAndSkipped() {
        val session = DjSessionContext()
        session.reset("bici full ruta")

        assertEquals("bici full ruta", session.originalPrompt)
        assertTrue(session.playedSongs.isEmpty())
        assertTrue(session.skippedSongs.isEmpty())

        session.recordPlayed("The Prodigy - Firestarter")
        session.recordPlayed("The Prodigy - Firestarter") // Duplicado no debe agregarse dos veces
        assertEquals(1, session.playedSongs.size)

        session.recordSkipped("Moby - Porcelain")
        session.recordSkipped("Kraftwerk - Tour de France")
        assertEquals(2, session.skippedSongs.size)

        session.reset("gym pesado")
        assertEquals("gym pesado", session.originalPrompt)
        assertTrue(session.playedSongs.isEmpty())
        assertTrue(session.skippedSongs.isEmpty())
    }

    @Test
    fun testDjAdjustmentTypes_HaveLabelsAndInstructions() {
        DjAdjustmentType.entries.forEach { adjustment ->
            assertNotNull(adjustment.label)
            assertTrue(adjustment.label.isNotBlank())
            assertNotNull(adjustment.instruction)
            assertTrue(adjustment.instruction.isNotBlank())
        }
    }
}
