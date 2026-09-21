package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 매통조 어댑터 검증 — mock client + fixture 맵만 사용. 실제 HTTP 호출 없음.
 */
class CrefiaCardsalesAdapterTest {

  private static final String CONN_REF = "crefia:1234567890";

  private CrefiaCardsalesClient client;
  private CrefiaCardsalesAdapter adapter;

  @BeforeEach
  void setUp() {
    client = mock(CrefiaCardsalesClient.class);
    adapter = new CrefiaCardsalesAdapter(client,
        () -> new CrefiaCredentialProvider.Credentials("id", "secret"));
    when(client.fetchApprovals(any(), any(), anyInt())).thenReturn(List.of(
        Map.of("승인번호", "AP123", "승인금액", "100,000", "거래일자", "20260901",
            "가맹점명", "압구정에듀홀릭", "카드구분", "신용", "할부기간", "0"),
        Map.of("승인번호", "AP124", "승인금액", "50000", "거래일자", "2026-09-02",
            "가맹점명", "압구정에듀홀릭")));
    when(client.fetchPurchases(any(), any(), anyInt())).thenReturn(List.of(
        Map.of("가맹점번호", "M001", "승인번호", "AP123", "매출금액", "100000",
            "수수료", "2000", "지급금액", "98000", "거래일자", "20260901",
            "매입일자", "20260902", "지급예정일자", "20260905", "카드사명", "신한카드")));
    when(client.fetchDeposits(any(), any(), anyInt())).thenReturn(List.of(
        Map.of("카드사명", "신한카드", "매출금액", "100000", "수수료", "2000",
            "입금예정금액", "98000", "입금예정일", "20260905", "대금지급은행", "국민은행")));
  }

  @Test
  @DisplayName("어댑터 SPI 계약: adapterCode=CARDSALES (V983 시드와 일치)")
  void adapterContract() {
    assertThat(adapter.adapterCode()).isEqualTo("CARDSALES");
    assertThat(adapter.adapterVersion()).isEqualTo("v1-crefia");
    assertThat(adapter.configKeys()).containsExactly("merchantNo");
  }

  @Test
  @DisplayName("fetch: 승인+매입+입금 4건, 전부 INCOME 방향 (가맹점 매출)")
  void fetch_allDatasets() {
    List<CollectionParsedRow> rows = adapter.fetch(CONN_REF, null, 5000);
    assertThat(rows).hasSize(4);
    assertThat(rows).allSatisfy(r ->
        assertThat(r.direction()).isEqualTo("INCOME"));
  }

  @Test
  @DisplayName("승인 매핑: externalId=APPR:{가맹점}:{승인번호}, provenance 보존")
  void approvalMapping() {
    CollectionParsedRow row = adapter.fetch(CONN_REF, null, 5000).stream()
        .filter(r -> "APPROVAL".equals(r.originalRow().get("recordType")))
        .filter(r -> r.externalId().contains("AP123"))
        .findFirst().orElseThrow();
    assertThat(row.externalId()).startsWith("APPR:");
    assertThat(row.amount()).isEqualTo(100000L);
    assertThat(row.occurredOn().toString()).isEqualTo("2026-09-01");
    assertThat(row.rawJson()).contains("\"provider\":\"CREFIA\"");
    assertThat(row.rawJson()).contains("CARDSALES_APPROVAL");
    assertThat(row.rawJson()).contains("AP123");
    assertThat(row.rawJson()).contains("collectedAt");
    assertThat(row.payMethod()).isEqualTo("CARD");
    assertThat(row.evidenceType()).isEqualTo("CARD_RECEIPT");
  }

  @Test
  @DisplayName("매입 매핑: 수수료·지급예정일이 description에 포함")
  void purchaseMapping() {
    CollectionParsedRow row = adapter.fetch(CONN_REF, null, 5000).stream()
        .filter(r -> "PURCHASE".equals(r.originalRow().get("recordType")))
        .findFirst().orElseThrow();
    assertThat(row.counterparty()).isEqualTo("신한카드");
    assertThat(row.description()).contains("2000").contains("20260905");
  }

  @Test
  @DisplayName("입금 매핑: ACCOUNT_TRANSFER/BANK_STATEMENT, DEP: 외부ID")
  void depositMapping() {
    CollectionParsedRow row = adapter.fetch(CONN_REF, null, 5000).stream()
        .filter(r -> "DEPOSIT".equals(r.originalRow().get("recordType")))
        .findFirst().orElseThrow();
    assertThat(row.externalId()).startsWith("DEP:");
    assertThat(row.payMethod()).isEqualTo("ACCOUNT_TRANSFER");
    assertThat(row.evidenceType()).isEqualTo("BANK_STATEMENT");
    assertThat(row.amount()).isEqualTo(98000L);
  }

