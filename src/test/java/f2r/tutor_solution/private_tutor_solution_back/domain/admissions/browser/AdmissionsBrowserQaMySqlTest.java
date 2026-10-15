package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.browser;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsLlmClient;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.task.scheduling.enabled=false",
        "app.admissions.public-api.enabled=true",
        "app.jwt.secret=12345678901234567890123456789012",
        "app.jwt.cookie-name=ACCESS_TOKEN",
        "app.jwt.secure-cookie=false"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdmissionsBrowserQaMySqlTest {
    @LocalServerPort int port;
    @Autowired AdmissionsUniversityRepository universities;
    @Autowired AdmissionsOfficialMaterialRepository materials;
    @Autowired AdmissionsDepartmentRepository departments;
    @Autowired AdmissionsCounselingSessionRepository sessions;
    @MockitoBean AdmissionsLlmClient llm;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository users;

    @Test
    @Transactional
    void anonymousCanChatButCannotReadAdminKnowledge() throws Exception {
        // given: no authentication cookie; when: public chat and protected admin read.
        assertChatAllowed(null);
        // then: anonymous admin requests must fail authentication.
        mvc.perform(get("/api/admin/admissions/knowledge").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void ordinaryStudentCanChatButCannotReadAdminKnowledge() throws Exception {
        // given: a real signed STUDENT JWT, processed by the application's security filters.
        Cookie cookie = signedCookie(Role.STUDENT);
        // when/then: public chat works, but the ADMIN API refuses the ordinary user.
        assertChatAllowed(cookie);
        mvc.perform(get("/api/admin/admissions/knowledge").cookie(cookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void adminCanChatAndReadAdminKnowledge() throws Exception {
        Cookie cookie = signedCookie(Role.ADMIN);
        assertChatAllowed(cookie);
        mvc.perform(get("/api/admin/admissions/knowledge").cookie(cookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
    }

    private Cookie signedCookie(Role role) {
        User user = users.save(new User("aq_" + UUID.randomUUID().toString().substring(0, 8),
                "unused-test-password", "입시 QA", "입시 QA", null, null, role));
        return new Cookie("ACCESS_TOKEN", tokenProvider.issue(user.getUserId(), user.getLoginId(), role.name()));
    }

    private void assertChatAllowed(Cookie cookie) throws Exception {
        var request = post("/api/admissions/chat").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"message":"서울대 2027학년도 경영학과 모집 정보를 알려줘",
                         "academicYear":2027,"student":{"grade":"고3"}}
                        """);
        if (cookie != null) request.cookie(cookie);
        mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @BeforeEach
    void stubLlmOnly() {
        when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(
                NewStartLlmCompletion.success(
                        "2027학년도 기준, 서울대학교 경영학과의 공식 모집요강 정보를 안내합니다.",
                        "stub", "admissions-qa-stub"));
    }

    @BeforeAll
    void seedOfficialEvidence() {
        AdmissionsUniversity university = universities.save(AdmissionsUniversity.builder()
                .name("서울대학교").region("서울").type("국립")
                .homepageUrl("https://admission.snu.ac.kr").build());
        AdmissionsOfficialMaterial material = materials.save(AdmissionsOfficialMaterial.builder()
                .university(university).academicYear(2027)
                .title("서울대학교 2027학년도 수시모집요강")
                .issuer("서울대학교 입학본부")
                .documentUrl("https://admission.snu.ac.kr/2027-guide")
                .status(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE).build());
        departments.save(AdmissionsDepartment.builder()
                .university(university).academicYear(2027).name("경영학과")
                .category("인문").quotaInfo("모집인원은 공식 모집요강 원문을 기준으로 확인")
                .source(material).status(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE).build());
    }

    @Test
    void pc1440AndMobile390CompleteTwoTurnCounselingWithSourcesAndHistory() throws Exception {
        runScenario(1440, 1000, "pc-1440.png");
        runScenario(390, 844, "mobile-390.png");

        List<AdmissionsCounselingSession> saved = sessions.findAll();
        assertThat(saved).hasSizeGreaterThanOrEqualTo(4);
        assertThat(saved).allMatch(row -> row.getStudentProfile() != null
                && row.getStudentProfile().contains("고3"));
        assertThat(saved.stream().map(AdmissionsCounselingSession::getSessionToken).distinct().count())
                .isEqualTo(2);
    }

    private void runScenario(int width, int height, String screenshotName) throws Exception {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
                "--window-size=" + width + "," + height);
        ChromeDriver driver = new ChromeDriver(options);
        try {
            driver.manage().window().setSize(new Dimension(width, height));
            if (width == 390) {
                driver.executeCdpCommand("Emulation.setDeviceMetricsOverride", Map.of(
                        "width", 390, "height", height, "deviceScaleFactor", 1, "mobile", false));
            }
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            driver.get("http://127.0.0.1:" + port + "/admissions/guide");

            if (width == 390) assertNoHorizontalOverflow(driver, "student-form");
            new Select(driver.findElement(By.id("ag-grade"))).selectByVisibleText("고3");
            driver.findElement(By.id("ag-target-universities")).sendKeys("서울대학교");
            driver.findElement(By.id("ag-target-majors")).sendKeys("경영학과");
            new Select(driver.findElement(By.id("ag-admission-track"))).selectByVisibleText("학생부종합");
            driver.findElement(By.id("ag-academic-summary")).sendKeys("내신과 모의고사 성적을 함께 검토 중");
            driver.findElement(By.id("ag-welcome-input"))
                    .sendKeys("서울대 2027학년도 경영학과 모집 정보를 알려줘");
            driver.findElement(By.cssSelector("#ag-welcome-form button[type=submit]")).click();

            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector(".ag-msg-ai"), "서울대학교 경영학과"));
            wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector(".ag-source"), 0));
            WebElement officialSource = driver.findElement(By.cssSelector(".ag-source"));
            assertThat(officialSource.getText()).contains("경영학과");
            assertThat(officialSource.getAttribute("href"))
                    .isEqualTo("https://admission.snu.ac.kr/2027-guide");

            driver.findElement(By.id("ag-input"))
                    .sendKeys("서울대 2027학년도 경영학과 공식 자료를 다시 설명해줘");
            driver.findElement(By.cssSelector("#ag-form button[type=submit]")).click();
            wait.until(driverState -> driverState.findElements(By.cssSelector(".ag-msg-ai")).stream()
                    .filter(element -> element.getText().contains("서울대학교 경영학과"))
                    .count() >= 2);

            if (width == 390) assertNoHorizontalOverflow(driver, "second-response-complete");
            Path out = Path.of("build/reports/admissions-qa", screenshotName);
            Files.createDirectories(out.getParent());
            Files.write(out, ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
        } finally {
            driver.quit();
        }
    }

    private void assertNoHorizontalOverflow(ChromeDriver driver, String phase) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Number> dimensions = (Map<String, Number>) driver.executeScript("""
                return {viewport: window.innerWidth,
                        scrollWidth: document.documentElement.scrollWidth,
                        clientWidth: document.documentElement.clientWidth,
                        bodyScrollWidth: document.body.scrollWidth,
                        bodyClientWidth: document.body.clientWidth};
                """);
        Path report = Path.of("build/reports/admissions-qa/mobile-dom-" + phase + ".txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report, phase + " " + dimensions + System.lineSeparator());
        assertThat(dimensions.get("viewport").intValue()).isEqualTo(390);
        assertThat(dimensions.get("clientWidth").intValue()).isPositive().isLessThanOrEqualTo(390);
        assertThat(dimensions.get("scrollWidth").intValue())
                .isLessThanOrEqualTo(dimensions.get("clientWidth").intValue());
        assertThat(dimensions.get("bodyScrollWidth").intValue())
                .isLessThanOrEqualTo(dimensions.get("bodyClientWidth").intValue());
    }
}
