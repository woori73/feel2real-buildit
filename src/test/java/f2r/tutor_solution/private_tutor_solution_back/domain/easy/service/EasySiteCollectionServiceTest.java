package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasySiteCollectionCatalog.Dataset;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasySiteCollectionService.SiteRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasySiteCollectionService.SiteRowsRequest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/** 모든 값은 합성 자료다. */
class EasySiteCollectionServiceTest {

  private static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(1L, "admin", "ADMIN");
  private final EasyCollectionIngestService ingest = mock(EasyCollectionIngestService.class);
  private final EasyTutorAccessService access = mock(EasyTutorAccessService.class);
  private final EasyBusinessRepository businesses = mock(EasyBusinessRepository.class);
  private final EasySiteCollectionService service = new EasySiteCollectionService(ingest, access, businesses);

  private static SiteRow row(String date, String direction, long amount, String counterparty) {
    return new SiteRow(date, direction, amount, counterparty, "적요", null, null, null);
  }

  private static SiteRowsRequest bankRequest(List<SiteRow> rows, int count, long total, String key) {
    return new SiteRowsRequest(7L, "KB_BANK", "BANK_ACCOUNT", "국민-운영통장", "2026-10-01",
        "2026-10-31", count, total, key, rows);
  }

  @Test
  @DisplayName("given 관리자가 아닌 사용자 when 저장 then 거절하고 저장 경로를 부르지 않는다")
  void nonAdminIsRejected() {
    // given
    var tutor = new AuthenticatedPrincipal(2L, "tutor", "TUTOR");
    var request = bankRequest(List.of(row("2026-10-02", "IN", 1000, "학부모A")), 1, 1000, "key-00001");

    // when / then
    assertThatThrownBy(() -> service.save(tutor, request)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.catalog(tutor)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(ingest);
  }

  @Test
  @DisplayName("given 사이트 건수와 다른 행 수 when 저장 then 아무것도 저장하지 않는다")
  void countMismatchSavesNothing() {
    // given
    var request = bankRequest(List.of(row("2026-10-02", "IN", 1000, "학부모A")), 2, 1000, "key-00001");

    // when / then
    assertThatThrownBy(() -> service.save(ADMIN, request))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("건수");
    verifyNoInteractions(ingest);
  }

  @Test
  @DisplayName("given 합계가 다른 행 when 저장 then 아무것도 저장하지 않는다")
  void totalMismatchSavesNothing() {
    // given
    var rows = List.of(row("2026-10-02", "IN", 1000, "학부모A"), row("2026-10-03", "OUT", 500, "문구점"));
    var request = bankRequest(rows, 2, 500, "key-00001");

    // when / then
    assertThatThrownBy(() -> service.save(ADMIN, request))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("합계");
    verifyNoInteractions(ingest);
  }

  @Test
  @DisplayName("given 조회 기간 밖 날짜 when 저장 then 거절한다")
  void rowOutsidePeriodIsRejected() {
    // given
    var request = bankRequest(List.of(row("2026-11-01", "IN", 1000, "학부모A")), 1, 1000, "key-00001");

    // when / then
    assertThatThrownBy(() -> service.save(ADMIN, request)).hasMessageContaining("기간 밖");
    verifyNoInteractions(ingest);
  }

  @Test
  @DisplayName("given 계좌 전체 번호가 들어간 accountRef when 저장 then 거절한다")
  void fullAccountNumberRefIsRejected() {
    // given
    var request = new SiteRowsRequest(7L, "KB_BANK", "BANK_ACCOUNT", "123-456789-01", "2026-10-01",
        "2026-10-31", 1, 1000L, "key-00001", List.of(row("2026-10-02", "IN", 1000, "학부모A")));

    // when / then
    assertThatThrownBy(() -> service.save(ADMIN, request)).hasMessageContaining("전체 번호");
    verifyNoInteractions(ingest);
  }

  @Test
  @DisplayName("given 홈택스에서 통장 자료 when 저장 then 그 사이트가 다루지 않는 자료라 거절한다")
  void datasetNotOfferedBySiteIsRejected() {
    // given
    var request = new SiteRowsRequest(7L, "HOMETAX", "BANK_ACCOUNT", "운영통장", "2026-10-01",
        "2026-10-31", 1, 1000L, "key-00001", List.of(row("2026-10-02", "IN", 1000, "학부모A")));

    // when / then
    assertThatThrownBy(() -> service.save(ADMIN, request)).hasMessageContaining("자료만");
    verifyNoInteractions(ingest);
  }

  @Test
  @DisplayName("given 올바른 통장 자료 when 저장 then 사이트 범위 라벨과 멱등 파일명으로 기존 수집 경로에 넘긴다")
  @SuppressWarnings("unchecked")
  void validRowsAreHandedToIngestPath() {
    // given
    var rows = List.of(row("2026-10-02", "IN", 300_000, "학부모A"),
        new SiteRow("2026-10-05", "IN", 980_000L, "KB국민카드", "가맹점정산", null, 5_000_000L, null));
    var request = bankRequest(rows, 2, 1_280_000, "key-00001");

    // when
    service.save(ADMIN, request);
    service.save(ADMIN, request);

    // then
    ArgumentCaptor<String> label = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> prefix = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<CollectionParsedRow>> parsed = ArgumentCaptor.forClass(List.class);
    verify(ingest, org.mockito.Mockito.times(2)).ingestSiteRows(eq(7L), label.capture(),
        prefix.capture(), name.capture(), parsed.capture());
    verify(access, org.mockito.Mockito.times(2)).requireAllowedBusiness(ADMIN, 7L);
    assertThat(label.getValue()).matches("MS_[0-9a-f]{24}");
    assertThat(name.getAllValues().get(0)).isEqualTo(name.getAllValues().get(1))
        .startsWith(prefix.getValue());
    var saved = parsed.getValue();
    assertThat(saved).hasSize(2);
    assertThat(saved.get(0).direction()).isEqualTo("INCOME");
    assertThat(saved.get(0).externalId()).isNull();
    assertThat(saved.get(1).externalId()).startsWith("MS:").endsWith("#1");
    assertThat(saved.get(1).rawJson()).contains("\"primary_disposition\":\"CARD_SETTLEMENT\"")
        .contains("\"provider\":\"MCP_SITE\"");
  }

  @Test
  @DisplayName("given 같은 키에 내용만 다른 요청 when 저장 then 파일명이 달라져 기존 경로가 충돌을 판정할 수 있다")
  void differentContentSameKeyGivesSamePrefixDifferentName() {
    // given
    var first = bankRequest(List.of(row("2026-10-02", "IN", 1000, "학부모A")), 1, 1000, "key-00001");
    var second = bankRequest(List.of(row("2026-10-02", "IN", 2000, "학부모A")), 1, 2000, "key-00001");

    // when
    service.save(ADMIN, first);
    service.save(ADMIN, second);

    // then
    ArgumentCaptor<String> prefix = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
    verify(ingest, org.mockito.Mockito.times(2)).ingestSiteRows(eq(7L), anyString(),
        prefix.capture(), name.capture(), any());
    assertThat(prefix.getAllValues().get(0)).isEqualTo(prefix.getAllValues().get(1));
    assertThat(name.getAllValues().get(0)).isNotEqualTo(name.getAllValues().get(1));
  }

  @Test
  @DisplayName("given 적요에 긴 계좌번호 when 행 변환 then 끝 4자리만 남긴다")
  void longDigitsInMemoAreMasked() {
    // given
    var site = EasySiteCollectionCatalog.find("KB_BANK").orElseThrow();
    var input = new SiteRow("2026-10-02", "OUT", 5000L, "홍길동", "이체 110234567890", null, null, null);

    // when
    var parsed = EasySiteCollectionService.toParsedRow(1, input, site, Dataset.BANK_ACCOUNT, "운영",
        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new HashMap<>());

    // then
    assertThat(parsed.description()).isEqualTo("이체 ********7890");
    assertThat(parsed.rawJson()).doesNotContain("110234567890");
  }

  @Test
  @DisplayName("given 승인번호·원승인일이 있는 카드 취소 when 행 변환 then 원거래 외부 ID와 연결된 역분개 행이 된다")
  void cardCancellationLinksToOriginal() {
    // given
    var site = EasySiteCollectionCatalog.find("SHINHAN_CARD").orElseThrow();
    Map<String, Integer> occurrence = new HashMap<>();
    var use = new SiteRow("2026-10-02", "OUT", 50_000L, "교보문고", null, "12345678", null, null);
    var cancel = new SiteRow("2026-10-04", "IN", 20_000L, "교보문고", null, "12345678", null, "2026-10-02");

    // when
    var original = EasySiteCollectionService.toParsedRow(1, use, site, Dataset.CARD_PURCHASE, "법인카드",
        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), occurrence);
    var reversal = EasySiteCollectionService.toParsedRow(2, cancel, site, Dataset.CARD_PURCHASE, "법인카드",
        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), occurrence);

