package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.ConsultationThreadView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.ConsultationMessageView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.ConsultationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.MessageAuthorRole;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultation;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultationMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository.NewStartConsultationMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository.NewStartConsultationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAiChatService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAiChatService.NewStartAiAnswer;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAtozCaseService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartEntitlementService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberDebtStatusService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberProfileService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
@DisplayName("NewStart 감사회원 1:1 비공개 상담 — 소유권 HARD GATE·AI조사 익명화·상태 전이")
class NewStartConsultationServiceTest {

  @Mock private NewStartConsultationRepository consultationRepository;
  @Mock private NewStartConsultationMessageRepository messageRepository;
  @Mock private NewStartEntitlementService entitlementService;
  @Mock private NewStartMemberProfileService memberProfileService;
  @Mock private UserRepository userRepository;
  @Mock private NewStartMemberDebtStatusService debtStatusService;
  @Mock private NewStartAtozCaseService atozCaseService;
  @Mock private NewStartAiChatService aiChatService;

  private NewStartConsultationService service;

  @BeforeEach
  void setUp() {
    service = new NewStartConsultationService(consultationRepository, messageRepository, entitlementService,
        memberProfileService, userRepository, debtStatusService, atozCaseService, aiChatService);
  }

  private static AuthenticatedPrincipal member(long id) {
    return new AuthenticatedPrincipal(id, "login" + id, "USER");
  }

  private static AuthenticatedPrincipal admin() {
    return new AuthenticatedPrincipal(999L, "admin", "ADMIN");
  }

  private static NewStartConsultation consult(long id, long ownerId, ConsultationStatus status) {
    NewStartConsultation c = NewStartConsultation.builder()
        .memberUserId(ownerId).status(status).linkDebt(false).lastMessageAt(java.time.LocalDateTime.now())
        .build();
    c.setConsultationId(id);
    return c;
  }

  private static NewStartConsultationMessage msg(long id, long consultationId, MessageAuthorRole role, String content) {
    NewStartConsultationMessage m = NewStartConsultationMessage.builder()
        .consultationId(consultationId).authorRole(role).content(content).build();
    m.setMessageId(id);
    return m;
  }

  @Test
  @DisplayName("given 감사회원 첫 질문 when create then NEW·MEMBER 메시지·결정적 A to Z 단계(지급명령=2)")
  void givenQuestion_whenCreate_thenNewWithDetectedStep() {
    when(consultationRepository.save(any())).thenAnswer(inv -> {
      NewStartConsultation c = inv.getArgument(0);
      if (c.getConsultationId() == null) c.setConsultationId(1L);
      return c;
    });
    when(messageRepository.save(any())).thenAnswer(inv -> {
      NewStartConsultationMessage m = inv.getArgument(0);
      m.setMessageId(500L);
      return m;
    });

    ConsultationThreadView view = service.create(member(1L), "법원에서 지급명령을 받았습니다. 어떻게 하나요?", false);

    assertThat(view.consultationId()).isEqualTo(1L);
    assertThat(view.status()).isEqualTo("NEW");
    assertThat(view.atozStep()).isEqualTo(2);
    assertThat(view.messages()).hasSize(1);
    assertThat(view.messages().get(0).authorRole()).isEqualTo("MEMBER");
  }

