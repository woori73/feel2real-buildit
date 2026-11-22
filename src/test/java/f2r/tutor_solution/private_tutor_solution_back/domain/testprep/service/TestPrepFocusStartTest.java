package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.AttemptView;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.StartAttemptRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpAttempt;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpQuestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpAttemptAnswerRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpAttemptRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpQuestionRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("TestPrepService.start — TOEIC 파트·유형만 골라 풀기")
class TestPrepFocusStartTest {

  TpExamRepository exams;
  TpQuestionRepository questions;
  TpAttemptRepository attempts;
  TestPrepService service;
  final AuthenticatedPrincipal student = new AuthenticatedPrincipal(7L, "student", "STUDENT");

  @BeforeEach
  void setUp() {
    exams = mock(TpExamRepository.class);
    questions = mock(TpQuestionRepository.class);
    attempts = mock(TpAttemptRepository.class);
    service = new TestPrepService(exams, questions, attempts, mock(TpAttemptAnswerRepository.class), new ObjectMapper(),
        mock(NaesinAccessService.class));
    when(attempts.save(any())).thenAnswer(inv -> {
      TpAttempt a = inv.getArgument(0);
      a.setAttemptId(99L);
      return a;
    });
  }

  private void exam(String kind, String code, int limitSec, List<TpQuestion> list) throws Exception {
    TpExam e = BeanUtils.instantiateClass(TpExam.class.getDeclaredConstructor());
    e.setExamId(1L);
    e.setKind(kind);
    e.setExamCode(code);
    e.setTitle(code);
    e.setTimeLimitSec(limitSec);
    when(exams.findByExamCodeAndStatus(code, "PUBLISHED")).thenReturn(Optional.of(e));
    when(questions.findByExamIdOrderByOrderNoAsc(1L)).thenReturn(list);
  }

  private static TpQuestion question(long id, String taskType, String explanation) throws Exception {
    TpQuestion q = BeanUtils.instantiateClass(TpQuestion.class.getDeclaredConstructor());
    q.setQuestionId(id);
    q.setExamId(1L);
    q.setSection("READING");
    q.setTaskType(taskType);
    q.setPrompt("prompt " + id);
    q.setAudioMode("NONE");
    q.setAutoScore(true);
    q.setExplanation(explanation);
    q.setOrderNo((int) id);
    return q;
  }

  /** Part 5 30문항(1~5번은 품사) + Part 6 16문항 + Part 7 54문항 = 100문항. */
  private static List<TpQuestion> readingSet() throws Exception {
    List<TpQuestion> list = new ArrayList<>();
    for (int i = 1; i <= 30; i++) list.add(question(i, "TOEIC_P5", i <= 5 ? "[문법-품사(부사 자리)] 정답 (A)." : "[어휘-동사] 정답 (B)."));
    for (int i = 31; i <= 46; i++) list.add(question(i, "TOEIC_P6", "[Part6-문장삽입] 정답 (C)."));
    for (int i = 47; i <= 100; i++) list.add(question(i, "TOEIC_P7", "[세부 사항] 정답 (D)."));
    return list;
  }

  @Test
  @DisplayName("given 100문항 75분 TOEIC 세트 when Part 5·6만 골라 시작 then 46문항만 나오고 부분 응시로 기록되며 시간은 문항 수만큼만 준다")
  void start_partsOnly() throws Exception {
    // given
    exam("TOEIC", "TP-TOEIC-RC-01", 4500, readingSet());

    // when
    AttemptView view = service.start(student,
        new StartAttemptRequest("TP-TOEIC-RC-01", null, List.of("TOEIC_P5", "TOEIC_P6"), null));

    // then
    assertThat(view.questions()).hasSize(46);
    assertThat(view.questions()).allMatch(q -> q.taskType().equals("TOEIC_P5") || q.taskType().equals("TOEIC_P6"));
    ArgumentCaptor<TpAttempt> saved = ArgumentCaptor.forClass(TpAttempt.class);
    verify(attempts).save(saved.capture());
    assertThat(saved.getValue().getQuestionIdsJson()).startsWith("[1,2,3").endsWith(",46]");
    assertThat(Duration.between(saved.getValue().getStartedAt(), saved.getValue().getDeadlineAt()).getSeconds()).isEqualTo(2100);
  }

  @Test
  @DisplayName("given 같은 세트 when Part 5의 품사 유형만 골라 시작 then 그 유형 5문항만 나온다")
  void start_focusTagWithinPart() throws Exception {
    // given
    exam("TOEIC", "TP-TOEIC-RC-01", 4500, readingSet());

    // when
    AttemptView view = service.start(student, new StartAttemptRequest("TP-TOEIC-RC-01", null, List.of("TOEIC_P5"), "문법-품사"));

    // then
    assertThat(view.questions()).extracting(q -> q.questionId()).containsExactly(1L, 2L, 3L, 4L, 5L);
  }

  @Test
  @DisplayName("given 그 세트에 없는 파트 when 골라 시작 then 400이고 응시를 만들지 않는다")
  void start_noQuestionsForPart() throws Exception {
    // given
    exam("TOEIC", "TP-TOEIC-RC-01", 4500, readingSet());

    // when & then
    assertThatThrownBy(() -> service.start(student, new StartAttemptRequest("TP-TOEIC-RC-01", null, List.of("TOEIC_P1"), null)))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
    verify(attempts, never()).save(any());
  }

