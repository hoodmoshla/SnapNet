package com.snapnet.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XTwitterExtractionTest {
    @Test
    fun xAndTwitterShareLinksAreRecognizedIndependently() {
        val urls =
            listOf(
                "https://x.com/example/status/12345?s=20",
                "https://twitter.com/example/status/12345?s=20",
            )
        urls.forEach { url ->
            assertEquals(
                listOf(url),
                findURLsFromString("Shared video: $url", firstMatchOnly = true),
            )
        }
    }

    @Test
    fun twitterFormatsWithMissingCodecNamesRemainSelectable() {
        val info = DownloadUtil.parseVideoInfo(TWITTER_STATUS_JSON)
        val sourceFormats = info.formats.orEmpty()
        val options = formatPickerOptions(sourceFormats, durationSeconds = info.duration)
        assertEquals(9, sourceFormats.size)
        assertEquals(3, sourceFormats.count { it.containsAudio() && !it.containsVideo() })
        assertEquals(6, sourceFormats.count { it.containsVideo() && !it.containsAudio() })
        assertEquals(3, options.music.size)
        assertEquals(6, options.video.size)
        assertEquals(setOf(320, 480, 720), options.video.mapNotNull { it.videoQualityLevel() }.toSet())
        assertTrue(options.video.all { it.containsAudio() && it.containsVideo() })
        assertTrue(options.video.all { it.formatId?.endsWith("+hls-audio-128000-Audio") == true })
        val bestVideo = options.video.first()
        assertEquals(720, bestVideo.videoQualityLevel())
        assertTrue(bestVideo.formatId?.startsWith("http-2176+") == true)
        val hls720 = options.video.single { it.formatId?.startsWith("hls-1453+") == true }
        assertEquals("avc1.64001F", hls720.vcodec)
        assertEquals("mp4", hls720.videoExt)
        assertEquals("mp4", hls720.audioExt)
        assertEquals("720×1280 (720p)", hls720.videoQualityLabel())
        assertTrue(options.video.none { it.videoQualityLevel() == 360 || it.videoQualityLevel() == 1080 })
        assertTrue(options.video.all { it.fileSize != null || it.fileSizeApprox != null })
    }

    @Test
    fun sharedShortLinksStayIntactForYtDlpToResolve() {
        val shortLink = "https://t.co/AbCdEf123"
        assertEquals(
            listOf(shortLink),
            findURLsFromString("Shared video: $shortLink", firstMatchOnly = true),
        )
    }

    @Test
    fun emptyOrPlaylistJsonFailsInsteadOfOpeningAnEmptyFormatPicker() {
        val emptyFormats =
            runCatching { DownloadUtil.parseVideoInfo("""{"id":"123","formats":[]}""") }
                .exceptionOrNull()
        assertTrue(emptyFormats is IllegalStateException)
        assertTrue(emptyFormats?.message.orEmpty().contains("No video formats found"))

        val playlist =
            runCatching {
                DownloadUtil.parseVideoInfo("""{"id":"123","_type":"playlist","entries":[]}""")
            }.exceptionOrNull()
        assertTrue(playlist is IllegalStateException)
        assertTrue(playlist?.message.orEmpty().contains("playlist instead of one video"))
    }

    private companion object {
        /** Sanitized fixture matching public yt-dlp Twitter JSON; media URLs and signed query data omitted. */
        val TWITTER_STATUS_JSON =
            """
            {
              "id": "12345",
              "title": "Public video fixture",
              "_type": "video",
              "duration": 30.0,
              "formats": [
                {"format_id":"hls-audio-32000-Audio","ext":"mp4","format_note":"Audio, low","resolution":"audio only","abr":32,"vcodec":"none","audio_ext":"mp4","video_ext":"none"},
                {"format_id":"hls-audio-64000-Audio","ext":"mp4","format_note":"Audio","resolution":"audio only","abr":64,"vcodec":"none","audio_ext":"mp4","video_ext":"none"},
                {"format_id":"hls-audio-128000-Audio","ext":"mp4","format_note":"Audio, high","resolution":"audio only","abr":128,"vcodec":"none","audio_ext":"mp4","video_ext":"none"},
                {"format_id":"http-632","ext":"mp4","format":"http-632 - 320x568","resolution":"320x568","width":320,"height":568,"filesize_approx":2125969,"tbr":632,"audio_ext":"none","video_ext":"mp4"},
                {"format_id":"hls-392","ext":"mp4","format":"hls-392 - 320x568","resolution":"320x568","width":320,"height":568,"tbr":392.099,"vbr":392.099,"abr":0,"acodec":"none","vcodec":"avc1.4D401E","audio_ext":"none","video_ext":"mp4"},
                {"format_id":"http-950","ext":"mp4","format":"http-950 - 480x852","resolution":"480x852","width":480,"height":852,"filesize_approx":3195681,"tbr":950,"audio_ext":"none","video_ext":"mp4"},
                {"format_id":"hls-738","ext":"mp4","format":"hls-738 - 480x852","resolution":"480x852","width":480,"height":852,"tbr":738.6,"vbr":738.6,"abr":0,"acodec":"none","vcodec":"avc1.4D401F","audio_ext":"none","video_ext":"mp4"},
                {"format_id":"http-2176","ext":"mp4","format":"http-2176 - 720x1280","resolution":"720x1280","width":720,"height":1280,"filesize_approx":7319792,"tbr":2176,"audio_ext":"none","video_ext":"mp4"},
                {"format_id":"hls-1453","ext":"mp4","format":"hls-1453 - 720x1280","resolution":"720x1280","width":720,"height":1280,"tbr":1453.928,"vbr":1453.928,"abr":0,"acodec":"none","vcodec":"avc1.64001F","audio_ext":"none","video_ext":"mp4"}
              ]
            }
            """.trimIndent()
    }
}
