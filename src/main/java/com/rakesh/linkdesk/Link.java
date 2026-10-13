package com.rakesh.linkdesk;

import java.time.Instant;

public record Link(
    String code,
    String destination,
    Instant createdAt,
    Instant expiresAt,
    boolean enabled,
    long visits,
    Instant lastVisitAt) {
  public String status(Instant now) {
    if (!enabled) return "disabled";
    return expiresAt != null && !now.isBefore(expiresAt) ? "expired" : "active";
  }
}
