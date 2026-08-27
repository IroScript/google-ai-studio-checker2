package com.example

import com.example.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsTubeLogicTest {

    private val sampleVideos = listOf(
        VideoItem(id = "1", title = "Rhyme A", uriString = "uri1", folderName = "Rhymes / English"),
        VideoItem(id = "2", title = "Rhyme B", uriString = "uri2", folderName = "Rhymes / Bengali"),
        VideoItem(id = "3", title = "Cartoon A", uriString = "uri3", folderName = "Cartoons"),
        VideoItem(id = "4", title = "Cartoon B", uriString = "uri4", folderName = "Cartoons / Animals"),
        VideoItem(id = "5", title = "Story A", uriString = "uri5", folderName = "Stories")
    )

    private fun filterVideos(all: List<VideoItem>, selectedFolder: String?): List<VideoItem> {
        if (selectedFolder == null) return all
        return all.filter {
            it.folderName == selectedFolder || it.folderName.startsWith("$selectedFolder /")
        }
    }

    @Test
    fun testAllFolderFiltering() {
        val all = filterVideos(sampleVideos, null)
        assertEquals(5, all.size)
    }

    @Test
    fun testSpecificSubfolderFiltering() {
        val rhymes = filterVideos(sampleVideos, "Rhymes")
        assertEquals(2, rhymes.size)
        assertTrue(rhymes.all { it.folderName.startsWith("Rhymes") })

        val cartoons = filterVideos(sampleVideos, "Cartoons")
        assertEquals(2, cartoons.size)
        assertTrue(cartoons.all { it.folderName.startsWith("Cartoons") })

        val stories = filterVideos(sampleVideos, "Stories")
        assertEquals(1, stories.size)
        assertEquals("Story A", stories.first().title)
    }

    @Test
    fun testDynamicShuffleChain() {
        val filtered = sampleVideos
        val selected = filtered[2] // Cartoon A
        val remaining = filtered.filter { it.id != selected.id }.shuffled()
        val dynamicChain = listOf(selected) + remaining

        assertEquals(selected.id, dynamicChain.first().id)
        assertEquals(filtered.size, dynamicChain.size)
    }

    @Test
    fun testMathQuestionVerification() {
        val a = 7
        val b = 8
        val expected = a * b
        assertEquals(56, expected)

        val correctInput = "56"
        val wrongInput = "54"

        assertEquals(expected, correctInput.toIntOrNull())
        assertNotEquals(expected, wrongInput.toIntOrNull())
    }
}
