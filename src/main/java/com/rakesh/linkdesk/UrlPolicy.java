package com.rakesh.linkdesk;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class UrlPolicy {
  private UrlPolicy() {}

  public static String validate(String value) {
    if (value == null) throw invalid();
    String cleaned = value.strip();
    if (cleaned.length() > 2048 || cleaned.chars().anyMatch(c -> c < 32 || c == 127))
      throw invalid();
    try {
      URI uri = new URI(cleaned);
      String scheme = uri.getScheme();
      if (scheme == null
          || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
          || uri.getHost() == null
          || uri.getRawUserInfo() != null
          || uri.getPort() > 65535
          || uri.getPort() == 0) throw invalid();
      // Never fetch or preview destinations on the server.
      String ascii = uri.toASCIIString();
      if (ascii.length() > 2048) throw invalid();
      return ascii;
    } catch (URISyntaxException ex) {
      throw invalid();
    }
  }

  public static String alias(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.strip().toLowerCase(Locale.ROOT);
    if (!normalized.matches("[a-z0-9][a-z0-9_-]{3,31}")) {
      throw new LinkFailure(
          400,
          "Alias must be 4–32 letters, digits, hyphens or underscores; start with a letter or digit.");
    }
    return normalized;
  }

  private static LinkFailure invalid() {
    return new LinkFailure(400, "Enter a valid HTTP or HTTPS URL without embedded credentials.");
  }
}
