package com.bandeja.app.data

import com.bandeja.app.data.remote.OEmbed
import com.bandeja.app.data.remote.OpenGraphParser
import com.bandeja.app.domain.model.ContentType
import com.bandeja.app.domain.model.LinkMetadata
import com.bandeja.app.ui.components.platformBrand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OEmbedTest {

    @Test
    fun `known providers get their official endpoint with the link encoded`() {
        val tiktok = OEmbed.endpointFor("https://www.tiktok.com/@user/video/123?is_from_webapp=1&x=y")!!
        assertTrue(tiktok.startsWith("https://www.tiktok.com/oembed?url="))
        assertTrue("the link must be percent-encoded: $tiktok", "%3A%2F%2F" in tiktok && "&x=y" !in tiktok)

        assertTrue(OEmbed.endpointFor("https://vimeo.com/76979871")!!.startsWith("https://vimeo.com/api/oembed.json?url="))
        assertTrue(OEmbed.endpointFor("https://open.spotify.com/track/abc")!!.startsWith("https://open.spotify.com/oembed?url="))
    }

    @Test
    fun `short links and mobile hosts belong to their provider`() {
        assertNotNull(OEmbed.endpointFor("https://vm.tiktok.com/ZMabc123/"))
        assertNotNull(OEmbed.endpointFor("https://m.tiktok.com/@u/video/1"))
    }

    @Test
    fun `facebook and instagram have no endpoint on purpose (Meta requires a token)`() {
        assertNull(OEmbed.endpointFor("https://www.facebook.com/watch/?v=1"))
        assertNull(OEmbed.endpointFor("https://www.instagram.com/p/abc/"))
    }

    @Test
    fun `unknown or invalid links have no endpoint, and lookalike hosts do not match`() {
        assertNull(OEmbed.endpointFor("https://example.com/tiktok.com"))
        assertNull(OEmbed.endpointFor("https://nottiktok.com/@u/video/1"))
        assertNull(OEmbed.endpointFor("not a url"))
    }

    @Test
    fun `parses title thumbnail and type`() {
        val json = """{"version":"1.0","type":"video","title":"  Baile   de prueba ","thumbnail_url":"https://p16.example.com/cover.jpg","html":"<iframe>"}"""
        val meta = OEmbed.parse(json)!!
        assertEquals("Baile de prueba", meta.title)
        assertEquals("https://p16.example.com/cover.jpg", meta.thumbnailUrl)
        assertEquals(ContentType.VIDEO, meta.contentType)
    }

    @Test
    fun `plain http thumbnails are upgraded and unsafe ones are dropped`() {
        assertEquals("https://cdn.example.com/a.jpg", OEmbed.parse("""{"title":"t","thumbnail_url":"http://cdn.example.com/a.jpg"}""")!!.thumbnailUrl)
        assertNull(OEmbed.parse("""{"title":"t","thumbnail_url":"javascript:alert(1)"}""")!!.thumbnailUrl)
        assertNull(OEmbed.parse("""{"title":"t","thumbnail_url":"data:image/png;base64,AAAA"}""")!!.thumbnailUrl)
    }

    @Test
    fun `an answer with nothing useful, or that is not json, is null`() {
        // TikTok answers 200 with only the embed html for videos it cannot find
        assertNull(OEmbed.parse("""{"version":"1.0","type":"video","html":"<iframe></iframe>","embed_type":"video"}"""))
        assertNull(OEmbed.parse("<html>login</html>"))
        assertNull(OEmbed.parse(""))
    }

    @Test
    fun `huge titles are capped`() {
        assertEquals(200, OEmbed.parse("""{"title":"${"x".repeat(900)}"}""")!!.title!!.length)
    }

    @Test
    fun `merge keeps the primary and fills the gaps`() {
        val primary = LinkMetadata(title = "A", thumbnailUrl = null, contentType = null)
        val fallback = LinkMetadata(title = "B", thumbnailUrl = "https://x.com/i.jpg", contentType = ContentType.VIDEO, publishedAt = 5L)
        val merged = OEmbed.merge(primary, fallback)!!
        assertEquals("A", merged.title)
        assertEquals("https://x.com/i.jpg", merged.thumbnailUrl)
        assertEquals(ContentType.VIDEO, merged.contentType)
        assertEquals(5L, merged.publishedAt)
        assertEquals(fallback, OEmbed.merge(null, fallback))
        assertEquals(primary, OEmbed.merge(primary, null))
        assertNull(OEmbed.merge(null, null))
    }

    // --- the endpoint a page announces about itself

    @Test
    fun `finds the oembed endpoint announced by a page, resolving relative addresses`() {
        val html = """<html><head><title>x</title>
            <link rel="alternate" type="application/json+oembed" href="/oembed?url=https%3A%2F%2Fsite.example%2Fv%2F1&format=json">
            </head></html>"""
        assertEquals(
            "https://site.example/oembed?url=https%3A%2F%2Fsite.example%2Fv%2F1&format=json",
            OpenGraphParser.oEmbedLink(html, "https://site.example/v/1"),
        )
    }

    @Test
    fun `no announced endpoint, xml-only endpoints and unsafe schemes give null`() {
        assertNull(OpenGraphParser.oEmbedLink("<html><head><title>x</title></head></html>", "https://a.example/"))
        assertNull(OpenGraphParser.oEmbedLink("""<link rel="alternate" type="text/xml+oembed" href="https://a.example/o.xml">""", "https://a.example/"))
        assertNull(OpenGraphParser.oEmbedLink("""<link rel="alternate" type="application/json+oembed" href="javascript:alert(1)">""", "https://a.example/"))
    }

    // --- placeholder for sites that give no image

    @Test
    fun `sites that hide their image get a recognizable color`() {
        assertEquals("f", platformBrand("facebook.com")!!.mark)
        assertEquals("f", platformBrand("m.facebook.com")!!.mark)
        assertEquals("📷", platformBrand("instagram.com")!!.mark)
        assertEquals("♪", platformBrand("tiktok.com")!!.mark)
        assertNull(platformBrand("example.com"))
        assertNull(platformBrand("notfacebook.com"))
        assertNull(platformBrand(null))
    }
}
