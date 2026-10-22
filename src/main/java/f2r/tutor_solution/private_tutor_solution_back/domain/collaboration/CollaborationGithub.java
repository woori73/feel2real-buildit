package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Fixed repository, read-only. No GitHub mutation or model-controlled URLs. */
@Component
public class CollaborationGithub {
  private final String token;
  private final ObjectMapper json = new ObjectMapper();
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public CollaborationGithub(@Value("${app.collaboration.github-read-token:}") String token) {
    this.token = token;
  }

  JsonNode get(String path) throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create("https://api.github.com/repos/woori73/feel2real-buildit/" + path))
            .timeout(Duration.ofSeconds(12))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28");
    if (!token.isBlank()) request.header("Authorization", "Bearer " + token);
    var response = client.send(request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
    byte[] bytes;
    try (var in = response.body()) {
      bytes = in.readNBytes(262145);
    }
    if (response.statusCode() != 200 || bytes.length > 262144)
      throw new IllegalStateException("GitHub read unavailable");
    return json.readTree(bytes);
  }

  public String snapshot(int number) throws Exception {
    if (number < 1 || number > 10_000_000) throw new IllegalArgumentException("PR number");
    var pr = get("pulls/" + number);
    String sha = pr.path("head").path("sha").asText();
    if (!sha.matches("[a-f0-9]{40}")) throw new IllegalStateException("GitHub head invalid");
    var checks = get("commits/" + sha + "/check-runs?per_page=100");
    var results = new ArrayList<Map<String, Object>>();
    for (var check : checks.path("check_runs")) {
      String status = check.path("status").asText(), conclusion = check.path("conclusion").asText();
      results.add(
          Map.of(
              "id",
              check.path("id").asLong(),
              "status",
              Set.of("queued", "in_progress", "completed", "waiting", "pending", "requested")
                      .contains(status)
                  ? status
                  : "unknown",
              "conclusion",
              Set.of(
                          "success",
                          "failure",
                          "cancelled",
                          "skipped",
                          "neutral",
                          "timed_out",
                          "action_required",
                          "stale")
                      .contains(conclusion)
                  ? conclusion
                  : "pending"));
    }
    return json.writeValueAsString(
        Map.of(
            "pr",
            number,
            "head",
            sha,
            "state",
            pr.path("state").asText().equals("open") ? "open" : "closed",
            "merged",
            pr.path("merged").asBoolean(),
            "checks",
            results,
            "totalChecks",
            checks.path("total_count").asInt(),
            "truncated",
            checks.path("total_count").asInt() > 100));
  }
}
