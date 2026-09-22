package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DiscoveryHttpClient {

  private final HttpClient httpClient;
  private static final String USER_AGENT = "Mozilla/5.0 (compatible; NewStartOfficialUpdates/1.0; +https://feel2real.co.kr/newstart)";

  public DiscoveryHttpClient() {
    this.httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  public String fetchHtml(String url) {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(30))
          .header("User-Agent", USER_AGENT)
          .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
          .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
          .GET()
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() >= 400) {
        throw new DiscoveryHttpException("HTTP " + response.statusCode() + " for " + url);
      }

      return response.body();

    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new DiscoveryHttpException("Fetch failed for " + url, e);
    }
  }

  public static class DiscoveryHttpException extends RuntimeException {
    public DiscoveryHttpException(String message) { super(message); }
    public DiscoveryHttpException(String message, Throwable cause) { super(message, cause); }
  }
}