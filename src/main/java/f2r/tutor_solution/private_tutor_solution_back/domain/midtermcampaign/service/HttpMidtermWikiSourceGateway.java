package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiCampaignScope;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiIdentitySigner;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Campaign-scoped TUTOR Wiki candidate endpoint의 signed HTTP gateway. */
@Component
public class HttpMidtermWikiSourceGateway implements MidtermWikiSourceGateway {

  private static final Logger log = LoggerFactory.getLogger(HttpMidtermWikiSourceGateway.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final String SCHOOL_PRINT = "school-print";
  private static final String FINAL = "final";

  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WikiIdentitySigner identitySigner;
  private final String baseUrl;

  public HttpMidtermWikiSourceGateway(
      WikiIdentitySigner identitySigner,
      @Value("${app.wiki.ai-router.base-url:http://127.0.0.1:8000}") String baseUrl) {
    this.identitySigner = identitySigner;
    this.baseUrl = baseUrl;
  }

  @Override
  public List<WikiSourceRow> listCandidates(
      AuthenticatedPrincipal tutor, WikiCampaignScope scope) {
    String path = "/api/live-material-candidates?year=" + scope.year()
        + "&semester=" + enc(scope.semester())
        + "&exam=" + enc(scope.exam());
    HttpResponse<byte[]> response;
    try {
      response = send(tutor, path, "GET");
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.warn("TUTOR Wiki LIVE 후보 조회 실패: {}", exception.toString());
      return List.of();
    }
    if (response.statusCode() / 100 != 2) {
      log.warn("TUTOR Wiki LIVE 후보 조회 비정상 status={}", response.statusCode());
      return List.of();
    }

    List<WikiSourceRow> rows = new ArrayList<>();
    try {
      JsonNode array = objectMapper.readTree(response.body());
      if (!array.isArray()) {
        return List.of();
      }
      for (JsonNode node : array) {
        String kind = node.path("sourceKind").asText("");
        if (!SCHOOL_PRINT.equals(kind) && !FINAL.equals(kind)) {
          continue;
        }
        if (!node.path("hasHtml").asBoolean(false)) {
          continue;
        }
        long id = node.path("sourceId").asLong(0);
        int year = node.path("year").asInt(0);
        String semester = node.path("semester").asText("");
        String exam = node.path("exam").asText("");
        // Upstream compromise/misconfiguration cannot broaden the campaign scope.
        if (id <= 0 || year != scope.year()
            || !scope.semester().equals(semester) || !scope.exam().equals(exam)) {
          continue;
        }
        rows.add(new WikiSourceRow(
            kind, id, "",
            node.path("school").asText(""),
            node.path("grade").asText(""),
            year, semester, exam,
            node.path("createdAt").asText("")));
      }
    } catch (IOException exception) {
      log.warn("TUTOR Wiki LIVE 후보 파싱 실패: {}", exception.toString());
      return List.of();
    }
    return rows;
  }

  @Override
  public Optional<byte[]> fetchHtml(AuthenticatedPrincipal tutor, String kind, long id) {
    String path = switch (kind) {
      case SCHOOL_PRINT -> "/api/exam-materials/" + id + "/html";
      case FINAL -> "/api/final-deliverables/" + id + "/html";
      default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "알 수 없는 자료 종류입니다.");
    };
    HttpResponse<byte[]> response;
    try {
      response = send(tutor, path, "GET");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TUTOR Wiki 연결에 실패했습니다.");
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TUTOR Wiki 연결에 실패했습니다.");
    }
    if (response.statusCode() == 404) {
      return Optional.empty();
    }
    if (response.statusCode() / 100 != 2) {
      throw new ResponseStatusException(
          response.statusCode() == 504 ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY,
          "TUTOR Wiki 자료를 불러오지 못했습니다.");
    }
    byte[] body = response.body();
    return body == null || body.length == 0 ? Optional.empty() : Optional.of(body);
  }

  private HttpResponse<byte[]> send(AuthenticatedPrincipal tutor, String path, String method)
      throws IOException, InterruptedException {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
        .timeout(TIMEOUT)
        .method(method, HttpRequest.BodyPublishers.noBody());
    identitySigner.sign(builder, tutor);
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
  }

  private static String enc(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}
