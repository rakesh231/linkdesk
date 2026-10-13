package com.rakesh.linkdesk;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class LinkRulesTest {
  private final LinkRepository repository = mock(LinkRepository.class);
  private final CodeSource codes = mock(CodeSource.class);
  private final Instant now = Instant.parse("2030-03-01T12:00:00Z");
  private final LinkService service =
      new LinkService(repository, codes, Clock.fixed(now, ZoneOffset.UTC));

  @Test
  void retriesActualInsertCollisions() {
    when(codes.next()).thenReturn("first001", "second02");
    doThrow(new DuplicateKeyException("collision")).doNothing().when(repository).insert(any());
    Link link = service.create(new CreateLink("https://example.com", null, 60));
    assertThat(link.code()).isEqualTo("second02");
    assertThat(link.expiresAt()).isEqualTo(now.plusSeconds(3600));
    verify(repository, times(2)).insert(any());
  }

  @Test
  void stopsAfterBoundedRetries() {
    when(codes.next()).thenReturn("taken001");
    doThrow(new DuplicateKeyException("collision")).when(repository).insert(any());
    assertThatThrownBy(() -> service.create(new CreateLink("https://example.com", null, null)))
        .isInstanceOfSatisfying(LinkFailure.class, ex -> assertThat(ex.status()).isEqualTo(503));
    verify(repository, times(5)).insert(any());
  }

  @Test
  void expiresAtExactBoundary() {
    Link link =
        new Link("boundary", "https://example.com", now.minusSeconds(60), now, true, 0, null);
    when(repository.lock("boundary")).thenReturn(Optional.of(link));
    when(repository.find("boundary")).thenReturn(Optional.of(link));
    assertThatThrownBy(() -> service.visit("boundary"))
        .isInstanceOfSatisfying(LinkFailure.class, ex -> assertThat(ex.status()).isEqualTo(410));
    assertThatThrownBy(() -> service.inspect("boundary")).isInstanceOf(LinkFailure.class);
    verify(repository, never()).record(any(), any());
  }

  @Test
  void headDoesNotWriteAnalytics() {
    Link link = new Link("healthy1", "https://example.com", now, null, true, 7, now);
    when(repository.find("healthy1")).thenReturn(Optional.of(link));
    assertThat(service.inspect("healthy1")).isEqualTo("https://example.com");
    verify(repository, never()).record(any(), any());
    verify(repository, never()).recordDay(any(), any());
  }

  @Test
  void aliasConflictIsNotRetriedAndMissingDisableIs404() {
    doThrow(new DuplicateKeyException("duplicate")).when(repository).insert(any());
    assertThatThrownBy(() -> service.create(new CreateLink("https://example.com", "chosen", null)))
        .isInstanceOfSatisfying(LinkFailure.class, ex -> assertThat(ex.status()).isEqualTo(409));
    verify(repository, times(1)).insert(any());
    assertThatThrownBy(() -> service.disable("missing"))
        .isInstanceOfSatisfying(LinkFailure.class, ex -> assertThat(ex.status()).isEqualTo(404));
  }

  @Test
  void aliasRemainsReservedAfterDisable() {
    Link link = new Link("stable", "https://example.com", now, null, false, 0, null);
    assertThat(link.status(now)).isEqualTo("disabled");
  }
}
