package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.GlobalExceptionHandler;
import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.ConsultationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.MessageAuthorRole;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultation;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultationMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository.NewStartConsultationMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository.NewStartConsultationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.service.NewStartConsultationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAiChatService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAtozCaseService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.service.NewStartConsultationNotifier;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberDebtStatusService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberProfileService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 상담 API의 <b>소유권 HARD GATE를 HTTP 상태 코드로</b> 검증(설계 §8). 실제 {@link NewStartConsultationService}를
 * 리포지토리만 모킹해 컨텍스트에 넣고, 회원 B(principal userId=2)가 회원 A(소유자 userId=1)의 consultationId를
 * 직접 URL/API로 요청하면 403, 없는 id는 404로 차단됨을 본다. 응답 본문에 A의 질문 내용이 노출되지 않는지도 확인한다.
 *
 * <p>상담은 로그인한 모든 회원이 쓴다(감사회원 요건 없음). 이 테스트의 초점은 소유권 격리이며, 발신자 userId는
 * 서버 발급 JWT 쿠키에서만 나온다(요청값으로 userId를 받지 않는다).
 */
@WebMvcTest(controllers = NewStartConsultationController.class)
@AutoConfigureMockMvc
@Import({
    SecurityConfig.class,
    WebMvcConfig.class,
    RoleModelInterceptor.class,
    GlobalExceptionHandler.class,
    JwtTokenProvider.class,
    CookieUtil.class,
    NewStartConsultationService.class
})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class NewStartConsultationOwnershipIsolationTest {

  private static final long A_OWNER_ID = 1L;
  private static final long B_MEMBER_ID = 2L;
  private static final long FOREIGN_CONSULTATION_ID = 7L;
  private static final String SECRET_QUESTION = "회원A의 개인 채무 상담 내용 마커-CTRL77";

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean NewStartConsultationRepository consultationRepository;
  @MockitoBean NewStartConsultationMessageRepository messageRepository;
  @MockitoBean NewStartConsultationNotifier notifier;
  @MockitoBean NewStartMemberProfileService memberProfileService;
  @MockitoBean UserRepository userRepository;
  @MockitoBean NewStartMemberDebtStatusService debtStatusService;
  @MockitoBean NewStartAtozCaseService atozCaseService;
  @MockitoBean NewStartAiChatService aiChatService;

  private Cookie cookie(long userId) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(userId, "member" + userId, "STUDENT"));
  }

  /** 회원 A가 소유한 상담 7 — 질문 본문에는 채무 PII 마커를 심는다. */
  private NewStartConsultation ownedByA() {
    NewStartConsultation c = NewStartConsultation.builder()
        .memberUserId(A_OWNER_ID).status(ConsultationStatus.NEW).linkDebt(true)
        .lastMessageAt(LocalDateTime.now()).build();
    c.setConsultationId(FOREIGN_CONSULTATION_ID);
    return c;
  }

  @Test
  @DisplayName("회원 B가 회원 A의 consultationId를 GET 하면 403, 본문에 A의 질문 내용이 노출되지 않는다")
  void memberCannotReadForeignConsultation() throws Exception {
    when(consultationRepository.findById(FOREIGN_CONSULTATION_ID)).thenReturn(Optional.of(ownedByA()));

    mockMvc.perform(get("/api/newstart/consultations/" + FOREIGN_CONSULTATION_ID).cookie(cookie(B_MEMBER_ID)))
        .andExpect(status().isForbidden())
        .andExpect(content().string(org.hamcrest.Matchers.not(
            org.hamcrest.Matchers.containsString("CTRL77"))));
  }

  @Test
  @DisplayName("회원 B가 회원 A의 consultationId에 POST(추가 질문) 하면 403")
  void memberCannotReplyToForeignConsultation() throws Exception {
    when(consultationRepository.findById(FOREIGN_CONSULTATION_ID)).thenReturn(Optional.of(ownedByA()));

    mockMvc.perform(post("/api/newstart/consultations/" + FOREIGN_CONSULTATION_ID + "/messages")
            .cookie(cookie(B_MEMBER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"남의 상담에 답글\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("비로그인은 상담 API 어느 것도 못 쓴다(401)")
  void anonymousIsRejected() throws Exception {
    mockMvc.perform(get("/api/newstart/consultations")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/newstart/consultations/" + FOREIGN_CONSULTATION_ID)).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/newstart/consultations")
            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("감사회원 grant가 없는 일반 회원도 자기 상담 목록을 200으로 받는다 (1:1 상담은 일반회원 허용)")
  void plainMemberCanListOwnConsultations() throws Exception {
    when(consultationRepository.findByMemberUserIdOrderByLastMessageAtDesc(B_MEMBER_ID)).thenReturn(List.of());

    mockMvc.perform(get("/api/newstart/consultations").cookie(cookie(B_MEMBER_ID)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("존재하지 않는 consultationId는 감사회원이 조회해도 404")
  void missingConsultationIsNotFound() throws Exception {
    when(consultationRepository.findById(anyLong())).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/newstart/consultations/999").cookie(cookie(B_MEMBER_ID)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("회원 A는 본인 상담을 200으로 읽는다(격리가 과 차단이 아님을 대조 확인)")
  void ownerReadsOwnConsultation() throws Exception {
    when(consultationRepository.findById(FOREIGN_CONSULTATION_ID)).thenReturn(Optional.of(ownedByA()));
    when(messageRepository.findByConsultationIdAndAuthorRoleInOrderByMessageIdAsc(eq(FOREIGN_CONSULTATION_ID), any()))
        .thenReturn(List.of(question(500L)));

    mockMvc.perform(get("/api/newstart/consultations/" + FOREIGN_CONSULTATION_ID).cookie(cookie(A_OWNER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.consultationId").value(FOREIGN_CONSULTATION_ID))
        .andExpect(jsonPath("$.messages[0].authorRole").value("MEMBER"))
        .andExpect(jsonPath("$.messages[0].content").value(SECRET_QUESTION));
  }

  private NewStartConsultationMessage question(long messageId) {
    NewStartConsultationMessage m = NewStartConsultationMessage.builder()
        .consultationId(FOREIGN_CONSULTATION_ID).authorRole(MessageAuthorRole.MEMBER).content(SECRET_QUESTION).build();
    m.setMessageId(messageId);
    return m;
  }
}
