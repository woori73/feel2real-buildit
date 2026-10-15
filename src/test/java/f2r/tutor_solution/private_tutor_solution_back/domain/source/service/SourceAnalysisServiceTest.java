package f2r.tutor_solution.private_tutor_solution_back.domain.source.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.Exam;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.ExamType;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.repository.ExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Passage;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionType;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.PassageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.QuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.ExamAnalysisResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.SourceUploadRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ContentKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ProcessingStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.QuestionCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.QuestionCategoryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceAnalysisServiceTest {

  private SourceRepository sourceRepository;
  private ExamRepository examRepository;
  private SubjectRepository subjectRepository;
  private QuestionRepository questionRepository;
  private QuestionCategoryRepository questionCategoryRepository;
  private PassageRepository passageRepository;
  private ExamMetaInferenceService examMetaInferenceService;
  private ExamExtractionService examExtractionService;
  private SourceAnalysisService service;

  @BeforeEach
  void setUp() {
    sourceRepository = mock(SourceRepository.class);
    examRepository = mock(ExamRepository.class);
    subjectRepository = mock(SubjectRepository.class);
    questionRepository = mock(QuestionRepository.class);
    questionCategoryRepository = mock(QuestionCategoryRepository.class);
    passageRepository = mock(PassageRepository.class);
    examMetaInferenceService = mock(ExamMetaInferenceService.class);
    examExtractionService = mock(ExamExtractionService.class);
    service = new SourceAnalysisService(
        sourceRepository, examRepository, subjectRepository, questionRepository,
        questionCategoryRepository, passageRepository,
        examMetaInferenceService, examExtractionService, new ObjectMapper());
  }

  // --- resolveExamMetaForBatch: LLM 추출 우선, 학생 폼 fallback ---

  @Test
  @DisplayName("resolveExamMetaForBatch — LLM 메타 호출은 학생 입력 유무와 무관하게 항상 수행한다")
  void always_calls_llm_inference_even_when_student_meta_is_complete() throws Exception {
    // given — 학생이 모든 필드를 채웠어도 LLM은 호출되어야 함
    List<Source> batch = List.of(sampleSource());
    SourceUploadRequest req = new SourceUploadRequest(
        ContentKind.EXAM_BLANK, "00중", (byte) 3, ExamType.MIDTERM, (short) 2026, (byte) 1);
    when(examMetaInferenceService.inferMetaFromBatch(batch)).thenReturn(
        new ExamAnalysisResult.ExamMeta(
            "세화고", (byte) 3, ExamType.FINAL, (short) 2025, (byte) 2, null));

    // when
    ExamAnalysisResult.ExamMeta meta = service.resolveExamMetaForBatch(batch, req);

    // then — LLM 값으로 전부 덮임 (학생 입력은 fallback이라 사용 안 됨)
    assertThat(meta.schoolName()).isEqualTo("세화고");
    assertThat(meta.grade()).isEqualTo((byte) 3);
    assertThat(meta.examType()).isEqualTo(ExamType.FINAL);
    assertThat(meta.examYear()).isEqualTo((short) 2025);
    assertThat(meta.examSemester()).isEqualTo((byte) 2);
    assertThat(meta.subjectId()).isEqualTo(1);
    verify(examMetaInferenceService, times(1)).inferMetaFromBatch(batch);
  }

  @Test
  @DisplayName("resolveExamMetaForBatch — LLM 추출값이 학생 폼 값을 덮어쓴다 (학교/학년 충돌 시 자료 우선)")
  void llm_extraction_overrides_student_form_values() throws Exception {
    // given — 폼: 현대고/2학년, 자료: 세화고/3학년
    List<Source> batch = List.of(sampleSource(), sampleSource());
    SourceUploadRequest req = new SourceUploadRequest(
        ContentKind.EXAM_BLANK, "현대고", (byte) 2, null, null, null);
    when(examMetaInferenceService.inferMetaFromBatch(batch)).thenReturn(
        new ExamAnalysisResult.ExamMeta(
            "세화고", (byte) 3, ExamType.FINAL, (short) 2024, (byte) 2, null));

    // when
    ExamAnalysisResult.ExamMeta meta = service.resolveExamMetaForBatch(batch, req);

    // then — 자료에 적힌 값이 우선
    assertThat(meta.schoolName()).isEqualTo("세화고");
    assertThat(meta.grade()).isEqualTo((byte) 3);
    assertThat(meta.examType()).isEqualTo(ExamType.FINAL);
    assertThat(meta.examYear()).isEqualTo((short) 2024);
    assertThat(meta.examSemester()).isEqualTo((byte) 2);
  }

  @Test
  @DisplayName("resolveExamMetaForBatch — LLM이 null로 응답한 필드만 학생 폼 값으로 fallback")
  void student_form_only_fills_llm_nulls() throws Exception {
    // given — 폼은 학교/학년만 채움, LLM은 examType/year/semester만 채워서 응답
    List<Source> batch = List.of(sampleSource());
    SourceUploadRequest req = new SourceUploadRequest(
        ContentKind.EXAM_BLANK, "현대고", (byte) 2, null, null, null);
    when(examMetaInferenceService.inferMetaFromBatch(batch)).thenReturn(
        new ExamAnalysisResult.ExamMeta(
            null, null, ExamType.MIDTERM, (short) 2026, (byte) 1, null));

    // when
    ExamAnalysisResult.ExamMeta meta = service.resolveExamMetaForBatch(batch, req);

    // then — LLM null인 학교/학년은 폼 값으로, 나머지는 LLM 값
    assertThat(meta.schoolName()).isEqualTo("현대고");
    assertThat(meta.grade()).isEqualTo((byte) 2);
    assertThat(meta.examType()).isEqualTo(ExamType.MIDTERM);
    assertThat(meta.examYear()).isEqualTo((short) 2026);
    assertThat(meta.examSemester()).isEqualTo((byte) 1);
  }

  @Test
  @DisplayName("resolveExamMetaForBatch — LLM 호출 실패 시에는 학생 폼 값만 사용해서 진행 (예외 안 던짐)")
  void degrades_gracefully_when_inference_fails() throws Exception {
    // given
    List<Source> batch = List.of(sampleSource());
    SourceUploadRequest req = new SourceUploadRequest(
        ContentKind.EXAM_BLANK, "현대고", (byte) 2, ExamType.MIDTERM, null, null);
    when(examMetaInferenceService.inferMetaFromBatch(batch))
        .thenThrow(new IllegalStateException("LLM 호출 실패"));

    // when
    ExamAnalysisResult.ExamMeta meta = service.resolveExamMetaForBatch(batch, req);

    // then — 학생 값으로만 채워짐
    assertThat(meta.schoolName()).isEqualTo("현대고");
    assertThat(meta.grade()).isEqualTo((byte) 2);
    assertThat(meta.examType()).isEqualTo(ExamType.MIDTERM);
    assertThat(meta.examYear()).isNull();
    assertThat(meta.examSemester()).isNull();
  }

  // --- lookupDedup: 모든 UNIQUE 키 필드가 채워졌을 때만 dedup 시도 ---

  @Test
  @DisplayName("lookupDedup — 모든 UNIQUE 키가 채워지고 매치되면 기존 exam을 반환한다")
  void lookupDedup_returns_existing_when_unique_key_matches() {
    // given
    ExamAnalysisResult.ExamMeta meta = new ExamAnalysisResult.ExamMeta(
        "00중", (byte) 3, ExamType.MIDTERM, (short) 2026, (byte) 1, 1);
    Exam existing = sampleExam(42L);
    when(examRepository.findByUniqueKey(
        "00중", (byte) 3, ExamType.MIDTERM, (short) 2026, (byte) 1, 1))
        .thenReturn(Optional.of(existing));

    // when
    Optional<Exam> result = service.lookupDedup(meta);

    // then
    assertThat(result).containsSame(existing);
  }

  @Test
  @DisplayName("lookupDedup — UNIQUE 키 중 하나라도 null이면 검색 자체를 건너뛴다")
  void lookupDedup_returns_empty_when_unique_key_partial() {
    // given — examYear가 null
    ExamAnalysisResult.ExamMeta meta = new ExamAnalysisResult.ExamMeta(
        "00중", (byte) 3, ExamType.MIDTERM, null, (byte) 1, 1);

    // when
    Optional<Exam> result = service.lookupDedup(meta);

    // then
    assertThat(result).isEmpty();
    verify(examRepository, never()).findByUniqueKey(any(), any(), any(), any(), any(), any());
  }

  // --- analyzeBatchAsync: 분기 통합 (Step 2) ---

  @Test
  @DisplayName("analyzeBatchAsync — GENERAL 묶음은 LLM 호출 없이 즉시 COMPLETED로 전이된다")
  void analyzeBatchAsync_general_completes_without_llm() throws Exception {
    // given
    Source src = sampleSource();
    src.updateContentKind(ContentKind.GENERAL);
    when(sourceRepository.findById(11L)).thenReturn(Optional.of(src));
    SourceUploadRequest req = new SourceUploadRequest(
        ContentKind.GENERAL, null, null, null, null, null);

    // when
    service.analyzeBatchAsync(List.of(11L), req);

    // then
    assertThat(src.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
    assertThat(src.getExam()).isNull();
    verify(examExtractionService, never()).extractFromBatch(anyList());
    verify(examMetaInferenceService, never()).inferMetaFromBatch(anyList());
  }

  @Test
  @DisplayName("analyzeBatchAsync — 채점 흔적 있는 시험지(dedup 비매치): exams/questions INSERT + EXAM_GRADED promote + PENDING_REVIEW")
  void analyzeBatchAsync_graded_with_no_dedup_promotes_and_pends_review() throws Exception {
    // given
    Source src = sampleSource();
    stubAnalysisCommon(src, /*matched*/ null, extractionResult(true, 2));
    SourceUploadRequest req = new SourceUploadRequest(
        ContentKind.EXAM_BLANK, "00중", (byte) 3, ExamType.MIDTERM, (short) 2026, (byte) 1);

    // when
    service.analyzeBatchAsync(List.of(11L), req);

    // then
    assertThat(src.getProcessingStatus()).isEqualTo(ProcessingStatus.PENDING_REVIEW);
    assertThat(src.getContentKind()).isEqualTo(ContentKind.EXAM_GRADED);
    assertThat(src.getExam().getExamId()).isEqualTo(99L);
    assertThat(src.getAnalysisResult()).isNotBlank();  // JSON 저장됨
    verify(examRepository).save(any());                // 새 exam INSERT
    verify(questionRepository, times(2)).save(any());  // 2문항 INSERT
  }

  @Test
  @DisplayName("analyzeBatchAsync — 채점 흔적 없는 시험지: questions INSERT 후 EXAM_BLANK 유지 + 즉시 COMPLETED")
  void analyzeBatchAsync_blank_with_no_dedup_completes_immediately() throws Exception {
    // given
    Source src = sampleSource();
    stubAnalysisCommon(src, /*matched*/ null, extractionResult(false, 2));
    SourceUploadRequest req = new SourceUploadRequest(
        ContentKind.EXAM_BLANK, "00중", (byte) 3, ExamType.MIDTERM, (short) 2026, (byte) 1);

    // when
    service.analyzeBatchAsync(List.of(11L), req);

    // then
    assertThat(src.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
    assertThat(src.getContentKind()).isEqualTo(ContentKind.EXAM_BLANK);
    assertThat(src.getExam().getExamId()).isEqualTo(99L);
    assertThat(src.getAnalysisResult()).isNull();      // GRADED 아니므로 JSON 저장 안 함
    verify(questionRepository, times(2)).save(any());  // questions는 INSERT
  }

  @Test
  @DisplayName("analyzeBatchAsync — 채점 있는 시험지(dedup 매치): exams/questions 재사용 + EXAM_GRADED promote + PENDING_REVIEW")
  void analyzeBatchAsync_graded_with_dedup_match_reuses_questions() throws Exception {
    // given
    Source src = sampleSource();
    Exam matched = sampleExam(42L);
    stubAnalysisCommon(src, matched, extractionResult(true, 2));
    SourceUploadRequest req = new SourceUploadRequest(
        ContentKind.EXAM_BLANK, "00중", (byte) 3, ExamType.MIDTERM, (short) 2026, (byte) 1);

    // when
    service.analyzeBatchAsync(List.of(11L), req);

    // then
    assertThat(src.getProcessingStatus()).isEqualTo(ProcessingStatus.PENDING_REVIEW);
    assertThat(src.getContentKind()).isEqualTo(ContentKind.EXAM_GRADED);
    assertThat(src.getExam().getExamId()).isEqualTo(42L);
    verify(examRepository, never()).save(any());       // exam 재사용
    verify(questionRepository, never()).save(any());   // questions 재사용 (INSERT skip)
  }

  @Test
  @DisplayName("analyzeBatchAsync — ExamExtractionService 실패 시 source는 FAILED로 전이")
  void analyzeBatchAsync_marks_failed_on_extraction_error() throws Exception {
    // given
    Source src = sampleSource();
    when(sourceRepository.findById(11L)).thenReturn(Optional.of(src));
    when(subjectRepository.findById(1)).thenReturn(Optional.of(sampleSubject()));
    when(examRepository.findByUniqueKey(any(), any(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(examRepository.save(any())).thenAnswer(inv -> {
      Exam e = inv.getArgument(0);
      ReflectionTestUtils.setField(e, "examId", 99L);
      return e;
    });
    when(examExtractionService.extractFromBatch(anyList()))
        .thenThrow(new IOException("Claude API 실패"));
    SourceUploadRequest req = new SourceUploadRequest(
        ContentKind.EXAM_BLANK, "00중", (byte) 3, ExamType.MIDTERM, (short) 2026, (byte) 1);

    // when
    service.analyzeBatchAsync(List.of(11L), req);

    // then
    assertThat(src.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
  }

  // --- Phase 11: 카테고리 / 지문 매핑 ---

  @Test
  @DisplayName("analyzeBatchAsync — Phase 11: 카테고리 lookup 성공 시 question.category에 매핑된다")
  void analyzeBatchAsync_maps_question_category_when_found() throws Exception {
    // given — 분기: dedup 비매치 + 채점 없음(즉시 COMPLETED). reading 카테고리 매칭.
    Source src = sampleSource();
    QuestionCategory reading = sampleCategory("reading", "독해");
    when(questionCategoryRepository.findByCategoryNameEn("reading"))
        .thenReturn(Optional.of(reading));
    when(questionCategoryRepository.findByCategoryNameEn("unknown_topic"))
        .thenReturn(Optional.empty());

    List<ExamAnalysisResult.QuestionEntry> qs = List.of(
        new ExamAnalysisResult.QuestionEntry(
            1, "1", "주제 추론", QuestionType.MULTIPLE_CHOICE,
            List.of("①a", "②b"), "②", null, null, 0, "reading", null),
        new ExamAnalysisResult.QuestionEntry(
            2, "2", "이상한 영역", QuestionType.MULTIPLE_CHOICE,
            List.of("①a", "②b"), "②", null, null, 0, "unknown_topic", null));
    ExamAnalysisResult extraction = new ExamAnalysisResult(null, null, qs, null, null, null);
    stubAnalysisCommon(src, null, extraction);
    SourceUploadRequest req = new SourceUploadRequest(
        ContentKind.EXAM_BLANK, "00중", (byte) 3, ExamType.MIDTERM, (short) 2026, (byte) 1);

    // when
    service.analyzeBatchAsync(List.of(11L), req);

    // then — questions 2개 INSERT, 1번은 reading category, 2번은 null category
    ArgumentCaptor<Question> qCap = forClass(Question.class);
    verify(questionRepository, times(2)).save(qCap.capture());
    List<Question> saved = qCap.getAllValues();
    assertThat(saved.get(0).getCategory()).isSameAs(reading);
    assertThat(saved.get(1).getCategory()).isNull();
    verify(questionCategoryRepository).findByCategoryNameEn("reading");
    verify(questionCategoryRepository).findByCategoryNameEn("unknown_topic");
  }

  @Test
  @DisplayName("analyzeBatchAsync — Phase 11: passages 먼저 INSERT 후 question.passage에 매핑된다 (source_origin은 exam.displayName)")
  void analyzeBatchAsync_inserts_passages_and_links_questions() throws Exception {
    // given — 두 문제가 같은 지문 "P1"을 공유
    Source src = sampleSource();
    List<ExamAnalysisResult.PassageEntry> passages = List.of(
        new ExamAnalysisResult.PassageEntry("P1", "Environmental protection has become a critical issue.\nMany countries are taking action."));
    List<ExamAnalysisResult.QuestionEntry> qs = List.of(
        new ExamAnalysisResult.QuestionEntry(
            1, "1", "지문 주제", QuestionType.MULTIPLE_CHOICE,
            List.of("①a", "②b"), "②", null, null, 0, "reading", "P1"),
        new ExamAnalysisResult.QuestionEntry(
            2, "2", "지문 제목", QuestionType.MULTIPLE_CHOICE,
            List.of("①a", "②b"), "②", null, null, 0, "reading", "P1"),
        new ExamAnalysisResult.QuestionEntry(
            3, "3", "독립 문제", QuestionType.SHORT_ANSWER,
            List.of(), "answer", null, null, 0, "vocabulary", null));
    ExamAnalysisResult extraction = new ExamAnalysisResult(null, null, qs, passages, null, null);
    stubAnalysisCommon(src, null, extraction);
    when(questionCategoryRepository.findByCategoryNameEn(any())).thenReturn(Optional.empty());

    Passage savedPassage = mock(Passage.class);
    when(passageRepository.save(any())).thenAnswer(inv -> {
      Passage p = inv.getArgument(0);
      ReflectionTestUtils.setField(p, "passageId", 555L);
      return p;
    });

    SourceUploadRequest req = new SourceUploadRequest(
        ContentKind.EXAM_BLANK, "00중", (byte) 3, ExamType.MIDTERM, (short) 2026, (byte) 1);

    // when
    service.analyzeBatchAsync(List.of(11L), req);

    // then
    // 1) passage 1건 INSERT
    ArgumentCaptor<Passage> pCap = forClass(Passage.class);
    verify(passageRepository, times(1)).save(pCap.capture());
    Passage persisted = pCap.getValue();
    assertThat(persisted.getContent()).startsWith("Environmental protection");
    assertThat(persisted.getTitle()).isEqualTo("Environmental protection has become a critical issue.");
    // sourceOrigin = exam.displayName (00중 3학년 2026 1학기 중간고사 등)
    assertThat(persisted.getSourceOrigin()).isNotBlank();
    assertThat(persisted.getSourceOrigin()).contains("00중");

    // 2) 3 questions INSERT, 1·2번은 같은 passage 매핑, 3번은 null
    ArgumentCaptor<Question> qCap = forClass(Question.class);
    verify(questionRepository, times(3)).save(qCap.capture());
    List<Question> saved = qCap.getAllValues();
    assertThat(saved.get(0).getPassage()).isSameAs(persisted);
    assertThat(saved.get(1).getPassage()).isSameAs(persisted);
    assertThat(saved.get(2).getPassage()).isNull();
  }

  @Test
  @DisplayName("analyzeBatchAsync — Phase 11: dedup 매치 시 passages/questions 모두 INSERT skip")
  void analyzeBatchAsync_dedup_match_skips_passages_too() throws Exception {
    // given — dedup 매치되면 questions 뿐 아니라 passages도 INSERT 안 함
    Source src = sampleSource();
    Exam matched = sampleExam(42L);
    List<ExamAnalysisResult.PassageEntry> passages = List.of(
        new ExamAnalysisResult.PassageEntry("P1", "Some shared passage."));
    List<ExamAnalysisResult.QuestionEntry> qs = List.of(
        new ExamAnalysisResult.QuestionEntry(
            1, "1", "Q1", QuestionType.MULTIPLE_CHOICE,
            List.of("①a", "②b"), "②", null, null, 0, "reading", "P1"));
    ExamAnalysisResult extraction = new ExamAnalysisResult(null, null, qs, passages, null, null);
    stubAnalysisCommon(src, matched, extraction);
    SourceUploadRequest req = new SourceUploadRequest(
        ContentKind.EXAM_BLANK, "00중", (byte) 3, ExamType.MIDTERM, (short) 2026, (byte) 1);

    // when
    service.analyzeBatchAsync(List.of(11L), req);

    // then — passage 도 questions 도 INSERT 없음
    verify(passageRepository, never()).save(any());
    verify(questionRepository, never()).save(any());
    assertThat(src.getExam().getExamId()).isEqualTo(42L);
  }

  @Test
  @DisplayName("deriveTitle — 첫 줄을 잘라서 title로 사용 (60자 컷)")
  void deriveTitle_extracts_first_line_with_60_char_limit() {
    // given/when/then
    assertThat(SourceAnalysisService.deriveTitle("First line\nSecond line")).isEqualTo("First line");
    assertThat(SourceAnalysisService.deriveTitle("   leading whitespace  \nrest")).isEqualTo("leading whitespace");
    assertThat(SourceAnalysisService.deriveTitle(null)).isNull();
    assertThat(SourceAnalysisService.deriveTitle("")).isNull();
    assertThat(SourceAnalysisService.deriveTitle("   ")).isNull();
    // 60자 컷
    String long61 = "a".repeat(61);
    assertThat(SourceAnalysisService.deriveTitle(long61)).hasSize(63);  // 60 + "..."
    assertThat(SourceAnalysisService.deriveTitle(long61)).endsWith("...");
  }

  private QuestionCategory sampleCategory(String en, String ko) {
    QuestionCategory cat = QuestionCategory.builder()
        .subject(sampleSubject())
        .categoryNameEn(en)
        .categoryNameKo(ko)
        .build();
    ReflectionTestUtils.setField(cat, "categoryId", (long) en.hashCode());
    return cat;
  }

  // --- helpers ---

  /**
   * 분기 통합 테스트용 공통 stub. dedup 매치 여부에 따라 분기:
   * - matched=null: findByUniqueKey가 비어 있고 새 exam INSERT (examId=99)
   * - matched 있음: 기존 exam 반환, questions도 동일 examId로 조회 (분기에서 INSERT skip 검증)
   */
  private void stubAnalysisCommon(Source src, Exam matched, ExamAnalysisResult extraction) throws IOException {
    when(sourceRepository.findById(src.getSourceId())).thenReturn(Optional.of(src));
    when(subjectRepository.findById(1)).thenReturn(Optional.of(sampleSubject()));
    if (matched == null) {
      when(examRepository.findByUniqueKey(any(), any(), any(), any(), any(), any()))
          .thenReturn(Optional.empty());
      when(examRepository.save(any())).thenAnswer(inv -> {
        Exam e = inv.getArgument(0);
        ReflectionTestUtils.setField(e, "examId", 99L);
        return e;
      });
      when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    } else {
      when(examRepository.findByUniqueKey(any(), any(), any(), any(), any(), any()))
          .thenReturn(Optional.of(matched));
    }
    when(examExtractionService.extractFromBatch(anyList())).thenReturn(extraction);
  }

  /** 채점 흔적 유무를 토글할 수 있는 ExamAnalysisResult fixture (questionCount 만큼 생성). */
  private ExamAnalysisResult extractionResult(boolean withGradingMarks, int questionCount) {
    List<ExamAnalysisResult.QuestionEntry> qs = new ArrayList<>();
    for (int i = 1; i <= questionCount; i++) {
      qs.add(new ExamAnalysisResult.QuestionEntry(
          i, String.valueOf(i), "Q" + i, QuestionType.MULTIPLE_CHOICE,
          List.of("①a", "②b"), "②",
          withGradingMarks ? "②" : null,
          withGradingMarks ? (i % 2 == 1) : null,
          0, null, null));
    }
    return new ExamAnalysisResult(null, null, qs, null, null, null);
  }

  private Subject sampleSubject() {
    Subject subject = Subject.builder().nameKo("영어").nameEn("English").build();
    ReflectionTestUtils.setField(subject, "subjectId", 1);
    return subject;
  }

  private Source sampleSource() {
    User uploader = User.builder()
        .loginId("std1").password("pw").name("홍길동")
        .phone("010").role(Role.STUDENT).build();
    ReflectionTestUtils.setField(uploader, "userId", 7L);
    Source src = Source.builder()
        .uploadedBy(uploader)
        .originalFileName("exam.jpg")
        .storageKey("/tmp/exam.jpg")
        .fileType("IMAGE")
        .fileSize(1024L)
        .sourceType(SourceType.STUDENT_MATERIAL)
        .contentKind(ContentKind.EXAM_BLANK)
        .build();
    ReflectionTestUtils.setField(src, "sourceId", 11L);
    return src;
  }

  private Exam sampleExam(Long examId) {
    Exam exam = Exam.builder()
        .schoolName("00중").grade((byte) 3).examType(ExamType.MIDTERM)
        .examYear((short) 2026).examSemester((byte) 1).build();
    ReflectionTestUtils.setField(exam, "examId", examId);
    return exam;
  }
}
