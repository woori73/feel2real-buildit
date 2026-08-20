package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.ConversationDetailResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.ConversationTitleResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.SourceAttachResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.MessageRole;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplication;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplicationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.repository.LessonApplicationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

class ConversationServiceTest {

  private ConversationRepository conversationRepository;
  private ConversationMessageRepository conversationMessageRepository;
  private ConversationSourceRepository conversationSourceRepository;
  private SourceRepository sourceRepository;
  private StudentRepository studentRepository;
  private UserRepository userRepository;
  private SourceService sourceService;
  private ClaudeChatService claudeChatService;
  private AuthenticatedUserResolver authResolver;
  private LessonApplicationRepository lessonApplicationRepository;
  private AcademyRepository academyRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private ConversationService service;

  @BeforeEach
  void setUp() {
    conversationRepository = mock(ConversationRepository.class);
    conversationMessageRepository = mock(ConversationMessageRepository.class);
    conversationSourceRepository = mock(ConversationSourceRepository.class);
    sourceRepository = mock(SourceRepository.class);
    studentRepository = mock(StudentRepository.class);
    userRepository = mock(UserRepository.class);
    sourceService = mock(SourceService.class);
    claudeChatService = mock(ClaudeChatService.class);
    authResolver = mock(AuthenticatedUserResolver.class);
    lessonApplicationRepository = mock(LessonApplicationRepository.class);
    academyRepository = mock(AcademyRepository.class);
    // 기본 — ownership 검증은 통과시킨다 (개별 테스트가 deny 케이스를 별도로 stub).
    service = new ConversationService(
        conversationRepository,
        conversationMessageRepository,
        conversationSourceRepository,
        sourceRepository,
        studentRepository,
        userRepository,
        sourceService,
        claudeChatService,
        objectMapper,
        authResolver,
        lessonApplicationRepository,
        academyRepository);
  }

  @Test
  @DisplayName("sendMessage — 첨부 source가 ANALYZING이면 차단 (409) + LLM 호출 안 함")
  void blocks_when_source_is_analyzing() {
    // given
    Long conversationId = 10L;
    Long userId = 7L;
    Conversation conv = baseConversation(conversationId);
    User user = baseUser(userId);
    Source analyzing = sourceWithStatus(1L, "exam.pdf", ProcessingStatus.ANALYZING);
    stubConversationSources(conversationId, List.of(analyzing));

    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    // when & then
    assertThatThrownBy(() -> service.sendMessage(conversationId, "안녕", userId))
        .isInstanceOf(SourceProcessingNotCompleteException.class)
        .hasMessageContaining("분석 중");

    verify(claudeChatService, never()).chat(any(), anyList(), any());
    verify(conversationMessageRepository, never()).save(any());
  }

  @Test
  @DisplayName("sendMessage — 첨부 source가 PENDING_REVIEW이면 검증 안내 메시지로 차단")
  void blocks_with_review_message_when_pending_review() {
    // given
    Long conversationId = 11L;
    Long userId = 7L;
    Conversation conv = baseConversation(conversationId);
    User user = baseUser(userId);
    Source pendingReview = sourceWithStatus(2L, "midterm.pdf", ProcessingStatus.PENDING_REVIEW);
    stubConversationSources(conversationId, List.of(pendingReview));

    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    // when & then
    assertThatThrownBy(() -> service.sendMessage(conversationId, "안녕", userId))
        .isInstanceOf(SourceProcessingNotCompleteException.class)
        .hasMessageContaining("검증");
  }

