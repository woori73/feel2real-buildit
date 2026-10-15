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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.task.scheduling.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdmissionsBrowserQaMySqlTest {
    @LocalServerPort int port;
    @Autowired AdmissionsUniversityRepository universities;
    @Autowired AdmissionsOfficialMaterialRepository materials;
    @Autowired AdmissionsDepartmentRepository departments;
    @Autowired AdmissionsCounselingSessionRepository sessions;
    @MockitoBean AdmissionsLlmClient llm;

    @BeforeAll
    void seedOfficialEvidence() {
        when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(
                NewStartLlmCompletion.success(
                        "2027학년도 기준, 서울대학교 경영학과의 공식 모집요강 정보를 안내합니다.",
                        "stub", "admissions-qa-stub"));
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
        WebDriver driver = new ChromeDriver(options);
        try {
            driver.manage().window().setSize(new Dimension(width, height));
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            driver.get("http://127.0.0.1:" + port + "/admissions/guide");

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
            assertThat(driver.findElement(By.cssSelector(".ag-source")).getText())
                    .contains("서울대학교 2027학년도 수시모집요강");

            driver.findElement(By.id("ag-input"))
                    .sendKeys("서울대 2027학년도 경영학과 공식 자료를 다시 설명해줘");
            driver.findElement(By.cssSelector("#ag-form button[type=submit]")).click();
            wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector(".ag-msg-ai"), 1));

            Path out = Path.of("build/reports/admissions-qa", screenshotName);
            Files.createDirectories(out.getParent());
            Files.write(out, ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
        } finally {
            driver.quit();
        }
    }
}
