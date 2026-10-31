package lk.mortal.tv

import org.junit.Assert.*
import org.junit.Test

class PlaylistTest {
    @Test fun handlesBomMetadataCommasAndRelativeUrls() {
        val text = "\uFEFF#EXTM3U\r\n#PLAYLIST:Test\r\n#EXTINF:-1 tvg-id=\"abc\" tvg-logo=\"logo.png\" group-title=\"News;Sri Lanka\",News, International\r\n../live.m3u8?token=a%2Fb\r\n"
        val c = Playlist.parse(text, "https://example.com/lists/channels.m3u", "Test").channels.single()
        assertEquals("News, International", c.name); assertEquals("News;Sri Lanka", c.group)
        assertEquals("https://example.com/live.m3u8?token=a%2Fb", c.url)
        assertEquals("https://example.com/lists/logo.png", c.logo)
    }
    @Test fun recognizesHlsAsOneStream() {
        val r = Playlist.parse("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment.ts", "https://example.com/live.m3u8", "Live")
        assertTrue(r.isHls); assertEquals(1, r.channels.size); assertEquals("https://example.com/live.m3u8", r.channels.single().url)
    }
    @Test fun rejectsUnsafeSchemesAndDoesNotMergeNames() {
        val r = Playlist.parse("#EXTINF:-1,Same\nhttps://example.com/a\n#EXTINF:-1,Same\nhttps://example.com/b\n#EXTINF:-1,Bad\njavascript:alert(1)")
        assertEquals(2, r.channels.size); assertEquals(1, r.skipped)
        assertFalse(Playlist.safeUrl("file:///tmp/test")); assertFalse(Playlist.safeUrl("https://user:password@example.com/a"))
    }
    @Test fun duplicateUrlsHaveStableIdentities() {
        val t = "#EXTINF:-1 tvg-id=\"a\",A\nhttps://example.com/a\n#EXTINF:-1 tvg-id=\"a\",A\nhttps://example.com/a"
        assertEquals(1, Playlist.parse(t).channels.size)
        assertEquals(Playlist.parse(t).channels.single().id, Playlist.parse(t).channels.single().id)
    }
    @Test fun handlesCommaInQuotedAttributeAndMissingName() {
        val c = Playlist.parse("#EXTINF:-1 tvg-name=\"Name, alternate\" group-title=\"A,B\",\nhttps://example.com/a").channels.single()
        assertEquals("Name, alternate", c.name); assertEquals("A,B", c.group)
    }
    @Test(expected = IllegalArgumentException::class) fun hlsTextNeedsUrl() { Playlist.parse("#EXTM3U\n#EXT-X-TARGETDURATION:6\nsegment.ts") }
    @Test fun preservesTvgIdentityAcrossTokenRefresh() {
        val a = Playlist.parse("#EXTINF:-1 tvg-id=\"a\",A\nhttps://example.com/a?token=one")
        val b = Playlist.parse("#EXTINF:-1 tvg-id=\"a\",A\nhttps://example.com/a?token=two")
        assertEquals(a.channels.single().id, b.channels.single().id)
    }
    @Test fun preservesVariantsSharingTvgId() {
        val r = Playlist.parse("#EXTINF:-1 tvg-id=\"a\",A SD\nhttps://example.com/a\n#EXTINF:-1 tvg-id=\"a\",A HD\nhttps://example.com/b")
        assertEquals(2, r.channels.size); assertNotEquals(r.channels[0].id, r.channels[1].id)
    }
}
