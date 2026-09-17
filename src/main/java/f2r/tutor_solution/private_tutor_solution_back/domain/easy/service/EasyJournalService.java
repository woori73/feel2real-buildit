package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalEntry;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalLine;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalEntryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalLineRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy 복식엔진 — 확정 장부의 차변/대변 이중 표현.
 *
 * 규칙 (내부 정산 장치이며 세무 계정이 아니다):
 * - 수입: 차변 자금정산(SETTLEMENT) / 대변 수익계정
 * - 지출: 차변 비용계정 / 대변 자금정산(SETTLEMENT)
 * - 계정 미분류·0원 거래는 분개하지 않는다 (분류 후 재분개)
 * - 거래당 활성 분개는 1건. 수정 시 기존 분개를 void + 재분개한다 (이력 보존).
 * - 차변 합계 == 대변 합계는 행 생성자 + DB CHECK + 시산표로 보증한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyJournalService {

  static final String SETTLEMENT_CODE = "SETTLEMENT";

  private final EasyJournalEntryRepository entryRepository;
  private final EasyJournalLineRepository lineRepository;
  private final EasyAccountRepository accountRepository;

  public record TrialBalance(long debitTotal, long creditTotal) {
    public boolean balanced() {
      return debitTotal == creditTotal;
    }
  }

  /** 거래 분개 — 기존 활성 분개를 void하고 새로 분개한다. */
  @Transactional
  public void repost(EasyTransaction txn) {
    List<EasyJournalEntry> actives =
        entryRepository.findByTxn_TxnIdAndStatusOrderByEntryIdDesc(
            txn.getTxnId(), EasyJournalStatus.POSTED);
    EasyJournalEntry superseded = actives.isEmpty() ? null : actives.get(0);
    for (EasyJournalEntry active : actives) {
      active.voidEntry();
    }
    if (txn.getAccount() == null || txn.getAmount() <= 0) {
      return;
    }
    EasyAccount settlement = requireSettlement();
    EasyJournalEntry entry = entryRepository.save(EasyJournalEntry.builder()
        .business(txn.getBusiness())
        .occurredOn(txn.getOccurredOn())
        .txn(txn)
        .memo(txn.getDescription())
        .reversedEntry(superseded)
        .build());
    if (txn.getDirection() == EasyTxnDirection.INCOME) {
      line(entry, settlement, txn.getAmount(), 0L, 0);
      line(entry, txn.getAccount(), 0L, txn.getAmount(), 1);
    } else {
      line(entry, txn.getAccount(), txn.getAmount(), 0L, 0);
      line(entry, settlement, 0L, txn.getAmount(), 1);
    }
  }

  /** 거래 삭제 시 활성 분개를 void한다 (이력은 남긴다). */
  @Transactional
  public void voidFor(EasyTransaction txn) {
    for (EasyJournalEntry active : entryRepository
        .findByTxn_TxnIdAndStatusOrderByEntryIdDesc(txn.getTxnId(), EasyJournalStatus.POSTED)) {
      active.voidEntry();
    }
  }

  /** 시산표 — 범위 내 활성 분개의 차변/대변 합계. */
  public TrialBalance trialBalance(Long businessId, LocalDate from, LocalDate to) {
    LocalDate start = from == null ? LocalDate.of(2000, 1, 1) : from;
    LocalDate end = to == null ? LocalDate.of(2100, 12, 31) : to;
    List<EasyJournalEntry> entries = businessId == null
        ? entryRepository.findByOccurredOnBetweenAndStatusOrderByEntryIdAsc(
            start, end, EasyJournalStatus.POSTED)
        : entryRepository.findByBusiness_BusinessIdAndOccurredOnBetweenAndStatusOrderByEntryIdAsc(
            businessId, start, end, EasyJournalStatus.POSTED);
    long debit = 0L;
    long credit = 0L;
    for (EasyJournalEntry entry : entries) {
      for (EasyJournalLine line : lineRepository
          .findByEntry_EntryIdOrderBySortOrderAsc(entry.getEntryId())) {
        debit += line.getDebit();
        credit += line.getCredit();
      }
    }
    return new TrialBalance(debit, credit);
  }

  private void line(EasyJournalEntry entry, EasyAccount account, long debit, long credit,
      int order) {
    lineRepository.save(EasyJournalLine.builder()
        .entry(entry).account(account).debit(debit).credit(credit).sortOrder(order).build());
  }

  private EasyAccount requireSettlement() {
    return accountRepository.findByCode(SETTLEMENT_CODE)
        .filter(EasyAccount::isActive)
        .orElseThrow(() -> new IllegalStateException(
            "자금정산(SETTLEMENT) 계정이 없습니다. V976 migration을 확인해 주세요."));
  }
}
