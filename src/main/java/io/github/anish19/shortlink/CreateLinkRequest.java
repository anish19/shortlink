package io.github.anish19.shortlink;

import jakarta.validation.constraints.NotBlank;

public record CreateLinkRequest(@NotBlank String url) {}

