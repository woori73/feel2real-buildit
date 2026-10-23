package f2r.tutor_solution.private_tutor_solution_back.domain.agentmedia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant.AssistantConversationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant.AssistantDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant.UnifiedAssistantService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * F2R AI 운영 대시보드(/dashboard) 실제 서버·실제 화면 검증.
 *
 * <p>실제 Spring 보안·컨트롤러·JSON·템플릿·JS(unified_assistant.js + f2r_agent_dashboard.js)를 Chrome으로 실행한다.
 * 업무 비서 서비스 계층만 고정 응답으로 바꾸고(학원 연동은 외부 AcademyCraft 서버가 필요), 그 로직은 기존 단위 테스트가 검증한다.
 * 캐릭터 자료는 테스트용 임시 비공개 폴더(가짜 파일)로 제공해 공개 저장소에 얼굴 자료를 두지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
    "server.port=18093",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.task.scheduling.enabled=false",
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "app.security.admin-session-binding.enabled=false",
    "buildit.unified-assistant.allowed-origins=http://127.0.0.1:18093"
})
@AutoConfigureMockMvc
class AgentDashboardBrowserQaMySqlTest {

  private static final String BASE = "http://127.0.0.1:18093";
  private static final Path MEDIA = privateMediaFolder();

  @DynamicPropertySource
  static void mediaFolder(DynamicPropertyRegistry registry) {
    registry.add("app.agent-media.dir", MEDIA::toString);
  }

  @MockitoBean UnifiedAssistantService service;
  @MockitoBean AssistantConversationService conversation;
  @Autowired JwtTokenProvider tokens;
  @Autowired UserRepository users;
  @Autowired AcademyRepository academies;
  @Autowired MockMvc mvc;

  @Test
  void givenAnonymous_whenReadingCharacterMedia_thenUnauthorized() throws Exception {
    // given / when / then: 캐릭터 자료는 로그인한 ADMIN·TUTOR에게만 제공된다.
    mvc.perform(get("/api/dashboard/agent-media")).andExpect(status().isUnauthorized());
    mvc.perform(get("/dashboard/agent-media/seven/idle.mp4")).andExpect(status().isUnauthorized());
  }

  @Test
  void givenTutor_whenTalkingApprovingAndSwitchingAgent_thenCharacterConsoleFollowsRealAssistantFlow() throws Exception {
    // given
    String token = tutorToken();
    // when / then
    scenario(token, 1440, 1000, "agent-dashboard-pc-1440.png");
    scenario(token, 390, 844, "agent-dashboard-mobile-390.png");
  }

