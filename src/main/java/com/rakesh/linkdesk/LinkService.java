package com.rakesh.linkdesk;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkService {
  private static final Logger LOG = LoggerFactory.getLogger(LinkService.class);
  private final LinkRepository repository;
  private final CodeSource codes;
  private final Clock clock;

  public LinkService(LinkRepository repository, CodeSource codes, Clock clock) {
    this.repository = repository;
    this.codes = codes;
    this.clock = clock;
  }

  // Each insert is its own committed statement: a collision cannot poison the next attempt.
  public Link create(CreateLink request) {
    String destination = UrlPolicy.validate(request.url());
    String alias = UrlPolicy.alias(request.alias());
    Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
    Instant expires =
        request.expiresInMinutes() == null
            ? null
            : now.plus(request.expiresInMinutes(), ChronoUnit.MINUTES);
    for (int attempt = 0; attempt < 5; attempt++) {
      String code = alias == null ? codes.next() : alias;
      Link link = new Link(code, destination, now, expires, true, 0, null);
      try {
        repository.insert(link);
        LOG.debug("Created link; generatedCode={}", alias == null);
        return link;
      } catch (DuplicateKeyException conflict) {
        if (alias != null)
          throw new LinkFailure(409, "That alias is already reserved. Choose another.");
      }
    }
    throw new LinkFailure(503, "Could not allocate a short code. Please retry.");
  }

  public Link get(String code) {
    return repository.find(code).orElseThrow(() -> new LinkFailure(404, "Link not found."));
  }

  public List<Link> list(int limit, int offset) {
    if (limit < 1 || limit > 100 || offset < 0)
      throw new LinkFailure(400, "Limit must be 1–100 and offset must be nonnegative.");
    return repository.list(limit, offset);
  }

  @Transactional
  public String visit(String code) {
    Link link = repository.lock(code).orElseThrow(() -> new LinkFailure(404, "Link not found."));
    Instant now = clock.instant();
    requireActive(link, now);
    repository.record(code, now);
    return link.destination();
  }

  public String inspect(String code) {
    Link link = get(code);
    requireActive(link, clock.instant());
    return link.destination();
  }

  @Transactional
  public void disable(String code) {
    repository.lock(code).orElseThrow(() -> new LinkFailure(404, "Link not found."));
    repository.disable(code);
  }

  private void requireActive(Link link, Instant now) {
    if (!link.status(now).equals("active"))
      throw new LinkFailure(410, "This link has expired or has been disabled.");
  }
}
