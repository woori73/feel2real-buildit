package f2r.tutor_solution.private_tutor_solution_back.domain.easy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * EASY 위젯 API — 실제 보안 필터 체인과 MySQL로 ADMIN 정상 응답과 사업체별 격리를 검증한다.
 *
 * <p>운영 데이터는 건드리지 않는다: 이 테스트는 CI의 일회용 MySQL에서만 돌고, 시드(2099년 거래)는
 * 테스트 트랜잭션이 끝나면 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("EASY 위젯 API — ADMIN 정상 응답·사업체 격리 (MySQL 통합)")
class EasyDashboardWidgetMySqlTest {

  private static final String URL = "/api/admin/easy/dashboard/widgets";
  private static final String MONTH = "2099-10";

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired JwtTokenProvider tokenProvider;
  @Autowired EasyBusinessRepository businesses;
  @Autowired EasyTransactionRepository transactions;

  private Cookie cookie(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "widget-" + role, role));
  }

  private EasyBusiness business(String tag, boolean active) {
    return businesses.save(EasyBusiness.builder()
        .code(tag + System.nanoTime() % 1_000_000_000L).name(tag).active(active).sortOrder(99).build());
  }

  private void txn(EasyBusiness business, EasyTxnDirection direction, long amount, String description) {
    transactions.save(EasyTransaction.builder()
        .business(business).occurredOn(LocalDate.of(2099, 10, 5)).direction(direction)
        .amount(amount).description(description).build());
  }

  private JsonNode widgets(long businessId, Cookie cookie) throws Exception {
    String body = mockMvc.perform(get(URL).param("businessId", String.valueOf(businessId))
            .param("month", MONTH).cookie(cookie).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get("widgets");
  }

  private static long card(JsonNode widgets, String label) {
    for (JsonNode c : widgets.get(0).get("data").get("cards")) {
      if (label.equals(c.get("label").asText())) {
        return c.get("value").asLong();
      }
    }
    throw new AssertionError("card not found: " + label);
  }

  private static String titles(JsonNode widgets) {
    StringBuilder sb = new StringBuilder();
    for (JsonNode item : widgets.get(2).get("data").get("items")) {
      sb.append(item.get("title").asText()).append('|');
    }
    return sb.toString();
  }

  @Test
  @DisplayName("ADMIN은 자기가 지정한 사업체의 수치만 받고, 다른 사업체 거래는 섞이지 않는다")
  void admin_getsOnlyRequestedBusinessData() throws Exception {
    // given: 서로 다른 거래를 가진 두 사업체
    EasyBusiness a = business("WIDGET_A", true);
    EasyBusiness b = business("WIDGET_B", true);
    txn(a, EasyTxnDirection.INCOME, 1_000_000L, "A-수납");
    txn(b, EasyTxnDirection.INCOME, 250_000L, "B-수납");
    txn(b, EasyTxnDirection.EXPENSE, 40_000L, "B-지출");

    // when
    JsonNode forA = widgets(a.getBusinessId(), cookie("ADMIN"));
    JsonNode forB = widgets(b.getBusinessId(), cookie("ADMIN"));
    JsonNode forAAgain = widgets(a.getBusinessId(), cookie("ADMIN")); // 캐시 적중 경로

    // then: 사업체별 수치가 분리되고, 캐시 적중이어도 다른 사업체 값으로 바뀌지 않는다
    assertThat(card(forA, "수납")).isEqualTo(1_000_000L);
    assertThat(card(forA, "지출")).isZero();
    assertThat(titles(forA)).contains("A-수납").doesNotContain("B-");
    assertThat(card(forB, "수납")).isEqualTo(250_000L);
    assertThat(card(forB, "지출")).isEqualTo(40_000L);
    assertThat(titles(forB)).contains("B-수납", "B-지출").doesNotContain("A-");
    assertThat(card(forAAgain, "수납")).isEqualTo(1_000_000L);
    assertThat(titles(forAAgain)).doesNotContain("B-");
  }

  @Test
  @DisplayName("비활성·존재하지 않는 사업체는 데이터 없이 오류 위젯만 받는다")
  void inactiveOrMissingBusiness_returnsErrorWidgetsOnly() throws Exception {
    // given
    EasyBusiness inactive = business("WIDGET_OFF", false);
    txn(inactive, EasyTxnDirection.INCOME, 777_000L, "OFF-수납");

    // when
    JsonNode off = widgets(inactive.getBusinessId(), cookie("ADMIN"));
    JsonNode missing = widgets(987_654_321L, cookie("ADMIN"));

    // then: 값이 새지 않는다
    for (JsonNode w : new JsonNode[] {off, missing}) {
      for (JsonNode widget : w) {
        assertThat(widget.get("error").asText()).isEqualTo("일시적으로 표시할 수 없습니다.");
      }
      assertThat(w.get(0).get("data").get("cards")).isEmpty();
    }
  }

  @Test
  @DisplayName("비인증은 401, TUTOR는 403 — 실제 보안 필터 체인")
  void nonAdmin_isRejected() throws Exception {
    mockMvc.perform(get(URL).param("businessId", "1").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get(URL).param("businessId", "1").cookie(cookie("TUTOR")).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }
}
