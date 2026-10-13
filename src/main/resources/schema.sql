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

CREATE TABLE IF NOT EXISTS daily_visits (
  code VARCHAR(32) NOT NULL REFERENCES links(code),
  visit_day DATE NOT NULL,
  visits BIGINT NOT NULL CHECK (visits > 0),
  PRIMARY KEY (code, visit_day)
);
