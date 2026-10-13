import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Small reproducible loopback load probe. Never follows destination redirects. */
class LoadCheck {
  public static void main(String[] args) throws Exception {
    String base = args.length == 0 ? "http://localhost:8092" : args[0];
    String alias = "load-" + UUID.randomUUID().toString().substring(0, 8);
    HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    String body = "{\"url\":\"https://example.com/load\",\"alias\":\"" + alias + "\"}";
    var created = client.send(HttpRequest.newBuilder(URI.create(base + "/api/links"))
        .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    if (created.statusCode() != 201) throw new IllegalStateException("Create failed: " + created.statusCode());
    ExecutorService pool = Executors.newFixedThreadPool(8);
    List<Double> latency = new ArrayList<>();
    long started = System.nanoTime();
    try {
      List<Callable<Double>> calls = new ArrayList<>();
      for (int i = 0; i < 120; i++) calls.add(() -> {
        long start = System.nanoTime();
        var result = client.send(HttpRequest.newBuilder(URI.create(base + "/s/" + alias))
            .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.discarding());
        if (result.statusCode() != 302) throw new IllegalStateException("Redirect failed: " + result.statusCode());
        return (System.nanoTime() - start) / 1_000_000.0;
      });
      for (Future<Double> result : pool.invokeAll(calls)) latency.add(result.get());
    } finally { pool.shutdownNow(); }
    double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
    String details = client.send(HttpRequest.newBuilder(URI.create(base + "/api/links/" + alias))
        .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString()).body();
    if (!details.matches("(?s).*\"visits\"\\s*:\\s*120\\s*[,}].*")) throw new IllegalStateException("Counter mismatch");
    Collections.sort(latency);
    System.out.printf(Locale.ROOT,
        "{\"requests\":120,\"workers\":8,\"successfulRedirects\":120,\"storedVisits\":120,\"elapsedSeconds\":%.3f,\"p50Ms\":%.2f,\"p95Ms\":%.2f,\"p99Ms\":%.2f}%n",
        seconds, latency.get(59), latency.get(113), latency.get(118));
  }
}
