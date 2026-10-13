CREATE TABLE IF NOT EXISTS links (
  code VARCHAR(32) PRIMARY KEY,
  destination VARCHAR(2048) NOT NULL,
  created_ms BIGINT NOT NULL,
  expires_ms BIGINT,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  visits BIGINT NOT NULL DEFAULT 0 CHECK (visits >= 0),
  last_visit_ms BIGINT
);
CREATE INDEX IF NOT EXISTS links_created ON links(created_ms DESC, code);
