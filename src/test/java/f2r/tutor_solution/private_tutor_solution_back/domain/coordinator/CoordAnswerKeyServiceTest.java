package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpQuestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpQuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.TestPrepService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("unchecked")
@DisplayName("CoordAnswerKeyService — 채점 참고용 디지털 내신 정답·해설")
class CoordAnswerKeyServiceTest {

  static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(1L, "admin", "ADMIN");
  TpExamRepository exams;
  TpQuestionRepository questions;
  CoordAnswerKeyService service;

  static <T> T entity(Class<T> type, Map<String, Object> fields) {
    try {
      var c = type.getDeclaredConstructor();
      c.setAccessible(true);
      T t = c.newInstance();
      fields.forEach((k, v) -> ReflectionTestUtils.setField(t, k, v));
      return t;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @BeforeEach
  void setUp() {
    exams = mock(TpExamRepository.class);
    questions = mock(TpQuestionRepository.class);
    service = new CoordAnswerKeyService(exams, questions);
    TpExam e = entity(TpExam.class, Map.of("examId", 7L, "examCode", "TP-NAESIN-SGM2-2602-R11", "kind",
        TestPrepService.KIND_NAESIN, "title", "신구중 2학년 2학기 기말 11회", "subtitle", "Lesson 7·8"));
    when(exams.findByKindAndStatusOrderByExamIdAsc(TestPrepService.KIND_NAESIN, "PUBLISHED")).thenReturn(List.of(e));
    when(exams.findByExamCodeAndStatus("TP-NAESIN-SGM2-2602-R11", "PUBLISHED")).thenReturn(Optional.of(e));
    when(questions.countByExamId(7L)).thenReturn(40L);
    when(questions.findByExamIdOrderByOrderNoAsc(7L)).thenReturn(List.of(
        entity(TpQuestion.class, Map.of("orderNo", 1, "answerKey", "A", "points", 2, "explanation", "정답 ① · 대화 내용 일치")),
        entity(TpQuestion.class, Map.of("orderNo", 2, "answerKey", "B,D", "points", 3, "explanation", "정답 ②④"))));
  }

  @Test
  @DisplayName("given 객관식 글자 when circled then 원문자, 여러 개는 쉼표, 서술형은 그대로")
  void circled() {
    // given & when & then
    assertThat(CoordAnswerKeyService.circled("B")).isEqualTo("②");
    assertThat(CoordAnswerKeyService.circled("a, c")).isEqualTo("①,③");
    assertThat(CoordAnswerKeyService.circled("has been")).isEqualTo("has been");
    assertThat(CoordAnswerKeyService.circled(null)).isNull();
  }

  @Test
  @DisplayName("given examCode 없이 학교 검색 when lookup then 맞는 시험 목록과 문항 수")
  void listExams() {
    // given & when
    Map<String, Object> out = service.lookup(ADMIN, null, "신구중");
    Map<String, Object> none = service.lookup(ADMIN, null, "압구정");

    // then
    assertThat((List<?>) out.get("exams")).hasSize(1).first()
        .satisfies(m -> assertThat((Map<String, Object>) m).containsEntry("questionCount", 40L));
    assertThat((List<?>) none.get("exams")).isEmpty();
  }

  @Test
  @DisplayName("given examCode when lookup then 문항별 정답(원문자)·배점·해설")
  void questionsOfExam() {
    // given & when
    Map<String, Object> out = service.lookup(ADMIN, "TP-NAESIN-SGM2-2602-R11", null);

    // then
    List<?> rows = (List<?>) out.get("questions");
    assertThat((Map<String, Object>) rows.get(0)).containsEntry("no", 1).containsEntry("answer", "①").containsEntry("points", 2);
    assertThat((Map<String, Object>) rows.get(1)).containsEntry("answer", "②,④");
  }

  @Test
  @DisplayName("given 없는 시험 when lookup then 거절")
  void unknownExam() {
    // given & when & then
    assertThatThrownBy(() -> service.lookup(ADMIN, "TP-NAESIN-NONE", null)).isInstanceOf(IllegalArgumentException.class);
  }
}
