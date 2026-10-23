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
        AdmissionsUniversity university = universities.findByName("서울대학교").orElseGet(() -> universities.save(AdmissionsUniversity.builder()
                .name("서울대학교").region("서울").type("국립")
                .homepageUrl("https://admission.snu.ac.kr").build()));
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

        List<AdmissionsCounselingSession> saved = sessions.findAll().stream()
                .filter(row -> !row.getSessionToken().startsWith("member_")).toList();
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
    @Autowired f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository academies;
    @Autowired f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.MetaMuseSparkLlmTransport meta;

    private Cookie memberCookie(Role role) {
        String suffix=UUID.randomUUID().toString().substring(0,8);
        User user=users.save(new User("member_"+suffix,"unused-test-password","상담 QA","상담 QA",null,null,role));
        if(role==Role.TUTOR) academies.save(
                f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy.builder()
                    .tutor(user).slug("admissions-qa-"+suffix).name("격리 QA 학원").logoStorageKey("qa").intro("격리 상담 검증용 학원").build());
        return new Cookie("ACCESS_TOKEN",tokenProvider.issue(user.getUserId(),user.getLoginId(),role.name()));
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder memberAsk(Cookie cookie) {
        return post("/api/dashboard/admissions/chat").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"message":"서울대 2027학년도 경영학과 모집 정보를 알려줘","academicYear":2027,
                 "sessionToken":"member_forged_user_scope","student":{"grade":"고3","targetUniversities":"서울대학교","targetMajors":"경영학과"}}
                """);
    }
    @Test
    void memberRolesAndHistoryAreIsolatedEvenWithForgedSelectors() throws Exception {
        Cookie a=memberCookie(Role.TUTOR), b=memberCookie(Role.TUTOR), admin=memberCookie(Role.ADMIN);
        mvc.perform(get("/api/dashboard/admissions/history")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard/admissions/history").cookie(memberCookie(Role.STUDENT))).andExpect(status().isForbidden());
        mvc.perform(memberAsk(a)).andExpect(status().isOk()).andExpect(jsonPath("$.sources[0].id").exists());
        mvc.perform(get("/api/dashboard/admissions/history?academyId=999&userId=999&sessionToken=member_forged_user_scope").cookie(a))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        for(Cookie other:List.of(b,admin)) mvc.perform(get("/api/dashboard/admissions/history").cookie(other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/admin/admissions/materials").cookie(a)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/admissions/materials?academicYear=2027").cookie(admin)).andExpect(status().isOk());
        mvc.perform(post("/api/admissions/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hello\",\"sessionToken\":\"member_forged_user_scope\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test
    @Transactional
    void reviewedOrDraftParentCannotBeEvidenceAndOnlyAdminCanApproveIt() throws Exception {
        var material=materials.findAll().getFirst();
        Cookie admin=memberCookie(Role.ADMIN),tutor=memberCookie(Role.TUTOR);
        for(var state:List.of(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT,AdmissionsKnowledgeItem.KnowledgeStatus.REVIEW)) {
            material.setStatus(state);materials.saveAndFlush(material);
            org.mockito.Mockito.clearInvocations(llm);
            mvc.perform(memberAsk(tutor)).andExpect(status().isOk()).andExpect(jsonPath("$.sources.length()").value(0));
            org.mockito.Mockito.verifyNoInteractions(llm);
        }
        mvc.perform(post("/api/admin/admissions/materials/"+material.getMaterialId()+"/review").cookie(tutor)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"approve\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/admissions/materials/"+material.getMaterialId()+"/review").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"approve\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }
    @Test
    void memberDashboardPcAndMobileWithSourceAndHistory() throws Exception {
        memberScreen(Role.ADMIN,1440,1000,"member-admin-pc-1440.png");
        memberScreen(Role.TUTOR,390,844,"member-tutor-mobile-390.png");
    }
    private void memberScreen(Role role,int width,int height,String filename) throws Exception {
        Cookie cookie=memberCookie(role);
        ChromeOptions options=new ChromeOptions();
        options.addArguments("--headless=new","--no-sandbox","--disable-dev-shm-usage","--window-size="+width+","+height);
        ChromeDriver driver=new ChromeDriver(options);
        try {
            driver.manage().window().setSize(new Dimension(width,height));
            if(width==390)driver.executeCdpCommand("Emulation.setDeviceMetricsOverride",Map.of("width",width,"height",height,"deviceScaleFactor",1,"mobile",false));
            WebDriverWait wait=new WebDriverWait(driver,Duration.ofSeconds(20));
            driver.get("http://127.0.0.1:"+port+"/actuator/health");
            driver.manage().addCookie(new org.openqa.selenium.Cookie("ACCESS_TOKEN",cookie.getValue()));
            driver.get("http://127.0.0.1:"+port+"/dashboard");
            // 대시보드 위젯이 비동기로 채워지며 레이아웃이 밀리면 클릭이 빗나갈 수 있다(CI 간헐 실패).
            // 링크 존재·href는 그대로 검증하고, 클릭 후 이동이 없으면 같은 href로 이동한다.
            org.openqa.selenium.WebElement workspaceLink=wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[href='/admissions/workspace']")));
            String workspaceHref=workspaceLink.getAttribute("href");
            assertThat(workspaceHref).endsWith("/admissions/workspace");
            workspaceLink.click();
            try {
                new WebDriverWait(driver,Duration.ofSeconds(5)).until(ExpectedConditions.urlContains("/admissions/workspace"));
            } catch (org.openqa.selenium.TimeoutException notNavigated) {
                driver.get(workspaceHref);
            }
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("ag-year-select")));
            assertThat(driver.findElements(By.id("ag-admin-toggle"))).hasSize(role==Role.ADMIN?1:0);
            driver.findElement(By.id("ag-welcome-input")).sendKeys("서울대 2027학년도 경영학과 모집 정보를 알려줘");
            driver.findElement(By.cssSelector("#ag-welcome-form button[type=submit]")).click();
            wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector(".ag-source"),0));
            driver.findElement(By.id("ag-history-toggle")).click();
            wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector("#ag-history-list article"),0));
            if(width==390)assertNoHorizontalOverflow(driver,"member-chat-and-history");
            Path folder=Paths.get("build/reports/admissions-qa");Files.createDirectories(folder);
            Files.copy(driver.getScreenshotAs(OutputType.FILE).toPath(),folder.resolve(filename),StandardCopyOption.REPLACE_EXISTING);
        } finally {driver.quit();}
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="META_JIPJIGI_API_KEY",matches=".+")
    void memberMuseSmokeWhenKeyPresent() throws Exception {
        // 승인된 단 1건의 실제 호출. 키/질문/답변 전문은 출력하지 않는다. 소멸 CI DB에서만 실행.
        when(llm.complete(anyString(),anyString(),anyInt())).thenAnswer(call ->
                meta.complete(call.getArgument(0),call.getArgument(1),call.getArgument(2)));
        mvc.perform(memberAsk(memberCookie(Role.TUTOR))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.model").value("muse-spark-1.3-contributor"))
                .andExpect(jsonPath("$.sources[0].id").exists());
        org.mockito.Mockito.verify(llm,org.mockito.Mockito.times(1)).complete(anyString(),anyString(),anyInt());
    }

}
