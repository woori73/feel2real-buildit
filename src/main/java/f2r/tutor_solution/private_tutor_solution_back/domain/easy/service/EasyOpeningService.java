package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCutover;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalEntry;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyJournalService.Leg;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCutoverRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy 개시잔액·cut-over (Phase A2).
 *
 * 개시잔액은 반드시 복식부기 분개로 표현한다. AI가 금액을 추정·생성하지 않으며,
 * 사용자가 확정한 legs만 전기한다. 균형·SETTLEMENT 금지·마감 차단은
 * {@link EasyJournalService#postLegs}에서 강제하므로 여기서는 추가 검증하지 않는다.
 * 기존 historical journal은 수정·삭제·재분개하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyOpeningService {

  private final EasyJournalService journalService;
  private final EasyBusinessRepository businessRepository;
  private final EasyAccountRepository accountRepository;
  private final EasyCutoverRepository cutoverRepository;

  /** 개시잔액 분개 행 입력 — 계정은 id로 지정한다 (추론 없음). */
  public record OpeningLeg(Long accountId, long debit, long credit) {
  }

  /** 개시잔액 분개 + DRAFT cut-over 기록. */
  @Transactional
  public EasyCutover postOpening(Long businessId, LocalDate cutoverDate,
      List<OpeningLeg> legs, String memo) {
    EasyBusiness business = business(businessId);
    if (cutoverDate == null) {
      throw new IllegalArgumentException("전환 기준일을 입력해 주세요.");
    }
    if (cutoverRepository.findByBusiness_BusinessId(businessId).isPresent()) {
      throw new IllegalArgumentException("이미 전환 기록이 있습니다.");
    }
    if (legs == null || legs.isEmpty()) {
      throw new IllegalArgumentException("개시잔액 행을 입력해 주세요.");
    }
    List<Leg> resolved = legs.stream()
        .map(leg -> new Leg(account(leg.accountId()), leg.debit(), leg.credit()))
        .toList();
    EasyJournalEntry entry = journalService.postLegs(business, cutoverDate, null,
        memo == null ? "개시잔액" : memo, resolved);
    EasyCutover cutover = cutoverRepository.save(
        EasyCutover.builder().business(business).cutoverDate(cutoverDate).build());
    cutover.linkOpeningEntry(entry);
    return cutover;
  }

  /** 전환 확정 — 개시분개 없이 확정할 수 없다. ADMIN 경계(컨트롤러는 /api/admin/**). */
  @Transactional
  public EasyCutover approveCutover(Long businessId) {
    EasyCutover cutover = cutoverRepository.findByBusiness_BusinessId(businessId)
        .orElseThrow(() -> new IllegalArgumentException("전환 기록이 없습니다."));
    cutover.approve();
    return cutover;
  }

  private EasyBusiness business(Long businessId) {
    if (businessId == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    return businessRepository.findById(businessId)
        .orElseThrow(() -> new IllegalArgumentException("사업체를 찾을 수 없습니다."));
  }

  private EasyAccount account(Long accountId) {
    if (accountId == null) {
      throw new IllegalArgumentException("계정을 선택해 주세요.");
    }
    return accountRepository.findById(accountId)
        .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
  }
}