  @Test
  @DisplayName("sendMessage — Phase 8: FAILED source는 차단하지 않고 채팅 진행 (다른 자료만으로 LLM 호출)")
  void allows_chat_when_only_failed_sources_remain() {
    // given
    Long conversationId = 12L;
    Long userId = 7L;
    Conversation conv = baseConversation(conversationId);
    User user = baseUser(userId);
    Source failed = sourceWithStatus(3L, "corrupt.pdf", ProcessingStatus.FAILED);
    stubConversationSources(conversationId, List.of(failed));

    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(conversationMessageRepository.findByConversationConversationIdOrderByCreatedAtAsc(conversationId))
        .thenReturn(List.of());
    when(claudeChatService.chat(any(), anyList(), any())).thenReturn("응답");
    when(conversationMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // when — 차단되지 않고 정상 호출되어야 함
    service.sendMessage(conversationId, "안녕", userId);

    // then
    verify(claudeChatService).chat(any(), anyList(), any());
  }

  @Test
  @DisplayName("sendMessage — Phase 8: FAILED와 ANALYZING이 섞이면 ANALYZING 때문에 차단 (FAILED는 차단 사유에 포함 안 됨)")
  void blocks_only_for_in_progress_sources_even_with_failed_mixed() {
    // given
    Long conversationId = 15L;
    Long userId = 7L;
    Conversation conv = baseConversation(conversationId);
    User user = baseUser(userId);
    Source analyzing = sourceWithStatus(5L, "in-progress.pdf", ProcessingStatus.ANALYZING);
    Source failed = sourceWithStatus(6L, "corrupt.pdf", ProcessingStatus.FAILED);
    stubConversationSources(conversationId, List.of(analyzing, failed));

    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    // when & then — ANALYZING 1개 때문에 차단되지만, pendingSources에 FAILED는 포함되지 않아야 함
    assertThatThrownBy(() -> service.sendMessage(conversationId, "안녕", userId))
        .isInstanceOf(SourceProcessingNotCompleteException.class)
        .satisfies(ex -> {
          SourceProcessingNotCompleteException e = (SourceProcessingNotCompleteException) ex;
          assertThat(e.getPendingSources())
              .extracting(p -> p.status())
              .containsExactly(ProcessingStatus.ANALYZING);
        });
  }

  @Test
  @DisplayName("sendMessage — 첨부 source가 모두 COMPLETED면 정상적으로 LLM 호출")
  void proceeds_when_all_sources_completed() {
    // given
    Long conversationId = 13L;
    Long userId = 7L;
    Conversation conv = baseConversation(conversationId);
    User user = baseUser(userId);
    Source ready = sourceWithStatus(4L, "ready.pdf", ProcessingStatus.COMPLETED);
    stubConversationSources(conversationId, List.of(ready));

    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(conversationMessageRepository.findByConversationConversationIdOrderByCreatedAtAsc(conversationId))
        .thenReturn(List.of());
    when(claudeChatService.chat(any(), anyList(), any())).thenReturn("응답");
    when(conversationMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // when
    service.sendMessage(conversationId, "안녕", userId);

    // then
    verify(claudeChatService).chat(any(), anyList(), any());
  }

  @Test
  @DisplayName("sendMessage — 첨부 자료가 없는 대화는 차단되지 않는다")
  void no_block_when_no_attached_sources() {
    // given
    Long conversationId = 14L;
    Long userId = 7L;
    Conversation conv = baseConversation(conversationId);
    User user = baseUser(userId);
    stubConversationSources(conversationId, List.of());

    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(conversationMessageRepository.findByConversationConversationIdOrderByCreatedAtAsc(conversationId))
        .thenReturn(List.of());
    when(claudeChatService.chat(any(), anyList(), any())).thenReturn("응답");
    when(conversationMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // when
    service.sendMessage(conversationId, "안녕", userId);

    // then
    verify(claudeChatService).chat(any(), anyList(), any());
  }

  @Test
  @DisplayName("getDetail — APPROVED 신청이 있으면 lessonId/lessonName/academyName 반환")
  void getDetail_returns_lesson_fields_when_approved_application_exists() {
    // given
    Long convId = 20L;
    Conversation conv = baseConversation(convId);
    when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
    when(conversationMessageRepository.findByConversationConversationIdOrderByCreatedAtAsc(convId)).thenReturn(List.of());
    when(conversationSourceRepository.findByConversationId(convId)).thenReturn(List.of());

    Academy academy = mock(Academy.class);
    when(academy.getName()).thenReturn("테스트영어학원");
    LessonProduct lesson = mock(LessonProduct.class);
    when(lesson.getLessonId()).thenReturn(5L);
    when(lesson.getName()).thenReturn("고등 영어 내신반");
    when(lesson.getAcademy()).thenReturn(academy);
    LessonApplication app = mock(LessonApplication.class);
    when(app.getLesson()).thenReturn(lesson);

    when(lessonApplicationRepository.findFirstByConversation_ConversationIdAndStatusOrderByAppliedAtDesc(
        convId, LessonApplicationStatus.APPROVED)).thenReturn(Optional.of(app));

    // when
    ConversationDetailResponse result = service.getDetail(convId);

    // then
    assertThat(result.lessonId()).isEqualTo(5L);
    assertThat(result.lessonName()).isEqualTo("고등 영어 내신반");
    assertThat(result.academyName()).isEqualTo("테스트영어학원");
  }

  @Test
  @DisplayName("getDetail — APPROVED 신청이 없으면 lessonId/lessonName/academyName 모두 null")
  void getDetail_returns_null_lesson_fields_when_no_application() {
    // given
    Long convId = 21L;
    Conversation conv = baseConversation(convId);
    when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
    when(conversationMessageRepository.findByConversationConversationIdOrderByCreatedAtAsc(convId)).thenReturn(List.of());
    when(conversationSourceRepository.findByConversationId(convId)).thenReturn(List.of());
    when(lessonApplicationRepository.findFirstByConversation_ConversationIdAndStatusOrderByAppliedAtDesc(
        convId, LessonApplicationStatus.APPROVED)).thenReturn(Optional.empty());

    // when
    ConversationDetailResponse result = service.getDetail(convId);

    // then
    assertThat(result.lessonId()).isNull();
    assertThat(result.lessonName()).isNull();
    assertThat(result.academyName()).isNull();
  }

  @Test
  @DisplayName("getDetail — CANCELED 신청만 있으면 null 반환 (stale-FK 버그 방어)")
  void getDetail_returns_null_lesson_fields_when_only_canceled_application() {
    // given: CANCELED 신청이 conversation_id FK를 유지하지만 APPROVED 조회는 empty를 반환
    Long convId = 22L;
    Conversation conv = baseConversation(convId);
    when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
    when(conversationMessageRepository.findByConversationConversationIdOrderByCreatedAtAsc(convId)).thenReturn(List.of());
    when(conversationSourceRepository.findByConversationId(convId)).thenReturn(List.of());
    when(lessonApplicationRepository.findFirstByConversation_ConversationIdAndStatusOrderByAppliedAtDesc(
        convId, LessonApplicationStatus.APPROVED)).thenReturn(Optional.empty());

    // when
    ConversationDetailResponse result = service.getDetail(convId);

    // then
    assertThat(result.lessonId()).isNull();
    assertThat(result.lessonName()).isNull();
    assertThat(result.academyName()).isNull();
    verify(lessonApplicationRepository).findFirstByConversation_ConversationIdAndStatusOrderByAppliedAtDesc(
        convId, LessonApplicationStatus.APPROVED);
  }

  @Test
  @DisplayName("updateTitle — given 정상 제목 when 호출 then trim 후 title 갱신 + 응답 반환")
  void updateTitle_updates_and_returns() {
    // given
    Long convId = 30L;
    Conversation conv = baseConversation(convId);
    when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));

    // when
    ConversationTitleResponse res = service.updateTitle(convId, "  중간고사 대비  ");

    // then
    assertThat(res.conversationId()).isEqualTo(convId);
    assertThat(res.title()).isEqualTo("중간고사 대비");
    assertThat(conv.getTitle()).isEqualTo("중간고사 대비");
    verify(authResolver).requireStudentOwnedConversationManage(conv);
  }

  @Test
  @DisplayName("updateTitle — given 빈 제목 when 호출 then 예외 + 조회/소유권검증 안 함")
  void updateTitle_rejects_blank() {
    // given / when & then
    assertThatThrownBy(() -> service.updateTitle(31L, "   "))
        .isInstanceOf(IllegalArgumentException.class);
    verify(conversationRepository, never()).findById(any());
    verify(authResolver, never()).verifyOwnsConversation(any());
  }

  @Test
  @DisplayName("updateTitle — given 200자 초과 when 호출 then 예외")
  void updateTitle_rejects_too_long() {
    // given
    String tooLong = "가".repeat(201);
    // when & then
    assertThatThrownBy(() -> service.updateTitle(32L, tooLong))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("updateTitle — given 타인 대화 when 호출 then 403 전파 + title 미변경")
  void updateTitle_denies_non_owner() {
    // given
    Long convId = 33L;
    Conversation conv = baseConversation(convId);
    when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
    doThrow(new AccessDeniedException("자기 대화에만 접근할 수 있습니다."))
        .when(authResolver).requireStudentOwnedConversationManage(conv);

    // when & then
    assertThatThrownBy(() -> service.updateTitle(convId, "변경 시도"))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(conv.getTitle()).isEqualTo("새 대화");
  }

  @Test
  @DisplayName("attachTutorMaterials — given 강사 when 업로드 then SourceType.TUTOR_MATERIAL 저장 + conversation_sources 매핑")
  void attachTutorMaterials_saves_as_tutor_material() {
    // given
    Long convId = 40L;
    Long tutorUserId = 9L;
    Conversation conv = baseConversation(convId);
    User tutor = baseUser(tutorUserId);
    when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
    when(userRepository.findById(tutorUserId)).thenReturn(Optional.of(tutor));
    Source saved = sourceWithStatus(100L, "ref.pdf", ProcessingStatus.COMPLETED);
    when(sourceService.saveBatch(anyList(), any(), eq(SourceType.TUTOR_MATERIAL), any()))
        .thenReturn(List.of(saved));
    MultipartFile file = mock(MultipartFile.class);

    // when
    List<SourceAttachResponse> res = service.attachTutorMaterials(convId, List.of(file), tutorUserId);

    // then
    assertThat(res).hasSize(1);
    verify(authResolver).verifyAdminOrTutor();
    verify(authResolver).verifyOwnsConversation(conv);
    verify(sourceService).saveBatch(anyList(), eq(tutor), eq(SourceType.TUTOR_MATERIAL), any());
    verify(conversationSourceRepository).save(any(ConversationSource.class));
  }

  @Test
  @DisplayName("attachTutorMaterials — given 학생(권한 없음) when 호출 then 403 + saveBatch 호출 안 함")
  void attachTutorMaterials_denies_student() {
    // given
    doThrow(new AccessDeniedException("관리자 또는 강사 권한이 필요합니다."))
        .when(authResolver).verifyAdminOrTutor();
    MultipartFile file = mock(MultipartFile.class);

    // when & then
    assertThatThrownBy(() -> service.attachTutorMaterials(41L, List.of(file), 7L))
        .isInstanceOf(AccessDeniedException.class);
    verify(sourceService, never()).saveBatch(anyList(), any(), any(), any());
  }

  @Test
  @DisplayName("attachTutorMaterials — given 빈 파일 when 호출 then 예외 + 권한검증/저장 안 함")
  void attachTutorMaterials_rejects_empty() {
    // when & then
    assertThatThrownBy(() -> service.attachTutorMaterials(42L, List.of(), 9L))
        .isInstanceOf(IllegalArgumentException.class);
    verify(authResolver, never()).verifyAdminOrTutor();
    verify(sourceService, never()).saveBatch(anyList(), any(), any(), any());
  }


  @Test
  @DisplayName("getDetail — student-shaped API는 학생 소유 가드를 사용한다")
  void getDetail_uses_student_owned_access_guard() {
    // given
    Long convId = 50L;
    Conversation conv = baseConversation(convId);
    when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
    when(conversationMessageRepository.findByConversationConversationIdOrderByCreatedAtAsc(convId)).thenReturn(List.of());
    when(conversationSourceRepository.findByConversationId(convId)).thenReturn(List.of());
    when(lessonApplicationRepository.findFirstByConversation_ConversationIdAndStatusOrderByAppliedAtDesc(
        convId, LessonApplicationStatus.APPROVED)).thenReturn(Optional.empty());

    // when
    service.getDetail(convId);

    // then
    verify(authResolver).requireStudentOwnedConversationAccess(conv);
  }

  @Test
  @DisplayName("updateTitle — student-shaped mutation은 학생 소유 관리 가드를 사용한다")
  void updateTitle_uses_student_owned_manage_guard() {
    // given
    Long convId = 51L;
    Conversation conv = baseConversation(convId);
    when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));

    // when
    service.updateTitle(convId, "학생 기록");

    // then
    verify(authResolver).requireStudentOwnedConversationManage(conv);
  }

