package io.github.anish19.shortlink;

import java.time.Instant;
import java.util.List;

public record LinkPage(List<LinkSummary> items, String nextCursor) {}
