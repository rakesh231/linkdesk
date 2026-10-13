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
}
