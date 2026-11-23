package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayName("FactoryExamUploader — 화면에서 바로 탑재(내신 영어 5지선다 글 문항 → tp_exams·tp_questions)")
class FactoryExamUploaderTest {

  static final JsonMapper JSON = JsonMapper.builder().build();

  ObjectNode question(String answer) {
    ObjectNode q = JSON.createObjectNode().put("prompt", "다음 중 어법상 옳은 것은?").put("body", "새로 쓴 지문").put("answer", answer)
        .put("points", 3).put("explanation", "정답 해설");
    q.putArray("choices").add("가").add("나").add("다").add("라").add("마");
    return q;
  }

  ObjectNode file() {
    ObjectNode f = JSON.createObjectNode().put("examCode", "tp-naesin-sgm2-2602-r12")
        .put("title", "신구중 2학년 2학기 기말 12회 · Lesson 7·8 종합 예상문제 2회").put("subtitle", "Lesson 7·8 · 2문항 · 6점");
    f.putArray("questions").add(question("a")).add(question("d, B"));
    return f;
  }

  @Test
  @DisplayName("given 올바른 게시용 파일 when parse then 시험 코드는 대문자, 학교 페이지 주소, 한 개 고르기·모두 고르기 유형과 정답 형식이 정해진다")
  void parseValidFile() {
    // given & when
    FactoryExamUploader.Pack pack = FactoryExamUploader.parse(file());

    // then
    assertThat(pack.examCode()).isEqualTo("TP-NAESIN-SGM2-2602-R12");
    assertThat(pack.url()).isEqualTo("/naesintest/school/singu-m2");
    assertThat(pack.timeLimitSec()).isEqualTo(7200);
    assertThat(pack.totalPoints()).isEqualTo(6);
    assertThat(pack.questions()).extracting(FactoryExamUploader.Question::taskType).containsExactly("NAESIN_MC5", "NAESIN_MC5_MULTI");
    assertThat(pack.questions()).extracting(FactoryExamUploader.Question::answerKey).containsExactly("A", "B,D");
    assertThat(pack.questions().get(0).choicesJson()).isEqualTo("[\"가\",\"나\",\"다\",\"라\",\"마\"]");
  }

  @Test
  @DisplayName("given 모르는 학교·그림 문항·선택지 4개·정답 F·배점 0·해설 없음 when parse then 틀린 곳을 문항 번호와 함께 한 번에 알려 준다")
  void parseCollectsErrors() {
    // given
    ObjectNode f = file().put("examCode", "TP-NAESIN-XXM9-2602-R01");
    ObjectNode bad = question("F").put("prompt", "[[img:/naesin/x/q01.webp]]").put("points", 0).put("explanation", "");
    bad.putArray("choices").add("가").add("나").add("다").add("라");
    f.putArray("questions").add(question("A")).add(bad);

    // when & then
    assertThatThrownBy(() -> FactoryExamUploader.parse(f)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("학생 풀이 페이지에 없습니다").hasMessageContaining("2번: 그림 문항").hasMessageContaining("2번: choices")
        .hasMessageContaining("2번: answer").hasMessageContaining("2번: points").hasMessageContaining("2번: explanation")
        .hasMessageNotContaining("1번");
    assertThatThrownBy(() -> FactoryExamUploader.parse(JSON.createObjectNode().put("examCode", "SGM2"))).hasMessageContaining("examCode")
        .hasMessageContaining("title").hasMessageContaining("questions");
    assertThatThrownBy(() -> FactoryExamUploader.parse(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 새 시험 코드 when upload then 시험 1행(PUBLISHED)과 문항을 순서대로 넣는다")
  void uploadInserts() {
    // given
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(contains("COUNT"), eq(Integer.class), any(Object[].class))).thenReturn(0);
    when(jdbc.queryForObject(contains("SELECT exam_id"), eq(Long.class), any(Object[].class))).thenReturn(77L);

    // when
    FactoryExamUploader.Pack pack = new FactoryExamUploader(jdbc).upload(file(), "admin");

    // then
    assertThat(pack.questions()).hasSize(2);
    verify(jdbc).update(contains("INSERT INTO tp_exams"), eq("TP-NAESIN-SGM2-2602-R12"), anyString(), anyString(), any(), eq(7200), eq("admin"));
    verify(jdbc, times(2)).update(contains("INSERT INTO tp_questions"), any(Object[].class));
  }

  @Test
  @DisplayName("given 이미 있는 시험 코드 when upload then 덮어쓰지 않고 거절, archive는 그 회차만 보관 처리한다")
  void uploadRejectsDuplicateAndArchives() {
    // given
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(contains("COUNT"), eq(Integer.class), any(Object[].class))).thenReturn(1);
    FactoryExamUploader uploader = new FactoryExamUploader(jdbc);

    // when & then
    assertThatThrownBy(() -> uploader.upload(file(), "admin")).isInstanceOf(IllegalStateException.class).hasMessageContaining("이미");
    verify(jdbc, never()).update(contains("INSERT"), any(Object[].class));
    uploader.archive("TP-NAESIN-SGM2-2602-R12", "admin");
    verify(jdbc).update(contains("status = 'ARCHIVED'"), eq("admin"), eq("TP-NAESIN-SGM2-2602-R12"));
  }
}
