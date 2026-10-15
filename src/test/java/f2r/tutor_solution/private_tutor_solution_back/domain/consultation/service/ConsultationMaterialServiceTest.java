package f2r.tutor_solution.private_tutor_solution_back.domain.consultation.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.consultation.dto.ConsultationMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.service.SourceService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ConsultationMaterialService — 상담자료 업로드/삭제 권한·IDOR 가드")
class ConsultationMaterialServiceTest {

  private static final Long CONVERSATION_ID = 1L;
  private static final Long SOURCE_ID = 100L;
  private static final Long TUTOR_USER_ID = 10L;

  private ConversationRepository conversationRepository;
  private ConversationSourceRepository conversationSourceRepository;
  private SourceRepository sourceRepository;
  private UserRepository userRepository;
  private SourceService sourceService;
  private AuthenticatedUserResolver authResolver;
  private ConsultationMaterialService service;

  private JwtTokenProvider.AuthenticatedPrincipal tutorPrincipal;
  private Conversation conversation;
  private User uploader;

  @BeforeEach
  void setUp() {
    conversationRepository = mock(ConversationRepository.class);
    conversationSourceRepository = mock(ConversationSourceRepository.class);
    sourceRepository = mock(SourceRepository.class);
    userRepository = mock(UserRepository.class);
    sourceService = mock(SourceService.class);
    authResolver = mock(AuthenticatedUserResolver.class);
    service = new ConsultationMaterialService(
        conversationRepository,
        conversationSourceRepository,
        sourceRepository,
        userRepository,
        sourceService,
        authResolver);

    tutorPrincipal = new JwtTokenProvider.AuthenticatedPrincipal(TUTOR_USER_ID, "tutor", Role.TUTOR.name());
    conversation = mock(Conversation.class);
    lenient().when(conversation.getConversationId()).thenReturn(CONVERSATION_ID);
    uploader = User.builder().loginId("tutor").password("pw").name("강사").role(Role.TUTOR).build();
    ReflectionTestUtils.setField(uploader, "userId", TUTOR_USER_ID);
  }

  private MultipartFile imageFile() {
    return new MockMultipartFile("files", "consult.jpg", "image/jpeg", new byte[]{1, 2, 3});
  }

  /* ── upload ─────────────────────────────────────────────── */

