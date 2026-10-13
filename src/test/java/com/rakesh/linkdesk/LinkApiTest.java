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
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean LinkRepository repository;

  @BeforeEach
  void reset() {
    jdbc.update("DELETE FROM daily_visits");
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

  @Test
  void rejectsUnknownFieldsAndFractionalExpiry() throws Exception {
    mvc.perform(
            post("/api/links")
                .contentType("application/json")
                .content("{\"url\":\"https://example.com\",\"expiresInMinutes\":0.5}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/links")
                .contentType("application/json")
                .content("{\"url\":\"https://example.com\",\"typo\":123}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unsupportedMethodAndContentTypeRemainClientErrors() throws Exception {
    mvc.perform(put("/api/links")).andExpect(status().isMethodNotAllowed());
    mvc.perform(post("/api/links").contentType("text/plain").content("hello"))
        .andExpect(status().isUnsupportedMediaType());
    mvc.perform(get("/api/links?limit=word")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/links/missing/stats")).andExpect(status().isNotFound());
  }

  @Test
  void dailyStatsExcludeHeadAndDisabledRequests() throws Exception {
    mvc.perform(
            post("/api/links")
                .contentType("application/json")
                .content("{\"url\":\"https://example.com\",\"alias\":\"daily-test\"}"))
        .andExpect(status().isCreated());
    mvc.perform(head("/s/daily-test")).andExpect(status().isFound());
    mvc.perform(get("/s/daily-test")).andExpect(status().isFound());
    mvc.perform(get("/api/links/daily-test/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.days.length()").value(30))
        .andExpect(jsonPath("$.days[29].visits").value(1));
    mvc.perform(delete("/api/links/daily-test")).andExpect(status().isNoContent());
    mvc.perform(get("/s/daily-test")).andExpect(status().isGone());
    assertThat(jdbc.queryForObject("SELECT SUM(visits) FROM daily_visits", Long.class))
        .isEqualTo(1);
  }

  @Test
  void concurrentHttpRequestsPreserveExactCounts() throws Exception {
    mvc.perform(
            post("/api/links")
                .contentType("application/json")
                .content("{\"url\":\"https://example.com\",\"alias\":\"concurrent\"}"))
        .andExpect(status().isCreated());
    java.util.concurrent.ExecutorService pool =
        java.util.concurrent.Executors.newFixedThreadPool(8);
    try {
      java.util.List<java.util.concurrent.Callable<Integer>> tasks = new java.util.ArrayList<>();
      for (int i = 0; i < 40; i++)
        tasks.add(() -> mvc.perform(get("/s/concurrent")).andReturn().getResponse().getStatus());
      for (var result : pool.invokeAll(tasks)) assertThat(result.get()).isEqualTo(302);
    } finally {
      pool.shutdownNow();
    }
    mvc.perform(get("/api/links/concurrent")).andExpect(jsonPath("$.visits").value(40));
    assertThat(jdbc.queryForObject("SELECT SUM(visits) FROM daily_visits", Long.class))
        .isEqualTo(40);
  }

  @Test
  void concurrentAliasAllocationHasExactlyOneWinner() throws Exception {
    java.util.concurrent.ExecutorService pool =
        java.util.concurrent.Executors.newFixedThreadPool(6);
    String body = "{\"url\":\"https://example.com\",\"alias\":\"contended\"}";
    try {
      java.util.List<java.util.concurrent.Callable<Integer>> tasks = new java.util.ArrayList<>();
      for (int i = 0; i < 12; i++)
        tasks.add(
            () ->
                mvc.perform(post("/api/links").contentType("application/json").content(body))
                    .andReturn()
                    .getResponse()
                    .getStatus());
      int created = 0;
      for (var result : pool.invokeAll(tasks)) {
        int status = result.get();
        assertThat(status).isIn(201, 409);
        if (status == 201) created++;
      }
      assertThat(created).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM links", Integer.class)).isEqualTo(1);
  }

  @Test
  void analyticsFailureRollsBackAggregateAndDoesNotRedirect() throws Exception {
    mvc.perform(
            post("/api/links")
                .contentType("application/json")
                .content("{\"url\":\"https://example.com\",\"alias\":\"rollback\"}"))
        .andExpect(status().isCreated());
    org.mockito.Mockito.doThrow(
            new org.springframework.dao.DataAccessResourceFailureException("test outage"))
        .when(repository)
        .recordDay(org.mockito.ArgumentMatchers.eq("rollback"), org.mockito.ArgumentMatchers.any());
    mvc.perform(get("/s/rollback")).andExpect(status().isServiceUnavailable());
    mvc.perform(get("/api/links/rollback")).andExpect(jsonPath("$.visits").value(0));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_visits", Integer.class)).isZero();
  }

  @Test
  void concurrentDeactivationDoesNotLoseAlreadyAdmittedClicks() throws Exception {
    mvc.perform(
            post("/api/links")
                .contentType("application/json")
                .content("{\"url\":\"https://example.com\",\"alias\":\"disable-race\"}"))
        .andExpect(status().isCreated());
    java.util.concurrent.ExecutorService pool =
        java.util.concurrent.Executors.newFixedThreadPool(8);
    int admitted = 0;
    try {
      java.util.List<java.util.concurrent.Callable<Integer>> tasks = new java.util.ArrayList<>();
      for (int i = 0; i < 30; i++) {
        if (i == 10)
          tasks.add(
              () ->
                  mvc.perform(delete("/api/links/disable-race"))
                      .andReturn()
                      .getResponse()
                      .getStatus());
        tasks.add(() -> mvc.perform(get("/s/disable-race")).andReturn().getResponse().getStatus());
      }
      for (var result : pool.invokeAll(tasks)) {
        int code = result.get();
        assertThat(code).isIn(302, 410, 204);
        if (code == 302) admitted++;
      }
    } finally {
      pool.shutdownNow();
    }
    mvc.perform(get("/s/disable-race")).andExpect(status().isGone());
    mvc.perform(get("/api/links/disable-race")).andExpect(jsonPath("$.visits").value(admitted));
  }
}
