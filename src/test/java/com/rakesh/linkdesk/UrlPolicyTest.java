package com.rakesh.linkdesk;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlPolicyTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "javascript:alert(1)",
        "ftp://example.com",
        "https://",
        "https://user:pass@example.com",
        "https://example.com/a b",
        "https://example.com:99999",
        "//example.com",
        "https://example.com/\r\nLocation:x"
      })
  void rejectsUnsafeOrMalformedInput(String url) {
    assertThatThrownBy(() -> UrlPolicy.validate(url)).isInstanceOf(LinkFailure.class);
  }

  @Test
  void normalizesWithoutChangingDestinationSemantics() {
    assertThat(UrlPolicy.validate("  https://example.com/a?x=1#part  "))
        .isEqualTo("https://example.com/a?x=1#part");
    assertThat(UrlPolicy.alias(" Team-Link ")).isEqualTo("team-link");
    assertThat(UrlPolicy.alias(" ")).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "has space", "../bad", "_bad"})
  void rejectsAmbiguousAliases(String alias) {
    assertThatThrownBy(() -> UrlPolicy.alias(alias)).isInstanceOf(LinkFailure.class);
  }
}
