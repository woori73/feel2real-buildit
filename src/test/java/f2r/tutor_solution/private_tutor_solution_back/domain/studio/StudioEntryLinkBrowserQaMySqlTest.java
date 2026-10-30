package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 홈페이지 → AI AGENT 제작협업실 진입 실제 화면 검증(PC 1440·모바일 390).
 *
 * <p>실제 Spring 보안·홈 템플릿·제작협업실 화면을 Chrome으로 열고, 주소 직접 이동 없이 링크를 사용자처럼 클릭한다.
 * 링크는 누구에게나 보이지만 접근 권한(ADMIN·modu)은 서버가 확인한다: 로그인 전 → 로그인 안내, 다른 강사 → 이용 안내(403).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
    "server.port=18094",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.task.scheduling.enabled=false",
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "app.security.admin-session-binding.enabled=false"
})
class StudioEntryLinkBrowserQaMySqlTest {

  private static final String BASE = "http://127.0.0.1:18094";

  @Autowired JwtTokenProvider tokens;
  @Autowired UserRepository users;

  @Test
  void givenHomeOnPcAndMobile_whenClickingStudioLinks_thenGuestLogsInFirstAdminEntersAndOtherTutorIsBlocked() throws Exception {
    // given
    String admin = token(Role.ADMIN, "studioqa_admin_");
    String tutor = token(Role.TUTOR, "studioqa_tutor_");
    // when / then
    scenario(1440, 1000, false, admin, tutor);
    scenario(390, 844, true, admin, tutor);
  }

  private void scenario(int width, int height, boolean mobile, String admin, String tutor) throws Exception {
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=" + width + "," + height);
    ChromeDriver driver = new ChromeDriver(options);
    try {
      driver.manage().window().setSize(new Dimension(width, height));
      if (mobile) driver.executeCdpCommand("Emulation.setDeviceMetricsOverride", Map.of("width", width, "height", height, "deviceScaleFactor", 1, "mobile", true));
      WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
      JavascriptExecutor js = driver;

      // 로그인 전: 상단(모바일은 메뉴) 링크 클릭 → 홈의 로그인 창이 열린다(제작협업실 화면은 열리지 않음)
      driver.get(BASE + "/");
      clickMenuLink(driver, wait, mobile);
      wait.until(d -> Boolean.TRUE.equals(js.executeScript(
          "var m=document.querySelector('[data-login-modal]');return !!m && !m.hidden;")));
      assertThat(URI.create(driver.getCurrentUrl()).getPath()).isEqualTo("/");
      assertThat(driver.getTitle()).doesNotContain("제작 스튜디오");

      // ADMIN 로그인 후: 메뉴 링크·비서 안내 링크를 실제 클릭하면 제작협업실이 열린다(홈의 AI AGENT 배너는 2026-10-30에 내렸다)
      driver.manage().addCookie(new Cookie("ACCESS_TOKEN", admin));
      driver.get(BASE + "/");
      clickMenuLink(driver, wait, mobile);
      waitStudio(driver, wait);
      shot(driver, (mobile ? "mobile-390" : "pc-1440") + "-studio.png");

      // 신청 타일은 신청 안내를 연다. 제작협업실은 기존 비서 입력으로 요청해 안내 링크를 실제 클릭한다.
      driver.get(BASE + "/");
      wait.until(d -> d.findElement(By.id("homeInput")).isDisplayed());
      driver.findElement(By.id("homeInput")).sendKeys("AI AGENT 제작협업실");
      clickVisible(driver, wait, By.cssSelector("#homeForm button[type='submit']"));
      clickVisible(driver, wait, By.cssSelector("#homeChat a[href='/ai-agent-studio']"));
      waitStudio(driver, wait);
      driver.get(BASE + "/");
      assertThat(js.executeScript("return document.documentElement.scrollWidth<=window.innerWidth;")).isEqualTo(true);
      shot(driver, (mobile ? "mobile-390" : "pc-1440") + "-home.png");

      // 다른 강사: 링크는 보이지만 서버가 막고 이용 안내를 보여준다
      driver.manage().deleteCookieNamed("ACCESS_TOKEN");
      driver.manage().addCookie(new Cookie("ACCESS_TOKEN", tutor));
      driver.get(BASE + "/");
      clickMenuLink(driver, wait, mobile);
      wait.until(d -> URI.create(d.getCurrentUrl()).getPath().equals("/ai-agent-studio"));
      wait.until(d -> String.valueOf(js.executeScript("return document.body.innerText;")).contains("관리자와 지정된 운영 계정만"));
      assertThat(driver.getTitle()).doesNotContain("제작 스튜디오");
    } finally {
      driver.quit();
    }
  }

  private static void clickMenuLink(ChromeDriver driver, WebDriverWait wait, boolean mobile) {
    if (mobile) {
      clickVisible(driver, wait, By.id("enBurger"));
      clickVisible(driver, wait, By.cssSelector("#enDrawer a.en-drawer-studio"));
    } else {
      clickVisible(driver, wait, By.cssSelector(".en-menu a.en-menu-studio"));
    }
  }

  private static void clickVisible(ChromeDriver driver, WebDriverWait wait, By by) {
    WebElement el = wait.until(d -> {
      WebElement e = d.findElement(by);
      return e.isDisplayed() ? e : null;
    });
    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", el);
    el.click();
  }

  private static void waitStudio(ChromeDriver driver, WebDriverWait wait) {
    wait.until(d -> URI.create(d.getCurrentUrl()).getPath().equals("/ai-agent-studio"));
    wait.until(d -> d.getTitle().contains("제작 스튜디오"));
  }

  private static void shot(ChromeDriver driver, String name) throws Exception {
    Path folder = Paths.get("build/reports/studio-entry-qa");
    Files.createDirectories(folder);
    Files.copy(driver.getScreenshotAs(OutputType.FILE).toPath(), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
  }

  private String token(Role role, String prefix) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    User user = users.save(new User(prefix + suffix, "unused-test-password", "제작협업실 QA", "제작협업실 QA", null, null, role));
    return tokens.issue(user.getUserId(), user.getLoginId(), role.name());
  }
}
