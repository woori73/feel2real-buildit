package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartRequestKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberGrantRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartEntitlementService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartRequestService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartRequestService.RequestPage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartRequestService.RequestResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartRequestService.RequestView;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
 * 질문요청 게시판 API(설계 §5) 접근 계약 — §7 경계를 UI가 아니라 backend에서 강제한다.
 *
 * <ul>
 *   <li>읽기(목록·상세): 인증 회원이면 서로의 요청 열람, 비로그인은 401</li>
 *   <li>쓰기(작성): THANKS_MEMBER만 — grant 없는 회원은 403 + 안내, service.create는 호출되지 않는다</li>
 *   <li>취소: 본인/ADMIN만(403), 없는 요청은 404</li>
 * </ul>
 */
@WebMvcTest(controllers = NewStartRequestController.class)
@AutoConfigureMockMvc
@Import({
    SecurityConfig.class,
    WebMvcConfig.class,
    RoleModelInterceptor.class,
    JwtTokenProvider.class,
    CookieUtil.class,
    NewStartEntitlementService.class
})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class NewStartRequestControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean NewStartRequestService requestService;
  @MockitoBean NewStartMemberGrantRepository grantRepository;

  private static final String QUESTION_BODY =
      "{\"kind\":\"QUESTION\",\"title\":\"개인회생 순서가 궁금해요\",\"problem\":\"어디부터 시작하나요?\"}";

  private Cookie memberCookie() {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "student", "STUDENT"));
  }

  private static RequestView view() {
    return new RequestView(7L, "QUESTION", "개인회생 순서가 궁금해요", null, null,
        "어디부터 시작하나요?", null, "RECEIVED", null, null, null, null, null,
        1L, "공용닉네임", "2026-09-27T09:00:00", List.of());
  }

  /** 기본 시나리오는 "grant 보유 회원" — 거절 케이스만 false로 되돌린다. */
  @BeforeEach
  void memberHoldsThanksGrant() {
    when(grantRepository.existsByUserIdAndCapability(anyLong(), any(NewStartCapability.class)))
        .thenReturn(true);
  }

  @Test
  @DisplayName("given: 비로그인 / when: 목록·상세·작성·취소 / then: 전부 4xx(SecurityConfig catch-all)")
  void anonymousRequestsAreRejected() throws Exception {
    mockMvc.perform(get("/api/newstart/requests")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/newstart/requests/7")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/newstart/requests")
            .contentType(MediaType.APPLICATION_JSON).content(QUESTION_BODY))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(delete("/api/newstart/requests/7")).andExpect(status().isUnauthorized());

    verify(requestService, never()).list(any(), any(), any());
    verify(requestService, never()).create(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("given: 인증 회원 / when: 목록 조회 / then: 서로의 요청 열람(kind 필터·hasMore 포함)")
  void memberReadsRequestList() throws Exception {
    when(requestService.list(eq(NewStartRequestKind.QUESTION), eq(0), eq(20)))
        .thenReturn(new RequestPage(List.of(view()), true));

    mockMvc.perform(get("/api/newstart/requests")
            .param("kind", "QUESTION").param("page", "0").param("size", "20")
            .cookie(memberCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests[0].id").value(7))
        .andExpect(jsonPath("$.requests[0].kind").value("QUESTION"))
        .andExpect(jsonPath("$.requests[0].author").value("공용닉네임"))
        .andExpect(jsonPath("$.requests[0].statusLogs.length()").value(0))
        .andExpect(jsonPath("$.hasMore").value(true));
  }

  @Test
  @DisplayName("given: 상세 / when: 서비스 실패(삭제·없음) / then: 404 + 안내 문구")
  void missingDetailIs404() throws Exception {
    when(requestService.detail(404L)).thenReturn(RequestResult.failed("요청을 찾을 수 없습니다."));

    mockMvc.perform(get("/api/newstart/requests/404").cookie(memberCookie()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.errorMessage").value("요청을 찾을 수 없습니다."));
  }

  @Test
  @DisplayName("given: grant 보유 회원 / when: 작성 / then: 200 + 저장된 view, principal이 작성자 근거")
  void thanksMemberCanCreateRequest() throws Exception {
    when(requestService.create(any(), eq(NewStartRequestKind.QUESTION),
        eq("개인회생 순서가 궁금해요"), isNull(), isNull(), eq("어디부터 시작하나요?"), isNull()))
        .thenReturn(RequestResult.success(view()));

    mockMvc.perform(post("/api/newstart/requests")
            .cookie(memberCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content(QUESTION_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.request.id").value(7))
        .andExpect(jsonPath("$.request.authorId").value(1));
  }

  @Test
  @DisplayName("given: grant 없는 회원 / when: 작성 / then: 403 + 안내, service.create 미호출")
  void memberWithoutGrantCannotCreate() throws Exception {
    when(grantRepository.existsByUserIdAndCapability(anyLong(), any(NewStartCapability.class)))
        .thenReturn(false);

    mockMvc.perform(post("/api/newstart/requests")
            .cookie(memberCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content(QUESTION_BODY))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.errorMessage").value(NewStartEntitlementService.DENIAL_MESSAGE));
    verify(requestService, never()).create(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("given: ADMIN / when: 작성 / then: grant row 없이 통과(DENIAL 규칙 우회)")
  void adminWritesWithoutGrantRow() throws Exception {
    when(requestService.create(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(RequestResult.success(view()));

    mockMvc.perform(post("/api/newstart/requests")
            .cookie(new Cookie("ACCESS_TOKEN", tokenProvider.issue(9L, "admin", "ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(QUESTION_BODY))
        .andExpect(status().isOk());
    verify(grantRepository, never()).existsByUserIdAndCapability(anyLong(), any(NewStartCapability.class));
  }

  @Test
  @DisplayName("given: 검증 실패(제목 없음) / when: 작성 / then: 400 + 사용자 안내만 노출")
  void validationFailureIs400() throws Exception {
    when(requestService.create(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(RequestResult.failed("제목을 입력해 주세요."));

    mockMvc.perform(post("/api/newstart/requests")
            .cookie(memberCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"kind\":\"QUESTION\",\"title\":\"\",\"problem\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.errorMessage").value("제목을 입력해 주세요."));
  }

  @Test
  @DisplayName("given: 타인의 요청 취소 / when: DELETE / then: 403, 본인·ADMIN 취소는 200")
  void withdrawMapsPermissionToHttpStatus() throws Exception {
    when(requestService.withdraw(any(), eq(8L)))
        .thenReturn(RequestResult.forbidden("본인이 작성한 요청만 취소할 수 있습니다."));
    mockMvc.perform(delete("/api/newstart/requests/8").cookie(memberCookie()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorMessage").value("본인이 작성한 요청만 취소할 수 있습니다."));

    when(requestService.withdraw(any(), eq(7L))).thenReturn(RequestResult.success(null));
    mockMvc.perform(delete("/api/newstart/requests/7").cookie(memberCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));

    when(requestService.withdraw(any(), eq(9L)))
        .thenReturn(RequestResult.failed("요청을 찾을 수 없습니다."));
    mockMvc.perform(delete("/api/newstart/requests/9").cookie(memberCookie()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("given: 알 수 없는 kind 값 / when: 목록 조회 / then: 400(서버가 열거형만 허용)")
  void unknownKindIsRejected() throws Exception {
    mockMvc.perform(get("/api/newstart/requests").param("kind", "SECRET").cookie(memberCookie()))
        .andExpect(status().isBadRequest());
  }
}
