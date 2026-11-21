package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.GradingForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.GradingView;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentAppCode;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentExamAssignment;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentExamResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.repository.StudentExamAssignmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.repository.StudentExamResultRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentAppCodeService;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentAppPrincipals;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentExamAppService;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.ReviewItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.ReviewView;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpAttempt;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpAttemptRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpQuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.TestPrepService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** 재원생 앱 학생 쪽: 배정된 시험만 시작, 제출하면 응시 기록·학생부 채점 기록 자동 저장(무DB). */
class StudentExamAppServiceTest {

  private final StudentAppCodeService codes = mock(StudentAppCodeService.class);
  private final StudentExamAssignmentRepository assignments = mock(StudentExamAssignmentRepository.class);
  private final StudentExamResultRepository results = mock(StudentExamResultRepository.class);
  private final TestPrepService testPrep = mock(TestPrepService.class);
  private final TpExamRepository exams = mock(TpExamRepository.class);
  private final TpQuestionRepository questions = mock(TpQuestionRepository.class);
  private final TpAttemptRepository attempts = mock(TpAttemptRepository.class);
  private final EasyLessonRecordService lessonRecords = mock(EasyLessonRecordService.class);
  private final EasyStudentRepository students = mock(EasyStudentRepository.class);
  private final StudentExamAppService service = new StudentExamAppService(codes, assignments, results, testPrep, exams,
      questions, attempts, lessonRecords, students, new ObjectMapper());

  private final StudentAppCode code = code();
  private final AuthenticatedPrincipal p = StudentAppPrincipals.of(code);

  private static StudentAppCode code() {
    StudentAppCode c = new StudentAppCode(1L, 7L, "h".repeat(64), "QT", 9L, LocalDateTime.now());
    ReflectionTestUtils.setField(c, "codeId", 3L);
    return c;
  }

  private ReviewItem item(int no, boolean correct, String explanation) {
    return new ReviewItem((long) no, "S", "NAESIN_MC5", null, null, null, List.of(), correct ? "B" : "A", "B", correct,
        true, explanation, no, null, null);
  }

