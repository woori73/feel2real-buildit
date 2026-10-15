package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportRowResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyShopImportRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyShopImportRequest.EasyShopRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRowStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportRowRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("EasyShopImportService 실데이터 importer 테스트")
class EasyShopImportServiceTest {

  @Mock EasyBusinessRepository businessRepository;
  @Mock EasyTransactionRepository txnRepository;
  @Mock EasyImportRowRepository rowRepository;
  @Mock EasyImportService importService;

  private EasyShopImportService service;
  private EasyBusiness academy;
  private EasyBusiness publishing;

  @BeforeEach
  void setUp() {
    service = new EasyShopImportService(
        businessRepository, txnRepository, rowRepository, importService);
    academy = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).sortOrder(1).build();
    publishing = EasyBusiness.builder().code("PUBLISHING").name("출판사").active(true)
        .sortOrder(2).build();
    ReflectionTestUtils.setField(academy, "businessId", 1L);
    ReflectionTestUtils.setField(publishing, "businessId", 2L);
  }

  private EasyShopRow sales(String date, String total, String businessCode) {
    return new EasyShopRow("sales", date, "현대카드", null, null, null, null, null, null,
        null, "카드(1)", null, null, null, total, "0", "신용(체크)카드", null,
        null, null, null, businessCode, null, null, null);
  }

  private EasyShopRow expense(String date, String total, String category, String businessCode) {
    return new EasyShopRow("expense", date, "거래처", null, null, null, null, null, null,
        category, "품목", "9090", "910", "0", total, null, null, "카드",
        null, null, null, businessCode, null, null, null);
  }

  private EasyShopRow funds(String date, String deposit, String withdrawal, String memo) {
    return new EasyShopRow("deposit_withdrawal", date, null, "신한은행 110498250866", memo,
        "계좌에서 현금으로 출금", deposit, withdrawal, "0", null, null, null, null, null,
        null, null, null, null, null, null, null, "ACADEMY", null, null, null);
  }

  private EasyImportBatchResponse batchResponse(Long batchId, Long businessId,
      List<EasyImportRowResponse> rows) {
    return new EasyImportBatchResponse(batchId, businessId, "사업체", "easyshop.csv",
        "EASYSHOP", "STAGED", rows.size(), 0, rows);
  }

  @Test
  @DisplayName("사업체 코드 혼합 파일은 사업장별 배치로 분리 staging")
  void stage_splitsByBusiness() {
    when(businessRepository.findAll()).thenReturn(List.of(academy, publishing));
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(businessRepository.findById(2L)).thenReturn(Optional.of(publishing));
    when(importService.stage(any(EasyImportBatchRequest.class)))
        .thenAnswer(invocation -> {
          EasyImportBatchRequest req = invocation.getArgument(0);
          return batchResponse(100L + req.businessId(), req.businessId(), List.of());
        });
    when(importService.getBatch(any())).thenAnswer(invocation ->
        batchResponse(invocation.getArgument(0), 1L, List.of()));

    List<EasyImportBatchResponse> staged = service.stage(new EasyShopImportRequest(null,
        "easyshop-2024.csv", List.of(
            sales("2024-03-05", "540,000", "ACADEMY"),
            expense("2024-03-06", "10,000", "잡화/소모품", "PUBLISHING"))));

    assertThat(staged).hasSize(2);
    ArgumentCaptor<EasyImportBatchRequest> captor =
        ArgumentCaptor.forClass(EasyImportBatchRequest.class);
    verify(importService, org.mockito.Mockito.times(2)).stage(captor.capture());
    assertThat(captor.getAllValues()).extracting(EasyImportBatchRequest::sourceKind)
        .containsOnly("EASYSHOP");
  }

  @Test
  @DisplayName("사업체 미확정 행이 있으면 staging 없이 전체 거부")
  void stage_homeless_rejectedWithoutStaging() {
    when(businessRepository.findAll()).thenReturn(List.of(academy, publishing));

    assertThatThrownBy(() -> service.stage(new EasyShopImportRequest(null,
        "easyshop.csv", List.of(sales("2025-01-05", "100000", "UNKNOWN")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("사업체");
    verify(importService, never()).stage(any());
  }

  @Test
  @DisplayName("음수 비용(환불)은 역분개로 보존 — 누락·절댓값 변환 없음")
  void stage_negativeExpense_reversal() {
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(importService.stage(any(EasyImportBatchRequest.class)))
        .thenAnswer(invocation -> {
          EasyImportBatchRequest req = invocation.getArgument(0);
          return batchResponse(9L, req.businessId(), List.of());
        });
    when(importService.getBatch(9L)).thenReturn(batchResponse(9L, 1L, List.of()));

    service.stage(new EasyShopImportRequest(1L, "easyshop.csv",
        List.of(expense("2026-07-06", "-330,000", "전화/소모품", null))));

    ArgumentCaptor<EasyImportBatchRequest> captor =
        ArgumentCaptor.forClass(EasyImportBatchRequest.class);
    verify(importService).stage(captor.capture());
    var input = captor.getValue().rows().get(0);
    assertThat(input.direction()).isEqualTo("INCOME");
    assertThat(input.amount()).isEqualTo(330000L);
    assertThat(input.reversal()).isTrue();
    assertThat(input.supplyAmount()).isEqualTo(9090L);
    assertThat(input.vatAmount()).isEqualTo(910L);
  }

  @Test
  @DisplayName("입출금은 자금 배치로 분리 — P&L 출처와 다름")
  void stage_funds_separateKind() {
    when(businessRepository.findAll()).thenReturn(List.of(academy, publishing));
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(importService.stage(any(EasyImportBatchRequest.class)))
        .thenAnswer(invocation -> {
          EasyImportBatchRequest req = invocation.getArgument(0);
          return batchResponse(9L, req.businessId(), List.of());
        });
    when(importService.getBatch(9L)).thenReturn(batchResponse(9L, 1L, List.of()));

    service.stage(new EasyShopImportRequest(1L, "easyshop.csv", List.of(
        funds("2026-06-13", "0", "967,000", "백승민"),
        sales("2026-06-14", "540,000", null))));

    ArgumentCaptor<EasyImportBatchRequest> captor =
        ArgumentCaptor.forClass(EasyImportBatchRequest.class);
    verify(importService, org.mockito.Mockito.times(2)).stage(captor.capture());
    assertThat(captor.getAllValues()).extracting(EasyImportBatchRequest::sourceKind)
        .containsExactlyInAnyOrder("EASYSHOP", "EASYSHOP_FUNDS");
  }

  @Test
  @DisplayName("장부에 같은 fingerprint가 있으면 DUPLICATE로 확정 제외")
  void stage_ledgerDuplicate_marked() {
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    EasyImportRow staged = EasyImportRow.builder()
        .batch(null).rowNo(1).occurredOn(LocalDate.of(2025, 3, 5))
        .direction(EasyTxnDirection.INCOME).amount(300000L)
        .counterparty("현대카드").description("카드(1)").payMethod("신용(체크)카드")
        .supplyAmount(null).vatAmount(null).evidenceType(null).rawData(null)
        .externalAccount("EASYSHOP:매출").reversal(null).build();
    ReflectionTestUtils.setField(staged, "rowId", 5L);
    staged.markReady(null, 0.0);
    EasyImportBatchResponse response = batchResponse(9L, 1L,
        List.of(EasyImportRowResponse.from(staged)));
    when(importService.stage(any(EasyImportBatchRequest.class))).thenReturn(response);
    when(importService.getBatch(9L)).thenReturn(response);
    when(rowRepository.findById(5L)).thenReturn(Optional.of(staged));
    EasyTransaction ledgerTxn = EasyTransaction.builder()
        .business(academy).occurredOn(LocalDate.of(2025, 3, 5))
        .direction(EasyTxnDirection.INCOME).amount(300000L)
        .counterparty("현대카드").description("카드(1)").payMethod("신용(체크)카드")
        .fingerprint(EasyFingerprint.of(1L, LocalDate.of(2025, 3, 5), "INCOME", 300000L,
            "현대카드", "카드(1)", "신용(체크)카드"))
        .build();
    when(txnRepository.findByBusiness_BusinessIdAndFingerprintIsNotNull(1L))
        .thenReturn(List.of(ledgerTxn));

    service.stage(new EasyShopImportRequest(1L, "easyshop.csv",
        List.of(sales("2025-03-05", "300000", null))));

    assertThat(staged.getStatus()).isEqualTo(EasyImportRowStatus.DUPLICATE);
  }
}