  private void scenario(String token, int width, int height, String screenshot) throws Exception {
    AssistantDraft draft = stubAssistant();
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=" + width + "," + height);
    ChromeDriver driver = new ChromeDriver(options);
    try {
      driver.manage().window().setSize(new Dimension(width, height));
      if (width == 390) driver.executeCdpCommand("Emulation.setDeviceMetricsOverride", Map.of("width", width, "height", height, "deviceScaleFactor", 1, "mobile", true));
      WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
      driver.get(BASE + "/actuator/health");
      driver.manage().addCookie(new org.openqa.selenium.Cookie("ACCESS_TOKEN", token));
      driver.get(BASE + "/dashboard");
      JavascriptExecutor js = driver;

      // 업무 비서 준비 완료(실제 /api/auth/me·/progress·/overview·/drafts 호출 후 버튼 활성화)
      wait.until(d -> Boolean.TRUE.equals(js.executeScript("var b=document.querySelector('#uaCommandForm button[type=submit]');return !!b && !b.disabled;")));
      // 비공개 자료 상태: 세븐은 승인 전 후보, 서수니는 자료 없음
      wait.until(d -> "외형 승인 전 후보".equals(text(driver, "agentBadge")));
      assertThat(js.executeAsyncScript("var done=arguments[arguments.length-1];fetch('/dashboard/agent-media/seven/idle.mp4',{headers:{Range:'bytes=0-1'}}).then(function(r){done(r.status)}).catch(function(){done(-1)});"))
          .isEqualTo(206L);
      assertThat(js.executeAsyncScript("var done=arguments[arguments.length-1];fetch('/dashboard/agent-media/seven/secret.txt').then(function(r){done(r.status)}).catch(function(){done(-1)});"))
          .isEqualTo(404L);

      // 듣기 → 생각 → 응답: 실제 /conversation 응답이 캐릭터 아래에 표시된다
      driver.findElement(By.id("uaCommand")).sendKeys("학생 김하늘 등록해줘, 장부 미수금도 확인");
      assertThat(driver.findElement(By.id("agentStage")).getAttribute("data-state")).isEqualTo("listening");
      js.executeScript("document.querySelector('#uaCommandForm button[type=submit]').click();");
      wait.until(d -> text(driver, "agentGreeting").contains("학생 등록 초안을 만들었습니다"));
      wait.until(d -> "1건".equals(text(driver, "statusPending")));
      assertThat(text(driver, "speechMode")).isEqualTo("텍스트 응답 · 음성 미연결");
      assertThat(driver.findElements(By.cssSelector("#statusServices em"))).hasSizeGreaterThanOrEqualTo(2);

      // 응답 아래 승인 안내 → 실제 /approve → 저장 결과 → 상태창 갱신
      js.executeScript("document.querySelector('#agentSuggest button[data-draft]').click();");
      js.executeScript("document.querySelector('[data-ua-draft=\"" + draft.getId() + "\"] .ua-primary').click();");
      wait.until(d -> "1건".equals(text(driver, "statusDone")));
      wait.until(d -> text(driver, "agentGreeting").contains("처리했습니다"));
      assertThat(text(driver, "statusPending")).isEqualTo("0건");

      // 비서를 바꿔도 대화 기록·업무 상태는 유지된다
      Object before = js.executeScript("return document.getElementById('uaDrafts').children.length;");
      js.executeScript("document.querySelector('[data-agent-pick=\"suni\"]').click();");
      wait.until(d -> "서수니".equals(text(driver, "agentName")));
      assertThat(js.executeScript("return document.getElementById('uaDrafts').children.length;")).isEqualTo(before);
      assertThat(text(driver, "statusDone")).isEqualTo("1건");
      assertThat(text(driver, "agentBadge")).isEqualTo("캐릭터 자료 미연결");
      assertThat(driver.findElement(By.id("agentStage")).getAttribute("data-media")).isEqualTo("missing");

      // 대화 기록 끝까지 내려도 입력창이 보이고 가로 넘침이 없다
      js.executeScript("document.getElementById('uaDrafts').scrollIntoView({block:'end'});");
      Thread.sleep(300);
      assertThat(js.executeScript("var r=document.getElementById('uaCommand').getBoundingClientRect();return r.top>=0&&r.bottom<=window.innerHeight;")).isEqualTo(true);
      assertThat(js.executeScript("return document.documentElement.scrollWidth<=window.innerWidth;")).isEqualTo(true);
      js.executeScript("window.scrollTo(0,0);");

      Path folder = Paths.get("build/reports/agent-dashboard-qa");
      Files.createDirectories(folder);
      Files.copy(driver.getScreenshotAs(OutputType.FILE).toPath(), folder.resolve(screenshot), StandardCopyOption.REPLACE_EXISTING);

      // 실제 사용자 클릭으로 입시상담 AI 서비스 카드가 열린다(주소 직접 이동 없이)
      driver.findElement(By.cssSelector("a[href='/admissions/workspace']")).click();
      wait.until(d -> d.getCurrentUrl().contains("/admissions/workspace"));
    } finally {
      driver.quit();
    }
  }

  private AssistantDraft stubAssistant() {
    AssistantDraft draft = new AssistantDraft(1L, "TUTOR", 7L, null, "STUDENT_CREATE",
        "{\"name\":\"김하늘\"}", "{\"name\":\"김하늘\",\"grade\":\"중2\"}", "agent-dashboard-qa-" + UUID.randomUUID());
    when(service.state(any())).thenReturn(new UnifiedAssistantService.ProgressView(5, true, false, 1L, "TUTOR"));
    when(service.overview(any(), any(), any())).thenReturn(Map.of("asOf", "2026-10-23T00:00:00Z"));
    when(service.history(any())).thenReturn(List.of());
    when(service.audit(any())).thenReturn(List.of());
    when(conversation.send(any(), any(), any(), anyString())).thenReturn(new AssistantConversationService.Turn(
        "학생 등록 초안을 만들었습니다. 내용을 확인하고 승인해 주세요.", "규칙 분석", null, List.of(draft), List.of(), null));
    when(service.decide(any(), eq(draft.getId()), anyString(), eq(false))).thenAnswer(call -> {
      draft.finish("VERIFIED", "{\"message\":\"학생 등록을 저장하고 다시 조회했습니다.\",\"recordId\":101}");
      return draft;
    });
    return draft;
  }

  private String tutorToken() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    User user = users.save(new User("agentqa_" + suffix, "unused-test-password", "비서 QA", "비서 QA", null, null, Role.TUTOR));
    academies.save(f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy.builder()
        .tutor(user).slug("agent-qa-" + suffix).name("격리 QA 학원").logoStorageKey("qa").intro("대시보드 검증용 학원").build());
    return tokens.issue(user.getUserId(), user.getLoginId(), Role.TUTOR.name());
  }

  private static String text(ChromeDriver driver, String id) {
    return String.valueOf(((JavascriptExecutor) driver).executeScript("var e=document.getElementById(arguments[0]);return e?e.textContent:'';", id));
  }

  private static Path privateMediaFolder() {
    try {
      Path dir = Files.createTempDirectory("agent-media-qa");
      Files.createDirectories(dir.resolve("seven"));
      Files.writeString(dir.resolve("seven/idle.mp4"), "not-a-real-video");
      Files.writeString(dir.resolve("seven/poster.jpg"), "not-a-real-image");
      Files.writeString(dir.resolve("seven/secret.txt"), "never-served");
      Files.writeString(dir.resolve("manifest.json"), "{\"seven\":{\"status\":\"CANDIDATE\"}}");
      return dir;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