  @Test
  @DisplayName("given 빈 내용 when create then IllegalArgumentException(400)")
  void givenBlank_whenCreate_thenThrows() {
    assertThatThrownBy(() -> service.create(member(1L), "   ", false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 다른 회원 상담 when thread then AccessDeniedException — 다른 회원 자료 접근 불가(HARD GATE §8)")
  void givenForeignConsultation_whenThread_thenAccessDenied() {
    when(consultationRepository.findById(7L)).thenReturn(Optional.of(consult(7L, 1L, ConsultationStatus.NEW)));

    assertThatThrownBy(() -> service.thread(member(2L), 7L))
        .isInstanceOf(AccessDeniedException.class);

    verify(messageRepository, never()).findByConsultationIdAndAuthorRoleInOrderByMessageIdAsc(anyLong(), any());
  }

  @Test
  @DisplayName("given AI_RESEARCH 존재 상담 when member thread then 회원 응답에서 AI 조사 원천 제외(§5)")
  void givenAiResearchExists_whenMemberThread_thenExcluded() {
    when(consultationRepository.findById(1L)).thenReturn(Optional.of(consult(1L, 1L, ConsultationStatus.NEW)));
    when(messageRepository.findByConsultationIdAndAuthorRoleInOrderByMessageIdAsc(eq(1L), any()))
        .thenReturn(List.of(msg(10L, 1L, MessageAuthorRole.MEMBER, "질문"),
            msg(11L, 1L, MessageAuthorRole.OPERATOR, "답변")));
    when(messageRepository.findTopByConsultationIdOrderByMessageIdDesc(1L))
        .thenReturn(Optional.of(msg(12L, 1L, MessageAuthorRole.AI_RESEARCH, "운영자 전용")));

    ConsultationThreadView view = service.thread(member(1L), 1L);

    assertThat(view.messages()).extracting(ConsultationMessageView::authorRole)
        .containsExactly("MEMBER", "OPERATOR")
        .doesNotContain("AI_RESEARCH");
    // 운영자 전용 전체 조회 메서드는 회원 경로에서 쓰이지 않는다.
    verify(messageRepository, never()).findByConsultationIdOrderByMessageIdAsc(anyLong());
    verify(consultationRepository).save(any());
  }

  @Test
  @DisplayName("given 운영자 답변 뒤 when 회원 추가 질문 then 상태 MEMBER_FOLLOWUP 전이")
  void givenAnswered_whenReply_thenFollowup() {
    NewStartConsultation c = consult(1L, 1L, ConsultationStatus.OPERATOR_ANSWERED);
    when(consultationRepository.findById(1L)).thenReturn(Optional.of(c));
    when(messageRepository.save(any())).thenAnswer(inv -> {
      NewStartConsultationMessage m = inv.getArgument(0);
      m.setMessageId(900L);
      return m;
    });
    when(messageRepository.findByConsultationIdAndAuthorRoleInOrderByMessageIdAsc(eq(1L), any()))
        .thenReturn(List.of());

    ConsultationThreadView view = service.reply(member(1L), 1L, "추가 질문입니다.");

    assertThat(view.status()).isEqualTo("MEMBER_FOLLOWUP");
  }

  @Test
  @DisplayName("given ROLE_ADMIN 아닌 principal when adminList then AccessDeniedException")
  void givenNonAdmin_whenAdminList_thenAccessDenied() {
    assertThatThrownBy(() -> service.adminList(member(1L)))
        .isInstanceOf(AccessDeniedException.class);
    verify(consultationRepository, never()).findAllByOrderByLastMessageAtDesc();
  }

  @Test
  @DisplayName("given 회원 질문 when adminResearch then 질문은 마스킹 후 지피지기에 전달·AI_RESEARCH만 저장·상담 상태/정렬 미변경(§4·§5·§8)")
  void givenQuestion_whenAdminResearch_thenAnonymizedAndOperatorOnly() {
    when(consultationRepository.findById(1L)).thenReturn(Optional.of(consult(1L, 1L, ConsultationStatus.AWAITING_ANSWER)));
    User memberUser = User.builder().loginId("m1").name("홍길동").nickname("길동").build();
    setUserId(memberUser, 1L);
    when(userRepository.findById(1L)).thenReturn(Optional.of(memberUser));
    when(memberProfileService.resolveDisplayNickname(memberUser)).thenReturn("홍길동님");
    when(messageRepository.findTopByConsultationIdAndAuthorRoleOrderByMessageIdDesc(1L, MessageAuthorRole.MEMBER))
        .thenReturn(Optional.of(msg(10L, 1L, MessageAuthorRole.MEMBER,
            "제 이름은 홍길동이고 닉네임은 길동, 연락처 010-1234-5678입니다. 지급명령을 받았어요.")));
    when(aiChatService.ask(any())).thenReturn(NewStartAiAnswer.failed("오프라인"));
    when(messageRepository.save(any())).thenAnswer(inv -> {
      NewStartConsultationMessage m = inv.getArgument(0);
      m.setMessageId(550L);
      return m;
    });

    ConsultationMessageView view = service.adminResearch(admin(), 1L);

    ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
    verify(aiChatService).ask(sent.capture());
    assertThat(sent.getValue())
        .doesNotContain("홍길동").doesNotContain("길동").doesNotContain("010-1234-5678")
        .contains("(작성자)").contains("(연락처 마스킹)");
    assertThat(view.authorRole()).isEqualTo("AI_RESEARCH");
    // 조사 결과는 상담 정렬 시각·상태를 건드리지 않는다(회원에게 자동 전송/노출 금지).
    verify(consultationRepository, never()).save(any());
  }

  @Test
  @DisplayName("given 조사된 상담 when adminAnswer then OPERATOR 메시지 저장·OPERATOR_ANSWERED 전이(§5 회원 전달은 이 경로뿐)")
  void givenThread_whenAdminAnswer_thenOperatorAnswered() {
    when(consultationRepository.findById(1L)).thenReturn(Optional.of(consult(1L, 1L, ConsultationStatus.AWAITING_ANSWER)));
    when(messageRepository.save(any())).thenAnswer(inv -> {
      NewStartConsultationMessage m = inv.getArgument(0);
      m.setMessageId(700L);
      return m;
    });

    ConsultationMessageView view = service.adminAnswer(admin(), 1L, "이 순서로 진행하세요.");

    ArgumentCaptor<NewStartConsultationMessage> saved = ArgumentCaptor.forClass(NewStartConsultationMessage.class);
    verify(messageRepository).save(saved.capture());
    assertThat(saved.getValue().getAuthorRole()).isEqualTo(MessageAuthorRole.OPERATOR);
    assertThat(view.authorRole()).isEqualTo("OPERATOR");
    verify(consultationRepository, times(1)).save(any());
  }

  private static void setUserId(User user, Long id) {
    try {
      var field = User.class.getDeclaredField("userId");
      field.setAccessible(true);
      field.set(user, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
