package com.example.urlshortener;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class UrlShortenerApplicationTests {

    @Test
    void contextLoads() {
        // Spring context should start without error (TICKET-001 acceptance criterion).
    }
}
