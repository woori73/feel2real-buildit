package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiExamContext;
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

/**
 * {@link MidtermWikiSourceGateway} 의 HTTP 구현. {@code WikiRouterAcademyAiProvider} 와 같은
 * base-url·서명 방식을 쓴다. Python 응답은 body 만 사용한다.
 */
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
  public List<WikiSourceRow> listByContext(AuthenticatedPrincipal tutor, WikiExamContext ctx) {
    String common = "school=" + enc(ctx.school())
        + "&grade=" + enc(ctx.grade())
        + "&year=" + ctx.year()
        + "&semester=" + enc(ctx.semester())
        + "&exam=" + enc(ctx.exam());
    List<WikiSourceRow> rows = new ArrayList<>();
    rows.addAll(fetchList(tutor, "/api/exam-materials?" + common, SCHOOL_PRINT, ctx));
    rows.addAll(fetchList(tutor, "/api/final-deliverables?" + common + "&kind=final", FINAL, ctx));
    return rows;
  }

  private List<WikiSourceRow> fetchList(
      AuthenticatedPrincipal tutor, String path, String kind, WikiExamContext ctx) {
    HttpResponse<byte[]> response;
    try {
      response = send(tutor, path, "GET");
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.warn("TUTOR Wiki 목록 조회 실패 kind={} : {}", kind, exception.toString());
      return List.of();
    }
    if (response.statusCode() / 100 != 2) {
      log.warn("TUTOR Wiki 목록 조회 비정상 status={} kind={}", response.statusCode(), kind);
      return List.of();
    }
    List<WikiSourceRow> rows = new ArrayList<>();
    try {
      JsonNode array = objectMapper.readTree(response.body());
      if (!array.isArray()) {
        return List.of();
      }
      for (JsonNode node : array) {
        // 학생 오답(PII)·HTML 없는 자료는 제외. core_type 는 exam-materials 응답에만 있다.
        if (SCHOOL_PRINT.equals(kind) && !"school_print".equals(node.path("core_type").asText())) {
          continue;
        }
        if (node.path("has_html").asInt(0) != 1) {
          continue;
        }
        long id = node.path("id").asLong(0);
        if (id <= 0) {
          continue;
        }
        rows.add(new WikiSourceRow(
            kind, id, node.path("title").asText(""),
            node.path("school").asText(ctx.school()),
            node.path("grade").asText(ctx.grade()),
            node.path("year").asInt(ctx.year()),
            node.path("semester").asText(ctx.semester()),
            node.path("exam").asText(ctx.exam()),
            node.path("created_at").asText("")));
      }
    } catch (IOException exception) {
      log.warn("TUTOR Wiki 목록 파싱 실패 kind={} : {}", kind, exception.toString());
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
    int status = response.statusCode();
    if (status == 404) {
      return Optional.empty();
    }
    if (status / 100 != 2) {
      throw new ResponseStatusException(
          status == 504 ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY,
          "TUTOR Wiki 자료를 불러오지 못했습니다.");
    }
    byte[] body = response.body();
    if (body == null || body.length == 0) {
      return Optional.empty();
    }
    return Optional.of(body); // body 만 사용 — 응답 헤더는 버린다.
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
