package com.jolumn.vtslshortlinkapi;

import com.jolumn.vtslshortlinkapi.service.UserAgentParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserAgentParserTest {

    private final UserAgentParser parser = new UserAgentParser();

    @Test
    void parse_chrome_desktop() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        var info = parser.parse(ua);
        assertEquals("Desktop", info.device());
        assertEquals("Windows", info.os());
        assertTrue(info.browser().startsWith("Chrome"));
    }

    @Test
    void parse_mobile_safari() {
        String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1";
        var info = parser.parse(ua);
        assertEquals("Mobile", info.device());
        assertEquals("iOS", info.os());
    }

    @Test
    void parse_bot() {
        String ua = "Googlebot/2.1 (+http://www.google.com/bot.html)";
        var info = parser.parse(ua);
        assertEquals("Bot", info.device());
    }

    @Test
    void parse_null() {
        var info = parser.parse(null);
        assertEquals("Desktop", info.device());
        assertEquals("", info.browser());
        assertEquals("", info.os());
    }
}