  @Test
  @DisplayName("given 배정 안 된 시험 when 시작하면 then 403이고 응시는 만들지 않는다")
  void start_rejectsUnassigned() {
    // given
    when(codes.activeSession(3L, 7L)).thenReturn(Optional.of(code));
    when(assignments.findByBusinessIdAndStudentIdAndExamCode(1L, 7L, "X")).thenReturn(Optional.empty());
    // when / then
    assertThatThrownBy(() -> service.start(p, "X")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("배정");
    verify(testPrep, never()).start(any(), any());
  }

  @Test
  @DisplayName("given 뺀(removedAt) 배정 when 시작하면 then 403")
  void start_rejectsRemovedAssignment() {
    // given
    StudentExamAssignment a = new StudentExamAssignment(1L, 7L, "X", 9L, LocalDateTime.now());
    a.remove(LocalDateTime.now());
    when(codes.activeSession(3L, 7L)).thenReturn(Optional.of(code));
    when(assignments.findByBusinessIdAndStudentIdAndExamCode(1L, 7L, "X")).thenReturn(Optional.of(a));
    // when / then
    assertThatThrownBy(() -> service.start(p, "X")).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  @DisplayName("given 폐기된 코드 when 앱 기능을 부르면 then 401")
  void session_revokedIs401() {
    // given
    when(codes.activeSession(3L, 7L)).thenReturn(Optional.empty());
    // when / then
    assertThatThrownBy(() -> service.me(p)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("다시 로그인");
  }

  @Test
  @DisplayName("given 두 번째 응시 제출 when 기록하면 then 2번째 응시·점수·걸린 시간·오답(번호·유형·내 답/정답)이 학생부(source=APP)와 응시 기록에 남는다")
  void submit_recordsResultAndGrading() {
    // given
    when(codes.activeSession(3L, 7L)).thenReturn(Optional.of(code));
    TpAttempt attempt = new TpAttempt();
    attempt.setUserId(-7L); attempt.setExamId(11L); attempt.setStatus("SUBMITTED");
    LocalDateTime start = LocalDateTime.of(2026, 11, 26, 16, 0, 0);
    attempt.setStartedAt(start); attempt.setSubmittedAt(start.plusSeconds(754));
    when(attempts.findById(50L)).thenReturn(Optional.of(attempt));
    TpExam exam = mock(TpExam.class);
    when(exam.getExamCode()).thenReturn("TP-NAESIN-APM2-2602-R01");
    when(exam.getTitle()).thenReturn("압구정중 2학년 기말 1회");
    when(exams.findById(11L)).thenReturn(Optional.of(exam));
    when(testPrep.review(p, 50L)).thenReturn(new ReviewView(50L, "t", "NAESIN", null, false, null, null, List.of(),
        List.of(item(1, false, "정답 ② · 발음부호 (3점)\n설명"), item(2, true, "정답 ②"), item(3, false, "정답 ② · 어법 (4점)\n어법 포인트: 관계대명사 what")),
        null, 4, 11));
    when(results.findByAttemptId(50L)).thenReturn(Optional.empty());
    when(results.countByBusinessIdAndStudentIdAndExamCode(1L, 7L, "TP-NAESIN-APM2-2602-R01")).thenReturn(1L);
    when(lessonRecords.createGrading(eq(1L), eq(7L), any(GradingForm.class), eq("APP"))).thenReturn(
        new GradingView(99L, 7L, null, "2026-11-26", null, null, 3, 1, List.of(), null, "GRADED", "APP", List.of()));
    when(results.save(any(StudentExamResult.class))).thenAnswer(i -> i.getArgument(0));
    // when
    service.submit(p, 50L, null);
    // then
    ArgumentCaptor<GradingForm> form = ArgumentCaptor.forClass(GradingForm.class);
    verify(lessonRecords).createGrading(eq(1L), eq(7L), form.capture(), eq("APP"));
    assertThat(form.getValue().gradedOn()).isEqualTo("2026-11-26");
    assertThat(form.getValue().material()).isEqualTo("압구정중 2학년 기말 1회 · 2번째 응시");
    assertThat(form.getValue().totalCount()).isEqualTo(3);
    assertThat(form.getValue().correctCount()).isEqualTo(1);
    assertThat(form.getValue().wrongItems()).extracting(w -> w.no() + "|" + w.topic() + "|" + w.reason())
        .containsExactly("1|발음부호|내 답 ① · 정답 ②", "3|관계대명사 what|내 답 ① · 정답 ②");
    assertThat(form.getValue().feedback()).contains("2번째 응시", "점수 4/11", "걸린 시간 12분 34초");
    ArgumentCaptor<StudentExamResult> saved = ArgumentCaptor.forClass(StudentExamResult.class);
    verify(results).save(saved.capture());
    assertThat(saved.getValue().getAttemptNo()).isEqualTo(2);
    assertThat(saved.getValue().getDurationSec()).isEqualTo(754);
    assertThat(saved.getValue().getGradingId()).isEqualTo(99L);
    assertThat(saved.getValue().getCheckedAt()).isNull();
  }

  @Test
  @DisplayName("given 이미 기록한 응시 when 다시 제출하면 then 학생부·응시 기록을 또 만들지 않는다")
  void submit_isIdempotent() {
    // given
    when(codes.activeSession(3L, 7L)).thenReturn(Optional.of(code));
    when(results.findByAttemptId(50L)).thenReturn(Optional.of(mock(StudentExamResult.class)));
    // when
    service.submit(p, 50L, null);
    // then
    verify(lessonRecords, never()).createGrading(any(), any(), any(), any());
    verify(results, never()).save(any());
  }

  @Test
  @DisplayName("given 해설 문자열 when 오답 유형을 뽑으면 then 어법 포인트 > 첫 줄 유형 > 빈 값 순")
  void topic_parsing() {
    // given / when / then
    assertThat(ReflectionTestUtils.<String>invokeMethod(StudentExamAppService.class, "topic", "정답 ④ · 어휘(영영풀이) (3점)\n...")).isEqualTo("어휘(영영풀이)");
    assertThat(ReflectionTestUtils.<String>invokeMethod(StudentExamAppService.class, "topic", "정답 ④ · 어법 (4점)\n어법 포인트: 분사구문")).isEqualTo("분사구문");
    assertThat(ReflectionTestUtils.<String>invokeMethod(StudentExamAppService.class, "topic", "설명만 있음")).isEmpty();
    assertThat(ReflectionTestUtils.<String>invokeMethod(StudentExamAppService.class, "topic", (Object) null)).isEmpty();
    assertThat(ReflectionTestUtils.<String>invokeMethod(StudentExamAppService.class, "circ", "B,D")).isEqualTo("②, ④");
    assertThat(ReflectionTestUtils.<String>invokeMethod(StudentExamAppService.class, "circ", (Object) null)).isEqualTo("없음");
  }
}
