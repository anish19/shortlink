package io.github.anish19.shortlink;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.mockito.ArgumentCaptor;
import java.time.Duration;
import java.util.Optional;


@ExtendWith(MockitoExtension.class)
class LinkServiceTest {
    @Mock LinkRepository linkRepository;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    LinkService service;

    @BeforeEach
    void setUp() {
        service = new LinkService(linkRepository, redis);
    }

    @Test
    void createLink_evictsNegativeCacheEntry() {
        String code = service.createLink("https://example.com");

        verify(redis).delete("link:" + code);

        ArgumentCaptor<Link> saved = ArgumentCaptor.forClass(Link.class);
        verify(linkRepository, times(1)).saveAndFlush(saved.capture());
        assertEquals(code, saved.getValue().getShortCode());
        assertEquals("https://example.com", saved.getValue().getLongUrl());

        assertEquals(7, code.length());
    }

    @Test
    void findByShortCode_redisDown_fallsBackToPostgres() {
        // ---------- Arrange ----------
        // A Link as Postgres would return it: no expiry, so it's valid.
        Link link = new Link();
        link.setShortCode("abc1234");
        link.setLongUrl("https://example.com");

        // 1. Your code calls redis.opsForValue() first. Without this, it returns null → NPE.
        when(redis.opsForValue()).thenReturn(valueOps);

        // 2. Simulate Redis being down: the GET throws (like a refused connection / timeout).
        when(valueOps.get("link:abc1234"))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        // 3. Redis is down for writes too. set(...) returns void, so you can't use when(...).
        //    For void methods the syntax is flipped: doThrow(...).when(mock).method(args).
        doThrow(new RedisConnectionFailureException("Redis down"))
                .when(valueOps).set(eq("link:abc1234"), anyString(), any(Duration.class));

        // 4. Postgres works and has the row.
        when(linkRepository.findByShortCode("abc1234")).thenReturn(Optional.of(link));

        // ---------- Act ----------
        String result = service.findByShortCode("abc1234");

        // ---------- Assert ----------
        // The request succeeded with the URL from Postgres, even though both Redis calls failed.
        assertEquals("https://example.com", result);

        // Postgres was actually consulted (the fallback happened).
        verify(linkRepository).findByShortCode("abc1234");

        // The code still *tried* to cache the result in Redis (cacheSet), and survived the failure.
        verify(valueOps).set(eq("link:abc1234"), eq("https://example.com"), any(Duration.class));
    }
}