  @Test
  @DisplayName("승인번호 없는 매입 → 가맹점+매입일자+금액 fingerprint 외부ID")
  void purchaseWithoutApprovalNo() {
    when(client.fetchApprovals(any(), any(), anyInt())).thenReturn(List.of());
    when(client.fetchPurchases(any(), any(), anyInt())).thenReturn(List.of(
        Map.of("가맹점번호", "M002", "매출금액", "30000", "매입일자", "20260903", "거래일자", "20260902")));
    when(client.fetchDeposits(any(), any(), anyInt())).thenReturn(List.of());
    List<CollectionParsedRow> rows = adapter.fetch(CONN_REF, null, 5000);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).externalId()).isEqualTo("PUR:M002:20260903:30000");
  }

  @Test
  @DisplayName("빈 결과 → 빈 리스트 (예외 없음)")
  void fetch_empty() {
    when(client.fetchApprovals(any(), any(), anyInt())).thenReturn(List.of());
    when(client.fetchPurchases(any(), any(), anyInt())).thenReturn(List.of());
    when(client.fetchDeposits(any(), any(), anyInt())).thenReturn(List.of());
    assertThat(adapter.fetch(CONN_REF, null, 5000)).isEmpty();
  }

  @Test
  @DisplayName("malformed 행 → error 필드로 구분 (예외를 던지지 않음)")
  void fetch_malformedRow() {
    when(client.fetchApprovals(any(), any(), anyInt())).thenReturn(List.of(
        Map.of("승인번호", "BAD1", "승인금액", "not-a-number", "거래일자", "20260901")));
    when(client.fetchPurchases(any(), any(), anyInt())).thenReturn(List.of());
    when(client.fetchDeposits(any(), any(), anyInt())).thenReturn(List.of());
    List<CollectionParsedRow> rows = adapter.fetch(CONN_REF, null, 5000);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).isValid()).isFalse();
    assertThat(rows.get(0).error()).isNotBlank();
  }

  @Test
  @DisplayName("자격증명 없음 → IllegalStateException (이용기관 승인 안내)")
  void fetch_noCredentials() {
    CrefiaCardsalesAdapter blocked = new CrefiaCardsalesAdapter(client,
        () -> new CrefiaCredentialProvider.Credentials(null, null));
    assertThatThrownBy(() -> blocked.fetch(CONN_REF, null, 100))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("이용기관");
  }

  @Test
  @DisplayName("connectionRef 검증: 빈 값 거부, 오형식 거부")
  void connectionRef_validation() {
    assertThatThrownBy(() -> adapter.fetch("", null, 100))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.fetch("cardsales-abc", null, 100))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("crefia:<사업자번호");
  }

  @Test
  @DisplayName("validateConnection: 네트워크 호출 없이 형식만 검증")
  void validateConnection_noNetwork() {
    adapter.validateConnection(CONN_REF);
    verifyNoInteractions(client);
    assertThatThrownBy(() -> adapter.validateConnection("bad"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("limit 하드 캡(5000) 초과 시 예외")
  void fetch_limitHardCap() {
    assertThatThrownBy(() -> adapter.fetch(CONN_REF, null, 5001))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1~5000");
    assertThatThrownBy(() -> adapter.fetch(CONN_REF, null, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("since 증분 수집: 기준일 이전 행 제외")
  void fetch_sinceIncremental() {
    List<CollectionParsedRow> rows =
        adapter.fetch(CONN_REF, LocalDateTime.of(2026, 9, 2, 0, 0), 5000);
    assertThat(rows).isNotEmpty();
    assertThat(rows).allSatisfy(r ->
        assertThat(r.occurredOn().atStartOfDay())
            .isAfter(LocalDateTime.of(2026, 9, 2, 0, 0)));
  }

  @Test
  @DisplayName("중복 수집 안정성: 동일 데이터 재조회 시 externalId 동일 (EXT dedupe 기반)")
  void fetch_duplicateStability() {
    List<String> first = adapter.fetch(CONN_REF, null, 5000).stream()
        .map(CollectionParsedRow::externalId).sorted().toList();
    List<String> second = adapter.fetch(CONN_REF, null, 5000).stream()
        .map(CollectionParsedRow::externalId).sorted().toList();
    assertThat(second).isEqualTo(first);
  }
}
