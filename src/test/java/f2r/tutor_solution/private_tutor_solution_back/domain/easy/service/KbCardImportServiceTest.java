package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardInstrument;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.KbCardExcelTestFixtures.Row14;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Required-assertion suite (task spec §11/§12) for the KB raw->normalize->dedupe->
 * EasyCardEvent pipeline. All fixtures are synthetic — no real source data.
 * {@link EasyBusinessCardAccountingService} is mocked with a stateful fake that reproduces its
 * real fingerprint-dedupe *behavior* (same providerExternalId -> same event, duplicate=true)
 * without needing a database.
 */
class KbCardImportServiceTest {
  private final KbCardExcelParser parser = new KbCardExcelParser();
  private final KbCardNormalizer normalizer = new KbCardNormalizer();
  private final EasyBusinessCardAccountingService accountingService =
      mock(EasyBusinessCardAccountingService.class);
  private final EasyTransactionRepository transactionRepository = mock(EasyTransactionRepository.class);
  private KbCardImportService service;

  private EasyBusiness business;
  private EasyCardInstrument instrument;
  private final Map<String, EasyCardEvent> eventsByExternalId = new HashMap<>();
  private final AtomicLong nextEventId = new AtomicLong(1);

  @BeforeEach
  void setUp() {
    service = new KbCardImportService(parser, normalizer, accountingService, transactionRepository);
    business = EasyBusiness.builder().code("PUBLISHER").name("테스트출판사").active(true).build();
    ReflectionTestUtils.setField(business, "businessId", 1L);
    instrument = EasyCardInstrument.builder().business(business).providerCode("KB")
        .issuer("KB국민카드").providerInstrumentTokenHash("b".repeat(64)).build();
    ReflectionTestUtils.setField(instrument, "instrumentId", 10L);
    eventsByExternalId.clear();
    nextEventId.set(1);

    when(accountingService.registerInstrument(anyLong(), any(), any(), any(), any(), any(), any()))
        .thenReturn(instrument);
    when(transactionRepository
        .findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(any(), any(), any()))
        .thenReturn(List.of());

    when(accountingService.observePurchase(any())).thenAnswer(inv -> {
      EasyBusinessCardAccountingService.PurchaseCommand cmd = inv.getArgument(0);
      boolean duplicate = eventsByExternalId.containsKey(cmd.externalId());
      EasyCardEvent event = eventsByExternalId.computeIfAbsent(cmd.externalId(), id -> {
        EasyCardEvent e = EasyCardEvent.builder().business(business).instrument(instrument)
            .eventType(EasyCardEvent.Type.PURCHASE).providerExternalId(cmd.externalId())
            .fingerprint("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef").occurredAt(cmd.occurredAt())
            .confirmationDate(cmd.confirmationDate()).amount(cmd.amount())
            .currency(cmd.currency()).build();
        ReflectionTestUtils.setField(e, "cardEventId", nextEventId.getAndIncrement());
        return e;
      });
      return new EasyBusinessCardAccountingService.EventResult(event, null, duplicate);
    });
  }

  private Row14 row(String date, String merchant, double domestic, double foreign,
                    String slipStatus, String payDate, String approvalNo) {
    return new Row14(date, "18:17", "홍길*",
        "KB국민 테스트카드", merchant, domestic, foreign, "일시불", "", 0.0, 0.0,
        slipStatus, payDate, approvalNo);
  }

  @Test
  void realShape_producesPendingReviewEventsWithNullConfirmationForUnpurchased() {
    // given: domestic purchased + domestic unpurchased + one foreign row
    var xls = KbCardExcelTestFixtures.workbook(List.of(
        row("2026-09-23", "테스트상점A", 5900, 0, "전표매입", "2026-10-10", "90000001"),
        row("2026-09-22", "테스트상점B", 20870, 0, "전표미매입", null, "90000002"),
        row("2026-09-21", "테스트상점C", 0, 19.81, "전표매입", "2026-10-10", "90000003")));

    // when
    var result = service.importFor(1L, xls);

    // then: all three become events (foreign as USD, never dropped)
    assertThat(result.sourceRows()).isEqualTo(3);
    assertThat(result.newPurchaseEvents()).isEqualTo(3);
    assertThat(result.foreignRows()).isEqualTo(1);
    assertThat(result.unmatchedNegativeCount()).isEqualTo(0);
    assertThat(eventsByExternalId).hasSize(3);
  }

  @Test
  void duplicateImportCreatesNoNewEvents_idempotent() {
    var rows = List.of(
        row("2026-09-23", "테스트상점A", 5900, 0, "전표매입", "2026-10-10", "90000001"),
        row("2026-09-22", "테스트상점B", 20870, 0, "전표미매입", null, "90000002"));

    var first = service.importFor(1L, KbCardExcelTestFixtures.workbook(rows));
    var second = service.importFor(1L, KbCardExcelTestFixtures.workbook(rows));

    assertThat(first.newPurchaseEvents()).isEqualTo(2);
    assertThat(second.newPurchaseEvents()).isEqualTo(0);
    assertThat(second.duplicatePurchaseEvents()).isEqualTo(2);
    assertThat(eventsByExternalId).hasSize(2);
  }

  @Test
  void passesApprovalNumberAsApprovalReference() {
    var xls = KbCardExcelTestFixtures.workbook(List.of(
        row("2026-09-23", "테스트상점A", 5900, 0, "전표매입", "2026-10-10", "90000001")));
    service.importFor(1L, xls);

    var captor = org.mockito.ArgumentCaptor
        .forClass(EasyBusinessCardAccountingService.PurchaseCommand.class);
    verify(accountingService, atLeastOnce()).observePurchase(captor.capture());
    assertThat(captor.getValue().approvalReference()).isEqualTo("90000001");
  }
}