  @Test
  @DisplayName("given TOEIC이 아닌 세트 when 파트를 골라 시작 then 400 — iBT·SAT·내신의 응시 계약은 그대로다")
  void start_focusOnlyForToeic() throws Exception {
    // given
    exam("SAT", "TP-SAT-01", 8040, List.of(question(1, "SAT_RW", null)));

    // when & then
    assertThatThrownBy(() -> service.start(student, new StartAttemptRequest("TP-SAT-01", null, List.of("SAT_RW"), null)))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
    verify(attempts, never()).save(any());
  }

  @Test
  @DisplayName("given 파트를 고르지 않은 요청 when 시작 then 전체 문항·세트 시간 그대로이고 부분 응시로 기록하지 않는다")
  void start_fullSetUnchanged() throws Exception {
    // given
    exam("TOEIC", "TP-TOEIC-RC-01", 4500, readingSet());

    // when
    AttemptView view = service.start(student, new StartAttemptRequest("TP-TOEIC-RC-01", null));

    // then
    assertThat(view.questions()).hasSize(100);
    ArgumentCaptor<TpAttempt> saved = ArgumentCaptor.forClass(TpAttempt.class);
    verify(attempts).save(saved.capture());
    assertThat(saved.getValue().getQuestionIdsJson()).isNull();
    assertThat(Duration.between(saved.getValue().getStartedAt(), saved.getValue().getDeadlineAt()).getSeconds()).isEqualTo(4500);
  }

  @Test
  @DisplayName("given 같은 세트 when Part 7 끝부분(176~200번에 해당하는 번호 구간)만 골라 시작 then 그 구간 문항만 나오고 문항당 45초를 준다")
  void start_orderRange() throws Exception {
    // given — 이 세트의 Part 7은 47~100번
    exam("TOEIC", "TP-TOEIC-RC-01", 4500, readingSet());

    // when
    AttemptView view = service.start(student,
        new StartAttemptRequest("TP-TOEIC-RC-01", null, List.of("TOEIC_P7"), null, 76, 100));

    // then
    assertThat(view.questions()).hasSize(25);
    assertThat(view.questions().get(0).orderNo()).isEqualTo(76);
    ArgumentCaptor<TpAttempt> saved = ArgumentCaptor.forClass(TpAttempt.class);
    verify(attempts).save(saved.capture());
    assertThat(Duration.between(saved.getValue().getStartedAt(), saved.getValue().getDeadlineAt()).getSeconds()).isEqualTo(1140);
  }

  @Test
  @DisplayName("given 읽기 문항 when toeicPartialTimeLimitSec then 세트 시간과 상관없이 문항당 45초를 분 단위로 올리고 최소 1분")
  void partialTime_readingIsOfficialPerQuestion() throws Exception {
    // given
    List<TpQuestion> set = readingSet();

    // when & then — 120분 194문항 세트(문항당 37초)에서 골라도 읽기는 45초
    assertThat(TestPrepService.toeicPartialTimeLimitSec(set.subList(0, 30), 7200, 194)).isEqualTo(1380); // 22.5분 → 23분
    assertThat(TestPrepService.toeicPartialTimeLimitSec(set.subList(0, 46), 4500, 100)).isEqualTo(2100); // 34.5분 → 35분
    assertThat(TestPrepService.toeicPartialTimeLimitSec(set.subList(75, 100), 4500, 100)).isEqualTo(1140); // 18.75분 → 19분
    assertThat(TestPrepService.toeicPartialTimeLimitSec(set.subList(0, 1), 4500, 100)).isEqualTo(60);
  }

  @Test
  @DisplayName("given 듣기 문항 when toeicPartialTimeLimitSec then 대본을 문항마다 다시 읽어 주므로 27초와 세트의 문항당 시간 가운데 긴 쪽을 쓴다")
  void partialTime_listeningKeepsSetPace() throws Exception {
    // given
    List<TpQuestion> listening = new ArrayList<>();
    for (int i = 1; i <= 25; i++) {
      TpQuestion q = question(i, "TOEIC_P2", "[Part2-의문사(Where)] 정답 (A).");
      q.setSection("LISTENING");
      listening.add(q);
    }

    // when & then
    assertThat(TestPrepService.toeicPartialTimeLimitSec(listening, 7200, 194)).isEqualTo(960); // 25 × 37.1초 → 16분
    assertThat(TestPrepService.toeicPartialTimeLimitSec(listening, 2000, 100)).isEqualTo(720); // 25 × 27초 → 12분
  }

  @Test
  @DisplayName("given 문항 when matchesFocus then 파트와 유형을 둘 다 주면 둘 다 맞아야 하고 비어 있으면 모두 맞는다")
  void matchesFocus() throws Exception {
    // given
    TpQuestion q = question(1, "TOEIC_P5", "[문법-품사(부사 자리)] 정답 (A).");

    // when & then
    assertThat(TestPrepService.matchesFocus(q, List.of("TOEIC_P5"), null)).isTrue();
    assertThat(TestPrepService.matchesFocus(q, List.of("TOEIC_P6"), null)).isFalse();
    assertThat(TestPrepService.matchesFocus(q, null, "문법-품사")).isTrue();
    assertThat(TestPrepService.matchesFocus(q, List.of("TOEIC_P5"), "어휘-동사")).isFalse();
    assertThat(TestPrepService.matchesFocus(q, List.of(), " ")).isTrue();
  }
}
