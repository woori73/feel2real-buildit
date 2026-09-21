package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cardsales 어댑터 단위 검증 — PHASE 2 준비②.
 * <ul>
 *   <li>어댑터 SPI 계약 준수</li>
 *   <li>합성 픽스처(approval/purchase/deposit) 생성 검증</li>
 *   <li>connection_ref 전달/보존</li>
 *   <li>external_id 기반 1차 dedupe(EXT 키)</li>
 *   <li>provenance(rawJson) 보존</li>
 *   <li>recordType 필드로 approval/purchase/deposit 구분</li>
 *   <li>since 파라미터로 증분 수집 가능</li>
 *   <li>limit 하드 캡 검증</li>
 * </ul>
 */
class EasyCardsalesAdapterTest {

  private static final String CONN_REF = "cardsales-test-connected-account-001";

  private final EasyCardsalesAdapter adapter = new EasyCardsalesAdapter();

  @Test
  @DisplayName("어댑터 SPI 계약: adapterCode/adapterVersion/configKeys 반환")
  void adapterContract() {
    assertThat(adapter.adapterCode()).isEqualTo("CARDSales");
    assertThat(adapter.adapterVersion()).isEqualTo("v1-skeleton");
    assertThat(adapter.configKeys()).containsExactly("clientId", "merchantId", "connectedAccountId", "apiEndpointBaseUrl");
  }

  @Test
  @DisplayName("validateConnection: 유효한 connectionRef 통과")
  void validateConnection_valid() {
    adapter.validateConnection(CONN_REF);
  }

  @Test
  @DisplayName("validateConnection: 빈 connectionRef 거부")
  void validateConnection_empty() {
    assertThatThrownBy(() -> adapter.validateConnection(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("비어 있습니다");
  }

  @Test
  @DisplayName("validateConnection: 잘못된 형식 거부")
  void validateConnection_invalidFormat() {
    assertThatThrownBy(() -> adapter.validateConnection("invalid-format"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("알 수 없는 connectionRef");
  }

  @Test
  @DisplayName("fetch: 승인(approval) 2건 + 매입(purchase) 2건 + 입금(deposit) 2건 = 총 6건 생성")
  void fetch_returnsSixFixtures() {
    List<CollectionParsedRow> rows = adapter.fetch(CONN_REF, null, 5000);
    assertThat(rows).hasSize(6);
  }

  @Test
  @DisplayName("fetch: recordType 필드로 approval/purchase/deposit 구분 가능")
  void fetch_recordTypeClassified() {
    List<CollectionParsedRow> rows = adapter.fetch(CONN_REF, null, 5000);
    long approvals = rows.stream().filter(r -> "approval".equals(r.originalRow().get("recordType"))).count();
    long purchases = rows.stream().filter(r -> "purchase".equals(r.originalRow().get("recordType"))).count();
    long deposits = rows.stream().filter(r -> "deposit".equals(r.originalRow().get("recordType"))).count();
    assertThat(approvals).isEqualTo(2);
    assertThat(purchases).isEqualTo(2);
    assertThat(deposits).isEqualTo(2);
  }

  @Test
  @DisplayName("fetch: 각 레코드에 provider 고유 externalId 존재 → 1차 dedupe(EXT 키) 검증 가능")
  void fetch_externalIdPresent() {
    List<CollectionParsedRow> rows = adapter.fetch(CONN_REF, null, 5000);
    assertThat(rows).allSatisfy(r -> {
      assertThat(r.externalId()).isNotNull();
      assertThat(r.externalId()).isNotBlank();
      assertThat(r.originalRow().get("externalId")).isEqualTo(r.externalId());
    });
  }

  @Test
  @DisplayName("fetch: rawJson에 원본 전체 보존(provenance)")
  void fetch_rawJsonPreservesProvenance() {
    List<CollectionParsedRow> rows = adapter.fetch(CONN_REF, null, 5000);
    assertThat(rows).allSatisfy(r -> {
      assertThat(r.rawJson()).isNotNull();
      assertThat(r.rawJson()).contains(r.externalId());
      assertThat(r.rawJson()).contains(r.originalRow().get("recordType"));
      assertThat(r.rawJson()).contains(CONN_REF);
    });
  }

  @Test
  @DisplayName("fetch: approval/purchase는 EXPENSE 방향, deposit은 INCOME 방향")
  void fetch_directionCorrect() {
    List<CollectionParsedRow> rows = adapter.fetch(CONN_REF, null, 5000);
    rows.stream().filter(r -> "approval".equals(r.originalRow().get("recordType")))
        .forEach(r -> assertThat(r.direction()).isEqualTo("EXPENSE"));
    rows.stream().filter(r -> "purchase".equals(r.originalRow().get("recordType")))
        .forEach(r -> assertThat(r.direction()).isEqualTo("EXPENSE"));
    rows.stream().filter(r -> "deposit".equals(r.originalRow().get("recordType")))
        .forEach(r -> assertThat(r.direction()).isEqualTo("INCOME"));
  }

  @Test
  @DisplayName("fetch: since 파라미터로 증분 수집 가능")
  void fetch_sinceIncremental() {
    List<CollectionParsedRow> all = adapter.fetch(CONN_REF, null, 5000);
    LocalDateTime since = LocalDateTime.of(2026, 9, 3, 0, 0);
    List<CollectionParsedRow> incremental = adapter.fetch(CONN_REF, since, 5000);
    assertThat(incremental.size()).isLessThan(all.size());
    assertThat(incremental).allSatisfy(r -> 
        assertThat(r.occurredOn().atStartOfDay()).isAfter(since));
  }

  @Test
  @DisplayName("fetch: limit 하드 캡(5000) 초과 시 예외")
  void fetch_limitHardCap() {
    assertThatThrownBy(() -> adapter.fetch(CONN_REF, null, 5001))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1~5000");
  }

  @Test
  @DisplayName("fetch: connectionRef가 rawJson에 포함되어 provenance 체인 유지")
  void fetch_connectionRefInRawJson() {
    List<CollectionParsedRow> rows = adapter.fetch(CONN_REF, null, 5000);
    assertThat(rows).allSatisfy(r -> 
        assertThat(r.rawJson()).contains(CONN_REF));
  }
}