package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto.JipijigiInventoryResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiCommand;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiCommand.CommandStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiCommand.CommandType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiAnswerSessionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiBoardService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiCommandService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiInventoryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiQuestionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiWorkflowService;
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
 * 지피지기 Agent ADMIN API 인증/응답 계약 (무DB, CI 실행).
 *
 * <ul>
 *   <li>ROLE_ADMIN만 접근 가능, 일반 회원은 403, 익명은 401</li>
 *   <li>게시 시도는 항상 409 PUBLISH_DISABLED — 화면/서버가 "V1 직접 게시 없음"을 함께 증명한다</li>
 * </ul>
 */
@WebMvcTest(controllers = JipijigiAdminController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("지피지기 Agent ADMIN API 접근 제어")
class JipijigiAdminControllerAuthTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider jwtTokenProvider;

  @MockitoBean JipijigiCommandService commandService;
  @MockitoBean JipijigiQuestionService questionService;
  @MockitoBean JipijigiWorkflowService workflowService;
  @MockitoBean JipijigiAnswerSessionService answerSessions;
  @MockitoBean JipijigiBoardService boardService;
  @MockitoBean JipijigiInventoryService inventoryService;

  private String adminToken;
  private String memberToken;

  @BeforeEach
  void auth() {
    adminToken = jwtTokenProvider.issue(1L, "jipijigi", "ADMIN");
    memberToken = jwtTokenProvider.issue(2L, "member", "USER");
    when(inventoryService.build()).thenReturn(JipijigiInventoryResponse.builder()
        .knowledgeTotal(12L).vectorSearch(false).keywordLikeRetrieval(true).gaps(List.of()).build());
    when(commandService.run(anyString(), anyString(), anyString())).thenReturn(
        new JipijigiCommandService.RunResult(JipijigiCommand.builder()
            .commandId(1L).instruction("Agent 소개글 작성").commandType(CommandType.INTRO_POST)
            .status(CommandStatus.DONE).resultSummary("초안 1건 생성 (상태 DRAFT, 게시 미수행)").build(), List.of()));
    when(workflowService.publish(anyLong(), anyString()))
        .thenThrow(new JipijigiWorkflowService.PublishUnavailableException());
  }

  @Test
  void adminCanRunCommandsAndReadInventory() throws Exception {
    mockMvc.perform(get("/api/admin/newstart/jipijigi/inventory").cookie(new jakarta.servlet.http.Cookie("ACCESS_TOKEN", adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.knowledgeTotal").value(12))
        .andExpect(jsonPath("$.vectorSearch").value(false));

    mockMvc.perform(post("/api/admin/newstart/jipijigi/commands")
            .cookie(new jakarta.servlet.http.Cookie("ACCESS_TOKEN", adminToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"instruction\":\"Agent 소개글 작성\",\"commandType\":\"INTRO_POST\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.command.status").value("DONE"))
        .andExpect(jsonPath("$.drafts").isArray());
  }

  @Test
  void publishIsRejectedWith409() throws Exception {
    mockMvc.perform(post("/api/admin/newstart/jipijigi/drafts/1/publish")
            .cookie(new jakarta.servlet.http.Cookie("ACCESS_TOKEN", adminToken)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("PUBLISH_DISABLED"));
  }

  @Test
  void memberIsForbidden() throws Exception {
    mockMvc.perform(get("/api/admin/newstart/jipijigi/inventory").cookie(new jakarta.servlet.http.Cookie("ACCESS_TOKEN", memberToken)))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/newstart/jipijigi/boards").cookie(new jakarta.servlet.http.Cookie("ACCESS_TOKEN", memberToken)))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/newstart/jipijigi/sessions").cookie(new jakarta.servlet.http.Cookie("ACCESS_TOKEN", memberToken)))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/admin/newstart/jipijigi/drafts/1/exposure-report")
            .cookie(new jakarta.servlet.http.Cookie("ACCESS_TOKEN", memberToken))
            .contentType(MediaType.APPLICATION_JSON).content("{\"publishedAs\":\"POST\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void anonymousIsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/admin/newstart/jipijigi/inventory"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/admin/newstart/jipijigi/questions")
            .contentType(MediaType.APPLICATION_JSON).content("[]"))
        .andExpect(status().isUnauthorized());
  }
}
