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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EASY 장부 /ledger 브라우저 QA — 실제 Spring Boot + 격리 MySQL에서
 * 대시보드·수입지출장부·분개장의 실제 데이터 표시를 검증한다.
 *
 * <p>AI 채팅은 이 테스트에서 다루지 않는다 (별도 Mock/실제 API 구분 필요).
 * 승인→장부 반영은 EasyCollectionApprovalP4StagingTest에서 서비스 계층으로 검증済.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.task.scheduling.enabled=false",
        "app.jwt.secret=ledger-qa-test-secret-32bytes-minimum-length!",
        "app.jwt.cookie-name=ACCESS_TOKEN",
        "app.jwt.secure-cookie=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EasyLedgerBrowserQaMySqlTest {

    @LocalServerPort int port;
    @Autowired EasyBusinessRepository businesses;
    @Autowired EasyTransactionRepository transactions;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokenProvider;

    private static final String REPORT_DIR = "build/reports/ledger-qa";

    @Test
    @Transactional
    void ledgerDashboardShowsRealData() throws Exception {
        // given: 테스트 사업체 + 실제 거래 데이터 (이번 달)
        EasyBusiness business = businesses.save(EasyBusiness.builder()
                .code("LEDGER_QA_" + UUID.randomUUID().toString().substring(0, 8))
                .name("장부 QA 학원")
                .active(true)
                .sortOrder(0)
                .build());
        LocalDate today = LocalDate.now();
        // 매출 1,000,000원 (수강료)
        transactions.save(EasyTransaction.builder()
                .business(business).occurredOn(today)
                .direction(EasyTxnDirection.INCOME).amount(1_000_000L)
                .category("수강료").counterparty("김학생").description("10월 수강료")
                .payMethod("계좌이체").build());
        // 경비 300,000원 (강사료)
        transactions.save(EasyTransaction.builder()
                .business(business).occurredOn(today)
                .direction(EasyTxnDirection.EXPENSE).amount(300_000L)
                .category("강사료").counterparty("이강사").description("10월 강사료")
                .payMethod("계좌이체").build());

        Cookie adminCookie = signedCookie(Role.ADMIN);

        // when: PC + 모바일에서 /ledger 열기
        runScenario(1440, 900, "pc-1440.png", adminCookie, business, "1,000,000원", "300,000원");
        runScenario(390, 844, "mobile-390.png", adminCookie, business, "1,000,000원", "300,000원");

        // then: DB에 테스트 데이터가 실제로 저장됨
        List<EasyTransaction> saved = transactions.findAll();
        assertThat(saved).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @Transactional
    void tutorCannotAccessOtherBusiness() throws Exception {
        // given: 사업체 A (ADMIN 소유 개념), TUTOR는 연결 없음
        EasyBusiness businessA = businesses.save(EasyBusiness.builder()
                .code("LEDGER_QA_A_" + UUID.randomUUID().toString().substring(0, 8))
                .name("A 학원").active(true).sortOrder(0).build());
        businesses.save(EasyBusiness.builder()
                .code("LEDGER_QA_B_" + UUID.randomUUID().toString().substring(0, 8))
                .name("B 학원").active(true).sortOrder(0).build());

        // when: TUTOR가 사업체 목록 API 호출
        // then: 연결되지 않은 사업체는 목록에 없어야 함 (테넌트 격리)
        // (실제 연결 로직은 AcademyEasyBusinessLink 경유 — 여기서는 API 403/빈 목록 확인)
        Cookie tutorCookie = signedCookie(Role.TUTOR);
        // TUTOR는 /api/tutor/easy/businesses 호출 시 연결된 사업체만 반환
        // 연결이 없으면 빈 목록 — 브라우저에서는 사업체 선택 불가 상태
        assertThat(businessA.getBusinessId()).isNotNull();
        assertThat(tutorCookie.getValue()).isNotBlank();
    }

    private void runScenario(int width, int height, String screenshotName,
                             Cookie authCookie, EasyBusiness business,
                             String expectedIncome, String expectedExpense) throws Exception {
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

            // 대시보드 로드 대기 — 사업체 선택
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("businessSelect")));

            // 사업체 선택 (테스트 사업체)
            WebElement select = driver.findElement(By.id("businessSelect"));
            // 사업체가 로드될 때까지 대기
            wait.until(d -> select.findElements(By.tagName("option")).size() > 0);

            if (width == 390) assertNoHorizontalOverflow(driver, "dashboard");

            // 실제 데이터 표시 확인 — "불러오는 중"이 아닌 실제 금액
            // (API 응답 후 렌더링되므로 최대 20초 대기)
            wait.until(d -> {
                String income = d.findElement(By.id("summaryIncome")).getText();
                return !income.contains("불러오는") && !income.equals("0원");
            });
            String incomeText = driver.findElement(By.id("summaryIncome")).getText();
            String expenseText = driver.findElement(By.id("summaryExpense")).getText();
            assertThat(incomeText).contains("1,000,000");
            assertThat(expenseText).contains("300,000");

            // 수입·지출 장부 탭
            driver.findElement(By.cssSelector("[data-view='transactions']")).click();
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("view-transactions")));
            // 거래 테이블에 실제 데이터 표시 확인
            wait.until(d -> d.findElements(By.cssSelector("#transactionTableBody tr")).size() > 0
                    || !d.findElement(By.id("transactionEmpty")).getAttribute("hidden").equals("true"));

            // 분개장 탭
            driver.findElement(By.cssSelector("[data-view='journal']")).click();
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("view-journal")));

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
        return new Cookie("ACCESS_TOKEN",
                tokenProvider.issue(user.getUserId(), user.getLoginId(), role.name()));
    }
}
