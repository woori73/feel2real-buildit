package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service;

import tools.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplicationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.repository.LessonApplicationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.SourceUploadRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ContentKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ProcessingStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.exception.SourceProcessingNotCompleteException;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.service.SourceService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {

  /** MVP는 영어 고정 (subjects.subject_id = 1). */
  private static final int ENGLISH_SUBJECT_ID = 1;

  private final ConversationRepository conversationRepository;
  private final ConversationMessageRepository conversationMessageRepository;
  private final ConversationSourceRepository conversationSourceRepository;
  private final SourceRepository sourceRepository;
  private final StudentRepository studentRepository;
  private final UserRepository userRepository;
  private final SourceService sourceService;
  private final ClaudeChatService claudeChatService;
  private final ObjectMapper objectMapper;
  private final AuthenticatedUserResolver authResolver;
  private final LessonApplicationRepository lessonApplicationRepository;
  private final AcademyRepository academyRepository;

  @Transactional
  public ConversationSummaryResponse create(Long studentId) {
    Student student = studentRepository.findById(studentId)
        .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다: " + studentId));
    try {
      AuditorContextHolder.set(student.getUser().getLoginId());
      String metadataJson = ConversationMetadata
          .initial(student, ENGLISH_SUBJECT_ID)
          .toJson(objectMapper);
      Conversation conv = conversationRepository.save(
          Conversation.forStudent(student, "새 학습기록", metadataJson));
      return ConversationSummaryResponse.from(conv, 0, 0);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  public List<ConversationSummaryResponse> list(Long studentId) {
    return conversationRepository.findRecentByStudentId(studentId)
        .stream()
        .map(conv -> {
          int sources = conversationSourceRepository.findByConversationId(conv.getConversationId()).size();
          int messages = conversationMessageRepository
              .findByConversationConversationIdOrderByCreatedAtAsc(conv.getConversationId()).size();
          var application = lessonApplicationRepository
              .findFirstByConversation_ConversationIdAndStatusOrderByAppliedAtDesc(
                  conv.getConversationId(), LessonApplicationStatus.APPROVED)
              .orElse(null);
          var lesson = application != null ? application.getLesson() : null;
          return ConversationSummaryResponse.from(
              conv,
              sources,
              messages,
              lesson != null ? lesson.getLessonId() : null,
              lesson != null ? lesson.getName() : null,
              lesson != null ? lesson.getAcademy().getName() : null);
        })
        .toList();
  }

  public ConversationDetailResponse getDetail(Long conversationId) {
    Conversation conv = findConversation(conversationId);
    authResolver.requireStudentOwnedConversationAccess(conv);  // 학생 소유 대화 아니면 403
    List<ConversationMessage> messages =
        conversationMessageRepository.findByConversationConversationIdOrderByCreatedAtAsc(conversationId);
    List<Source> sources = loadSources(conversationId);
    // Phase 10 — 라이트박스가 PDF 페이지를 슬라이드할 수 있도록 페이지 개수도 함께 응답
    Map<Long, Integer> pageCounts = new HashMap<>();
    for (Source s : sources) pageCounts.put(s.getSourceId(), sourceService.pageCountOf(s));
    // 최신 APPROVED 신청을 역조회해 lesson/academy 정보 노출 (CANCELED는 conversation_id FK 유지하므로 status 필터 필수)
    var lesson = lessonApplicationRepository
        .findFirstByConversation_ConversationIdAndStatusOrderByAppliedAtDesc(
            conversationId, LessonApplicationStatus.APPROVED)
        .map(a -> a.getLesson())
        .orElse(null);
    Long lessonId = lesson != null ? lesson.getLessonId() : null;
    String lessonName = lesson != null ? lesson.getName() : null;
    String academyName = lesson != null ? lesson.getAcademy().getName() : null;
    return ConversationDetailResponse.from(conv, messages, sources, pageCounts, lessonId, lessonName, academyName);
  }

  /**
   * Phase 9 — 한 번에 한 시험(또는 자료) 단위로 여러 파일을 첨부한다.
   * 모든 파일은 같은 contentKind/메타로 처리되며, 시험지(EXAM_BLANK/EXAM_GRADED)면 모두 같은 exam_id로 묶인다.
   */
  @Transactional
  public List<SourceAttachResponse> attachSources(
      Long conversationId, List<MultipartFile> files, Long userId, SourceUploadRequest request) {
    if (files == null || files.isEmpty()) {
      throw new IllegalArgumentException("업로드할 파일이 없습니다.");
    }
    if (files.size() > MAX_FILES_PER_UPLOAD) {
      throw new IllegalArgumentException(
          "한 번에 최대 " + MAX_FILES_PER_UPLOAD + "개까지만 업로드할 수 있습니다.");
    }
    Conversation conv = findConversation(conversationId);
    authResolver.requireStudentOwnedConversationAccess(conv);  // 학생 소유 대화 아니면 403
    User user = findUser(userId);

    try {
      AuditorContextHolder.set(user.getLoginId());
      SourceUploadRequest effective = applyStudentDefaults(request, conv);

      // 모든 파일을 sources에 INSERT + conversation_sources 매핑 + metadata 누적
      // 분석 트리거는 sourceService.saveBatch가 묶음 단위로 1회만 등록한다.
      List<Source> saved = sourceService.saveBatch(files, user, SourceType.STUDENT_MATERIAL, effective);

      ConversationMetadata metadata = ConversationMetadata.fromJson(conv.getMetadata(), objectMapper);
      for (Source source : saved) {
        conversationSourceRepository.save(
            ConversationSource.builder()
                .conversationId(conversationId)
                .sourceId(source.getSourceId())
                .build());
        metadata = metadata.withSourceAppended(
            new ConversationMetadata.SourceSummary(source.getSourceId(), source.getOriginalFileName()));
      }
      conv.updateMetadata(metadata.toJson(objectMapper));

      // 첫 묶음 첨부 시 제목을 첫 파일명 기반으로 업데이트
      if (("새 대화".equals(conv.getTitle()) || "새 학습기록".equals(conv.getTitle())) && !saved.isEmpty()) {
        conv.updateTitle(saved.get(0).getOriginalFileName());
      }

      return saved.stream().map(SourceAttachResponse::from).toList();
    } finally {
      AuditorContextHolder.clear();
    }
  }

  /**
   * 강사/Admin이 학생 대화에 '시험대비 참고자료'(제출 불필요)를 첨부한다.
   * SourceType=TUTOR_MATERIAL, ContentKind=GENERAL 고정 — 시험지 추출/채점 워크플로를 타지 않는다.
   * 학생은 호출 불가({@link AuthenticatedUserResolver#verifyAdminOrTutor}). TUTOR는 매핑된 학생의 대화만.
   */
  @Transactional
  public List<SourceAttachResponse> attachTutorMaterials(
      Long conversationId, List<MultipartFile> files, Long userId) {
    if (files == null || files.isEmpty()) {
      throw new IllegalArgumentException("업로드할 파일이 없습니다.");
    }
    if (files.size() > MAX_FILES_PER_UPLOAD) {
      throw new IllegalArgumentException(
          "한 번에 최대 " + MAX_FILES_PER_UPLOAD + "개까지만 업로드할 수 있습니다.");
    }
    authResolver.verifyAdminOrTutor();          // 학생은 시험대비 자료 업로드 불가
    Conversation conv = findConversation(conversationId);
    authResolver.verifyOwnsConversation(conv);  // ADMIN 통과 / TUTOR는 매핑 대화만
    User user = findUser(userId);

    try {
      AuditorContextHolder.set(user.getLoginId());
      List<Source> saved = sourceService.saveBatch(
          files, user, SourceType.TUTOR_MATERIAL, SourceUploadRequest.defaultGeneral());

      ConversationMetadata metadata = ConversationMetadata.fromJson(conv.getMetadata(), objectMapper);
      for (Source source : saved) {
        conversationSourceRepository.save(
            ConversationSource.builder()
                .conversationId(conversationId)
                .sourceId(source.getSourceId())
                .build());
        metadata = metadata.withSourceAppended(
            new ConversationMetadata.SourceSummary(source.getSourceId(), source.getOriginalFileName()));
      }
      conv.updateMetadata(metadata.toJson(objectMapper));

      return saved.stream().map(SourceAttachResponse::from).toList();
    } finally {
      AuditorContextHolder.clear();
    }
  }

  /** Phase 9 — 한 묶음 업로드 시 허용 최대 파일 수. 프론트 MAX_FILES와 동기화. */
  private static final int MAX_FILES_PER_UPLOAD = 20;

  @Transactional
  public MessageResponse sendMessage(Long conversationId, String content, Long userId) {
    Conversation conv = findConversation(conversationId);
    authResolver.requireStudentOwnedConversationAccess(conv);  // 학생 소유 대화 아니면 403
    User user = findUser(userId);

    // 첨부 자료 중 분석이 끝나지 않은 게 있으면 LLM 호출 차단 (MVP §5.0)
    ensureAttachedSourcesReady(conversationId);

    try {
      AuditorContextHolder.set(user.getLoginId());

      // 히스토리 먼저 로드 (새 메시지 저장 전 — Claude에 중복 전달 방지)
      List<ConversationMessage> history =
          conversationMessageRepository.findByConversationConversationIdOrderByCreatedAtAsc(conversationId);

      // 사용자 메시지 저장
      ConversationMessage userMsg = conversationMessageRepository.save(
          ConversationMessage.builder()
              .conversation(conv)
              .role(MessageRole.USER)
              .content(content)
              .build());

      String assistantText = claudeChatService.chat(conv, history, content);

      // 어시스턴트 응답 저장
      ConversationMessage assistantMsg = conversationMessageRepository.save(
          ConversationMessage.builder()
              .conversation(conv)
              .role(MessageRole.ASSISTANT)
              .content(assistantText)
              .build());

      conv.updateLastMessageAt(LocalDateTime.now());

      // 첫 메시지 기반 제목 설정
      if ("새 대화".equals(conv.getTitle()) || conv.getTitle() == null) {
        conv.updateTitle(content.length() > 30 ? content.substring(0, 30) + "…" : content);
      }

      return MessageResponse.of(userMsg, assistantMsg);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  /**
   * 대화명(title) 수정. 학생 본인 inline 편집에서 사용. 학생 본인 / ADMIN만 통과시키며 TUTOR는 403.
   */
  @Transactional
  public ConversationTitleResponse updateTitle(Long conversationId, String title) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("대화명을 입력해 주세요.");
    }
    String trimmed = title.strip();
    if (trimmed.length() > 200) {
      throw new IllegalArgumentException("대화명은 200자 이하여야 합니다.");
    }
    Conversation conv = findConversation(conversationId);
    authResolver.requireStudentOwnedConversationManage(conv);
    conv.updateTitle(trimmed);
    return ConversationTitleResponse.from(conv);
  }

  /**
   * 학습기록 삭제. 학생 본인 / ADMIN만 허용한다. TUTOR는 조회/자료 부여만 가능하고 삭제는 403.
   * 원본 Source 파일은 다른 화면/감사 기록에서 참조될 수 있으므로 conversation 연결만 FK cascade로 제거한다.
   */
  @Transactional
  public void delete(Long conversationId) {
    Conversation conv = findConversation(conversationId);
    authResolver.requireStudentOwnedConversationManage(conv);
    conversationRepository.delete(conv);
  }

  @Transactional
  public ConversationSummaryResponse createForTutor(Long tutorUserId) {
    User tutor = findUser(tutorUserId);
    if (tutor.getRole() != Role.TUTOR) {
      throw new AccessDeniedException("강사 권한이 필요합니다.");
    }
    try {
      AuditorContextHolder.set(tutor.getLoginId());
      Conversation conv = conversationRepository.save(
          Conversation.forTutor(tutor, "새 강사 기록", null));
      return ConversationSummaryResponse.from(conv, 0, 0);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  public List<ConversationSummaryResponse> listForTutor(Long tutorUserId) {
    return conversationRepository.findRecentByTutorUserId(tutorUserId)
        .stream()
        .map(conv -> {
          authResolver.requireTutorOwnedConversationAccess(conv);
          int messages = conversationMessageRepository
              .findByConversationConversationIdOrderByCreatedAtAsc(conv.getConversationId()).size();
          return ConversationSummaryResponse.from(conv, 0, messages);
        })
        .toList();
  }

  public ConversationDetailResponse getTutorDetail(Long conversationId) {
    Conversation conv = findConversation(conversationId);
    authResolver.requireTutorOwnedConversationAccess(conv);
    List<ConversationMessage> messages =
        conversationMessageRepository.findByConversationConversationIdOrderByCreatedAtAsc(conversationId);
    List<Source> sources = loadSources(conversationId);
    Map<Long, Integer> pageCounts = new HashMap<>();
    for (Source source : sources) pageCounts.put(source.getSourceId(), sourceService.pageCountOf(source));
    return ConversationDetailResponse.from(conv, messages, sources, pageCounts, null, null, null);
  }

  @Transactional
  public List<SourceAttachResponse> attachTutorConversationSources(
      Long conversationId, List<MultipartFile> files, Long tutorUserId, SourceUploadRequest request) {
    if (files == null || files.isEmpty()) {
      throw new IllegalArgumentException("업로드할 파일이 없습니다.");
    }
    if (files.size() > MAX_FILES_PER_UPLOAD) {
      throw new IllegalArgumentException(
          "한 번에 최대 " + MAX_FILES_PER_UPLOAD + "개까지만 업로드할 수 있습니다.");
    }
    Conversation conv = findConversation(conversationId);
    authResolver.requireTutorOwnedConversationManage(conv);
    User tutor = findUser(tutorUserId);

    try {
      AuditorContextHolder.set(tutor.getLoginId());
      SourceUploadRequest effective = applyTutorUploadDefaults(
          request != null ? request : SourceUploadRequest.defaultGeneral(), tutor);
      List<Source> saved = sourceService.saveBatch(files, tutor, SourceType.STUDENT_MATERIAL, effective);

      ConversationMetadata metadata = ConversationMetadata.fromJson(conv.getMetadata(), objectMapper);
      for (Source source : saved) {
        conversationSourceRepository.save(
            ConversationSource.builder()
                .conversationId(conversationId)
                .sourceId(source.getSourceId())
                .build());
        metadata = metadata.withSourceAppended(
            new ConversationMetadata.SourceSummary(source.getSourceId(), source.getOriginalFileName()));
      }
      conv.updateMetadata(metadata.toJson(objectMapper));

      if (("새 강사 기록".equals(conv.getTitle()) || conv.getTitle() == null) && !saved.isEmpty()) {
        conv.updateTitle(saved.get(0).getOriginalFileName());
      }

      return saved.stream().map(SourceAttachResponse::from).toList();
    } finally {
      AuditorContextHolder.clear();
    }
  }

  private SourceUploadRequest applyTutorUploadDefaults(SourceUploadRequest request, User tutor) {
    if (request == null || request.contentKind() != ContentKind.EXAM_BLANK) return request;
    String schoolName = request.schoolName();
    if (schoolName == null || schoolName.isBlank()) {
      schoolName = academyRepository.findByTutor_UserId(tutor.getUserId())
          .map(academy -> academy.getName())
          .filter(name -> name != null && !name.isBlank())
          .orElse(tutor.getLoginId());
    }
    Byte grade = request.grade() != null ? request.grade() : (byte) 0;
    return new SourceUploadRequest(
        request.contentKind(),
        schoolName,
        grade,
        request.examType(),
        request.examYear(),
        request.examSemester());
  }

  @Transactional
  public ConversationTitleResponse updateTutorTitle(Long conversationId, String title) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("기록명을 입력해 주세요.");
    }
    String trimmed = title.strip();
    if (trimmed.length() > 200) {
      throw new IllegalArgumentException("기록명은 200자 이하여야 합니다.");
    }
    Conversation conv = findConversation(conversationId);
    authResolver.requireTutorOwnedConversationManage(conv);
    conv.updateTitle(trimmed);
    return ConversationTitleResponse.from(conv);
  }

  @Transactional
  public void deleteTutorConversation(Long conversationId) {
    Conversation conv = findConversation(conversationId);
    authResolver.requireTutorOwnedConversationManage(conv);
    conversationRepository.delete(conv);
  }

  @Transactional
  public MessageResponse sendTutorMessage(Long conversationId, String content, Long tutorUserId) {
    Conversation conv = findConversation(conversationId);
    authResolver.requireTutorOwnedConversationManage(conv);
    User tutor = findUser(tutorUserId);
    ensureAttachedSourcesReady(conversationId);
    try {
      AuditorContextHolder.set(tutor.getLoginId());
      List<ConversationMessage> history =
          conversationMessageRepository.findByConversationConversationIdOrderByCreatedAtAsc(conversationId);
      ConversationMessage userMsg = conversationMessageRepository.save(
          ConversationMessage.builder()
              .conversation(conv)
              .role(MessageRole.USER)
              .content(content)
              .build());
      String assistantText = claudeChatService.chat(conv, history, content);
      ConversationMessage assistantMsg = conversationMessageRepository.save(
          ConversationMessage.builder()
              .conversation(conv)
              .role(MessageRole.ASSISTANT)
              .content(assistantText)
              .build());
      conv.updateLastMessageAt(LocalDateTime.now());
      if ("새 강사 기록".equals(conv.getTitle()) || conv.getTitle() == null) {
        conv.updateTitle(content.length() > 30 ? content.substring(0, 30) + "…" : content);
      }
      return MessageResponse.of(userMsg, assistantMsg);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  private Conversation findConversation(Long id) {
    return conversationRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다: " + id));
  }

  private User findUser(Long userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
  }

  private List<Source> loadSources(Long conversationId) {
    return conversationSourceRepository.findByConversationId(conversationId).stream()
        .map(cs -> sourceRepository.findById(cs.getSourceId()))
        .filter(java.util.Optional::isPresent)
        .map(java.util.Optional::get)
        .toList();
  }

  /**
   * 첨부된 source 중 LLM 호출을 막아야 하는 상태(PENDING/ANALYZING/PENDING_REVIEW)가 하나라도 있으면
   * {@link SourceProcessingNotCompleteException}을 던져 차단한다.
   *
   * <p>Phase 8 변경 — {@link ProcessingStatus#FAILED}는 차단 사유에서 제외한다.
   * FAILED 자료는 ClaudeChatService.loadSources에서 LLM 컨텍스트로부터도 제외되며,
   * 학생은 다른 자료만으로 채팅을 계속 진행할 수 있다.
   * (Phase 9에서 FAILED 자료를 detach/재시도하는 UI를 별도로 제공할 예정)
   */
  private void ensureAttachedSourcesReady(Long conversationId) {
    List<SourceProcessingNotCompleteException.PendingSource> pending = loadSources(conversationId).stream()
        .filter(s -> isBlocking(s.getProcessingStatus()))
        .map(s -> new SourceProcessingNotCompleteException.PendingSource(
            s.getSourceId(), s.getOriginalFileName(), s.getProcessingStatus()))
        .toList();
    if (pending.isEmpty()) {
      return;
    }
    boolean reviewWaiting = pending.stream()
        .anyMatch(p -> p.status() == ProcessingStatus.PENDING_REVIEW);
    String message = reviewWaiting
        ? "시험지 검증이 필요합니다. 대시보드에서 검증을 완료해 주세요."
        : "자료 분석 중입니다. 잠시 후 다시 시도해 주세요.";
    throw new SourceProcessingNotCompleteException(message, pending);
  }

  /** LLM 호출을 차단해야 하는 상태인지. FAILED와 COMPLETED는 차단하지 않는다 (Phase 8). */
  private boolean isBlocking(ProcessingStatus status) {
    return status == ProcessingStatus.PENDING
        || status == ProcessingStatus.ANALYZING
        || status == ProcessingStatus.PENDING_REVIEW;
  }

  /**
   * Phase 9 — 폼에서 schoolName/grade가 비어 들어오면 학생 정보로 보충한다.
   * 시험지(EXAM_BLANK)가 아니면 빈 값을 강요하지 않고 그대로 전달한다 (analysis 단계에서 사용 안 함).
   */
  private SourceUploadRequest applyStudentDefaults(SourceUploadRequest request, Conversation conv) {
    if (request == null) return SourceUploadRequest.defaultGeneral();
    if (conv.getStudent() == null) return request;
    Student student = conv.getStudent();
    String schoolName = request.schoolName() != null ? request.schoolName() : student.getSchoolName();
    Byte grade = request.grade() != null ? request.grade() : student.getGrade();
    return new SourceUploadRequest(
        request.contentKind(),
        schoolName,
        grade,
        request.examType(),
        request.examYear(),
        request.examSemester());
  }
}
