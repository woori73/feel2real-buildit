package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * V1141 테이블·JPA 매핑과 청구 링크 → 공개 화면 → 입금 대조 → 납부 기록, 시험 결제창 흐름을 실제 MySQL에서 확인한다
 * (롤백 트랜잭션). 이름·계좌는 모두 지어낸 것이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "app.payment.enabled=true",
    "app.payment.card-test=true",
    "app.payment.webhook-secret=test-only-webhook-value"
})
@DisplayName("수강료 청구 링크·입금 대조·시험 결제창 MySQL 흐름 (스위치 ON)")
class TuitionBillingFlowMySqlTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @Autowired EasyBusinessRepository businesses;
  @Autowired EasyStudentRepository students;
  @Autowired EasyTuitionPaymentRepository payments;
  @Autowired EasyTransactionRepository transactions;
  @Autowired EasyTuitionDepositMatchRepository matches;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entityManager;

  private Cookie admin() {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(2L, "admin", "ADMIN"));
  }

  private EasyBusiness business() {
    return businesses.findAllByActiveTrueOrderBySortOrderAsc().get(0);
  }

  private EasyTuitionPayment bill(String studentName, long billed) {
    EasyStudent student = students.save(EasyStudent.builder().name(studentName).grade((byte) 1)
        .parentPhone("010-0000-0000").status(EasyStudentStatus.ENROLLED).build());
    return payments.save(EasyTuitionPayment.builder().student(student).business(business())
        .targetMonth("2026-11").billedAmount(billed).paidAmount(0).build());
  }

  private Long deposit(long amount, String counterparty) {
    return transactions.save(EasyTransaction.builder().business(business())
        .occurredOn(LocalDate.now()).direction(EasyTxnDirection.INCOME).amount(amount)
        .category("미분류").counterparty(counterparty).description("타행이체").payMethod("계좌")
        .build()).getTxnId();
  }

  private JsonNode createLink(Long paymentId) throws Exception {
    String body = mockMvc.perform(post("/api/admin/easy/billing/links").cookie(admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"paymentId\":" + paymentId + ",\"bankName\":\"시험은행\","
                + "\"bankAccountNo\":\"110-123-456789\",\"bankAccountHolder\":\"시험학원\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return JSON.readTree(body);
  }

  private String runMatch() throws Exception {
    return mockMvc.perform(post("/api/admin/easy/billing/matches/run").cookie(admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"businessId\":" + business().getBusinessId() + ",\"from\":\""
                + LocalDate.now().minusDays(3) + "\",\"to\":\"" + LocalDate.now() + "\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  }

  @Test
  @DisplayName("given 로그인하지 않은 요청 when 관리 API·공개 화면 then 관리 API는 401, 없는 토큰은 404")
  void accessControl() throws Exception {
    mockMvc.perform(get("/api/admin/easy/billing/links").param("month", "2026-11"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/admin/easy/billing/matches/run")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/pay/" + TuitionBillTokens.newToken())).andExpect(status().isNotFound());
    mockMvc.perform(get("/pay/not-a-token")).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/admin/easy/billing/status").cookie(admin()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
  }

  @Test
  @DisplayName("given 청구 링크 when 학부모가 로그인 없이 열기 then 금액·계좌·가린 이름만 보이고 실명·연락처·토큰 원문 저장이 없다")
  void publicPageShowsNoRealName() throws Exception {
    // given
    EasyTuitionPayment payment = bill("가나다", 300_000);
    // when
    JsonNode created = createLink(payment.getPaymentId());
    String url = created.get("url").asText();
    String html = mockMvc.perform(get(url))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andReturn().getResponse().getContentAsString();
    // then
    assertThat(html).contains("가*다").contains("300,000원").contains("110-123-456789")
        .contains(created.get("depositorCode").asText())
        .doesNotContain("가나다").doesNotContain("010-0000-0000");
    String token = url.substring("/pay/".length());
    entityManager.flush();
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM easy_tuition_bill_links WHERE token_hash = ?", Integer.class,
        token)).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM easy_tuition_bill_links WHERE token_hash = ?", Integer.class,
        TuitionBillTokens.hash(token))).isEqualTo(1);
  }

  @Test
  @DisplayName("given 링크 회수 when 다시 열기 then 404")
  void revokedLinkIsGone() throws Exception {
    // given
    JsonNode created = createLink(bill("가나다", 300_000).getPaymentId());
    // when
    mockMvc.perform(post("/api/admin/easy/billing/links/" + created.get("linkId").asLong()
        + "/revoke").cookie(admin())).andExpect(status().isNoContent());
    // then
    mockMvc.perform(get(created.get("url").asText())).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("given 번호·금액이 맞는 입금과 금액만 같은 입금 when 대조를 두 번 실행 then 납부는 한 번만 기록되고 애매한 입금은 확인 목록에 남는다")
  void depositMatchRecordsOnce() throws Exception {
    // given
    EasyTuitionPayment certain = bill("가나다", 300_000);
    EasyTuitionPayment unsure = bill("라마바", 250_000);
    String code = createLink(certain.getPaymentId()).get("depositorCode").asText();
    createLink(unsure.getPaymentId());
    Long certainTxn = deposit(300_000, "홍길동" + code);
    deposit(250_000, "김아무개");
    entityManager.flush();
    // when
    JsonNode first = JSON.readTree(runMatch());
    JsonNode second = JSON.readTree(runMatch());
    entityManager.flush();
    entityManager.clear();
    // then
    assertThat(first.get("autoRecorded").asInt()).isEqualTo(1);
    assertThat(first.get("review").asInt()).isEqualTo(1);
    assertThat(second.get("autoRecorded").asInt()).isZero();
    assertThat(second.get("alreadyHandled").asInt()).isEqualTo(2);
    EasyTuitionPayment paid = payments.findById(certain.getPaymentId()).orElseThrow();
    assertThat(paid.getPaidAmount()).isEqualTo(300_000);
    assertThat(paid.getPayMethod()).isEqualTo("계좌이체");
    assertThat(payments.findById(unsure.getPaymentId()).orElseThrow().getPaidAmount()).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM easy_tuition_deposit_matches WHERE txn_id = ?", Integer.class,
        certainTxn)).isEqualTo(1);
    // 장부 거래는 고치지 않는다
    assertThat(transactions.findById(certainTxn).orElseThrow().getCategory()).isEqualTo("미분류");
    mockMvc.perform(get("/api/admin/easy/billing/matches").cookie(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.reason=='AMOUNT_ONLY' && @.studentName=='라마바')]").exists());
  }

  @Test
  @DisplayName("given 자동 기록된 입금 when 원장이 취소 후 다시 확정 then 금액이 빠졌다가 다시 더해진다")
  void cancelAndConfirm() throws Exception {
    // given
    EasyTuitionPayment payment = bill("가나다", 300_000);
    String code = createLink(payment.getPaymentId()).get("depositorCode").asText();
    Long txnId = deposit(300_000, "홍길동 " + code);
    entityManager.flush();
    runMatch();
    Long matchId = matches.findByTxnIdIn(java.util.List.of(txnId)).get(0).getMatchId();
    // when
    mockMvc.perform(post("/api/admin/easy/billing/matches/" + matchId + "/cancel").cookie(admin()))
        .andExpect(status().isNoContent());
    // then
    assertThat(payments.findById(payment.getPaymentId()).orElseThrow().getPaidAmount()).isZero();
    // when
    mockMvc.perform(post("/api/admin/easy/billing/matches/" + matchId + "/confirm")
        .cookie(admin())).andExpect(status().isNoContent());
    // then
    assertThat(payments.findById(payment.getPaymentId()).orElseThrow().getPaidAmount())
        .isEqualTo(300_000);
    mockMvc.perform(post("/api/admin/easy/billing/matches/" + matchId + "/confirm")
        .cookie(admin())).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("given 시험 결제창 when 성공 주소가 두 번 열리고 알림까지 옴 then 납부는 한 번만 기록된다")
  void cardTestFlowRecordsOnce() throws Exception {
    // given
    EasyTuitionPayment payment = bill("가나다", 300_000);
    String url = createLink(payment.getPaymentId()).get("url").asText();
    String checkout = mockMvc.perform(post(url + "/checkout"))
        .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
    String orderId = checkout.substring(checkout.indexOf("orderId=") + "orderId=".length());
    mockMvc.perform(get(checkout)).andExpect(status().isOk());
    String success = url + "/success?orderId=" + orderId + "&paymentKey=test_ok_" + orderId
        + "&amount=300000";
    // when
    String firstHtml = mockMvc.perform(get(success)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    mockMvc.perform(get(success)).andExpect(status().isOk());
    mockMvc.perform(post("/pay/notify").header("X-F2R-Webhook-Secret", "test-only-webhook-value")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderId\":\"" + orderId + "\",\"paymentKey\":\"test_ok_" + orderId
                + "\",\"amount\":300000}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("ALREADY_PAID"));
    // then
    assertThat(firstHtml).contains("결제가 완료되었습니다");
    EasyTuitionPayment paid = payments.findById(payment.getPaymentId()).orElseThrow();
    assertThat(paid.getPaidAmount()).isEqualTo(300_000);
    assertThat(paid.getPayMethod()).isEqualTo("카드(시험)");
    mockMvc.perform(post("/pay/notify").header("X-F2R-Webhook-Secret", "wrong")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("given 시험 결제창 when 브라우저가 금액을 바꿔 성공 주소를 열기 then 승인하지 않고 납부 기록도 없다")
  void cardTestRejectsTamperedAmount() throws Exception {
    // given
    EasyTuitionPayment payment = bill("가나다", 300_000);
    String url = createLink(payment.getPaymentId()).get("url").asText();
    String checkout = mockMvc.perform(post(url + "/checkout")).andReturn().getResponse()
        .getRedirectedUrl();
    String orderId = checkout.substring(checkout.indexOf("orderId=") + "orderId=".length());
    // when
    String html = mockMvc.perform(get(url + "/success?orderId=" + orderId
            + "&paymentKey=test_ok_" + orderId + "&amount=1000"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    // then
    assertThat(html).contains("결제가 완료되지 않았습니다").contains("금액");
    assertThat(payments.findById(payment.getPaymentId()).orElseThrow().getPaidAmount()).isZero();
    mockMvc.perform(get(url + "/fail")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("given 수납 삭제 when 링크·대조·주문이 딸려 있음 then 함께 지워진다(CASCADE)")
  void deletingPaymentCascades() throws Exception {
    // given
    EasyTuitionPayment payment = bill("가나다", 300_000);
    String url = createLink(payment.getPaymentId()).get("url").asText();
    mockMvc.perform(post(url + "/checkout")).andExpect(status().is3xxRedirection());
    entityManager.flush();
    entityManager.clear();
    // when
    jdbc.update("DELETE FROM easy_tuition_payments WHERE payment_id = ?", payment.getPaymentId());
    // then
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM easy_tuition_bill_links WHERE payment_id = ?", Integer.class,
        payment.getPaymentId())).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM easy_tuition_pg_orders WHERE payment_id = ?", Integer.class,
        payment.getPaymentId())).isZero();
  }
}
