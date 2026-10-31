package lk.mortal.tv

import org.junit.Assert.*
import org.junit.Test

class VodCatalogTest {
    private val movie = """{"id":"film","title":"A Film","url":"https://example.com/film.mp4","year":2020,"minutes":90,"category":"Drama","description":"Full film","credit":"Creator","license":"Creator authorized","sourceUrl":"https://example.com/rights","verified":"2026-11-01"}"""
    @Test fun movieHasDistinctVodIdentity() { val m = VodCatalog.parse("[$movie]").single(); assertEquals("vod:film", m.channel().id); assertEquals("vod", m.channel().kind) }
    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicateEntries() { VodCatalog.parse("[$movie,$movie]") }
    @Test(expected = IllegalArgumentException::class) fun rejectsUntrustedSchemes() { VodCatalog.parse("[${movie.replace("https://example.com/film.mp4", "javascript:alert(1)")}]") }
    @Test(expected = IllegalArgumentException::class) fun requiresAuthorizationDetails() { VodCatalog.parse("[${movie.replace("Creator authorized", "")}]") }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyRefreshToKeepExistingCatalog() { VodCatalog.parse("[]") }
}
