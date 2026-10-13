package com.rakesh.linkdesk;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class SchemaUpgradeTest {
  @Test
  void additiveAnalyticsSchemaPreservesExistingLinks() {
    DriverManagerDataSource ds =
        new DriverManagerDataSource("jdbc:h2:mem:upgrade;DB_CLOSE_DELAY=-1", "sa", "");
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    jdbc.execute(
        "CREATE TABLE links(code VARCHAR(32) PRIMARY KEY,destination VARCHAR(2048) NOT NULL,created_ms BIGINT NOT NULL,expires_ms BIGINT,enabled BOOLEAN DEFAULT TRUE NOT NULL,visits BIGINT DEFAULT 0 NOT NULL,last_visit_ms BIGINT)");
    jdbc.update(
        "INSERT INTO links(code,destination,created_ms,visits) VALUES('legacy01','https://example.com',0,7)");
    ResourceDatabasePopulator initializer =
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
    initializer.execute(ds);
    initializer.execute(ds);
    assertThat(jdbc.queryForObject("SELECT visits FROM links WHERE code='legacy01'", Long.class))
        .isEqualTo(7);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_visits", Integer.class)).isZero();
  }
}
