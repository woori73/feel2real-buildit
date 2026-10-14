package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardInstrument;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.KbCardExcelTestFixtures.Row14;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.KbCollectorIngestService.CollectedFile;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * PoC matrix §10 (items 6-10) at service level with a synthetic 50-row KB workbook
 * (49 domestic + 1 foreign — the real file's shape, entirely fake values):
 * integrity gate, ingest to PENDING_REVIEW, and second-run dedupe (0 new events).
 * No database, no real source data.
 */
class KbCollectorIngestServiceTest {
  private static final String FP = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  private final EasyBusinessCardAccountingService accountingService =
      mock(EasyBusinessCardAccountingService.class);
  private final EasyTransactionRepository transactionRepository = mock(EasyTransactionRepository.class);
  private KbCollectorIngestService service;

  private EasyBusiness business;
  private EasyCardInstrument instrument;
  private final Map<String, EasyCardEvent> eventsByExternalId = new HashMap<>();
  private final AtomicLong nextEventId = new AtomicLong(1);

  @BeforeEach
  void setUp() {
    var importService = new KbCardImportService(
        new KbCardExcelParser(), new KbCardNormalizer(), accountingService, transactionRepository);
    service = new KbCollectorIngestService(importService);
    business = EasyBusiness.builder().code("PUBLISHER").name("테스트출판사").active(true).build();
    ReflectionTestUtils.setField(business, "businessId", 1L);
    instrument = EasyCardInstrument.builder().business(business).providerCode("KB")
        .issuer("KB국민카드").providerInstrumentTokenHash("b".repeat(64)).build();
    ReflectionTestUtils.setField(instrument, "instrumentId", 10L);
    eventsByExternalId.clear();
    nextEventId.set(1);

    when(accountingService.registerInstrument(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(instrument);
    when(transactionRepository
        .findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(any(), any(), any()))
        .thenReturn(List.of());
    when(accountingService.observePurchase(any())).thenAnswer(inv -> {
      var cmd = (EasyBusinessCardAccountingService.PurchaseCommand) inv.getArgument(0);
      boolean duplicate = eventsByExternalId.containsKey(cmd.externalId());
      var event = eventsByExternalId.computeIfAbsent(cmd.externalId(), id -> {
        var e = EasyCardEvent.builder().business(business).instrument(instrument)
            .eventType(EasyCardEvent.Type.PURCHASE).providerExternalId(cmd.externalId())
            .fingerprint(FP).occurredAt(cmd.occurredAt())
            .confirmationDate(cmd.confirmationDate()).amount(cmd.amount())
            .currency(cmd.currency()).build();
        ReflectionTestUtils.setField(e, "cardEventId", nextEventId.getAndIncrement());
        return e;
      });
      return new EasyBusinessCardAccountingService.EventResult(event, null, duplicate);
    });
  }

  private byte[] fiftyRowWorkbookBytes() {
    List<Row14> rows = new ArrayList<>();
    for (int i = 0; i < 49; i++) {
      int day = 3 + (i % 21);
      rows.add(new Row14("2026-09-" + String.format("%02d", day), "12:00", "홍길*",
          "KB국민 테스트카드", "테스트상점" + (i % 17), 1000 + i * 137, 0, "일시불", "",
          0.0, 0.0, "전표매입", "2026-10-10", "9100" + String.format("%04d", i)));
    }
    rows.add(new Row14("2026-09-10", "09:30", "홍길*",
        "KB국민 테스트카드", "테스트해외상점", 0, 19.81, "일시불", "",
        0.0, 0.0, "전표매입", "2026-10-10", "91999999"));
    try (var xls = KbCardExcelTestFixtures.workbook(rows);
        var out = new ByteArrayOutputStream()) {
      xls.transferTo(out);
      return out.toByteArray();
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private CollectedFile collected(byte[] bytes) {
    return new CollectedFile(bytes, "kb-usage-mock.xls", bytes.length,
        KbCollectorIngestService.sha256Hex(bytes),
        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 24));
  }

  @Test
  void fiftyRowRun_producesFiftyPendingReviewEvents() {
    var summary = service.ingestCollected(1L, collected(fiftyRowWorkbookBytes()));

    assertThat(summary.sourceRows()).isEqualTo(50);
    assertThat(summary.newEvents()).isEqualTo(50);
    assertThat(summary.duplicateEvents()).isEqualTo(0);
    assertThat(summary.foreignRows()).isEqualTo(1);
    assertThat(eventsByExternalId).hasSize(50);
  }

  @Test
  void secondIdenticalRun_producesZeroNewEvents() {
    byte[] bytes = fiftyRowWorkbookBytes();
    var first = service.ingestCollected(1L, collected(bytes));
    var second = service.ingestCollected(1L, collected(bytes));

    assertThat(first.newEvents()).isEqualTo(50);
    assertThat(second.newEvents()).isEqualTo(0);
    assertThat(second.duplicateEvents()).isEqualTo(50);
    assertThat(eventsByExternalId).hasSize(50);
  }

  @Test
  void tamperedSha256_isRejectedBeforeParse() {
    byte[] bytes = fiftyRowWorkbookBytes();
    var tampered = new CollectedFile(bytes, "kb-usage-mock.xls", bytes.length,
        "0".repeat(64), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 24));

    assertThatThrownBy(() -> service.ingestCollected(1L, tampered))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SHA-256 mismatch");
    assertThat(eventsByExternalId).isEmpty();
  }

  @Test
  void sizeMismatch_isRejectedBeforeParse() {
    byte[] bytes = fiftyRowWorkbookBytes();
    var wrong = new CollectedFile(bytes, "kb-usage-mock.xls", bytes.length + 1,
        KbCollectorIngestService.sha256Hex(bytes),
        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 24));

    assertThatThrownBy(() -> service.ingestCollected(1L, wrong))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("size mismatch");
    assertThat(eventsByExternalId).isEmpty();
  }
}
