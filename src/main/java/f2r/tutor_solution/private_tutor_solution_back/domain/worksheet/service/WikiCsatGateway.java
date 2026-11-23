package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiIdentitySigner;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 내신위키(BuildIt Wiki)에 올라와 있는 수능·평가원 모의평가 영어 문항을 읽는다(읽기만).
 *
 * <p>위키 자료는 원장 계정의 작업 공간마다 따로 있으므로, 브라우저 중계(BuildItWikiProxyController)와 같은 신원 서명으로
 * 로그인한 사람으로서 부른다. 위키가 꺼져 있거나 응답이 이상하면 빈 목록을 돌려준다 — 학습지 화면의 다른 자료는 그대로 쓴다.
 */
@Component
public class WikiCsatGateway {

  private static final Logger log = LoggerFactory.getLogger(WikiCsatGateway.class);

  /** 위키 시험 한 개. examYear는 학년도. */
  public record Exam(long id, int examYear, String examType, String label, int questionCount) {}

  /** 위키 문항 한 개(글 문항). answer는 위키에 적힌 그대로. */
  public record Item(int no, String typeName, String passage, String prompt, List<String> choices, String answer,
      String solution, String groupLabel) {}

  private final WikiIdentitySigner signer;
  private final ObjectMapper json;
  private final String baseUrl;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  public WikiCsatGateway(WikiIdentitySigner signer, ObjectMapper json,
      @Value("${app.wiki.base-url:http://127.0.0.1:8000}") String baseUrl) {
    this.signer = signer;
    this.json = json;
    this.baseUrl = baseUrl;
  }

  public List<Exam> exams(AuthenticatedPrincipal p) {
    List<Exam> out = new ArrayList<>();
    for (JsonNode n : get(p, "/api/csat/exams")) {
      out.add(new Exam(n.path("id").asLong(), n.path("exam_year").asInt(), n.path("exam_type").asText(""),
          n.path("label").asText(""), n.path("question_count").asInt()));
    }
    return out;
  }

  public List<Item> questions(AuthenticatedPrincipal p, long examId) {
    List<Item> out = new ArrayList<>();
    for (JsonNode n : get(p, "/api/csat/exams/" + examId + "/questions")) {
      List<String> choices = new ArrayList<>();
      n.path("choices").forEach(c -> choices.add(c.isTextual() ? c.asText() : c.path("text").asText(c.toString())));
      out.add(new Item(n.path("question_no").asInt(), n.path("csat_type_name").asText(""), n.path("passage").asText(""),
          n.path("prompt").asText(""), choices, n.path("answer").asText(""), n.path("solution").asText(""),
          n.path("group_label").asText("")));
    }
    return out;
  }

  /** 위키 GET. 실패하면 빈 배열(로그만 남긴다). */
  JsonNode get(AuthenticatedPrincipal p, String path) {
    if (p == null) return json.createArrayNode();
    try {
      HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(10)).GET();
      signer.sign(b, p);
      HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() != 200) {
        log.warn("wiki csat {} -> {}", path, res.statusCode());
        return json.createArrayNode();
      }
      JsonNode node = json.readTree(res.body());
      return node != null && node.isArray() ? node : json.createArrayNode();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return json.createArrayNode();
    } catch (Exception e) {
      log.warn("wiki csat {} 실패: {}", path, e.getMessage());
      return json.createArrayNode();
    }
  }
}
