package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardInstrument;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Required-assertion suite (task spec §11/§12) for the Shinhan raw->normalize->dedupe->
 * EasyCardEvent pipeline. {@link EasyBusinessCardAccountingService} is mocked with a stateful
 * fake that reproduces its real fingerprint-dedupe *behavior* (same providerExternalId -> same
 * event, duplicate=true) without needing a database — the real service's own unit tests already
 * cover that its fingerprint logic is correct; this suite is about what ShinhanCardImportService
 * does with that contract.
 */
class ShinhanCardImportServiceTest {
  private final ShinhanCardExcelParser parser = new ShinhanCardExcelParser();
  private final ShinhanCardNormalizer normalizer = new ShinhanCardNormalizer();
  private final EasyBusinessCardAccountingService accountingService =
      mock(EasyBusinessCardAccountingService.class);
  private final EasyTransactionRepository transactionRepository = mock(EasyTransactionRepository.class);
  private ShinhanCardImportService service;

  private EasyBusiness business;
  private EasyCardInstrument instrument;
  private final Map<String, EasyCardEvent> eventsByExternalId = new HashMap<>();
  private final AtomicLong nextEventId = new AtomicLong(1);

  @BeforeEach
  void setUp() {
    service = new ShinhanCardImportService(parser, normalizer, accountingService, transactionRepository);
    business = EasyBusiness.builder().code("ACADEMY").name("압구정에듀홀릭학원").active(true).build();
    ReflectionTestUtils.setField(business, "businessId", 1L);
    instrument = EasyCardInstrument.builder().business(business).providerCode("SHINHAN")
        .issuer("신한카드").providerInstrumentTokenHash("a".repeat(64)).build();
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
            .fingerprint(fingerprintFor(cmd.externalId())).occurredAt(cmd.occurredAt())
            .confirmationDate(cmd.confirmationDate()).amount(cmd.amount()).currency("KRW").build();
        ReflectionTestUtils.setField(e, "cardEventId", nextEventId.getAndIncrement());
        return e;
      });
      return new EasyBusinessCardAccountingService.EventResult(event, null, duplicate);
    });

    when(accountingService.observeCancellation(any(), any(), any(), any(), any(), anyLong()))
        .thenAnswer(inv -> {
          String externalId = inv.getArgument(2);
          Long originalEventId = inv.getArgument(0);
          long amount = inv.getArgument(5);
          boolean duplicate = eventsByExternalId.containsKey(externalId);
          EasyCardEvent original = eventsByExternalId.values().stream()
              .filter(e -> e.getCardEventId().equals(originalEventId)).findFirst().orElseThrow();
          EasyCardEvent event = eventsByExternalId.computeIfAbsent(externalId, id -> {
            EasyCardEvent e = EasyCardEvent.builder().business(business).instrument(instrument)
                .originalEvent(original).eventType(EasyCardEvent.Type.CANCELLATION)
                .providerExternalId(externalId).fingerprint(fingerprintFor(externalId))
                .occurredAt(inv.getArgument(3)).confirmationDate(inv.getArgument(4))
                .amount(amount).currency("KRW").build();
            ReflectionTestUtils.setField(e, "cardEventId", nextEventId.getAndIncrement());
            return e;
          });
          return new EasyBusinessCardAccountingService.EventResult(event, null, duplicate);
        });
  }

  private String fingerprintFor(String seed) {
    return String.format("%064x", seed.hashCode() & 0xffffffffL);
  }

  private InputStream normalPositiveWorkbook() {
    return ShinhanCardExcelTestFixtures.workbook(List.of(new ShinhanCardExcelTestFixtures.Row11(
        "2026.09.14", "2026.09.15", "본인", "본인111*", "일시불",
        "가맹점A", 15000, 13637, 1363, 0, "111-11-11111")));
  }

  @Test
  void normalPositiveTransactionBecomesPendingReviewPurchaseEvent() {
    var result = service.importFor(1L, normalPositiveWorkbook());
    assertThat(result.newPurchaseEvents()).isEqualTo(1);
    assertThat(result.duplicatePurchaseEvents()).isEqualTo(0);
    EasyCardEvent event = eventsByExternalId.values().iterator().next();
    assertThat(event.getReviewStatus()).isEqualTo(EasyCardEvent.ReviewStatus.PENDING_REVIEW);
    assertThat(event.getPostedTxn()).isNull(); // accounting transaction created before approval -> 0
  }

  @Test
  void matchedPositiveNegativePairBecomesCancellationReferencingOriginal() {
    var rows = List.of(
        new ShinhanCardExcelTestFixtures.Row11("2026.09.19", "2026.09.20", "본인", "본인111*",
            "일시불", "쿠팡", 34500, 34500, 0, 0, "105-87-65162"),
        new ShinhanCardExcelTestFixtures.Row11("2026.09.19", "2026.09.22", "본인", "본인111*",
            "일시불", "쿠팡", -34500, -34500, 0, 0, "105-87-65162"));
    var result = service.importFor(1L, ShinhanCardExcelTestFixtures.workbook(rows));

    assertThat(result.matchedCancellationPairs()).isEqualTo(1);
    assertThat(result.unmatchedNegativeCount()).isEqualTo(0);
    assertThat(result.newPurchaseEvents()).isEqualTo(1);
    assertThat(result.newCancellationEvents()).isEqualTo(1);
    EasyCardEvent cancellation = eventsByExternalId.values().stream()
        .filter(e -> e.getEventType() == EasyCardEvent.Type.CANCELLATION).findFirst().orElseThrow();
    assertThat(cancellation.getOriginalEvent()).isNotNull();
    assertThat(cancellation.getAmount()).isEqualTo(34500L); // positive magnitude, sign conveyed by type
  }

  @Test
  void unmatchedNegativeIsPreservedNotDroppedAndNotForcedIntoACardEvent() {
    // Real shape: 09/20 쿠팡 할부3개월 -34,500 has no matching positive 할부3개월/34,500 in-window.
    var rows = List.of(new ShinhanCardExcelTestFixtures.Row11(
        "2026.09.20", "2026.09.22", "본인", "본인111*", "할부3개월",
        "쿠팡", -34500, -34500, 0, 0, "105-87-65162"));
    var result = service.importFor(1L, ShinhanCardExcelTestFixtures.workbook(rows));

    assertThat(result.unmatchedNegativeCount()).isEqualTo(1);
    assertThat(result.unmatchedNegatives()).hasSize(1);
    assertThat(result.unmatchedNegatives().get(0).amount()).isEqualTo(-34500L); // sign preserved
    assertThat(result.newCancellationEvents()).isEqualTo(0);
    verify(accountingService, never()).observeCancellation(any(), any(), any(), any(), any(), anyLong());
  }

  @Test
  void fifoMatchRespectsTransactionDateOrderNotExcelRowOrder() {
    // Real data has Excel rows sorted by confirmation date DESC.
    // 09/21 positive appears before 09/19 rows in Excel, but 09/19 negative
    // should match the 09/19 positive (same transaction date), not the 09/21 one.
    var rows = List.of(
        new ShinhanCardExcelTestFixtures.Row11("2026.09.21", "2026.09.22", "본인", "본인111*",
            "일시불", "쿠팡", 34500, 34500, 0, 0, "105-87-65162"), // appears first in Excel
        new ShinhanCardExcelTestFixtures.Row11("2026.09.19", "2026.09.21", "본인", "본인111*",
            "일시불", "쿠팡", 34500, 34500, 0, 0, "105-87-65162"), // same day positive
        new ShinhanCardExcelTestFixtures.Row11("2026.09.19", "2026.09.22", "본인", "본인111*",
            "일시불", "쿠팡", -34500, -34500, 0, 0, "105-87-65162"), // same day negative
        new ShinhanCardExcelTestFixtures.Row11("2026.09.20", "2026.09.22", "본인", "본인111*",
            "할부3개월", "쿠팡", -34500, -34500, 0, 0, "105-87-65162")); // unmatched negative
    var result = service.importFor(1L, ShinhanCardExcelTestFixtures.workbook(rows));

    assertThat(result.matchedCancellationPairs()).isEqualTo(1);
    assertThat(result.unmatchedNegativeCount()).isEqualTo(1);
    assertThat(result.newPurchaseEvents()).isEqualTo(2);
    assertThat(result.newCancellationEvents()).isEqualTo(1);

    // Verify the cancellation references the 09/19 positive, not the 09/21 one
    EasyCardEvent cancellation = eventsByExternalId.values().stream()
        .filter(e -> e.getEventType() == EasyCardEvent.Type.CANCELLATION).findFirst().orElseThrow();
    assertThat(cancellation.getOriginalEvent()).isNotNull();
    assertThat(cancellation.getOriginalEvent().getOccurredAt().toLocalDate())
        .isEqualTo(LocalDate.of(2026, 9, 19)); // matched to 09/19, not 09/21
  }

  @Test
  void sameDateDifferentMerchantsAreBothCreatedNotTreatedAsDuplicates() {
    var rows = List.of(
        new ShinhanCardExcelTestFixtures.Row11("2026.09.17", "2026.09.18", "본인", "본인111*",
            "일시불", "가맹점A", 10000, 9091, 909, 0, "111-11-11111"),
        new ShinhanCardExcelTestFixtures.Row11("2026.09.17", "2026.09.18", "본인", "본인111*",
            "일시불", "가맹점B", 20000, 18182, 1818, 0, "222-22-22222"));
    var result = service.importFor(1L, ShinhanCardExcelTestFixtures.workbook(rows));

    assertThat(result.newPurchaseEvents()).isEqualTo(2);
    assertThat(result.duplicatePurchaseEvents()).isEqualTo(0);
  }

  @Test
  void transactionDateAndConfirmationDateArePreservedSeparatelyOnTheEvent() {
    var result = service.importFor(1L, normalPositiveWorkbook());
    assertThat(result.newPurchaseEvents()).isEqualTo(1);
    EasyCardEvent event = eventsByExternalId.values().iterator().next();
    assertThat(event.getOccurredAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 14));
    assertThat(event.getConfirmationDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    assertThat(event.getOccurredAt().toLocalDate()).isNotEqualTo(event.getConfirmationDate());
  }

  @Test
  void businessAssignmentComesFromCallerConfigurationNotRowContent() {
    var result = service.importFor(1L, normalPositiveWorkbook());
    assertThat(result.newPurchaseEvents()).isEqualTo(1);
    verify(accountingService).observePurchase(argThat(cmd -> cmd.businessId().equals(1L)));
  }

  @Test
  void reimportingTheSameSourceCreatesZeroNewEvents() {
    InputStream first = normalPositiveWorkbook();
    var firstRun = service.importFor(1L, first);
    assertThat(firstRun.newPurchaseEvents()).isEqualTo(1);

    InputStream second = normalPositiveWorkbook(); // same content, fresh stream
    var secondRun = service.importFor(1L, second);
    assertThat(secondRun.newPurchaseEvents()).isEqualTo(0);
    assertThat(secondRun.duplicatePurchaseEvents()).isEqualTo(1);
  }

  @Test
  void boundaryDuplicateOfExistingEasyShopTransactionIsSkippedNotReimported() {
    EasyTransaction existing = EasyTransaction.builder().business(business)
        .occurredOn(LocalDate.of(2026, 9, 12)).direction(EasyTxnDirection.EXPENSE)
        .amount(15600L).category("접대비").counterparty("더빈마켓 송파점").description("")
        .payMethod("카드불").sourceKind("EASYSHOP").build();
    when(transactionRepository.findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        eq(1L), eq(LocalDate.of(2026, 9, 12)), eq(LocalDate.of(2026, 9, 12))))
        .thenReturn(List.of(existing));

    var rows = List.of(new ShinhanCardExcelTestFixtures.Row11(
        "2026.09.12", "2026.09.13", "본인", "본인111*", "일시불",
        "더빈마켓 송파점", 15600, 14182, 1418, 0, "693-31-01732"));
    var result = service.importFor(1L, ShinhanCardExcelTestFixtures.workbook(rows));

    assertThat(result.boundaryDuplicateSkipped()).isEqualTo(1);
    assertThat(result.newPurchaseEvents()).isEqualTo(0);
    verify(accountingService, never()).observePurchase(any());
  }

  @Test
  void nonBoundaryDayIsNotAffectedByBoundaryCheck() {
    var rows = List.of(new ShinhanCardExcelTestFixtures.Row11(
        "2026.09.14", "2026.09.15", "본인", "본인111*", "일시불",
        "김밥천국", 15000, 13637, 1363, 0, "211-06-64232"));
    var result = service.importFor(1L, ShinhanCardExcelTestFixtures.workbook(rows));
    assertThat(result.boundaryDuplicateSkipped()).isEqualTo(0);
    assertThat(result.newPurchaseEvents()).isEqualTo(1);
  }
}
