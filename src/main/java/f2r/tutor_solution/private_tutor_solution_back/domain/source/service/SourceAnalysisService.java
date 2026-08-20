package f2r.tutor_solution.private_tutor_solution_back.domain.source.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.Exam;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.ExamType;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.repository.ExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Passage;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.PassageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.QuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.ExamAnalysisResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.SourceUploadRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ContentKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ProcessingStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.QuestionCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.QuestionCategoryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MVP §5.0 4단계 — 업로드 묶음(한 시험 또는 한 자료 단위) 분석.
 *
 * <p>contentKind에 따라 분기:
 * <ul>
 *   <li>{@link ContentKind#GENERAL} — 즉시 COMPLETED (LLM 호출 0회).</li>
 *   <li>{@link ContentKind#EXAM_BLANK} (학생 폼에서 "시험지" 선택 시 기본 진입값):
 *     <ol>
 *       <li>메타 합성: 학생 폼 메타 우선, 빈 칸만 LLM 유추 1회 ({@link ExamMetaInferenceService}).</li>
 *       <li>exams dedup 검색 → 매치 시 기존 exam_id 재사용, 비매치 시 새 exam INSERT.</li>
 *       <li>LLM 1회 추가 호출 ({@link ExamExtractionService}) — 묶음 전체 이미지에서 questions + 학생 답안 추출.</li>
 *       <li>dedup 매치면 기존 questions 재사용 (INSERT skip), 비매치면 questions INSERT.</li>
 *       <li>추출 결과의 questions 중 is_correct가 채워진 게 1개라도 있으면 → 채점된 시험지로 판단:
 *         contentKind = EXAM_GRADED로 promote, analysis_result JSON 저장, status = PENDING_REVIEW.</li>
 *       <li>채점 흔적이 없으면 → contentKind = EXAM_BLANK 유지, status = COMPLETED.</li>
 *     </ol>
 *   </li>
 *   <li>{@link ContentKind#EXAM_GRADED} — 학생이 직접 보낼 수 없는 값(서버 promote 전용). 들어오면 EXAM_BLANK처럼 처리한다.</li>
 * </ul>
 *
 * <p>student_answers / student_scores INSERT는 검증 페이지 확정 시 {@link ExamReviewService}가 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceAnalysisService {

  /** MVP 영어 고정. */
  private static final int ENGLISH_SUBJECT_ID = 1;

  private final SourceRepository sourceRepository;
  private final ExamRepository examRepository;
  private final SubjectRepository subjectRepository;
  private final QuestionRepository questionRepository;
  private final QuestionCategoryRepository questionCategoryRepository;
  private final PassageRepository passageRepository;
  private final ExamMetaInferenceService examMetaInferenceService;
  private final ExamExtractionService examExtractionService;
  private final ObjectMapper objectMapper;

  @Async("examAnalysisExecutor")
  @Transactional
  public void analyzeBatchAsync(List<Long> sourceIds, SourceUploadRequest request) {
    if (sourceIds == null || sourceIds.isEmpty()) return;

    List<Source> batch = new ArrayList<>(sourceIds.size());
    for (Long id : sourceIds) {
      sourceRepository.findById(id).ifPresent(batch::add);
    }
    if (batch.isEmpty()) {
      log.warn("분석 대상 source가 모두 사라졌습니다: sourceIds={}", sourceIds);
      return;
    }

    String loginId = batch.get(0).getUploadedBy() != null
        ? batch.get(0).getUploadedBy().getLoginId() : null;
    try {
      if (loginId != null) AuditorContextHolder.set(loginId);

      for (Source src : batch) src.updateProcessingStatus(ProcessingStatus.ANALYZING);

      ContentKind kind = batch.get(0).getContentKind();
      if (kind != ContentKind.EXAM_BLANK && kind != ContentKind.EXAM_GRADED) {
        for (Source src : batch) src.updateProcessingStatus(ProcessingStatus.COMPLETED);
        log.info("source 묶음 즉시 완료: count={}, kind={}", batch.size(), kind);
        return;
      }

      // 1) 메타 합성 + exams dedup/INSERT
      ExamAnalysisResult.ExamMeta finalMeta = resolveExamMetaForBatch(batch, request);
      Subject subject = subjectRepository.findById(ENGLISH_SUBJECT_ID)
          .orElseThrow(() -> new IllegalStateException(
              "영어 subject(id=" + ENGLISH_SUBJECT_ID + ")가 존재하지 않습니다. init.sql 시드 확인 필요."));
      Optional<Exam> matched = lookupDedup(finalMeta);
      Exam exam = matched.orElseGet(() -> examRepository.save(buildNewExam(finalMeta, subject)));
      boolean dedupMatched = matched.isPresent();
      for (Source src : batch) src.linkToExam(exam);

      // 2) questions + 학생 답안 추출 (LLM 1회)
      ExamAnalysisResult extracted = examExtractionService.extractFromBatch(batch);

      // 3) questions 처리 — dedup 매치 시 기존 재사용, 비매치 시 INSERT
      if (!dedupMatched) {
        insertQuestions(extracted, exam, subject);
      } else {
        log.info("dedup 매치 — questions INSERT skip: examId={}, extracted={}",
            exam.getExamId(), extracted.questions().size());
      }

      // 4) 채점 흔적 판별 → EXAM_GRADED promote 여부
      boolean hasGradingMarks = extracted.questions().stream()
          .anyMatch(q -> q.isCorrect() != null);
      int total = extracted.questions().size();
      int correct = (int) extracted.questions().stream()
          .filter(q -> Boolean.TRUE.equals(q.isCorrect()))
          .count();

      ContentKind resolvedKind = hasGradingMarks ? ContentKind.EXAM_GRADED : ContentKind.EXAM_BLANK;
      ProcessingStatus resolvedStatus = hasGradingMarks
          ? ProcessingStatus.PENDING_REVIEW
          : ProcessingStatus.COMPLETED;

      ExamAnalysisResult toPersist = new ExamAnalysisResult(
          finalMeta, exam.getExamId(), extracted.questions(), extracted.passages(), total, correct);
      String json = hasGradingMarks ? toPersist.toJson(objectMapper) : null;

      for (Source src : batch) {
        src.updateContentKind(resolvedKind);
        if (json != null) src.updateAnalysisResult(json);
        src.updateProcessingStatus(resolvedStatus);
      }

      log.info("시험지 묶음 분석 완료: count={}, examId={}, dedup={}, total={}, correct={}, kind={}, status={}",
          batch.size(), exam.getExamId(), dedupMatched, total, correct, resolvedKind, resolvedStatus);
    } catch (f2r.tutor_solution.private_tutor_solution_back.config.DemoFeatureDisabledException e) {
      // Claude 비활성(anthropic.enabled=false). 강사 개인 conversation 자료는 분석 없이 첨부 완료로 둔다.
      if (isTutorUpload(batch)) {
        log.warn("데모 버전 — 강사 시험지 분석 생략 후 첨부 완료 처리: sourceIds={}, reason={}",
            sourceIds, e.getMessage());
        for (Source src : batch) src.updateProcessingStatus(ProcessingStatus.COMPLETED);
      } else {
        log.warn("데모 버전 — 시험지 분석 미동작: sourceIds={}, reason={}", sourceIds, e.getMessage());
        for (Source src : batch) src.updateProcessingStatus(ProcessingStatus.FAILED);
      }
    } catch (com.anthropic.errors.BadRequestException e) {
      // 400 errors는 대개 영구적 (재시도 무의미). content_filter / max_tokens 등 별도로 식별.
      String msg = e.getMessage() != null ? e.getMessage() : "";
      if (msg.contains("content filtering policy") || msg.contains("content_filter")) {
        log.error("source 묶음 분석 실패 — Anthropic content filter에 차단됨. "
                + "자료의 독해 지문/해설 내용이 안전 정책에 걸렸을 가능성. sourceIds={}, msg={}",
            sourceIds, msg);
      } else {
        log.error("source 묶음 분석 실패 — Anthropic API 400 Bad Request. sourceIds={}, msg={}",
            sourceIds, msg);
      }
      for (Source src : batch) src.updateProcessingStatus(ProcessingStatus.FAILED);
    } catch (Exception e) {
      log.error("source 묶음 분석 실패: sourceIds={}", sourceIds, e);
      for (Source src : batch) src.updateProcessingStatus(ProcessingStatus.FAILED);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  private boolean isTutorUpload(List<Source> batch) {
    if (batch == null || batch.isEmpty() || batch.get(0).getUploadedBy() == null) return false;
    return batch.get(0).getUploadedBy().getRole() == Role.TUTOR;
  }

  /**
   * 묶음의 모든 source에 대해 LLM 메타 유추 + 학생 폼 입력을 합성한다.
   *
   * <p>우선순위: <strong>LLM 추출값 우선, 학생 폼은 fallback</strong>. 시험지에 적힌 학교/학년/시험종류가
   * 진실에 가까우므로 (학생이 다른 학교 시험을 풀어볼 수도, 폼에 잘못 적었을 수도 있음) LLM 응답을
   * 신뢰한다. LLM이 null로 응답한 필드만 학생 폼 값으로 보충한다.
   *
   * <p>LLM 호출은 학생 입력 유무와 무관하게 항상 수행한다 (추출값 검증 차원).
   * LLM 호출 실패 시에는 학생 폼 값만 사용해서 진행한다.
   */
  ExamAnalysisResult.ExamMeta resolveExamMetaForBatch(List<Source> batch, SourceUploadRequest request) {
    String studentSchool = request != null ? request.schoolName() : null;
    Byte studentGrade = request != null ? request.grade() : null;
    ExamType studentExamType = request != null ? request.examType() : null;
    Short studentYear = request != null ? request.examYear() : null;
    Byte studentSemester = request != null ? request.examSemester() : null;

    ExamAnalysisResult.ExamMeta inferred = null;
    try {
      inferred = examMetaInferenceService.inferMetaFromBatch(batch);
      log.info("묶음 메타 유추 완료: count={}, inferred={}", batch.size(), inferred);
    } catch (Exception e) {
      log.warn("묶음 메타 유추 실패 — 학생 입력값만으로 진행: reason={}", e.getMessage());
    }

    return new ExamAnalysisResult.ExamMeta(
        pickNonNull(inferred != null ? inferred.schoolName() : null, studentSchool),
        pickNonNull(inferred != null ? inferred.grade() : null, studentGrade),
        pickNonNull(inferred != null ? inferred.examType() : null, studentExamType),
        pickNonNull(inferred != null ? inferred.examYear() : null, studentYear),
        pickNonNull(inferred != null ? inferred.examSemester() : null, studentSemester),
        ENGLISH_SUBJECT_ID);
  }

  /** UNIQUE 키 필드가 모두 채워진 경우에만 dedup 시도. 하나라도 null이면 매번 새 exam을 INSERT한다. */
  Optional<Exam> lookupDedup(ExamAnalysisResult.ExamMeta meta) {
    if (meta.schoolName() == null || meta.grade() == null
        || meta.examType() == null || meta.examYear() == null || meta.examSemester() == null) {
      return Optional.empty();
    }
    return examRepository.findByUniqueKey(
        meta.schoolName(), meta.grade(), meta.examType(),
        meta.examYear(), meta.examSemester(), ENGLISH_SUBJECT_ID);
  }

  private Exam buildNewExam(ExamAnalysisResult.ExamMeta meta, Subject subject) {
    return Exam.builder()
        .schoolName(meta.schoolName())
        .grade(meta.grade())
        .examType(meta.examType() != null ? meta.examType() : ExamType.OTHER)
        .examYear(meta.examYear())
        .examSemester(meta.examSemester())
        .subject(subject)
        .displayName(buildDisplayName(meta))
        .build();
  }

  /**
   * LLM 추출 결과의 passages와 questions를 exam에 INSERT한다.
   *
   * <ol>
   *   <li>passages 먼저 INSERT — title은 content의 첫 줄에서 자르고, source_origin은 exam.displayName으로 자동 채움.
   *       LLM 임시 id(예: "P1") → 실제 passage_id 맵을 만든다.</li>
   *   <li>questions INSERT — entry.category는 {@code question_categories.category_name_en}로 lookup,
   *       entry.passageRef는 위 맵으로 lookup해 매핑. 매핑 실패는 null로 두고 경고 로그.</li>
   * </ol>
   *
   * <p>order_num은 정렬용으로 question_no 우선 사용, question_label은 시험지의 실제 라벨(예: "1", "[서답형 1] (1)")
   * 그대로 저장한다. 라벨이 비면 order_num을 문자열로 fallback.
   */
  private void insertQuestions(ExamAnalysisResult extracted, Exam exam, Subject subject) {
    Map<String, Passage> passageMap = insertPassages(extracted, exam, subject);

    int fallbackOrder = 1;
    for (ExamAnalysisResult.QuestionEntry entry : extracted.questions()) {
      Integer orderNum = entry.questionNo() != null ? entry.questionNo() : fallbackOrder++;
      String label = entry.questionLabel() != null && !entry.questionLabel().isBlank()
          ? entry.questionLabel()
          : String.valueOf(orderNum);
      QuestionCategory category = resolveCategory(entry.category());
      Passage passage = resolvePassage(entry.passageRef(), passageMap);
      questionRepository.save(Question.builder()
          .subject(subject)
          .category(category)
          .passage(passage)
          .examId(exam.getExamId())
          .questionType(entry.questionType())
          .questionText(entry.questionText())
          .choices(serializeChoices(entry.choices()))
          .correctAnswer(entry.correctAnswer())
          .source(QuestionSource.READFILE)
          .orderNum(orderNum)
          .questionLabel(label)
          .point(entry.point() != null ? entry.point() : 0)
          .build());
    }
    log.info("questions INSERT 완료: examId={}, count={}, passages={}",
        exam.getExamId(), extracted.questions().size(), passageMap.size());
  }

  /**
   * LLM 추출 결과의 passages를 먼저 INSERT하고, LLM 임시 id → 영속 Passage 맵을 반환한다.
   * passages 가 null이거나 비어 있으면 빈 맵 반환.
   *
   * <p>title은 content 첫 줄에서 잘라낸 값(최대 60자), source_origin은 exam.displayName으로 채운다.
   * 모두 코드 자동 처리 — LLM은 id와 content만 응답하면 된다.
   */
  private Map<String, Passage> insertPassages(ExamAnalysisResult extracted, Exam exam, Subject subject) {
    Map<String, Passage> map = new HashMap<>();
    if (extracted.passages() == null || extracted.passages().isEmpty()) {
      return map;
    }
    String origin = exam.getDisplayName();
    for (ExamAnalysisResult.PassageEntry entry : extracted.passages()) {
      if (entry.id() == null || entry.content() == null || entry.content().isBlank()) {
        log.warn("passage 항목이 불완전합니다 (skip): id={}, content blank={}",
            entry.id(), entry.content() == null || entry.content().isBlank());
        continue;
      }
      Passage saved = passageRepository.save(Passage.builder()
          .subject(subject)
          .title(deriveTitle(entry.content()))
          .content(entry.content())
          .sourceOrigin(origin)
          .build());
      map.put(entry.id(), saved);
    }
    return map;
  }

  /**
   * 지문 본문의 첫 줄 일부(최대 60자)를 title로 사용. 매우 긴 한 줄짜리 본문은 잘라 쓴다.
   * passage.title은 nullable이므로 추출 실패 시 null 반환.
   */
  static String deriveTitle(String content) {
    if (content == null) return null;
    String trimmed = content.trim();
    if (trimmed.isEmpty()) return null;
    int newline = trimmed.indexOf('\n');
    String firstLine = newline >= 0 ? trimmed.substring(0, newline).trim() : trimmed;
    if (firstLine.length() > 60) {
      firstLine = firstLine.substring(0, 60).trim() + "...";
    }
    return firstLine.isEmpty() ? null : firstLine;
  }

  /**
   * LLM이 응답한 category 키(예: "vocabulary")로 question_categories.category_name_en을 lookup.
   * null/빈 문자열이거나 매칭 실패 시 null 반환(질문은 category 없이 INSERT됨 → 취약점 분석에서 제외).
   */
  private QuestionCategory resolveCategory(String categoryKey) {
    if (categoryKey == null || categoryKey.isBlank()) return null;
    return questionCategoryRepository.findByCategoryNameEn(categoryKey.trim().toLowerCase())
        .orElseGet(() -> {
          log.warn("알 수 없는 category_name_en: {} — null로 INSERT", categoryKey);
          return null;
        });
  }

  /**
   * LLM 임시 passage 참조(예: "P1") → 앞 단계에서 INSERT된 영속 Passage 매핑.
   * 참조가 null이면 그냥 null 반환. 참조는 있지만 맵에 없으면 경고 로그 후 null.
   */
  private Passage resolvePassage(String passageRef, Map<String, Passage> passageMap) {
    if (passageRef == null || passageRef.isBlank()) return null;
    Passage hit = passageMap.get(passageRef);
    if (hit == null) {
      log.warn("question이 참조한 passage_ref={}가 passages 배열에 없습니다 (null로 INSERT)", passageRef);
    }
    return hit;
  }

  private String serializeChoices(List<String> choices) {
    if (choices == null || choices.isEmpty()) return null;
    try {
      return objectMapper.writeValueAsString(choices);
    } catch (Exception e) {
      throw new IllegalStateException("choices 직렬화 실패", e);
    }
  }

  private String buildDisplayName(ExamAnalysisResult.ExamMeta meta) {
    StringBuilder sb = new StringBuilder();
    if (meta.schoolName() != null) sb.append(meta.schoolName()).append(' ');
    if (meta.grade() != null) sb.append(meta.grade()).append("학년 ");
    if (meta.examYear() != null) sb.append(meta.examYear()).append(' ');
    if (meta.examSemester() != null) sb.append(meta.examSemester()).append("학기 ");
    if (meta.examType() != null) sb.append(examTypeLabel(meta.examType()));
    String s = sb.toString().trim();
    return s.isEmpty() ? null : s;
  }

  private String examTypeLabel(ExamType type) {
    return switch (type) {
      case MIDTERM -> "중간고사";
      case FINAL -> "기말고사";
      case MOCK -> "모의고사";
      case OTHER -> "기타";
    };
  }

  private static <T> T pickNonNull(T preferred, T fallback) {
    return preferred != null ? preferred : fallback;
  }
}
