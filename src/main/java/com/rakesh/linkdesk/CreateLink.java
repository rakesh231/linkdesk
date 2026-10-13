package com.rakesh.linkdesk;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLink(
    @NotBlank @Size(max = 2048) String url,
    @Size(max = 32) String alias,
    @Min(1) @Max(525600) Integer expiresInMinutes) {}
