package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyClosingStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalEntry;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalLine;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAnnualClosingRepository;
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
  private final EasyAnnualClosingRepository closingRepository;

  public record TrialBalance(long debitTotal, long creditTotal) {
    public boolean balanced() {
      return debitTotal == creditTotal;
    }
  }

  /**
   * 실계정 분개 행 — 차변/대변 중 한쪽만 양수 금액을 가진다.
   */
  public record Leg(EasyAccount account, long debit, long credit) {
  }

  /**
   * 실계정 복식 전기 (Phase A1).
   *
   * 기존 {@link #repost}와 달리 상대계정을 호출자가 명시한다.
   * insert 전에 전체 legs를 검증하므로 불균형이면 아무 행도 저장되지 않고
   * 전체 트랜잭션이 롤백된다.
   *
   * 규칙:
   * - legs 2개 이상
   * - 각 행은 차변/대변 중 한쪽만 양수
   * - 차변 합계 == 대변 합계
   * - 신규 분개에 SETTLEMENT 기술계정 사용 금지
   *   (historical 조회에는 영향 없음 — 기존 분개는 그대로 읽힌다)
   *
   * @param txn 연결 거래 (순수 B/S 이동이면 null 가능)
   */
  @Transactional
  public EasyJournalEntry postLegs(EasyBusiness business, LocalDate occurredOn,
      EasyTransaction txn, String memo, List<Leg> legs) {
    if (business == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    if (occurredOn == null) {
      throw new IllegalArgumentException("거래일을 입력해 주세요.");
    }
    requirePeriodOpen(business, occurredOn);
    if (legs == null || legs.size() < 2) {
      throw new IllegalArgumentException("분개 행은 2개 이상이어야 합니다.");
    }
    long debitTotal = 0L;
    long creditTotal = 0L;
    for (Leg leg : legs) {
      if (leg == null || leg.account() == null) {
        throw new IllegalArgumentException("분개 행의 계정을 선택해 주세요.");
      }
      if (!leg.account().isActive()) {
        throw new IllegalArgumentException("비활성 계정은 분개할 수 없습니다: "
            + leg.account().getCode());
      }
      if (SETTLEMENT_CODE.equals(leg.account().getCode())) {
        throw new IllegalArgumentException("신규 분개에 자금정산(SETTLEMENT) 계정을 "
            + "사용할 수 없습니다. 실계정을 지정해 주세요.");
      }
      if (!((leg.debit() > 0 && leg.credit() == 0)
          || (leg.credit() > 0 && leg.debit() == 0))) {
        throw new IllegalArgumentException("분개 행은 차변/대변 중 한쪽만 값을 가집니다.");
      }
      debitTotal += leg.debit();
      creditTotal += leg.credit();
    }
    if (debitTotal != creditTotal) {
      throw new IllegalArgumentException("차변 합계(" + debitTotal + ")와 대변 합계("
          + creditTotal + ")가 일치해야 합니다.");
    }
    if (txn != null) {
      List<EasyJournalEntry> actives =
          entryRepository.findByTxn_TxnIdAndStatusOrderByEntryIdDesc(
              txn.getTxnId(), EasyJournalStatus.POSTED);
      for (EasyJournalEntry active : actives) {
        active.voidEntry();
      }
    }
    EasyJournalEntry entry = entryRepository.save(EasyJournalEntry.builder()
        .business(business)
        .occurredOn(occurredOn)
        .txn(txn)
        .memo(memo)
        .build());
    int order = 0;
    for (Leg leg : legs) {
      line(entry, leg.account(), leg.debit(), leg.credit(), order++);
    }
    return entry;
  }

  /** 거래 분개 — 기존 활성 분개를 void하고 새로 분개한다. */
  @Transactional
  public void repost(EasyTransaction txn) {
    requirePeriodOpen(txn.getBusiness(), txn.getOccurredOn());
    List<EasyJournalEntry> actives =
        entryRepository.findByTxn_TxnIdAndStatusOrderByEntryIdDesc(
            txn.getTxnId(), EasyJournalStatus.POSTED);
    EasyJournalEntry superseded = actives.isEmpty() ? null : actives.get(0);
    for (EasyJournalEntry active : actives) {
      active.voidEntry();
    }
    // 자금 이동(EASYSHOP_FUNDS)은 P&L 분개 대상이 아니다 — 원본 보존만 하고 합산하지 않는다.
    if (EasyShopImportService.SOURCE_KIND_FUNDS.equals(txn.getSourceKind())) {
      return;
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

  /**
   * 마감기간 전기 차단 (Phase A2) — 중앙 전기 경로(repost/postLegs)에서 강제한다.
   * easy_annual_closings의 해당 귀속연도 상태가 CLOSED면 거부한다.
   * 조회·void·reopen은 차단하지 않는다.
   */
  private void requirePeriodOpen(EasyBusiness business, LocalDate occurredOn) {
    if (business == null || business.getBusinessId() == null || occurredOn == null) {
      return;
    }
    int fiscalYear = occurredOn.getYear();
    boolean closed = closingRepository
        .findByBusiness_BusinessIdAndFiscalYear(business.getBusinessId(), fiscalYear)
        .map(closing -> closing.getStatus() == EasyClosingStatus.CLOSED)
        .orElse(false);
    if (closed) {
      throw new EasyAccountingPeriodClosedException(business.getBusinessId(), fiscalYear);
    }
  }
}
