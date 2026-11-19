package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("2027 수능 영어 모션 해설 — 자료(data.json)·홈 카드·화면 계약")
class CsatEnglishMotionContractTest {

  static final String BASE = "static/img/csat/2027-en/";

  static String read(String path) throws IOException {
    return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("given 자료 when 정답 대조 then 빠른 정답 45개와 문항별 정답이 모두 같고 1~45번이 빠짐없다")
  void answersMatchQuickTable() throws IOException {
    // given
    JsonNode d = JsonMapper.builder().build().readTree(read(BASE + "data.json"));

    // when
    Set<Integer> covered = new HashSet<>();
    int checked = 0;
    for (JsonNode q : d.get("questions")) {
      for (JsonNode n : q.get("nos")) {
        int no = n.asInt();
        covered.add(no);
        int quick = d.get("quick").get(no - 1).get("answer").asInt();
        assertThat(q.get("answers").get(String.valueOf(no)).asInt()).as(no + "번").isEqualTo(quick);
        checked++;
      }
    }

    // then
    assertThat(d.get("quick")).hasSize(45);
    assertThat(checked).isEqualTo(45);
    assertThat(covered).hasSize(45);
    assertThat(d.get("answerBasis").asText()).contains("가답안");
  }

  @Test
  @DisplayName("given 자료 when 그림·선택지 해설 확인 then 그림 파일이 모두 있고, 문항마다 정답 이유가 하나씩 표시된다")
  void imagesExistAndOneCorrectReason() throws IOException {
    // given
    JsonNode d = JsonMapper.builder().build().readTree(read(BASE + "data.json"));
    int highlights = 0;

    // when & then
    for (JsonNode q : d.get("questions")) {
      for (JsonNode it : q.get("left")) {
        if ("img".equals(it.get("kind").asText())) {
          assertThat(new ClassPathResource(BASE + it.get("src").asText()).exists()).as(it.get("src").asText()).isTrue();
          highlights += it.get("highlights").size();
        }
      }
      for (JsonNode s : q.get("right")) {
        if (s.get("choices").size() > 0) {
          long ok = 0;
          for (JsonNode c : s.get("choices")) if (c.get("correct").asBoolean()) ok++;
          assertThat(ok).as(q.get("key").asText() + " " + s.get("title").asText()).isEqualTo(1);
        }
      }
    }
    assertThat(highlights).as("노란 근거 수").isGreaterThan(100);
  }

  @Test
  @DisplayName("given 홈 when 영상 아래 카드 then 2027 수능 영어 해설 모션과 해설 화면·PDF 링크가 있다")
  void homeCard() throws IOException {
    // given
    String html = read("templates/public/landing.html");

    // when & then
    assertThat(html).contains("id=\"csatHome\"", "2027 수능 영어 해설", "data-cm-stage", "data-cm-pause",
        "href=\"/csat/2027-english\"", "href=\"/csat/2027-english/pdf\"", "/js/csat_home.js", "/css/csat_motion.css");
    assertThat(html.indexOf("id=\"examPromo\"")).isLessThan(html.indexOf("id=\"csatHome\""));
    String js = read("static/js/csat_home.js");
    assertThat(js).contains("is-evidence", "is-answer", "IntersectionObserver");
  }

  @Test
  @DisplayName("given 해설 화면 when 단계 then 문제 → 근거 → 정답 → 해설 순서, 출처·가답안 안내")
  void pageSteps() throws IOException {
    // given
    String html = read("templates/public/csat_2027_english.html");
    String js = read("static/js/csat_page.js");

    // when & then
    assertThat(html).contains("id=\"csatMap\"", "id=\"csatView\"", "id=\"csatStep\"", "한국교육과정평가원", "가답안");
    assertThat(js).contains("'문제', '근거', '정답', '해설'", "'근거 보기', '정답 보기', '해설 보기', '다음 문항'", "download=1");
    assertThat(read("static/css/csat_motion.css")).contains("prefers-reduced-motion");
  }
}