  @Test
  @DisplayName("강사가 본인 담당 conversation에 업로드하면 Source(CONSULTATION) + ConversationSource 매핑이 생성된다")
  void upload_byTutor_createsSourceAndMapping() {
    // given
    when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
    when(userRepository.findById(TUTOR_USER_ID)).thenReturn(Optional.of(uploader));
    Source saved = mock(Source.class);
    when(saved.getSourceId()).thenReturn(SOURCE_ID);
    when(saved.getOriginalFileName()).thenReturn("consult.jpg");
    when(saved.getFileType()).thenReturn("IMAGE");
    when(sourceService.saveManualFile(any(), eq(uploader), eq(SourceType.CONSULTATION), any()))
        .thenReturn(saved);
    when(sourceService.pageCountOf(saved)).thenReturn(1);

    // when
    List<ConsultationMaterialResponse> result =
        service.upload(tutorPrincipal, CONVERSATION_ID, List.of(imageFile()));

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).sourceId()).isEqualTo(SOURCE_ID);
    verify(authResolver).verifyAdminOrTutor();
    verify(authResolver).verifyOwnsConversation(conversation);
    verify(sourceService).saveManualFile(any(), eq(uploader), eq(SourceType.CONSULTATION), any());
    verify(conversationSourceRepository).save(any());
  }

  @Test
  @DisplayName("학생이 업로드하면 verifyAdminOrTutor에서 막혀 파일이 저장되지 않는다")
  void upload_byStudent_blocked() {
    // given
    doThrow(new AccessDeniedException("관리자 또는 강사 권한이 필요합니다."))
        .when(authResolver).verifyAdminOrTutor();

    // when / then
    assertThatThrownBy(() -> service.upload(tutorPrincipal, CONVERSATION_ID, List.of(imageFile())))
        .isInstanceOf(AccessDeniedException.class);
    verify(sourceService, never()).saveManualFile(any(), any(), any(), any());
    verify(conversationSourceRepository, never()).save(any());
  }

  @Test
  @DisplayName("담당이 아닌 강사가 업로드하면 verifyOwnsConversation에서 막혀 파일이 저장되지 않는다")
  void upload_byNonOwningTutor_blocked() {
    // given
    when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
    doThrow(new AccessDeniedException("담당 수업의 대화가 아닙니다."))
        .when(authResolver).verifyOwnsConversation(conversation);

    // when / then
    assertThatThrownBy(() -> service.upload(tutorPrincipal, CONVERSATION_ID, List.of(imageFile())))
        .isInstanceOf(AccessDeniedException.class);
    verify(sourceService, never()).saveManualFile(any(), any(), any(), any());
  }

  /* ── delete (정상) ──────────────────────────────────────── */

  @Test
  @DisplayName("강사가 본인 conversation의 CONSULTATION 자료를 삭제하면 deleteTrusted가 1회 호출된다")
  void delete_consultationSource_callsDeleteTrusted() {
    // given
    when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
    when(conversationSourceRepository.existsByConversationIdAndSourceId(CONVERSATION_ID, SOURCE_ID))
        .thenReturn(true);
    Source source = mock(Source.class);
    when(source.getSourceType()).thenReturn(SourceType.CONSULTATION);
    when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));

    // when
    service.delete(CONVERSATION_ID, SOURCE_ID);

    // then
    verify(authResolver).verifyOwnsConversation(conversation);
    verify(authResolver).verifyAdminOrTutor();
    verify(sourceService).deleteTrusted(SOURCE_ID);
  }

  /* ── delete (차단/거부) ─────────────────────────────────── */

  @Test
  @DisplayName("학생이 삭제하면 verifyAdminOrTutor에서 막혀 deleteTrusted가 호출되지 않는다")
  void delete_byStudent_blocked() {
    // given
    when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
    doThrow(new AccessDeniedException("관리자 또는 강사 권한이 필요합니다."))
        .when(authResolver).verifyAdminOrTutor();

    // when / then
    assertThatThrownBy(() -> service.delete(CONVERSATION_ID, SOURCE_ID))
        .isInstanceOf(AccessDeniedException.class);
    verify(sourceService, never()).deleteTrusted(anyLong());
  }

  @Test
  @DisplayName("C1(a) 타 conversation에만 매핑된 sourceId 삭제 시 404, deleteTrusted 미호출")
  void delete_sourceMappedToOtherConversation_404() {
    // given
    when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
    when(conversationSourceRepository.existsByConversationIdAndSourceId(CONVERSATION_ID, SOURCE_ID))
        .thenReturn(false);

    // when / then
    assertThatThrownBy(() -> service.delete(CONVERSATION_ID, SOURCE_ID))
        .isInstanceOf(ResponseStatusException.class);
    verify(sourceRepository, never()).findById(anyLong());
    verify(sourceService, never()).deleteTrusted(anyLong());
  }

  @Test
  @DisplayName("C1(b) CONSULTATION이 아닌 type(TUTOR_MATERIAL) 삭제 시 404, deleteTrusted 미호출")
  void delete_nonConsultationType_404() {
    // given
    when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
    when(conversationSourceRepository.existsByConversationIdAndSourceId(CONVERSATION_ID, SOURCE_ID))
        .thenReturn(true);
    Source source = mock(Source.class);
    when(source.getSourceType()).thenReturn(SourceType.TUTOR_MATERIAL);
    when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));

    // when / then
    assertThatThrownBy(() -> service.delete(CONVERSATION_ID, SOURCE_ID))
        .isInstanceOf(ResponseStatusException.class);
    verify(sourceService, never()).deleteTrusted(anyLong());
  }

  @Test
  @DisplayName("C1(c) 미존재 sourceId 삭제 시 매핑 부재로 404, deleteTrusted 미호출")
  void delete_nonexistentSource_404() {
    // given
    when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
    when(conversationSourceRepository.existsByConversationIdAndSourceId(CONVERSATION_ID, SOURCE_ID))
        .thenReturn(false);

    // when / then
    assertThatThrownBy(() -> service.delete(CONVERSATION_ID, SOURCE_ID))
        .isInstanceOf(ResponseStatusException.class);
    verify(sourceService, never()).deleteTrusted(anyLong());
  }
}