    // then
    assertThat(original.direction()).isEqualTo("EXPENSE");
    assertThat(original.evidenceType()).isEqualTo("CREDIT_CARD");
    assertThat(reversal.direction()).isEqualTo("EXPENSE");
    assertThat(reversal.rawJson()).contains("\"original_external_id\":\"" + original.externalId() + "\"");
    assertThat(reversal.externalId()).isNotEqualTo(original.externalId());
  }

  @Test
  @DisplayName("given 원승인일 없는 카드 취소 when 행 변환 then 연결 없이 검토 메모를 단다")
  void unlinkedCardCancellationGetsReviewNote() {
    // given
    var site = EasySiteCollectionCatalog.find("SHINHAN_CARD").orElseThrow();
    var cancel = new SiteRow("2026-10-04", "IN", 20_000L, "교보문고", null, null, null, null);

    // when
    var parsed = EasySiteCollectionService.toParsedRow(1, cancel, site, Dataset.CARD_PURCHASE, "법인카드",
        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new HashMap<>());

    // then
    assertThat(parsed.direction()).isEqualTo("INCOME");
    assertThat(parsed.rawJson()).contains("review_note").doesNotContain("original_external_id");
  }

  @Test
  @DisplayName("given 결제선생 수납 완료·취소 when 행 변환 then 수입 행과 원거래에 연결된 역분개 행이 된다")
  void tuitionPaymentRowsBecomeIncomeAndLinkedCancellation() {
    // given
    var site = EasySiteCollectionCatalog.find("PAYSSAM").orElseThrow();
    Map<String, Integer> occurrence = new HashMap<>();
    var paid = new SiteRow("2026-10-05", "IN", 300_000L, "합성학생", "10월 수강료", "P0001", null, null);
    var cancel = new SiteRow("2026-10-09", "OUT", 300_000L, "합성학생", "10월 수강료 취소", "P0001", null, "2026-10-05");

    // when
    var income = EasySiteCollectionService.toParsedRow(1, paid, site, Dataset.TUITION_PAYMENT, "결제선생",
        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), occurrence);
    var reversal = EasySiteCollectionService.toParsedRow(2, cancel, site, Dataset.TUITION_PAYMENT, "결제선생",
        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), occurrence);

    // then
    assertThat(income.direction()).isEqualTo("INCOME");
    assertThat(income.payMethod()).isEqualTo("결제선생");
    assertThat(income.evidenceType()).isNull();
    assertThat(reversal.direction()).isEqualTo("INCOME");
    assertThat(reversal.rawJson()).contains("\"original_external_id\":\"" + income.externalId() + "\"");
  }

  @Test
  @DisplayName("given 결제선생에서 통장 자료 when 저장 then 다루지 않는 자료라 거절한다")
  void payssamRejectsBankDataset() {
    // given
    var request = new SiteRowsRequest(7L, "PAYSSAM", "BANK_ACCOUNT", "결제선생", "2026-10-01",
        "2026-10-31", 1, 1000L, "key-00001", List.of(row("2026-10-02", "IN", 1000, "합성학생")));

    // when / then
    assertThatThrownBy(() -> service.save(ADMIN, request)).hasMessageContaining("자료만");
    verifyNoInteractions(ingest);
  }

  @Test
  @DisplayName("given 건수·합계 누락·0원 when verifyTotals then 거절한다")
  void verifyTotalsRequiresBothAndPositiveAmounts() {
    // given
    var rows = List.of(row("2026-10-02", "IN", 1000, "A"));

    // when / then
    assertThatThrownBy(() -> EasySiteCollectionService.verifyTotals(rows, null, 1000L))
        .hasMessageContaining("expectedCount");
    assertThatThrownBy(() -> EasySiteCollectionService.verifyTotals(
        List.of(new SiteRow("2026-10-02", "IN", 0L, "A", null, null, null, null)), 1, 0L))
        .hasMessageContaining("0보다 큰");
    EasySiteCollectionService.verifyTotals(rows, 1, 1000L);
  }

  @Test
  @DisplayName("given 활성 사업체 when catalog then 기관 목록·순서·규칙과 사업체를 함께 준다")
  @SuppressWarnings("unchecked")
  void catalogListsSitesAndBusinesses() {
    // given
    var business = new EasyBusiness("ACADEMY", "테스트학원", true, 1);
    ReflectionTestUtils.setField(business, "businessId", 7L);
    when(businesses.findAllByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(business));

    // when
    var out = service.catalog(ADMIN);

    // then
    assertThat((List<Map<String, Object>>) out.get("sites")).hasSize(EasySiteCollectionCatalog.sites().size());
    assertThat((Map<String, Object>) out.get("steps")).containsKeys("BANK", "CARD", "HOMETAX", "CREFIA");
    assertThat((List<Map<String, Object>>) out.get("businesses"))
        .containsExactly(Map.of("businessId", 7L, "name", "테스트학원"));
    assertThat(out.get("saveTool")).isEqualTo("save_easy_site_rows");
  }
}
