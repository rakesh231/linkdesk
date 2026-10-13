package com.rakesh.linkdesk;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:api;DB_CLOSE_DELAY=-1",
      "app.public-origin=http://localhost:8092"
    })
@AutoConfigureMockMvc
class LinkApiTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void reset() {
    jdbc.update("DELETE FROM links");
  }

  @Test
  void completeLifecycle() throws Exception {
    mvc.perform(
            post("/api/links")
                .contentType("application/json")
                .content("{\"url\":\"https://example.com/path\",\"alias\":\"My-Link\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("my-link"));
    mvc.perform(head("/s/my-link"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/path"));
    mvc.perform(get("/api/links/my-link")).andExpect(jsonPath("$.visits").value(0));
    mvc.perform(get("/s/my-link"))
        .andExpect(status().isFound())
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(get("/api/links/my-link")).andExpect(jsonPath("$.visits").value(1));
    mvc.perform(delete("/api/links/my-link")).andExpect(status().isNoContent());
    mvc.perform(delete("/api/links/my-link")).andExpect(status().isNoContent());
    mvc.perform(get("/s/my-link")).andExpect(status().isGone());
    mvc.perform(get("/api/links/my-link"))
        .andExpect(jsonPath("$.visits").value(1))
        .andExpect(jsonPath("$.status").value("disabled"));
  }

  @Test
  void validationAndConflict() throws Exception {
    mvc.perform(
            post("/api/links")
                .contentType("application/json")
                .content("{\"url\":\"javascript:alert(1)\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/links")
                .contentType("application/json")
                .content("{\"url\":\"https://example.com\",\"expiresInMinutes\":0}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/links").contentType("application/json").content("not-json"))
        .andExpect(status().isBadRequest());
    String body = "{\"url\":\"https://example.com\",\"alias\":\"unique\"}";
    mvc.perform(post("/api/links").contentType("application/json").content(body))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/links").contentType("application/json").content(body))
        .andExpect(status().isConflict());
    mvc.perform(get("/s/missing")).andExpect(status().isNotFound());
    mvc.perform(get("/api/links?limit=101")).andExpect(status().isBadRequest());
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM links", Integer.class)).isEqualTo(1);
  }
}
