package f2r.tutor_solution.private_tutor_solution_back.config.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller.AdmissionsAdminController;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsOfficialMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsActivationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsDataLoader;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsVectorIndex;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdmissionsAdminController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("입시자료 전체 ACTIVE 승격 API — ADMIN 전용·감사 로그")
class AdmissionsActivateAllSecurityTest {

  private static final String URL = "/api/admin/admissions/activate-all";
  private static final String BODY = "{\"academicYear\":2027,\"confirm\":\"ACTIVATE-ALL-2027\",\"note\":\"전체 ACTIVE 정책\"}";

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean AuthenticatedUserResolver authResolver;
  @MockitoBean AdmissionsActivationService activationService;
  @MockitoBean AdmissionsKnowledgeItemRepository knowledgeRepository;
  @MockitoBean AdmissionsVectorIndex vectorIndex;
  @MockitoBean AdmissionsDataLoader dataLoader;
  @MockitoBean AdmissionsOfficialMaterialRepository materials;

  private ListAppender<ILoggingEvent> logs;
  private Logger controllerLogger;

  @BeforeEach
  void setUp() {
    when(authResolver.currentPrincipal()).thenReturn(Optional.empty());
    controllerLogger = (Logger) LoggerFactory.getLogger(AdmissionsAdminController.class);
    logs = new ListAppender<>();
    logs.start();
    controllerLogger.addAppender(logs);
  }

  @AfterEach
  void tearDown() {
    controllerLogger.detachAppender(logs);
  }

  private Cookie token(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "test-" + role.toLowerCase(), role));
  }

  @Test
  @DisplayName("given 비로그인 JSON 요청 when 호출하면 then 401이고 서비스는 호출되지 않는다")
  void anonymousIsUnauthorized() throws Exception {
    // when / then
    mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)
            .header("Accept", MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isUnauthorized());
    verify(activationService, never()).activateAll(anyInt(), any(), any());
  }

  @Test
  @DisplayName("given TUTOR·STUDENT when 호출하면 then 403이고 서비스는 호출되지 않는다")
  void nonAdminIsForbidden() throws Exception {
    // when / then
    for (String role : new String[] {"TUTOR", "STUDENT"}) {
      mockMvc.perform(post(URL).cookie(token(role)).contentType(MediaType.APPLICATION_JSON).content(BODY))
          .andExpect(status().isForbidden());
    }
    verify(activationService, never()).activateAll(anyInt(), any(), any());
  }

  @Test
  @DisplayName("given ADMIN when 호출하면 then 200과 건수를 돌려주고 실행 관리자·건수를 감사 로그로 남긴다")
  void adminSucceedsAndLogsAudit() throws Exception {
    // given
    when(activationService.activateAll(eq(2027), eq("ACTIVATE-ALL-2027"), eq(Set.of())))
        .thenReturn(new AdmissionsActivationService.ActivationResult(2027, 20, 0, 116, 72, 0, 0));

    // when / then
    mockMvc.perform(post(URL).cookie(token("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.types").value(116))
        .andExpect(jsonPath("$.schedules").value(72))
        .andExpect(jsonPath("$.status").value("ACTIVATED"));
    assertThat(logs.list).anySatisfy(e -> assertThat(e.getFormattedMessage())
        .contains("admissions activate-all by=test-admin(userId=1)").doesNotContain("credentialStamp").contains("year=2027")
        .contains("materials=20").contains("types=116").contains("schedules=72"));
  }

  @Test
  @DisplayName("given ADMIN이 excludeFileKeys를 지정 when 호출하면 then 제외 키가 서비스로 전달되고 제외 건수가 응답·로그에 남는다")
  void adminPassesExcludeFileKeysAndReturnsExcludedCounts() throws Exception {
    // given
    when(activationService.activateAll(eq(2027), eq("ACTIVATE-ALL-2027"), eq(Set.of("doc_cau_2027_susi"))))
        .thenReturn(new AdmissionsActivationService.ActivationResult(2027, 19, 0, 116, 72, 1, 0));

    // when / then
    mockMvc.perform(post(URL).cookie(token("ADMIN")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"academicYear\":2027,\"confirm\":\"ACTIVATE-ALL-2027\",\"excludeFileKeys\":[\"doc_cau_2027_susi\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.materials").value(19))
        .andExpect(jsonPath("$.excludedMaterials").value(1))
        .andExpect(jsonPath("$.excludedChildren").value(0))
        .andExpect(jsonPath("$.status").value("ACTIVATED"));
    assertThat(logs.list).anySatisfy(e -> assertThat(e.getFormattedMessage())
        .contains("excludedMaterials=1").contains("excludedKeys=[doc_cau_2027_susi]"));
  }

  @Test
  @DisplayName("given ADMIN이 잘못된 확인 문자열 when 호출하면 then 400이고 성공 감사 로그를 남기지 않는다")
  void badConfirmationIsRejectedWithoutAuditSuccess() throws Exception {
    // given
    when(activationService.activateAll(anyInt(), any(), any())).thenThrow(new IllegalArgumentException("확인 문자열이 맞지 않습니다."));

    // when / then
    mockMvc.perform(post(URL).cookie(token("ADMIN")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"academicYear\":2027,\"confirm\":\"yes\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("REJECTED"));
    assertThat(logs.list).noneSatisfy(e -> assertThat(e.getFormattedMessage()).contains("admissions activate-all by="));
  }
}
