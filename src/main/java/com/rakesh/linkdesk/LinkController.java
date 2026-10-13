package com.rakesh.linkdesk;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class LinkController {
  private final LinkService service;
  private final Clock clock;
  private final String origin;

  public LinkController(
      LinkService service, Clock clock, @Value("${app.public-origin}") String origin) {
    this.service = service;
    this.clock = clock;
    this.origin = origin.replaceAll("/+$", "");
  }

  @PostMapping("/api/links")
  public ResponseEntity<LinkView> create(@Valid @RequestBody CreateLink request) {
    Link link = service.create(request);
    return ResponseEntity.created(URI.create("/api/links/" + link.code())).body(view(link));
  }

  @GetMapping("/api/links")
  public List<LinkView> list(
      @RequestParam(defaultValue = "20") int limit, @RequestParam(defaultValue = "0") int offset) {
    return service.list(limit, offset).stream().map(this::view).toList();
  }

  @GetMapping("/api/links/{code}")
  public LinkView detail(@PathVariable String code) {
    return view(service.get(code));
  }

  @DeleteMapping("/api/links/{code}")
  public ResponseEntity<Void> disable(@PathVariable String code) {
    service.disable(code);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/s/{code}")
  public ResponseEntity<Void> follow(@PathVariable String code) {
    return redirect(service.visit(code));
  }

  @RequestMapping(value = "/s/{code}", method = RequestMethod.HEAD)
  public ResponseEntity<Void> inspect(@PathVariable String code) {
    return redirect(service.inspect(code));
  }

  private ResponseEntity<Void> redirect(String url) {
    return ResponseEntity.status(302)
        .cacheControl(CacheControl.noStore())
        .location(URI.create(url))
        .build();
  }

  private LinkView view(Link link) {
    return new LinkView(
        link.code(),
        link.destination(),
        origin + "/s/" + link.code(),
        link.createdAt(),
        link.expiresAt(),
        link.status(clock.instant()),
        link.visits(),
        link.lastVisitAt());
  }

  public record LinkView(
      String code,
      String url,
      String shortUrl,
      Instant createdAt,
      Instant expiresAt,
      String status,
      long visits,
      Instant lastVisitAt) {}
}
