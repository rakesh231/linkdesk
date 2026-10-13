package com.rakesh.linkdesk;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LinkRepository {
  private final JdbcTemplate jdbc;

  public LinkRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(Link link) {
    jdbc.update(
        "INSERT INTO links(code, destination, created_ms, expires_ms) VALUES(?, ?, ?, ?)",
        link.code(),
        link.destination(),
        link.createdAt().toEpochMilli(),
        link.expiresAt() == null ? null : link.expiresAt().toEpochMilli());
  }

  public Optional<Link> find(String code) {
    return jdbc.query("SELECT * FROM links WHERE code = ?", this::map, code).stream().findFirst();
  }

  public Optional<Link> lock(String code) {
    return jdbc.query("SELECT * FROM links WHERE code = ? FOR UPDATE", this::map, code).stream()
        .findFirst();
  }

  public List<Link> list(int limit, int offset) {
    return jdbc.query(
        "SELECT * FROM links ORDER BY created_ms DESC, code LIMIT ? OFFSET ?",
        this::map,
        limit,
        offset);
  }

  public void record(String code, Instant now) {
    jdbc.update(
        "UPDATE links SET visits = visits + 1, last_visit_ms = ? WHERE code = ?",
        now.toEpochMilli(),
        code);
  }

  public void disable(String code) {
    jdbc.update("UPDATE links SET enabled = FALSE WHERE code = ?", code);
  }

  private Link map(ResultSet rs, int row) throws SQLException {
    return new Link(
        rs.getString("code"),
        rs.getString("destination"),
        Instant.ofEpochMilli(rs.getLong("created_ms")),
        nullableInstant(rs, "expires_ms"),
        rs.getBoolean("enabled"),
        rs.getLong("visits"),
        nullableInstant(rs, "last_visit_ms"));
  }

  private Instant nullableInstant(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : Instant.ofEpochMilli(value);
  }

  public void recordDay(String code, java.time.LocalDate day) {
    // Caller holds the link row lock, so the first daily insert cannot race another visit.
    int updated =
        jdbc.update(
            "UPDATE daily_visits SET visits = visits + 1 WHERE code = ? AND visit_day = ?",
            code,
            day);
    if (updated == 0)
      jdbc.update("INSERT INTO daily_visits(code, visit_day, visits) VALUES(?, ?, 1)", code, day);
  }

  public java.util.Map<java.time.LocalDate, Long> days(
      String code, java.time.LocalDate first, java.time.LocalDate last) {
    java.util.Map<java.time.LocalDate, Long> result = new java.util.HashMap<>();
    jdbc.query(
        "SELECT visit_day, visits FROM daily_visits WHERE code = ? AND visit_day >= ? AND visit_day <= ?",
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs ->
                result.put(
                    rs.getObject("visit_day", java.time.LocalDate.class), rs.getLong("visits")),
        code,
        first,
        last);
    return result;
  }
}
