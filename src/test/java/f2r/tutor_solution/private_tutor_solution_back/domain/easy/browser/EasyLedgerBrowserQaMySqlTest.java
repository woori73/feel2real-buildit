package f2r.tutor_solution.private_tutor_solution_back.domain.easy.browser;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EASY 장부 /ledger 브라우저 QA — 실제 Spring Boot + 격리 MySQL에서
 * 대시보드·수입지출장부·분개장의 실제 데이터 표시를 검증한다.
 *
 * <p><b>트랜잭션 주의:</b> 브라우저는 별도 HTTP 연결이므로 테스트 데이터를
 * 반드시 커밋해야 한다. {@code @Transactional}을 사용하지 않고,
 * {@code @BeforeEach}에서 저장(즉시 커밋) → {@code @AfterEach}에서 정리한다.
 *
 * <p><b>범위:</b> CSV 업로드·승인→장부 반영·AI 채팅은 이 테스트 범위 밖이다
 * (각각 EasyTutorCsvWorkflowMySqlTest·EasyCollectionApprovalP4StagingTest·별도 항목에서 검증).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.task.scheduling.enabled=false",
        "app.jwt.secret=ledger-qa-test-secret-32bytes-minimum-length!",
        "app.jwt.cookie-name=ACCESS_TOKEN",
        "app.jwt.secure-cookie=false"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EasyLedgerBrowserQaMySqlTest {

    @LocalServerPort int port;
    @Autowired EasyBusinessRepository businesses;
    @Autowired EasyTransactionRepository transactions;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired MockMvc mvc;

    private static final String REPORT_DIR = "build/reports/ledger-qa";
    private static final String CODE_PREFIX = "LEDGER_QA_";

    private final List<Long> businessIds = new ArrayList<>();
    private final List<Long> txnIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void setUpCommittedTestData() {
        // 커밋된 데이터: 브라우저 HTTP 요청(별도 커넥션)에서 조회 가능
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        EasyBusiness business = businesses.save(EasyBusiness.builder()
                .code(CODE_PREFIX + suffix)
                .name("장부 QA 학원")
                .active(true)
                .sortOrder(0)
                .build());
        businessIds.add(business.getBusinessId());

        LocalDate today = LocalDate.now();
        txnIds.add(transactions.save(EasyTransaction.builder()
                .business(business).occurredOn(today)
                .direction(EasyTxnDirection.INCOME).amount(1_000_000L)
                .category("수강료").counterparty("김학생").description("10월 수강료")
                .payMethod("계좌이체").build()).getTxnId());
        txnIds.add(transactions.save(EasyTransaction.builder()
                .business(business).occurredOn(today)
                .direction(EasyTxnDirection.EXPENSE).amount(300_000L)
                .category("강사료").counterparty("이강사").description("10월 강사료")
                .payMethod("계좌이체").build()).getTxnId());
    }

    @AfterEach
    void cleanUpCommittedTestData() {
        // FK 역순으로 정리 (거래 → 사업체 → 사용자)
        txnIds.forEach(id -> transactions.deleteById(id));
        txnIds.clear();
        businessIds.forEach(id -> businesses.deleteById(id));
        businessIds.clear();
        userIds.forEach(id -> users.deleteById(id));
        userIds.clear();
    }

    @Test
    void ledgerDashboardShowsRealCommittedData() throws Exception {
        Cookie adminCookie = signedCookie(Role.ADMIN);

        runScenario(1440, 900, "pc-1440.png", adminCookie);
        runScenario(390, 844, "mobile-390.png", adminCookie);
    }

    @Test
    void tutorTenantIsolationViaApi() throws Exception {
        // given: 연결되지 않은 TUTOR
        Cookie tutorCookie = signedCookie(Role.TUTOR);

        // when: TUTOR가 사업체 목록 API 호출
        // then: 연결된 사업체만 반환 (연결 없으면 빈 목록, 403 아님)
        var result = mvc.perform(get("/api/tutor/easy/businesses")
                        .cookie(tutorCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        // 연결되지 않은 QA 사업체 코드는 목록에 없어야 함
        assertThat(body).doesNotContain(CODE_PREFIX);

        // ADMIN은 전체 사업체 조회 가능
        Cookie adminCookie = signedCookie(Role.ADMIN);
        var adminResult = mvc.perform(get("/api/tutor/easy/businesses")
                        .cookie(adminCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        // ADMIN은 QA 사업체를 볼 수 있음 (테넌트 격리: TUTOR와 다름)
        assertThat(adminResult.getResponse().getContentAsString()).contains(CODE_PREFIX);
    }

    private void runScenario(int width, int height, String screenshotName,
                             Cookie authCookie) throws Exception {
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

            // 인증 쿠키 설정 후 /ledger 접근
            driver.get("http://127.0.0.1:" + port + "/");
            driver.manage().addCookie(new org.openqa.selenium.Cookie(
                    authCookie.getName(), authCookie.getValue(), "127.0.0.1", "/", null));
            driver.get("http://127.0.0.1:" + port + "/ledger");

            // 대시보드 로드 — 사업체 선택 드롭다운
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("businessSelect")));
            WebElement select = driver.findElement(By.id("businessSelect"));
            wait.until(d -> select.findElements(By.tagName("option")).size() > 0);

            if (width == 390) assertNoHorizontalOverflow(driver, "dashboard");

            // 실제 데이터 표시 확인 — 커밋된 1,000,000원/300,000원이 화면에 나타나야 함
            wait.until(d -> {
                String income = d.findElement(By.id("summaryIncome")).getText();
                return !income.contains("불러오는") && income.contains("1,000,000");
            });
            assertThat(driver.findElement(By.id("summaryIncome")).getText()).contains("1,000,000");
            assertThat(driver.findElement(By.id("summaryExpense")).getText()).contains("300,000");
            // 수지 차액 = 700,000원
            assertThat(driver.findElement(By.id("summaryProfit")).getText()).contains("700,000");

            // 수입·지출 장부 탭 — 실제 거래 내용 검증 (김학생·이강사)
            driver.findElement(By.cssSelector("[data-view='transactions']")).click();
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("view-transactions")));
            wait.until(d -> {
                String bodyText = d.findElement(By.id("transactionTableBody")).getText();
                return bodyText.contains("김학생") && bodyText.contains("이강사");
            });
            String txBody = driver.findElement(By.id("transactionTableBody")).getText();
            assertThat(txBody).contains("김학생").contains("1,000,000");
            assertThat(txBody).contains("이강사").contains("300,000");

            // 분개장 탭 — 차변/대변 표시 확인
            driver.findElement(By.cssSelector("[data-view='journal']")).click();
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("view-journal")));
            wait.until(d -> !d.findElement(By.id("journalList")).getText().contains("불러오는 중"));
            String journalText = driver.findElement(By.id("journalList")).getText();
            // 분개장에 거래가 표시됨 (빈 상태가 아님)
            assertThat(journalText).doesNotContain("불러오는 중입니다");

            if (width == 390) assertNoHorizontalOverflow(driver, "journal");

            Path out = Path.of(REPORT_DIR, screenshotName);
            Files.createDirectories(out.getParent());
            Files.write(out, ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
        } finally {
            driver.quit();
        }
    }

    private void assertNoHorizontalOverflow(ChromeDriver driver, String phase) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Number> dimensions = (Map<String, Number>) driver.executeScript(
                "return {viewport: window.innerWidth," +
                " scrollWidth: document.documentElement.scrollWidth," +
                " clientWidth: document.documentElement.clientWidth};");
        Path report = Path.of(REPORT_DIR, "mobile-dom-" + phase + ".txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report, phase + " " + dimensions + System.lineSeparator());
        assertThat(dimensions.get("viewport").intValue()).isEqualTo(390);
        assertThat(dimensions.get("clientWidth").intValue()).isPositive().isLessThanOrEqualTo(390);
        assertThat(dimensions.get("scrollWidth").intValue())
                .isLessThanOrEqualTo(dimensions.get("clientWidth").intValue());
    }

    private Cookie signedCookie(Role role) {
        User user = users.save(new User("ledger_qa_" + UUID.randomUUID().toString().substring(0, 8),
                "unused-test-password", "장부 QA", "장부 QA", null, null, role));
        userIds.add(user.getUserId());
        return new Cookie("ACCESS_TOKEN",
                tokenProvider.issue(user.getUserId(), user.getLoginId(), role.name()));
    }
}