  @Test
  @DisplayName("createForTutor — given 강사 when 생성 then tutor-owned conversation 저장")
  void createForTutor_saves_tutor_owned_conversation() {
    // given
    Long tutorUserId = 60L;
    User tutor = baseUser(tutorUserId);
    ReflectionTestUtils.setField(tutor, "role", Role.TUTOR);
    when(userRepository.findById(tutorUserId)).thenReturn(Optional.of(tutor));
    when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
      Conversation conv = inv.getArgument(0);
      ReflectionTestUtils.setField(conv, "conversationId", 601L);
      return conv;
    });

    // when
    ConversationTitleResponse ignored = null;
    var result = service.createForTutor(tutorUserId);

    // then
    assertThat(result.conversationId()).isEqualTo(601L);
    verify(conversationRepository).save(argThat(conv -> conv.getTutorUser() == tutor && conv.getStudent() == null));
  }

  @Test
  @DisplayName("sendTutorMessage — tutor-owned 관리 가드 후 메시지 저장")
  void sendTutorMessage_uses_tutor_owned_manage_guard() {
    // given
    Long convId = 61L;
    Long tutorUserId = 60L;
    User tutor = baseUser(tutorUserId);
    ReflectionTestUtils.setField(tutor, "role", Role.TUTOR);
    Conversation conv = Conversation.forTutor(tutor, "새 강사 기록", null);
    ReflectionTestUtils.setField(conv, "conversationId", convId);
    when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
    when(userRepository.findById(tutorUserId)).thenReturn(Optional.of(tutor));
    when(conversationMessageRepository.findByConversationConversationIdOrderByCreatedAtAsc(convId)).thenReturn(List.of());
    when(claudeChatService.chat(any(), anyList(), any())).thenReturn("응답");
    when(conversationMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // when
    service.sendTutorMessage(convId, "상담 메모", tutorUserId);

    // then
    verify(authResolver).requireTutorOwnedConversationManage(conv);
    verify(claudeChatService).chat(eq(conv), eq(List.of()), eq("상담 메모"));
  }

  @Test
  @DisplayName("getTutorDetail — given 강사 소유 기록 when 상세 조회 then 업로드 source와 messages 반환")
  void getTutorDetail_returns_sources_and_messages_for_tutor_owned_conversation() {
    // given
    Long convId = 62L;
    User tutor = tutorUser(60L);
    Conversation conv = tutorConversation(convId, tutor, "강사 상담 기록");
    ConversationMessage message = ConversationMessage.builder()
        .conversation(conv)
        .role(MessageRole.USER)
        .content("오늘 상담 메모")
        .build();
    ReflectionTestUtils.setField(message, "messageId", 620L);
    Source source = sourceWithStatus(621L, "자료.pdf", ProcessingStatus.COMPLETED);
    stubConversationSources(convId, List.of(source));
    when(sourceService.pageCountOf(source)).thenReturn(3);
    when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
    when(conversationMessageRepository.findByConversationConversationIdOrderByCreatedAtAsc(convId))
        .thenReturn(List.of(message));

    // when
    ConversationDetailResponse result = service.getTutorDetail(convId);

    // then
    assertThat(result.conversationId()).isEqualTo(convId);
    assertThat(result.title()).isEqualTo("강사 상담 기록");
    assertThat(result.student()).isNull();
    assertThat(result.sources())
        .extracting(ConversationDetailResponse.SourceInfo::fileName)
        .containsExactly("자료.pdf");
    assertThat(result.sources().get(0).pageCount()).isEqualTo(3);
    assertThat(result.lessonId()).isNull();
    assertThat(result.lessonName()).isNull();
    assertThat(result.academyName()).isNull();
    assertThat(result.messages())
        .extracting(ConversationDetailResponse.MessageInfo::content)
        .containsExactly("오늘 상담 메모");
    verify(authResolver).requireTutorOwnedConversationAccess(conv);
  }

  @Test
  @DisplayName("attachTutorConversationSources — given 강사 소유 기록 when 업로드 then source 저장·매핑·metadata/title 갱신")
  void attachTutorConversationSources_saves_sources_and_maps_to_tutor_conversation() {
    // given
    Long convId = 6200L;
    Long tutorUserId = 60L;
    User tutor = tutorUser(tutorUserId);
    Conversation conv = tutorConversation(convId, tutor, "새 강사 기록");
    MultipartFile file = mock(MultipartFile.class);
    Source saved = sourceWithStatus(6201L, "상담자료.pdf", ProcessingStatus.PENDING);
    when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
    when(userRepository.findById(tutorUserId)).thenReturn(Optional.of(tutor));
    when(sourceService.saveBatch(eq(List.of(file)), eq(tutor), eq(SourceType.STUDENT_MATERIAL), any(SourceUploadRequest.class)))
        .thenReturn(List.of(saved));

    // when
    List<SourceAttachResponse> result = service.attachTutorConversationSources(
        convId, List.of(file), tutorUserId, SourceUploadRequest.defaultGeneral());

    // then
    assertThat(result).extracting(SourceAttachResponse::sourceId).containsExactly(6201L);
    assertThat(conv.getTitle()).isEqualTo("상담자료.pdf");
    assertThat(conv.getMetadata()).contains("상담자료.pdf");
    verify(authResolver).requireTutorOwnedConversationManage(conv);
    verify(conversationSourceRepository).save(argThat(mapping ->
        mapping.getConversationId().equals(convId) && mapping.getSourceId().equals(6201L)));
  }

  @Test
  @DisplayName("attachTutorConversationSources — given 강사 시험지 메타 누락 when 업로드 then 학원명과 0학년으로 보정")
  void attachTutorConversationSources_defaults_missing_exam_meta_for_tutor_conversation() {
    // given
    Long convId = 6210L;
    Long tutorUserId = 60L;
    User tutor = tutorUser(tutorUserId);
    Conversation conv = tutorConversation(convId, tutor, "새 강사 기록");
    Academy academy = Academy.builder()
        .tutor(tutor)
        .slug("test-academy")
        .name("테스트 학원")
        .logoStorageKey("logo.png")
        .intro("intro")
        .build();
    MultipartFile file = mock(MultipartFile.class);
    Source saved = sourceWithStatus(6211L, "시험지.pdf", ProcessingStatus.PENDING);
    SourceUploadRequest examWithoutDefaults = new SourceUploadRequest(
        ContentKind.EXAM_BLANK, null, null, null, null, null);
    when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
    when(userRepository.findById(tutorUserId)).thenReturn(Optional.of(tutor));
    when(academyRepository.findByTutor_UserId(tutorUserId)).thenReturn(Optional.of(academy));
    when(sourceService.saveBatch(eq(List.of(file)), eq(tutor), eq(SourceType.STUDENT_MATERIAL),
        argThat(request -> request.contentKind() == ContentKind.EXAM_BLANK
            && "테스트 학원".equals(request.schoolName())
            && request.grade() == 0)))
        .thenReturn(List.of(saved));

    // when
    List<SourceAttachResponse> result = service.attachTutorConversationSources(
        convId, List.of(file), tutorUserId, examWithoutDefaults);

    // then
    assertThat(result).extracting(SourceAttachResponse::sourceId).containsExactly(6211L);
    verify(conversationSourceRepository).save(any());
  }

  @Test
  @DisplayName("updateTutorTitle — given 소유 강사 when rename then trim 후 title 갱신")
  void updateTutorTitle_updates_for_owning_tutor() {
    // given
    Long convId = 63L;
    Conversation conv = tutorConversation(convId, tutorUser(60L), "새 강사 기록");
    when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));

    // when
    ConversationTitleResponse result = service.updateTutorTitle(convId, "  상담 기록  ");

    // then
    assertThat(result.conversationId()).isEqualTo(convId);
    assertThat(result.title()).isEqualTo("상담 기록");
    assertThat(conv.getTitle()).isEqualTo("상담 기록");
    verify(authResolver).requireTutorOwnedConversationManage(conv);
  }

  @Test
  @DisplayName("deleteTutorConversation — given 소유 강사 when 삭제 then tutor-owned 가드 후 삭제")
  void deleteTutorConversation_deletes_for_owning_tutor() {
    // given
    Long convId = 64L;
    Conversation conv = tutorConversation(convId, tutorUser(60L), "삭제할 기록");
    when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));

    // when
    service.deleteTutorConversation(convId);

    // then
    verify(authResolver).requireTutorOwnedConversationManage(conv);
    verify(conversationRepository).delete(conv);
  }

  @Test
  @DisplayName("getTutorDetail — given ADMIN/비강사 등 가드 거부 when 상세 조회 then 403 전파 + source/lesson 조회 없음")
  void getTutorDetail_propagates_service_guard_denial() {
    // given
    Long convId = 65L;
    Conversation conv = tutorConversation(convId, tutorUser(60L), "강사 기록");
    when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
    doThrow(new AccessDeniedException("강사 권한이 필요합니다."))
        .when(authResolver).requireTutorOwnedConversationAccess(conv);

    // when & then
    assertThatThrownBy(() -> service.getTutorDetail(convId))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(conversationSourceRepository, sourceRepository, sourceService, lessonApplicationRepository);
  }

  @Test
  @DisplayName("updateTutorTitle/delete/send — given non-owner tutor 가드 거부 then 변경/삭제/메시지 저장 안 함")
  void tutorMutations_stop_when_service_guard_denies_non_owner_or_admin() {
    // given
    Long convId = 66L;
    Long tutorUserId = 61L;
    Conversation conv = tutorConversation(convId, tutorUser(60L), "원래 제목");
    when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
    doThrow(new AccessDeniedException("자기 강사 기록만 접근할 수 있습니다."))
        .when(authResolver).requireTutorOwnedConversationManage(conv);

    // when & then — rename
    assertThatThrownBy(() -> service.updateTutorTitle(convId, "변경 시도"))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(conv.getTitle()).isEqualTo("원래 제목");

    // when & then — delete
    assertThatThrownBy(() -> service.deleteTutorConversation(convId))
        .isInstanceOf(AccessDeniedException.class);
    verify(conversationRepository, never()).delete(any());

    // when & then — send message
    assertThatThrownBy(() -> service.sendTutorMessage(convId, "메시지", tutorUserId))
        .isInstanceOf(AccessDeniedException.class);
    verify(userRepository, never()).findById(tutorUserId);
    verify(conversationMessageRepository, never()).save(any());
    verify(claudeChatService, never()).chat(any(), anyList(), any());
  }

  // --- helpers ---

  private Conversation baseConversation(Long id) {
    Conversation conv = Conversation.builder()
        .student(sampleStudent())
        .title("새 대화")
        .metadata(null)
        .build();
    ReflectionTestUtils.setField(conv, "conversationId", id);
    return conv;
  }

  private User baseUser(Long userId) {
    User user = User.builder()
        .loginId("std1").password("pw").name("홍길동")
        .phone("010").role(Role.STUDENT).build();
    ReflectionTestUtils.setField(user, "userId", userId);
    return user;
  }

  private User tutorUser(Long userId) {
    User tutor = User.builder()
        .loginId("tutor" + userId).password("pw").name("강사")
        .phone("010").role(Role.TUTOR).build();
    ReflectionTestUtils.setField(tutor, "userId", userId);
    return tutor;
  }

  private Conversation tutorConversation(Long id, User tutor, String title) {
    Conversation conv = Conversation.forTutor(tutor, title, null);
    ReflectionTestUtils.setField(conv, "conversationId", id);
    return conv;
  }

  private Student sampleStudent() {
    User user = User.builder()
        .loginId("std1").password("pw").name("홍길동")
        .phone("010").role(Role.STUDENT).build();
    return Student.builder()
        .user(user)
        .schoolName("00중학교")
        .grade((byte) 3)
        .parentName("홍부모")
        .parentPhone("010-1111-2222")
        .build();
  }

  private Source sourceWithStatus(Long sourceId, String fileName, ProcessingStatus status) {
    Source source = Source.builder()
        .uploadedBy(baseUser(1L))
        .originalFileName(fileName)
        .storageKey("/tmp/" + fileName)
        .fileType("PDF")
        .fileSize(1L)
        .sourceType(SourceType.STUDENT_MATERIAL)
        .build();
    ReflectionTestUtils.setField(source, "sourceId", sourceId);
    source.updateProcessingStatus(status);
    return source;
  }

  private void stubConversationSources(Long conversationId, List<Source> sources) {
    List<ConversationSource> mappings = sources.stream()
        .map(s -> ConversationSource.builder()
            .conversationId(conversationId)
            .sourceId(s.getSourceId())
            .build())
        .toList();
    when(conversationSourceRepository.findByConversationId(conversationId)).thenReturn(mappings);
    for (Source s : sources) {
      when(sourceRepository.findById(s.getSourceId())).thenReturn(Optional.of(s));
    }
  }
}
