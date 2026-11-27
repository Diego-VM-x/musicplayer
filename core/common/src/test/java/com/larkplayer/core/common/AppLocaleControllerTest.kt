package com.larkplayer.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

/** A bad or missing language tag must never produce an invalid Locale. */
class AppLocaleControllerTest {

    @Test
    fun `blank tag falls back to Spanish`() {
        assertEquals("es", AppLocaleController.normalizeTag(""))
    }

    @Test
    fun `region tag is preserved`() {
        assertEquals("zh-TW", AppLocaleController.normalizeTag("zh-TW"))
    }

    @Test
    fun `uppercase tag is normalized`() {
        assertEquals("en", AppLocaleController.normalizeTag("EN"))
    }

    @Test
    fun `malformed tag falls back to Spanish`() {
        assertEquals("es", AppLocaleController.normalizeTag("not a tag!"))
    }
}
