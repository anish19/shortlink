package io.github.anish19.shortlink;

import java.time.Instant;

public record LinkSummary (String shortCode, String longUrl, Instant createdAt, Instant expiresAt) {}
