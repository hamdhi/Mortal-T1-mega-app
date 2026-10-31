package lk.mortal.tv

import org.junit.Assert.*
import org.junit.Test

class ChannelQualityTest {
    @Test fun recognizesExplicitResolutionAndDimensions() {
        assertEquals("1080p", ChannelQuality.label("Movie [1080P]"))
        assertEquals("720p", ChannelQuality.label("News 1280x720"))
        assertEquals("512p", ChannelQuality.label("Film (512p)"))
        assertEquals("1080p", ChannelQuality.label("News 1080i"))
    }
    @Test fun aliasesAndGroupFallbackRespectName() {
        assertEquals("2160p", ChannelQuality.label("Cinema UHD"))
        assertEquals("1080p", ChannelQuality.label("Cinema Full HD"))
        assertEquals("720p", ChannelQuality.label("Cinema", "Movies;HD"))
        assertEquals("512p", ChannelQuality.label("Cinema 512p", "HD"))
        assertEquals("SD", ChannelQuality.label("Cinema SD"))
    }
    @Test fun avoidsUnlabeledAndAmbiguousClaims() {
        assertEquals(ChannelQuality.UNKNOWN, ChannelQuality.label("Channel 1080"))
        assertEquals(ChannelQuality.UNKNOWN, ChannelQuality.label("SHDTV"))
        assertEquals(ChannelQuality.UNKNOWN, ChannelQuality.label("Movie 720p / 1080p"))
        assertEquals(ChannelQuality.UNKNOWN, ChannelQuality.label("Movie"))
    }
    @Test fun filtersAndListsAdditionalResolutions() {
        val c = Channel("1", "Movie 576p", "https://example.com/1080p")
        assertTrue(ChannelQuality.matches(c, "576p"))
        assertFalse(ChannelQuality.matches(c, "1080p"))
        assertTrue(ChannelQuality.matches(c, ChannelQuality.ALL))
        val extra = c.copy(name = "Movie 540p")
        assertEquals(listOf("All qualities", "4320p", "2160p", "1440p", "1080p", "720p", "576p", "540p", "512p", "480p", "360p", "240p", "144p", "SD", "Unknown"), ChannelQuality.options(listOf(c, extra)))
    }
}
