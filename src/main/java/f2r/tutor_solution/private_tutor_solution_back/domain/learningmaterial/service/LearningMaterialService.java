package f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.ConversationMetadata;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.dto.LearningMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity.LearningMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity.LearningMaterialAnswer;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity.LearningMaterialFeedback;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity.LearningMaterialStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.repository.LearningMaterialAnswerRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.repository.LearningMaterialFeedbackRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.repository.LearningMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ContentKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ProcessingStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.UploadSizeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 2 §5.5 학습자료 부여 + 학생 풀이 재업로드.
 *
 * <p>설계 요지:
 * <ul>
 *   <li>문제 PDF는 강사/Admin이 올린 그대로 보존해야 학생이 PDF로 다운받을 수 있다. → {@link FileStorage#saveRaw}로 저장 (fileType=UNKNOWN).</li>
 *   <li>학생 풀이 결과물도 마찬가지로 raw 보관 (이미지/PDF 혼재 가능). 미리보기는 브라우저가 Content-Type으로 처리.</li>
 *   <li>재업로드는 기존 answers row + storage 파일을 전부 정리한 뒤 새로 INSERT (대치 모드).</li>
 *   <li>SourceService.saveBatch 우회 — Phase 2 source는 LLM 분석 대상이 아니므로 trigger 안 함.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningMaterialService {

  static final List<String> ALLOWED_HOMEWORK_EXTENSIONS = List.of("pdf", "jpg", "jpeg", "png", "webp");
  static final List<String> ALLOWED_ANSWER_EXTENSIONS = ALLOWED_HOMEWORK_EXTENSIONS;

  private final LearningMaterialRepository materialRepository;
  private final LearningMaterialAnswerRepository answerRepository;
  private final LearningMaterialFeedbackRepository feedbackRepository;
  private final ConversationRepository conversationRepository;
  private final SourceRepository sourceRepository;
  private final UserRepository userRepository;
  private final FileStorage fileStorage;
  private final AuthenticatedUserResolver authResolver;
  private final ConversationSourceRepository conversationSourceRepository;
  private final ObjectMapper objectMapper;
  private final UploadSizeGuard uploadSizeGuard;

  /**
   * Admin 또는 TUTOR가 학생에게 숙제 자료(PDF/이미지)를 부여한다.
   *
   * @param conversationId 부여 컨텍스트(어느 대화에서 부여했는지)
   * @param assignerUserId 부여자 user_id (ADMIN 또는 TUTOR)
   * @param file           PDF/이미지 1개 (선택 — 없으면 title+description만으로 부여)
   * @param title          제목 (필수, 비공백)
   * @param description    설명 (선택)
   */
  @Transactional
  public LearningMaterialResponse assign(
      Long conversationId,
      Long assignerUserId,
      MultipartFile file,
      String title,
      String description) {

    authResolver.verifyAdminOrTutor();

    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("학습자료 제목이 필요합니다.");
    }
    boolean hasFile = file != null && !file.isEmpty();
    if (hasFile) {
      ensureExtensionAllowed(file, ALLOWED_HOMEWORK_EXTENSIONS, "학습자료는 PDF / JPG / PNG / WebP만 허용합니다.");
    }

    Conversation conversation = conversationRepository.findById(conversationId)
        .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationId));
    authResolver.verifyOwnsConversation(conversation);  // TUTOR이면 매핑 검증 포함

    User assigner = userRepository.findById(assignerUserId)
        .orElseThrow(() -> new IllegalArgumentException("user not found: " + assignerUserId));

    // 첨부 파일이 없으면 problemSource=null로 저장하고 source 생성/링크를 스킵한다.
    Source problemSource = hasFile
        ? storeRawSource(file, assigner, SourceType.HOMEWORK_PROBLEM)
        : null;

    LearningMaterial material = LearningMaterial.builder()
        .student(conversation.getStudent())
        .conversation(conversation)
        .problemSource(problemSource)
        .assignedBy(assigner)
        .title(title.trim())
        .description(description != null ? description.trim() : null)
        .build();
    LearningMaterial saved = materialRepository.save(material);

    // 숙제 문제 파일을 대화 컨텍스트(conversation_sources)에 편입 — AI Chat이 접근 가능하게.
    // 첨부 없는 숙제는 편입할 source가 없으므로 스킵.
    if (problemSource != null) {
      linkSourceToConversation(conversation, problemSource);
    }

    return toResponse(saved, List.of(), List.of());
  }

  /** 학생 대시보드용 — 해당 학생에게 부여된 학습자료 전체 (최신 부여순). */
  @Transactional(readOnly = true)
  public List<LearningMaterialResponse> listForStudent(Long studentId) {
    List<LearningMaterial> materials = materialRepository
        .findByStudent_StudentIdOrderByAssignedAtDesc(studentId);
    return materials.stream()
        .map(m -> toResponse(m, loadAnswers(m.getMaterialId()), loadFeedbacks(m.getMaterialId())))
        .toList();
  }

  /** 대화 화면용 — 한 conversation에서 부여된 학습자료 (최신순). */
  @Transactional(readOnly = true)
  public List<LearningMaterialResponse> listForConversation(Long conversationId) {
    Conversation conversation = conversationRepository.findById(conversationId)
        .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationId));
    authResolver.verifyOwnsConversation(conversation);

    List<LearningMaterial> materials = materialRepository
        .findByConversation_ConversationIdOrderByAssignedAtDesc(conversationId);
    return materials.stream()
        .map(m -> toResponse(m, loadAnswers(m.getMaterialId()), loadFeedbacks(m.getMaterialId())))
        .toList();
  }

  /** 문제 파일 다운로드/미리보기용 — 본인/Admin/매핑 TUTOR만 통과. */
  @Transactional(readOnly = true)
  public ProblemFileBundle getProblemFileBundle(Long materialId) {
    LearningMaterial material = loadOwned(materialId);
    Source src = material.getProblemSource();
    if (src == null) {
      // 첨부 없이 부여된 숙제 — 내려줄 문제 파일이 없다.
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          "문제 파일이 없는 숙제입니다: materialId=" + materialId);
    }
    try {
      return new ProblemFileBundle(
          fileStorage.readPage(src.getStorageKey(), src.getFileType(), 1),
          guessContentType(src.getOriginalFileName()),
          src.getOriginalFileName());
    } catch (IOException e) {
      throw new UncheckedIOException("학습자료 파일을 읽을 수 없습니다: materialId=" + materialId, e);
    }
  }

  /**
   * 학생이 풀이 결과물을 업로드한다.
   * 재업로드면 기존 answers + 해당 source/storage를 전부 정리한 뒤 새로 INSERT.
   *
   * @return 갱신된 material (answers 포함)
   */
  @Transactional
  public LearningMaterialResponse complete(Long materialId, List<MultipartFile> files) {
    LearningMaterial material = loadOwned(materialId);
    // #16 IDOR 가드: 제출은 STUDENT 본인 또는 ADMIN만. TUTOR는 학생 제출물 대치 차단.
    verifyCanSubmitAnswer(material);

    if (files == null || files.isEmpty()) {
      throw new IllegalArgumentException("풀이 결과 파일이 필요합니다.");
    }
    for (MultipartFile f : files) {
      if (f == null || f.isEmpty()) {
        throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
      }
      ensureExtensionAllowed(f, ALLOWED_ANSWER_EXTENSIONS,
          "풀이 파일은 PDF / JPG / PNG / WebP만 허용합니다.");
    }

    Conversation conversation = material.getConversation();

    // 기존 풀이 결과물 정리 — FK-safe 순서(C1):
    // (1) conversation_sources 링크 제거 → (2) metadata SourceSummary 제거 → (3) source/storage 제거.
    // source delete 전에 링크를 먼저 끊지 않으면 FK 위반.
    List<LearningMaterialAnswer> existing = answerRepository
        .findByMaterial_MaterialIdOrderByOrderNumAsc(materialId);
    for (LearningMaterialAnswer prev : existing) {
      Source prevSrc = prev.getSource();
      answerRepository.delete(prev);
      unlinkSourceFromConversation(conversation, prevSrc.getSourceId());
      String key = prevSrc.getStorageKey();
      String type = prevSrc.getFileType();
      sourceRepository.delete(prevSrc);
      fileStorage.delete(key, type);  // best-effort
    }

    // 신규 업로드 INSERT (학생 user_id로 saveRaw — 학생 디렉터리에 저장)
    User studentUser = material.getStudent().getUser();
    int order = 1;
    for (MultipartFile file : files) {
      Source answerSource = storeRawSource(file, studentUser, SourceType.HOMEWORK_ANSWER);
      LearningMaterialAnswer ans = LearningMaterialAnswer.builder()
          .material(material)
          .source(answerSource)
          .orderNum(order++)
          .build();
      answerRepository.save(ans);
      // 신규 답안 source를 대화 컨텍스트에 편입 — AI Chat이 접근 가능하게.
      linkSourceToConversation(conversation, answerSource);
    }
    // 재제출 = 답안 대치 → 이전 답안에 달린 강사 피드백도 stale이므로 함께 정리 (FK-safe).
    List<LearningMaterialFeedback> staleFeedbacks = feedbackRepository
        .findByMaterial_MaterialIdOrderByOrderNumAsc(materialId);
    for (LearningMaterialFeedback fb : staleFeedbacks) {
      Source fbSrc = fb.getSource();
      feedbackRepository.delete(fb);
      unlinkSourceFromConversation(conversation, fbSrc.getSourceId());
      String fk = fbSrc.getStorageKey();
      String ft = fbSrc.getFileType();
      sourceRepository.delete(fbSrc);
      fileStorage.delete(fk, ft);
    }

    material.markCompleted();
    material.unmarkReviewed();  // 재제출 = 강사 확인 리셋

    return toResponse(material, loadAnswers(materialId), loadFeedbacks(materialId));
  }

  /** 풀이 결과물 미리보기 — answer가 해당 material에 속하는지 확인 후 raw bytes 반환. */
  @Transactional(readOnly = true)
  public AnswerFileBundle getAnswerFile(Long materialId, Long answerId) {
    LearningMaterial material = loadOwned(materialId);
    LearningMaterialAnswer answer = answerRepository.findById(answerId)
        .orElseThrow(() -> new IllegalArgumentException("answer not found: " + answerId));
    if (!answer.getMaterial().getMaterialId().equals(material.getMaterialId())) {
      throw new IllegalArgumentException("answer does not belong to material: " + materialId);
    }
    Source src = answer.getSource();
    try {
      byte[] bytes = fileStorage.readPage(src.getStorageKey(), src.getFileType(), 1);
      return new AnswerFileBundle(bytes, guessContentType(src.getOriginalFileName()),
          src.getOriginalFileName());
    } catch (IOException e) {
      throw new UncheckedIOException("풀이 파일을 읽을 수 없습니다: answerId=" + answerId, e);
    }
  }

  /**
   * 학습자료 영구 삭제. cascade:
   * <ol>
   *   <li>모든 answers row + storage 정리</li>
   *   <li>material row 삭제</li>
   *   <li>problem source row + storage 정리</li>
   * </ol>
   * 부여자 본인 또는 ADMIN만 가능 (TUTOR라도 다른 TUTOR가 부여한 자료는 못 지움).
   */
  @Transactional
  public void delete(Long materialId, Long requesterUserId) {
    LearningMaterial material = materialRepository.findById(materialId)
        .orElseThrow(() -> new IllegalArgumentException("material not found: " + materialId));

    boolean isAdmin = authResolver.currentPrincipal()
        .map(p -> "ADMIN".equals(p.role()))
        .orElse(false);
    if (!isAdmin && !material.getAssignedBy().getUserId().equals(requesterUserId)) {
      throw new org.springframework.security.access.AccessDeniedException(
          "본인이 부여한 학습자료만 삭제할 수 있습니다.");
    }

    Conversation conversation = material.getConversation();

    // 1) answers + 링크 + storage 정리 (FK-safe: 링크/metadata 먼저, source는 그 다음)
    List<LearningMaterialAnswer> answers = answerRepository
        .findByMaterial_MaterialIdOrderByOrderNumAsc(materialId);
    for (LearningMaterialAnswer ans : answers) {
      Source ansSrc = ans.getSource();
      answerRepository.delete(ans);
      unlinkSourceFromConversation(conversation, ansSrc.getSourceId());
      String k = ansSrc.getStorageKey();
      String t = ansSrc.getFileType();
      sourceRepository.delete(ansSrc);
      fileStorage.delete(k, t);
    }

    // 2) feedback 링크 + storage 정리 (FK-safe).
    // 테이블 CASCADE는 material 삭제 시 feedback row만 지우고 source row/링크/storage는 남기므로 직접 정리.
    // material delete(3단계)가 feedback row를 CASCADE로 지우므로, 그 전에 source 링크/storage만 끊는다.
    List<LearningMaterialFeedback> feedbacks = feedbackRepository
        .findByMaterial_MaterialIdOrderByOrderNumAsc(materialId);
    for (LearningMaterialFeedback fb : feedbacks) {
      Source fbSrc = fb.getSource();
      feedbackRepository.delete(fb);
      unlinkSourceFromConversation(conversation, fbSrc.getSourceId());
      String fk = fbSrc.getStorageKey();
      String ft = fbSrc.getFileType();
      sourceRepository.delete(fbSrc);
      fileStorage.delete(fk, ft);
    }

    // 3) material row 삭제
    materialRepository.delete(material);

    // 4) problem source 링크 + storage 정리 (FK-safe).
    //    PDF 없이 부여된 숙제(problemSource=null)는 정리할 source가 없으므로 스킵.
    Source problemSrc = material.getProblemSource();
    if (problemSrc != null) {
      unlinkSourceFromConversation(conversation, problemSrc.getSourceId());
      String pk = problemSrc.getStorageKey();
      String pt = problemSrc.getFileType();
      sourceRepository.delete(problemSrc);
      fileStorage.delete(pk, pt);
    }
  }

  /**
   * 강사/Admin이 학습자료의 '확인(채점 완료)' 상태를 토글한다.
   * 가드: ADMIN 또는 APPROVED 담당 TUTOR (학생은 차단).
   */
  @Transactional
  public LearningMaterialResponse setReviewed(Long materialId, boolean reviewed) {
    LearningMaterial material = materialRepository.findById(materialId)
        .orElseThrow(() -> new IllegalArgumentException("material not found: " + materialId));
    authResolver.verifyAdminOrTutor();          // 학생 차단 (role gate)
    authResolver.verifyOwnsLearningMaterial(material);  // TUTOR는 담당 학생만

    if (reviewed) {
      User reviewer = authResolver.currentPrincipal()
          .flatMap(p -> userRepository.findById(p.userId()))
          .orElseThrow(() -> new AccessDeniedException("인증이 필요합니다."));
      material.markReviewed(reviewer);
    } else {
      material.unmarkReviewed();
    }
    return toResponse(material, loadAnswers(materialId), loadFeedbacks(materialId));
  }

  /**
   * 강사/Admin이 학생 풀이에 대한 피드백 파일(PDF/이미지)을 업로드한다 (다중, append).
   * 가드: ADMIN 또는 담당 TUTOR (학생 차단). 피드백 파일은 PDF/이미지를 허용한다.
   * 각 파일을 raw source로 저장 → feedback row(기존 최대 order_num 이어붙임) → 대화 컨텍스트 편입.
   *
   * @return 갱신된 material (feedbacks 포함)
   */
  @Transactional
  public LearningMaterialResponse addFeedback(Long materialId, String feedbackText, List<MultipartFile> files) {
    LearningMaterial material = materialRepository.findById(materialId)
        .orElseThrow(() -> new IllegalArgumentException("material not found: " + materialId));
    authResolver.verifyAdminOrTutor();          // 학생 차단 (role gate)
    authResolver.verifyOwnsLearningMaterial(material);  // TUTOR는 담당 학생만

    String normalizedFeedbackText = feedbackText == null || feedbackText.isBlank() ? null : feedbackText.trim();
    List<MultipartFile> uploadFiles = files == null
        ? List.of()
        : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
    if (normalizedFeedbackText == null && uploadFiles.isEmpty()) {
      throw new IllegalArgumentException("피드백 설명 또는 자료 파일 중 하나는 필요합니다.");
    }
    for (MultipartFile f : uploadFiles) {
      ensureExtensionAllowed(f, ALLOWED_HOMEWORK_EXTENSIONS, "피드백은 PDF / JPG / PNG / WebP만 허용합니다.");
    }

    Conversation conversation = material.getConversation();
    User uploader = authResolver.currentPrincipal()
        .flatMap(p -> userRepository.findById(p.userId()))
        .orElseThrow(() -> new AccessDeniedException("인증이 필요합니다."));

    int order = feedbackRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(materialId).stream()
        .mapToInt(LearningMaterialFeedback::getOrderNum)
        .max()
        .orElse(0) + 1;
    if (uploadFiles.isEmpty()) {
      LearningMaterialFeedback fb = LearningMaterialFeedback.builder()
          .material(material)
          .source(null)
          .feedbackText(normalizedFeedbackText)
          .orderNum(order)
          .build();
      feedbackRepository.save(fb);
    } else {
      for (MultipartFile file : uploadFiles) {
        Source feedbackSource = storeRawSource(file, uploader, SourceType.HOMEWORK_FEEDBACK);
        LearningMaterialFeedback fb = LearningMaterialFeedback.builder()
            .material(material)
            .source(feedbackSource)
            .feedbackText(normalizedFeedbackText)
            .orderNum(order++)
            .build();
        feedbackRepository.save(fb);
        linkSourceToConversation(conversation, feedbackSource);
      }
    }

    return toResponse(material, loadAnswers(materialId), loadFeedbacks(materialId));
  }

  /** 피드백 결과물 미리보기 — feedback이 해당 material에 속하는지 확인 후 raw bytes 반환. */
  @Transactional(readOnly = true)
  public AnswerFileBundle getFeedbackFile(Long materialId, Long feedbackId) {
    LearningMaterial material = loadOwned(materialId);
    LearningMaterialFeedback feedback = feedbackRepository.findById(feedbackId)
        .orElseThrow(() -> new IllegalArgumentException("feedback not found: " + feedbackId));
    if (!feedback.getMaterial().getMaterialId().equals(material.getMaterialId())) {
      throw new IllegalArgumentException("feedback does not belong to material: " + materialId);
    }
    Source src = feedback.getSource();
    if (src == null) {
      throw new IllegalArgumentException("첨부 파일이 없는 피드백입니다: feedbackId=" + feedbackId);
    }
    try {
      byte[] bytes = fileStorage.readPage(src.getStorageKey(), src.getFileType(), 1);
      return new AnswerFileBundle(bytes, guessContentType(src.getOriginalFileName()),
          src.getOriginalFileName());
    } catch (IOException e) {
      throw new UncheckedIOException("피드백 파일을 읽을 수 없습니다: feedbackId=" + feedbackId, e);
    }
  }

  /**
   * 피드백 결과물 1건 삭제. 가드: ADMIN 또는 담당 TUTOR (학생 차단).
   * FK-safe: 링크 제거 → feedback row delete → source/storage 제거.
   *
   * @return 갱신된 material (feedbacks 포함)
   */
  @Transactional
  public LearningMaterialResponse deleteFeedback(Long materialId, Long feedbackId) {
    LearningMaterial material = materialRepository.findById(materialId)
        .orElseThrow(() -> new IllegalArgumentException("material not found: " + materialId));
    authResolver.verifyAdminOrTutor();          // 학생 차단 (role gate)
    authResolver.verifyOwnsLearningMaterial(material);  // TUTOR는 담당 학생만

    LearningMaterialFeedback feedback = feedbackRepository.findById(feedbackId)
        .orElseThrow(() -> new IllegalArgumentException("feedback not found: " + feedbackId));
    if (!feedback.getMaterial().getMaterialId().equals(material.getMaterialId())) {
      throw new IllegalArgumentException("feedback does not belong to material: " + materialId);
    }

    Conversation conversation = material.getConversation();
    Source src = feedback.getSource();
    feedbackRepository.delete(feedback);
    if (src != null) {
      unlinkSourceFromConversation(conversation, src.getSourceId());
      String key = src.getStorageKey();
      String type = src.getFileType();
      sourceRepository.delete(src);
      fileStorage.delete(key, type);
    }

    return toResponse(material, loadAnswers(materialId), loadFeedbacks(materialId));
  }

  /* ── 내부 헬퍼 ─────────────────────────────────────────── */

  /**
   * #16 제출 전용 가드 — STUDENT 본인 또는 ADMIN만 통과, TUTOR 차단.
   * 공용 {@link AuthenticatedUserResolver#verifyOwnsLearningMaterial}(읽기용, tutor/admin 통과)을
   * 약화시키지 않기 위해 제출 경로에만 별도 가드를 둔다.
   */
  private void verifyCanSubmitAnswer(LearningMaterial material) {
    JwtTokenProvider.AuthenticatedPrincipal p = authResolver.currentPrincipal()
        .orElseThrow(() -> new AccessDeniedException("인증이 필요합니다."));
    if ("ADMIN".equals(p.role())) {
      return;  // 운영 대리 제출 허용 (ADR-3)
    }
    Long ownerUserId = material.getStudent().getUser().getUserId();
    if (!"STUDENT".equals(p.role()) || !ownerUserId.equals(p.userId())) {
      throw new AccessDeniedException("풀이는 학생 본인만 제출할 수 있습니다.");
    }
  }

  /**
   * source를 대화 컨텍스트에 편입한다 — conversation_sources 링크 + metadata SourceSummary append.
   * {@code ExamReviewService} / {@code ConversationService.attachTutorMaterials} 패턴 미러.
   */
  private void linkSourceToConversation(Conversation conversation, Source source) {
    conversationSourceRepository.save(
        ConversationSource.builder()
            .conversationId(conversation.getConversationId())
            .sourceId(source.getSourceId())
            .build());
    ConversationMetadata metadata = ConversationMetadata
        .fromJson(conversation.getMetadata(), objectMapper)
        .withSourceAppended(new ConversationMetadata.SourceSummary(
            source.getSourceId(), source.getOriginalFileName()));
    conversation.updateMetadata(metadata.toJson(objectMapper));
  }

  /**
   * source를 대화 컨텍스트에서 분리한다 — conversation_sources 링크 + metadata SourceSummary 제거.
   * source row를 지우기 전에 호출해야 FK 위반이 없다 (C1).
   */
  private void unlinkSourceFromConversation(Conversation conversation, Long sourceId) {
    conversationSourceRepository.deleteBySourceId(sourceId);
    ConversationMetadata metadata = ConversationMetadata
        .fromJson(conversation.getMetadata(), objectMapper)
        .withSourceRemoved(sourceId);
    conversation.updateMetadata(metadata.toJson(objectMapper));
  }

  /** ownership 가드. STUDENT 본인 / ADMIN / 매핑된 TUTOR만 통과. */
  private LearningMaterial loadOwned(Long materialId) {
    LearningMaterial material = materialRepository.findById(materialId)
        .orElseThrow(() -> new IllegalArgumentException("material not found: " + materialId));
    authResolver.verifyOwnsLearningMaterial(material);
    return material;
  }

  /**
   * 원본 그대로 저장한 Source row를 만든다 (LLM 분석 없음).
   * content_kind=GENERAL, processing_status=COMPLETED, fileType=UNKNOWN.
   */
  private Source storeRawSource(MultipartFile file, User uploader, SourceType sourceType) {
    uploadSizeGuard.requireWithinDocumentLimit(file);
    String key;
    try (InputStream in = file.getInputStream()) {
      key = fileStorage.saveRaw(uploader.getUserId(), file.getOriginalFilename(), in, file.getSize());
    } catch (IOException e) {
      throw new UncheckedIOException("파일 저장 실패: " + file.getOriginalFilename(), e);
    }

    Source source = Source.builder()
        .uploadedBy(uploader)
        .originalFileName(file.getOriginalFilename())
        .storageKey(key)
        .fileType("UNKNOWN")
        .fileSize(file.getSize())
        .sourceType(sourceType)
        .contentKind(ContentKind.GENERAL)
        .build();
    source.updateProcessingStatus(ProcessingStatus.COMPLETED);
    return sourceRepository.save(source);
  }

  private List<LearningMaterialResponse.MaterialAnswerInfo> loadAnswers(Long materialId) {
    return answerRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(materialId).stream()
        .map(a -> new LearningMaterialResponse.MaterialAnswerInfo(
            a.getAnswerId(),
            a.getSource().getSourceId(),
            a.getSource().getOriginalFileName(),
            a.getOrderNum()))
        .toList();
  }

  private List<LearningMaterialResponse.MaterialFeedbackInfo> loadFeedbacks(Long materialId) {
    return feedbackRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(materialId).stream()
        .map(f -> new LearningMaterialResponse.MaterialFeedbackInfo(
            f.getFeedbackId(),
            f.getSource() != null ? f.getSource().getSourceId() : null,
            f.getSource() != null ? f.getSource().getOriginalFileName() : null,
            f.getFeedbackText(),
            f.getOrderNum()))
        .toList();
  }

  private LearningMaterialResponse toResponse(
      LearningMaterial m,
      List<LearningMaterialResponse.MaterialAnswerInfo> answers,
      List<LearningMaterialResponse.MaterialFeedbackInfo> feedbacks) {
    Source problemSrc = m.getProblemSource();  // 첨부 없이 부여된 숙제면 null
    return new LearningMaterialResponse(
        m.getMaterialId(),
        m.getConversation().getConversationId(),
        problemSrc != null ? problemSrc.getSourceId() : null,
        problemSrc != null ? problemSrc.getOriginalFileName() : null,
        problemSrc != null,
        m.getTitle(),
        m.getDescription(),
        m.getStatus(),
        m.getAssignedAt(),
        m.getCompletedAt(),
        m.getAssignedBy().getUserId(),
        m.getAssignedBy().getName(),
        m.getStudent().getStudentId(),
        m.getStudent().getUser().getName(),
        m.isReviewed(),
        m.getReviewedAt(),
        m.getReviewedBy() != null ? m.getReviewedBy().getName() : null,
        answers,
        feedbacks
    );
  }

  static void ensureExtensionAllowed(MultipartFile file, List<String> allowed, String errorMessage) {
    String name = file.getOriginalFilename();
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException(errorMessage);
    }
    int dot = name.lastIndexOf('.');
    String ext = (dot >= 0 ? name.substring(dot + 1) : "").toLowerCase(Locale.ROOT);
    if (!allowed.contains(ext)) {
      throw new IllegalArgumentException(errorMessage);
    }
  }

  static String guessContentType(String filename) {
    if (filename == null) return "application/octet-stream";
    String lower = filename.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".pdf")) return "application/pdf";
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".webp")) return "image/webp";
    return "application/octet-stream";
  }

  /** 숙제 문제 파일 미리보기/다운로드 응답에 필요한 묶음. */
  public record ProblemFileBundle(byte[] bytes, String contentType, String originalFileName) {}

  /** 미리보기/다운로드 응답에 필요한 묶음. */
  public record AnswerFileBundle(byte[] bytes, String contentType, String originalFileName) {}
}
