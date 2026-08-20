package f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.dto.ScoreTimelineResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.Exam;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.ExamType;
import f2r.tutor_solution.private_tutor_solution_back.domain.score.entity.StudentScore;
import f2r.tutor_solution.private_tutor_solution_back.domain.score.repository.StudentScoreRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScoreTimelineServiceTest {

  private StudentScoreRepository studentScoreRepository;
  private ConversationRepository conversationRepository;
  private ConversationSourceRepository conversationSourceRepository;
  private AuthenticatedUserResolver authResolver;
  private ScoreTimelineService service;

  @BeforeEach
  void setUp() {
    studentScoreRepository = mock(StudentScoreRepository.class);
    conversationRepository = mock(ConversationRepository.class);
    conversationSourceRepository = mock(ConversationSourceRepository.class);
    authResolver = mock(AuthenticatedUserResolver.class);
    service = new ScoreTimelineService(
        studentScoreRepository,
        conversationRepository,
        conversationSourceRepository,
        authResolver);
  }

  @Test
  @DisplayName("forStudent — 점수가 없으면 빈 points")
  void empty_when_no_scores() {
    when(studentScoreRepository.findTimelineByStudent(99L)).thenReturn(List.of());

    ScoreTimelineResponse res = service.forStudent(99L);

    assertThat(res.points()).isEmpty();
  }

  @Test
  @DisplayName("forStudent — 시계열 순서 유지 + accuracyPercent 반올림")
  void converts_to_points_with_percent() {
    StudentScore s1 = sampleScore(1L, 100L, "2024 1학기 중간", LocalDate.of(2024, 4, 25), 10, 6);
    StudentScore s2 = sampleScore(2L, 101L, "2024 1학기 기말", LocalDate.of(2024, 6, 28), 10, 7);
    StudentScore s3 = sampleScore(3L, 102L, "2024 2학기 중간", LocalDate.of(2024, 10, 15), 7, 5); // 71.4 → 71
    when(studentScoreRepository.findTimelineByStudent(99L)).thenReturn(List.of(s1, s2, s3));

    ScoreTimelineResponse res = service.forStudent(99L);

    assertThat(res.points()).hasSize(3);
    assertThat(res.points().get(0).examDisplayName()).isEqualTo("2024 1학기 중간");
    assertThat(res.points().get(0).accuracyPercent()).isEqualTo(60);
    assertThat(res.points().get(1).accuracyPercent()).isEqualTo(70);
    assertThat(res.points().get(2).accuracyPercent()).isEqualTo(71); // 71.43 반올림
  }

  @Test
  @DisplayName("forStudent — totalQuestions=0 이면 percent=0 (NaN/Infinity 방어)")
  void zero_total_returns_zero_percent() {
    StudentScore s = sampleScore(1L, 100L, "비정상", LocalDate.of(2024, 1, 1), 0, 0);
    when(studentScoreRepository.findTimelineByStudent(99L)).thenReturn(List.of(s));

    ScoreTimelineResponse res = service.forStudent(99L);

    assertThat(res.points().get(0).accuracyPercent()).isZero();
  }

  @Test
  @DisplayName("forStudent — 점수가 채워진 신규 데이터는 점수 기반으로 percent 계산 (정답률 폴백 안 함)")
  void point_based_when_total_points_present() {
    // given — 정답률은 50%(5/10)이지만 점수는 75%(75/100) — 어려운 문제를 맞춰 점수 가중치 높음
    StudentScore s = sampleScoreWithPoints(1L, 100L, "신규 시험", LocalDate.of(2026, 4, 1), 10, 5, 100, 75);
    when(studentScoreRepository.findTimelineByStudent(99L)).thenReturn(List.of(s));

    // when
    ScoreTimelineResponse res = service.forStudent(99L);

    // then
    ScoreTimelineResponse.Point p = res.points().get(0);
    assertThat(p.totalPoints()).isEqualTo(100);
    assertThat(p.earnedPoints()).isEqualTo(75);
    assertThat(p.accuracyPercent()).isEqualTo(75); // 점수 기반
  }

  @Test
  @DisplayName("forStudent — 구 데이터(totalPoints=null)는 정답률로 폴백")
  void fallback_to_accuracy_when_total_points_null() {
    // given — 점수 컬럼 null, 정답률은 60%
    StudentScore s = sampleScore(1L, 100L, "구 시험", LocalDate.of(2024, 4, 1), 10, 6);
    when(studentScoreRepository.findTimelineByStudent(99L)).thenReturn(List.of(s));

    // when
    ScoreTimelineResponse res = service.forStudent(99L);

    // then
    ScoreTimelineResponse.Point p = res.points().get(0);
    assertThat(p.totalPoints()).isNull();
    assertThat(p.earnedPoints()).isNull();
    assertThat(p.accuracyPercent()).isEqualTo(60); // 정답률 기반
  }

  @Test
  @DisplayName("forStudent — 같은 (year, semester, examType)이면 confirmedAt 순으로 정렬 (cross-school tiebreaker)")
  void same_year_semester_type_breaks_tie_by_confirmed_at() {
    // given — 업로드 순서:
    //   1) 현대고 2026 1학기 중간 (T+0)
    //   2) 현대고 2026 1학기 기말 (T+1)
    //   3) 현대고 2026 2학기 기말 (T+2)
    //   4) 세화고 2026 1학기 중간 (T+3)  ← 1)과 (year, semester, type) 동률 → confirmedAt 비교로 1) 뒤에 와야 함
    LocalDateTime t0 = LocalDateTime.of(2026, 5, 1, 10, 0);
    StudentScore s1 = sortSample(1L, 100L, "현대고 1학기 중간", "현대고",
        (short) 2026, (byte) 1, ExamType.MIDTERM, t0);
    StudentScore s2 = sortSample(2L, 101L, "현대고 1학기 기말", "현대고",
        (short) 2026, (byte) 1, ExamType.FINAL, t0.plusMinutes(1));
    StudentScore s3 = sortSample(3L, 102L, "현대고 2학기 기말", "현대고",
        (short) 2026, (byte) 2, ExamType.FINAL, t0.plusMinutes(2));
    StudentScore s4 = sortSample(4L, 103L, "세화고 1학기 중간", "세화고",
        (short) 2026, (byte) 1, ExamType.MIDTERM, t0.plusMinutes(3));
    // repo가 일부러 뒤섞인 순서로 응답해도 서비스가 정렬해야 함
    when(studentScoreRepository.findTimelineByStudent(99L))
        .thenReturn(List.of(s4, s2, s1, s3));

    // when
    ScoreTimelineResponse res = service.forStudent(99L);

    // then — 기대 순서: 현대고 1학기 중간 → 세화고 1학기 중간 → 현대고 1학기 기말 → 현대고 2학기 기말
    assertThat(res.points())
        .extracting(ScoreTimelineResponse.Point::examDisplayName)
        .containsExactly("현대고 1학기 중간", "세화고 1학기 중간",
            "현대고 1학기 기말", "현대고 2학기 기말");
  }

  @Test
  @DisplayName("forStudent — examYear가 null인 시험은 알려진 시점 뒤로 (nulls last)")
  void null_year_goes_to_end() {
    // given — 연도 모르는 시험을 더 먼저 업로드했어도, 시점 알려진 시험이 앞으로 와야 함
    LocalDateTime t0 = LocalDateTime.of(2026, 5, 1, 10, 0);
    StudentScore unknown = sortSample(1L, 100L, "연도 모름 시험", "어딘가",
        null, null, ExamType.MIDTERM, t0);
    StudentScore known = sortSample(2L, 101L, "2024 1학기 중간", "현대고",
        (short) 2024, (byte) 1, ExamType.MIDTERM, t0.plusMinutes(1));
    when(studentScoreRepository.findTimelineByStudent(99L))
        .thenReturn(List.of(unknown, known));

    // when
    ScoreTimelineResponse res = service.forStudent(99L);

    // then
    assertThat(res.points())
        .extracting(ScoreTimelineResponse.Point::examDisplayName)
        .containsExactly("2024 1학기 중간", "연도 모름 시험");
  }

  @Test
  @DisplayName("forStudent — 같은 (year, semester)이면 examType ordinal로 정렬 (MIDTERM < FINAL < MOCK < OTHER)")
  void same_year_semester_ordered_by_exam_type_ordinal() {
    // given — 의도적으로 입력은 역순으로 (OTHER → MOCK → FINAL → MIDTERM)
    LocalDateTime t0 = LocalDateTime.of(2026, 5, 1, 10, 0);
    StudentScore other   = sortSample(1L, 100L, "기타",   "현대고", (short) 2024, (byte) 1, ExamType.OTHER,   t0);
    StudentScore mock_   = sortSample(2L, 101L, "모의",   "현대고", (short) 2024, (byte) 1, ExamType.MOCK,    t0.plusMinutes(1));
    StudentScore finalEx = sortSample(3L, 102L, "기말",   "현대고", (short) 2024, (byte) 1, ExamType.FINAL,   t0.plusMinutes(2));
    StudentScore midterm = sortSample(4L, 103L, "중간",   "현대고", (short) 2024, (byte) 1, ExamType.MIDTERM, t0.plusMinutes(3));
    when(studentScoreRepository.findTimelineByStudent(99L))
        .thenReturn(List.of(other, mock_, finalEx, midterm));

    // when
    ScoreTimelineResponse res = service.forStudent(99L);

    // then — confirmedAt이 거꾸로여도 examType ordinal이 우선
    assertThat(res.points())
        .extracting(ScoreTimelineResponse.Point::examDisplayName)
        .containsExactly("중간", "기말", "모의", "기타");
  }

  @Test
  @DisplayName("forConversation — conversation에 첨부된 source 점수만 조회한다")
  void for_conversation_uses_attached_sources_only() {
    // given
    Student student = mock(Student.class);
    when(student.getStudentId()).thenReturn(99L);
    Conversation conversation = Conversation.builder()
        .student(student)
        .title("중간고사 학습기록")
        .metadata("{}")
        .build();
    ReflectionTestUtils.setField(conversation, "conversationId", 7L);
    when(conversationRepository.findById(7L)).thenReturn(Optional.of(conversation));
    when(conversationSourceRepository.findByConversationId(7L))
        .thenReturn(List.of(
            ConversationSource.builder().conversationId(7L).sourceId(10L).build(),
            ConversationSource.builder().conversationId(7L).sourceId(11L).build()));

    StudentScore attachedScore = sampleScore(
        1L, 100L, "이 학습기록 시험", LocalDate.of(2026, 5, 1), 10, 8);
    when(studentScoreRepository.findTimelineByStudentAndSources(99L, List.of(10L, 11L)))
        .thenReturn(List.of(attachedScore));

    // when
    ScoreTimelineResponse res = service.forConversation(7L);

    // then
    verify(authResolver).requireStudentOwnedConversationAccess(conversation);
    assertThat(res.points())
        .extracting(ScoreTimelineResponse.Point::examDisplayName)
        .containsExactly("이 학습기록 시험");
  }

  @Test
  @DisplayName("forConversation — 첨부 source가 없으면 빈 points")
  void for_conversation_empty_when_no_sources() {
    // given
    Student student = mock(Student.class);
    when(student.getStudentId()).thenReturn(99L);
    Conversation conversation = Conversation.builder()
        .student(student)
        .title("빈 학습기록")
        .metadata("{}")
        .build();
    ReflectionTestUtils.setField(conversation, "conversationId", 7L);
    when(conversationRepository.findById(7L)).thenReturn(Optional.of(conversation));
    when(conversationSourceRepository.findByConversationId(7L)).thenReturn(List.of());

    // when
    ScoreTimelineResponse res = service.forConversation(7L);

    // then
    verify(authResolver).requireStudentOwnedConversationAccess(conversation);
    assertThat(res.points()).isEmpty();
  }

  @Test
  @DisplayName("forConversation — student-owned guard가 거부하면 source/score 조회 전에 403")
  void for_conversation_guard_denies_before_queries() {
    // given
    Conversation conversation = Conversation.forTutor(mock(f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User.class), "강사 기록", null);
    ReflectionTestUtils.setField(conversation, "conversationId", 7L);
    when(conversationRepository.findById(7L)).thenReturn(Optional.of(conversation));
    doThrow(new AccessDeniedException("학생 소유 대화만 접근"))
        .when(authResolver).requireStudentOwnedConversationAccess(conversation);

    // when & then
    assertThatThrownBy(() -> service.forConversation(7L))
        .isInstanceOf(AccessDeniedException.class);
    verify(conversationSourceRepository, never()).findByConversationId(7L);
    verify(studentScoreRepository, never()).findTimelineByStudentAndSources(any(), any());
  }

  // --- helpers ----------------------------------------------------

  private StudentScore sampleScore(Long scoreId, Long examId, String displayName,
                                   LocalDate examDate, int total, int correct) {
    return buildScore(scoreId, examId, displayName, examDate, total, correct, null, null);
  }

  private StudentScore sampleScoreWithPoints(Long scoreId, Long examId, String displayName,
                                             LocalDate examDate, int total, int correct,
                                             Integer totalPoints, Integer earnedPoints) {
    return buildScore(scoreId, examId, displayName, examDate, total, correct, totalPoints, earnedPoints);
  }

  /**
   * 정렬 테스트 전용 helper — (year, semester, examType, confirmedAt)을 명시적으로 지정.
   * 점수/정답 수 등 무관 컬럼은 sort 검증에 영향이 없도록 임의 값을 채운다.
   */
  private StudentScore sortSample(Long scoreId, Long examId, String displayName,
                                  String schoolName, Short examYear, Byte examSemester,
                                  ExamType examType, LocalDateTime confirmedAt) {
    Subject subject = Subject.builder().nameKo("영어").nameEn("english").build();
    ReflectionTestUtils.setField(subject, "subjectId", 1);

    Exam exam = Exam.builder()
        .schoolName(schoolName).grade((byte) 1).examType(examType)
        .examYear(examYear).examSemester(examSemester).subject(subject)
        .displayName(displayName).examDate(null)
        .build();
    ReflectionTestUtils.setField(exam, "examId", examId);

    Source source = mock(Source.class);
    Student student = mock(Student.class);
    StudentScore s = StudentScore.builder()
        .student(student).exam(exam).source(source)
        .totalQuestions(10).correctCount(7)
        .build();
    ReflectionTestUtils.setField(s, "scoreId", scoreId);
    ReflectionTestUtils.setField(s, "confirmedAt", confirmedAt);
    return s;
  }

  private StudentScore buildScore(Long scoreId, Long examId, String displayName,
                                  LocalDate examDate, int total, int correct,
                                  Integer totalPoints, Integer earnedPoints) {
    Subject subject = Subject.builder().nameKo("영어").nameEn("english").build();
    ReflectionTestUtils.setField(subject, "subjectId", 1);

    Exam exam = Exam.builder()
        .schoolName("학교").grade((byte) 1).examType(ExamType.MIDTERM)
        .examYear((short) 2024).examSemester((byte) 1).subject(subject)
        .displayName(displayName).examDate(examDate)
        .build();
    ReflectionTestUtils.setField(exam, "examId", examId);

    Source source = mock(Source.class);
    Student student = mock(Student.class);
    StudentScore s = StudentScore.builder()
        .student(student).exam(exam).source(source)
        .totalQuestions(total).correctCount(correct)
        .totalPoints(totalPoints).earnedPoints(earnedPoints)
        .build();
    ReflectionTestUtils.setField(s, "scoreId", scoreId);
    return s;
  }
}
