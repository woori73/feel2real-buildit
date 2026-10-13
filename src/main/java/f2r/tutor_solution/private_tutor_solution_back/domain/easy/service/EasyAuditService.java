package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyClosingResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCorrectionLogResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCorrectionPreview;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyReviewItemResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyReviewResolveRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyReviewSummaryResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWithholdingRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWithholdingResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAnnualClosing;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyClosingStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyEvidenceReview;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyEvidenceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRowStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyWithholdingRecord;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyWithholdingReview;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAnnualClosingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCorrectionLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportBatchRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportRowRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyReviewItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyWithholdingRecordRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Easy AI 감사 기반 — "AI가 확인할 장부" 큐 + 원천징수 검토 + 연도 마감.
 *
 * 원칙:
 * - 세법 판정을 확정하지 않는다. "확인 필요"만 만든다.
 * - 중복은 의심만 탐지한다. 자동합치기는 하지 않는다.
 * - import staging 구조를 재사용한다. 별도 병렬 시스템을 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyAuditService {

  /** 인적용역 가능성 키워드 — 원천징수 "검토 필요" 후보 (판정 아님). */
  static final List<String> WITHHOLDING_KEYWORDS = List.of(
      "외주", "용역", "개발비", "강사료", "원고료", "자문", "중개료",
      "프리랜서", "도급", "번역료");

  /** 성격 확인 키워드 — 기부·경조·선물·접대 (판정 아님, 확인 필요). */
  static final List<String> EVIDENCE_KEYWORDS = List.of(
      "기부", "후원", "경조", "화환", "축의", "부의", "조의", "선물", "접대");

  private final EasyReviewItemRepository reviewRepository;
  private final EasyTransactionRepository txnRepository;
  private final EasyBusinessRepository businessRepository;
  private final EasyAccountRepository accountRepository;
  private final EasyImportBatchRepository batchRepository;
  private final EasyImportRowRepository rowRepository;
  private final EasyWithholdingRecordRepository withholdingRepository;
  private final EasyAnnualClosingRepository closingRepository;
  private final EasyJournalService journalService;
  private final EasyCorrectionService correctionService;
  private final EasyCorrectionLogRepository correctionLogRepository;

  // ================= 검토 요약·목록 =================

  /** 검토 개수 — businessId null이면 전체 합산. */
  public EasyReviewSummaryResponse reviewSummary(Long businessId) {
    String businessName = businessId == null ? "전체"
        : businessRepository.findById(businessId).map(EasyBusiness::getName).orElse("전체");
    long unclassified = count(businessId, EasyReviewKind.UNCLASSIFIED);
    long evidence = count(businessId, EasyReviewKind.EVIDENCE);
    long business = count(businessId, EasyReviewKind.BUSINESS);
    long withholding = count(businessId, EasyReviewKind.WITHHOLDING);
    long duplicate = count(businessId, EasyReviewKind.DUPLICATE_SUSPECT);
    long unconfirmed = count(businessId, EasyReviewKind.IMPORT_UNCONFIRMED);
    long missingExpense = count(businessId, EasyReviewKind.MISSING_EXPENSE);
    return new EasyReviewSummaryResponse(
        businessId, businessName,
        unclassified, evidence, business, withholding, duplicate, unconfirmed, missingExpense,
        unclassified + evidence + business + withholding + duplicate + unconfirmed
            + missingExpense);
  }

  public List<EasyReviewItemResponse> listReviews(Long businessId, String kind, String status) {
    EasyReviewStatus parsedStatus = parseStatus(status);
    EasyReviewKind parsedKind = parseKind(kind);
    List<EasyReviewItem> items;
    if (businessId == null && parsedKind == null) {
      items = reviewRepository.findByStatusOrderByReviewIdDesc(parsedStatus);
    } else if (businessId == null) {
      items = reviewRepository.findByKindAndStatusOrderByReviewIdDesc(parsedKind, parsedStatus);
    } else if (parsedKind == null) {
      items = reviewRepository.findByBusiness_BusinessIdAndStatusOrderByReviewIdDesc(
          businessId, parsedStatus);
    } else {
      items = reviewRepository.findByBusiness_BusinessIdAndKindAndStatusOrderByReviewIdDesc(
          businessId, parsedKind, parsedStatus);
    }
    return items.stream().map(EasyReviewItemResponse::from).toList();
  }

  // ================= 검토 재계산 =================

  /**
   * 검토 재계산 — 현재 장부에서 확인 필요 항목을 다시 찾는다.
   * 이미 OPEN인 항목은 중복 생성하지 않고, RESOLVED/IGNORED는 건드리지 않는다.
   */
  @Transactional
  public EasyReviewSummaryResponse rebuild(Long businessId) {
    List<EasyBusiness> scope = scopeBusinesses(businessId);
    for (EasyBusiness business : scope) {
      rebuildBusiness(business);
    }
    return reviewSummary(businessId);
  }

  private void rebuildBusiness(EasyBusiness business) {
    Long businessId = business.getBusinessId();
    List<EasyTransaction> txns = txnRepository
        .findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
            businessId, LocalDate.of(2000, 1, 1), LocalDate.of(2100, 12, 31));

    for (EasyTransaction txn : txns) {
      // 1. 계정과목 미분류
      if (txn.getAccount() == null
          && !reviewRepository.existsByBusiness_BusinessIdAndKindAndStatusAndTxn_TxnId(
              businessId, EasyReviewKind.UNCLASSIFIED, EasyReviewStatus.OPEN, txn.getTxnId())) {
        reviewRepository.save(EasyReviewItem.builder()
            .business(business)
            .kind(EasyReviewKind.UNCLASSIFIED)
            .txn(txn)
            .title("계정과목 미분류 — " + describe(txn))
            .detail("자동분류 규칙에 걸리지 않았습니다. 계정을 선택해 장부에 반영해 주세요.")
            .amount(txn.getAmount())
            .occurredOn(txn.getOccurredOn())
            .build());
      }

      // 2. 증빙 확인 필요 (검토 확정분은 제외 — 세법 판정이 아니라 확인 표시)
      EasyEvidenceReview evidenceReview = txn.getEvidenceReview();
      boolean evidenceConfirmed = evidenceReview == EasyEvidenceReview.VERIFIED
          || evidenceReview == EasyEvidenceReview.NOT_REQUIRED
          || evidenceReview == EasyEvidenceReview.MISSING;
      String searchable = (txn.getCounterparty() + " " + txn.getDescription());
      boolean keywordHit = containsAny(searchable, EVIDENCE_KEYWORDS);
      boolean evidenceMissing = txn.getEvidenceType() == null
          || txn.getEvidenceType() == EasyEvidenceType.NONE
          || txn.getEvidenceType() == EasyEvidenceType.OTHER;
      if (!evidenceConfirmed && (evidenceMissing || keywordHit)
          && !reviewRepository.existsByBusiness_BusinessIdAndKindAndStatusAndTxn_TxnId(
              businessId, EasyReviewKind.EVIDENCE, EasyReviewStatus.OPEN, txn.getTxnId())) {
        String detail = evidenceMissing
            ? "증빙이 입력되지 않았습니다. 증빙 상태를 확인해 주세요. (비용 인정 판단 아님)"
            : "기부·경조·선물 가능성이 있는 표현이 있습니다. 성격을 확인해 주세요. (판정 아님)";
        reviewRepository.save(EasyReviewItem.builder()
            .business(business)
            .kind(EasyReviewKind.EVIDENCE)
            .txn(txn)
            .title("증빙 확인 필요 — " + describe(txn))
            .detail(detail)
            .amount(txn.getAmount())
            .occurredOn(txn.getOccurredOn())
            .build());
      }

      // 3. 원천징수 검토 필요 — 지출 + 인적용역 키워드 + 적격증빙 없음
      if (txn.getDirection() == EasyTxnDirection.EXPENSE
          && txn.getWithholdingReview() != EasyWithholdingReview.CHECKED
          && containsAny(searchable, WITHHOLDING_KEYWORDS)
          && (txn.getEvidenceType() == null || txn.getEvidenceType() == EasyEvidenceType.NONE)
          && !reviewRepository.existsByBusiness_BusinessIdAndKindAndStatusAndTxn_TxnId(
              businessId, EasyReviewKind.WITHHOLDING, EasyReviewStatus.OPEN, txn.getTxnId())) {
        reviewRepository.save(EasyReviewItem.builder()
            .business(business)
            .kind(EasyReviewKind.WITHHOLDING)
            .txn(txn)
            .title("원천징수 확인 — " + describe(txn))
            .detail("인적용역 가능성이 있습니다. 지급총액·원천징수·신고 여부를 확인해 주세요. (신고 기능 아님)")
            .amount(txn.getAmount())
            .occurredOn(txn.getOccurredOn())
            .build());
      }
    }

    // 4. 대응 비용 없음 — 자금 유출인데 비용 장부에 같은 금액 대응거래가 없음.
    // 임의로 개발비/외주비 등으로 분류하지 않고 누락 확인 후보로만 둔다.
    // (예: 2026 입출금의 인명 메모 출금 2건 — 비용 대응 없음)
    for (EasyTransaction txn : txns) {
      if (!isUnmatchedFundOutflow(businessId, txn, txns)) {
        continue;
      }
      if (reviewRepository.existsByBusiness_BusinessIdAndKindAndStatusAndTxn_TxnId(
          businessId, EasyReviewKind.MISSING_EXPENSE, EasyReviewStatus.OPEN,
          txn.getTxnId())) {
        continue;
      }
      reviewRepository.save(EasyReviewItem.builder()
          .business(business)
          .kind(EasyReviewKind.MISSING_EXPENSE)
          .txn(txn)
          .title("대응 비용 없음 — " + describe(txn))
          .detail("자금 유출에 대응하는 비용 거래가 ±30일 안에 없습니다. "
              + "장부 누락인지 확인해 주세요. 임의로 분류하지 않았습니다.")
          .amount(txn.getAmount())
          .occurredOn(txn.getOccurredOn())
          .build());
    }

    // 6. 중복 의심 — 같은 fingerprint가 2건 이상 (자동합치기 없음)
    List<EasyTransaction> fingerprinted =
        txnRepository.findByBusiness_BusinessIdAndFingerprintIsNotNull(businessId);
    Map<String, List<EasyTransaction>> groups = new HashMap<>();
    for (EasyTransaction txn : fingerprinted) {
      groups.computeIfAbsent(txn.getFingerprint(), key -> new ArrayList<>()).add(txn);
    }
    for (Map.Entry<String, List<EasyTransaction>> entry : groups.entrySet()) {
      if (entry.getValue().size() < 2) {
        continue;
      }
      EasyTransaction first = entry.getValue().get(0);
      if (reviewRepository.existsByBusiness_BusinessIdAndKindAndStatusAndTxn_TxnId(
          businessId, EasyReviewKind.DUPLICATE_SUSPECT, EasyReviewStatus.OPEN,
          first.getTxnId())) {
        continue;
      }
      reviewRepository.save(EasyReviewItem.builder()
          .business(business)
          .kind(EasyReviewKind.DUPLICATE_SUSPECT)
          .txn(first)
          .title("중복 의심 " + entry.getValue().size() + "건 — " + describe(first))
          .detail("같은 거래가 여러 source에서 들어온 것으로 보입니다. "
              + "자동으로 합치지 않았습니다. 중복이면 한 건만 남겨 주세요.")
          .amount(first.getAmount())
          .occurredOn(first.getOccurredOn())
          .build());
    }

    // 7. 장부 미입력 — STAGED 배치의 미확정 행 (staging 재사용)
    List<EasyImportBatch> batches =
        batchRepository.findAllByBusiness_BusinessIdOrderByBatchIdDesc(businessId);
    for (EasyImportBatch batch : batches) {
      if (batch.getStatus() != EasyImportStatus.STAGED) {
        continue;
      }
      long pending = rowRepository.findByBatch_BatchIdOrderByRowNoAsc(batch.getBatchId()).stream()
          .filter(row -> row.getStatus() != EasyImportRowStatus.IMPORTED)
          .count();
      if (pending > 0
          && !reviewRepository.existsByBusiness_BusinessIdAndKindAndStatusAndBatch_BatchId(
              businessId, EasyReviewKind.IMPORT_UNCONFIRMED, EasyReviewStatus.OPEN,
              batch.getBatchId())) {
        reviewRepository.save(EasyReviewItem.builder()
            .business(business)
            .kind(EasyReviewKind.IMPORT_UNCONFIRMED)
            .batch(batch)
            .title("장부 미입력 " + pending + "건 — " + batch.getSourceName())
            .detail("staging에 대기 중인 행이 있습니다. 확정분만 장부로 옮겨 주세요.")
            .occurredOn(null)
            .build());
      }
    }
    // BUSINESS 종류는 자동 생성하지 않는다 — 사업장 추측을 하지 않기 때문이다.
    // 사용자가 거래를 "사업장 확인 필요"로 표시하면 큐에 오른다 (flagBusinessCheck).
  }

  // ================= 검토 확인 =================

  /** 검토 확인 — 사업장·계정·증빙 상태를 장부에 반영하고 항목을 닫는다. */
  @Transactional
  public EasyReviewItemResponse resolve(Long reviewId, EasyReviewResolveRequest request,
      String resolvedBy) {
    EasyReviewItem item = reviewRepository.findById(reviewId)
        .orElseThrow(() -> new IllegalArgumentException("검토 항목을 찾을 수 없습니다: " + reviewId));
    if (item.getStatus() != EasyReviewStatus.OPEN) {
      throw new IllegalArgumentException("이미 처리된 항목입니다. 다시 열기 후 처리해 주세요.");
    }
    String resolution = request == null || request.resolution() == null
        ? "" : request.resolution().trim();
    if (request != null && Boolean.TRUE.equals(request.ignore())) {
      item.ignore(resolution, resolvedBy);
      return EasyReviewItemResponse.from(item);
    }
    if (item.getTxn() != null) {
      applyToTxn(item.getTxn(), request, resolvedBy);
    }
    item.resolve(resolution, resolvedBy);
    return EasyReviewItemResponse.from(item);
  }

  /**
   * Workspace 승인 계약. review 행을 잠근 뒤 사업체 복합 조건, 처리 여부, 원·대상
   * 회계기간을 한 트랜잭션 안에서 검증하고 거래 수정·정정 로그·재분개·검토 종료를 적용한다.
   */
  @Transactional
  public EasyReviewItemResponse resolveWorkspace(Long businessId, Long reviewId,
      EasyReviewResolveRequest request, String resolvedBy) {
    requireActiveBusiness(businessId);
    EasyReviewItem item = reviewRepository
        .findLockedByReviewIdAndBusiness_BusinessId(reviewId, businessId)
        .orElseThrow(() -> new IllegalArgumentException(
            "해당 사업체의 검토 항목을 찾을 수 없습니다: reviewId=" + reviewId));
    if (item.getStatus() != EasyReviewStatus.OPEN) {
      // 현재 모델에는 동일 승인 요청을 재식별할 키가 없으므로 성공으로 간주하지 않는다.
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "이미 처리된 검토입니다. 동일 요청임을 증명할 수 없어 재승인할 수 없습니다.");
    }
    String resolution = request == null || request.resolution() == null
        ? "" : request.resolution().trim();
    if (request != null && Boolean.TRUE.equals(request.ignore())) {
      item.ignore(resolution, resolvedBy);
      return EasyReviewItemResponse.from(item);
    }
    if (item.getTxn() != null) {
      EasyTransaction txn = item.getTxn();
      requireOpenPeriod(txn.getBusiness().getBusinessId(), txn.getOccurredOn().getYear());
      Long targetBusinessId = request == null || request.businessId() == null
          ? txn.getBusiness().getBusinessId() : request.businessId();
      requireActiveBusiness(targetBusinessId);
      requireOpenPeriod(targetBusinessId, txn.getOccurredOn().getYear());
      applyToTxn(txn, request, resolvedBy);
    }
    item.resolve(resolution, resolvedBy);
    return EasyReviewItemResponse.from(item);
  }

  /** 보류는 데이터 변경 없이 현재 OPEN 항목만 반환한다. */
  public EasyReviewItemResponse holdWorkspace(Long businessId, Long reviewId) {
    requireActiveBusiness(businessId);
    EasyReviewItem item = reviewRepository
        .findByReviewIdAndBusiness_BusinessId(reviewId, businessId)
        .orElseThrow(() -> new IllegalArgumentException(
            "해당 사업체의 검토 항목을 찾을 수 없습니다: reviewId=" + reviewId));
    if (item.getStatus() != EasyReviewStatus.OPEN) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 검토입니다.");
    }
    return EasyReviewItemResponse.from(item);
  }

  private void requireOpenPeriod(Long businessId, int fiscalYear) {
    closingRepository.findByBusiness_BusinessIdAndFiscalYear(businessId, fiscalYear)
        .filter(closing -> closing.getStatus() == EasyClosingStatus.CLOSED)
        .ifPresent(closing -> {
          throw new ResponseStatusException(HttpStatus.CONFLICT,
              "마감된 회계기간입니다: businessId=" + businessId + ", fiscalYear=" + fiscalYear);
        });
  }

  /** 사업장 확인 필요 표시 — "이 거래는 어느 사업장 거래입니까?"의 저장 기반. */
  @Transactional
  public EasyReviewItemResponse flagBusinessCheck(Long txnId, String note) {
    EasyTransaction txn = txnRepository.findById(txnId)
        .orElseThrow(() -> new IllegalArgumentException("거래를 찾을 수 없습니다: txnId=" + txnId));
    EasyBusiness business = txn.getBusiness();
    if (reviewRepository.existsByBusiness_BusinessIdAndKindAndStatusAndTxn_TxnId(
        business.getBusinessId(), EasyReviewKind.BUSINESS, EasyReviewStatus.OPEN, txnId)) {
      throw new IllegalArgumentException("이미 사업장 확인 대기 중인 거래입니다.");
    }
    EasyReviewItem item = reviewRepository.save(EasyReviewItem.builder()
        .business(business)
        .kind(EasyReviewKind.BUSINESS)
        .txn(txn)
        .title("사업장 확인 필요 — " + describe(txn))
        .detail(note == null || note.isBlank()
            ? "어느 사업장 거래인지 선택해 주세요. (학원/출판사/사업 무관)"
            : note.trim())
        .amount(txn.getAmount())
        .occurredOn(txn.getOccurredOn())
        .build());
    return EasyReviewItemResponse.from(item);
  }

  private void applyToTxn(EasyTransaction txn, EasyReviewResolveRequest request,
      String resolvedBy) {
    String before = EasyTxnSnapshot.of(txn);
    EasyBusiness business = txn.getBusiness();
    if (request != null && request.businessId() != null
        && !request.businessId().equals(business.getBusinessId())) {
      business = requireActiveBusiness(request.businessId());
    }
    // easy_accounts는 사업체 FK 없는 공용 마스터(V974)다.
    // 계정-사업장 조합 제한을 두지 않는다 — 제한을 만들 근거가 없고
    // 사업장 isolation은 거래의 business_id가 담당한다.
    EasyAccount account = txn.getAccount();
    if (request != null && request.accountId() != null) {
      account = accountRepository.findById(request.accountId())
          .orElseThrow(() -> new IllegalArgumentException(
              "계정을 찾을 수 없습니다: " + request.accountId()));
      if (!account.isActive()) {
        throw new IllegalArgumentException("비활성 계정은 사용할 수 없습니다.");
      }
    }
    EasyEvidenceReview evidenceReview = txn.getEvidenceReview();
    if (request != null && request.evidenceReview() != null
        && !request.evidenceReview().isBlank()) {
      evidenceReview = EasyEvidenceReview.parse(request.evidenceReview());
    }
    EasyWithholdingReview withholdingReview = txn.getWithholdingReview();
    if (request != null && request.withholdingReview() != null
        && !request.withholdingReview().isBlank()) {
      withholdingReview = EasyWithholdingReview.parse(request.withholdingReview());
    }
    txn.update(
        business,
        txn.getOccurredOn(),
        txn.getDirection(),
        txn.getAmount(),
        txn.getCategory(),
        txn.getCounterparty(),
        txn.getDescription(),
        txn.getPayMethod(),
        txn.getMemo(),
        txn.getReceiptRef(),
        account,
        txn.getSupplyAmount(),
        txn.getVatAmount(),
        txn.getEvidenceType(),
        txn.getSourceKind(),
        txn.getSourceRef(),
        EasyFingerprint.of(
            business.getBusinessId(), txn.getOccurredOn(), txn.getDirection().name(),
            txn.getAmount(), txn.getCounterparty(), txn.getDescription(), txn.getPayMethod()),
        evidenceReview,
        withholdingReview,
        txn.isReversal() ? Boolean.TRUE : null);
    correctionService.record(business, txn, "RESOLVE", before, EasyTxnSnapshot.of(txn),
        resolvedBy);
    journalService.repost(txn);
  }

  // ================= 수정 미리보기·감사 로그 =================

  /**
   * 수정 미리보기 — 적용하지 않고 before/after와 변경점만 보여준다.
   * 향후 AI 수정 (요청→확인→수정→재분개) 계약의 확인 단계용이다.
   */
  public EasyCorrectionPreview previewCorrection(Long txnId, EasyReviewResolveRequest request) {
    EasyTransaction txn = txnRepository.findById(txnId)
        .orElseThrow(() -> new IllegalArgumentException("거래를 찾을 수 없습니다: txnId=" + txnId));
    List<String> changes = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    if (request != null && Boolean.TRUE.equals(request.ignore())) {
      changes.add("검토 항목만 닫습니다 (장부 변경 없음).");
      return new EasyCorrectionPreview(txnId, EasyTxnSnapshot.of(txn),
          EasyTxnSnapshot.of(txn), changes, warnings);
    }
    Long afterBusiness = txn.getBusiness().getBusinessId();
    if (request != null && request.businessId() != null
        && !request.businessId().equals(afterBusiness)) {
      String beforeName = txn.getBusiness().getName();
      String afterName = businessRepository.findById(request.businessId())
          .map(EasyBusiness::getName).orElse("사업체#" + request.businessId());
      changes.add("사업장: " + beforeName + " → " + afterName);
      warnings.add("사업장 이동 시 분개를 다시 작성하고 중복검사용 fingerprint가 바뀝니다.");
      afterBusiness = request.businessId();
    }
    Long afterAccount = txn.getAccount() == null ? null : txn.getAccount().getAccountId();
    if (request != null && request.accountId() != null
        && !request.accountId().equals(afterAccount)) {
      String beforeName = txn.getAccount() == null ? "(미분류)" : txn.getAccount().getName();
      String afterName = accountRepository.findById(request.accountId())
          .map(EasyAccount::getName).orElse("계정#" + request.accountId());
      changes.add("계정: " + beforeName + " → " + afterName);
      if (txn.getAccount() == null) {
        warnings.add("미분류 거래에 분개가 새로 생성됩니다.");
      } else {
        warnings.add("기존 분개를 void하고 재분개합니다.");
      }
      afterAccount = request.accountId();
    }
    String afterEvidence = currentName(txn.getEvidenceReview() == null ? null
        : txn.getEvidenceReview().name());
    if (request != null && request.evidenceReview() != null
        && !request.evidenceReview().isBlank()
        && !request.evidenceReview().trim().equalsIgnoreCase(
            String.valueOf(afterEvidence))) {
      changes.add("증빙 검토: " + afterEvidence + " → " + request.evidenceReview().trim());
      afterEvidence = request.evidenceReview().trim();
    }
    String afterWithholding = currentName(txn.getWithholdingReview() == null ? null
        : txn.getWithholdingReview().name());
    if (request != null && request.withholdingReview() != null
        && !request.withholdingReview().isBlank()
        && !request.withholdingReview().trim().equalsIgnoreCase(
            String.valueOf(afterWithholding))) {
      changes.add("원천징수 검토: " + afterWithholding + " → "
          + request.withholdingReview().trim());
      afterWithholding = request.withholdingReview().trim();
    }
    if (changes.isEmpty()) {
      changes.add("변경 사항이 없습니다.");
    }
    return new EasyCorrectionPreview(txnId, EasyTxnSnapshot.of(txn),
        EasyTxnSnapshot.of(txn, afterBusiness, afterAccount, afterEvidence, afterWithholding),
        changes, warnings);
  }

  private String currentName(String value) {
    return value == null ? "null" : value;
  }

  public List<EasyCorrectionLogResponse> listCorrectionLogs(Long businessId, Long txnId) {
    if (txnId != null) {
      return correctionLogRepository.findByTxn_TxnIdOrderByLogIdDesc(txnId).stream()
          .map(EasyCorrectionLogResponse::from).toList();
    }
    if (businessId != null) {
      return correctionLogRepository.findByBusiness_BusinessIdOrderByLogIdDesc(businessId)
          .stream().map(EasyCorrectionLogResponse::from).toList();
    }
    return correctionLogRepository.findAllByOrderByLogIdDesc().stream()
        .map(EasyCorrectionLogResponse::from).toList();
  }

  // ================= 원천징수 검토 기록 =================

  public List<EasyWithholdingResponse> listWithholdings(Long businessId) {
    List<EasyWithholdingRecord> records = businessId == null
        ? withholdingRepository.findAllByOrderByPaidOnDescRecordIdDesc()
        : withholdingRepository.findByBusiness_BusinessIdOrderByPaidOnDescRecordIdDesc(businessId);
    return records.stream().map(EasyWithholdingResponse::from).toList();
  }

  @Transactional
  public EasyWithholdingResponse createWithholding(EasyWithholdingRequest request) {
    EasyBusiness business = requireActiveBusiness(requireBusinessId(request));
    EasyWithholdingRecord record = new EasyWithholdingRecord(
        business,
        requirePayee(request),
        requireGross(request),
        optionalNonNegative(request == null ? null : request.netAmount(), "실제 지급액"),
        optionalNonNegative(request == null ? null : request.withholdingAmount(), "원천징수액"),
        request == null ? null : request.paidOn(),
        requireLinkedTxn(request, business),
        request != null && Boolean.TRUE.equals(request.filingChecked()),
        request != null && Boolean.TRUE.equals(request.statementChecked()),
        request == null ? null : request.memo());
    return EasyWithholdingResponse.from(withholdingRepository.save(record));
  }

  @Transactional
  public EasyWithholdingResponse updateWithholding(Long recordId, EasyWithholdingRequest request) {
    EasyWithholdingRecord record = withholdingRepository.findById(recordId)
        .orElseThrow(() -> new IllegalArgumentException(
            "원천징수 기록을 찾을 수 없습니다: " + recordId));
    EasyBusiness business = record.getBusiness();
    if (request != null && request.businessId() != null
        && !request.businessId().equals(business.getBusinessId())) {
      business = requireActiveBusiness(request.businessId());
    }
    record.update(
        requirePayee(request),
        requireGross(request),
        optionalNonNegative(request == null ? null : request.netAmount(), "실제 지급액"),
        optionalNonNegative(request == null ? null : request.withholdingAmount(), "원천징수액"),
        request == null ? null : request.paidOn(),
        requireLinkedTxn(request, business),
        request != null && Boolean.TRUE.equals(request.filingChecked()),
        request != null && Boolean.TRUE.equals(request.statementChecked()),
        request == null ? null : request.memo());
    return EasyWithholdingResponse.from(record);
  }

  @Transactional
  public void deleteWithholding(Long recordId) {
    EasyWithholdingRecord record = withholdingRepository.findById(recordId)
        .orElseThrow(() -> new IllegalArgumentException(
            "원천징수 기록을 찾을 수 없습니다: " + recordId));
    withholdingRepository.delete(record);
  }

  // ================= 연도 마감 =================

  public List<EasyClosingResponse> listClosings(Long businessId, Integer fiscalYear) {
    List<EasyAnnualClosing> closings;
    if (businessId != null && fiscalYear != null) {
      closings = closingRepository.findByBusiness_BusinessIdAndFiscalYear(businessId, fiscalYear)
          .map(List::of).orElseGet(List::of);
    } else if (businessId != null) {
      closings = closingRepository.findByBusiness_BusinessIdOrderByFiscalYearDesc(businessId);
    } else if (fiscalYear != null) {
      closings = closingRepository.findByFiscalYearOrderByFiscalYearDesc(fiscalYear);
    } else {
      closings = closingRepository.findAll();
    }
    return closings.stream().map(EasyClosingResponse::from).toList();
  }

  @Transactional
  public EasyClosingResponse openClosing(Long businessId, int fiscalYear) {
    EasyBusiness business = requireActiveBusiness(businessId);
    if (fiscalYear < 2000 || fiscalYear > 2100) {
      throw new IllegalArgumentException("귀속연도가 올바르지 않습니다.");
    }
    if (closingRepository.findByBusiness_BusinessIdAndFiscalYear(businessId, fiscalYear)
        .isPresent()) {
      throw new IllegalArgumentException("이미 열린 연도 마감입니다.");
    }
    return EasyClosingResponse.from(
        closingRepository.save(new EasyAnnualClosing(business, fiscalYear)));
  }

  @Transactional
  public EasyClosingResponse markClosingReview(Long closingId) {
    EasyAnnualClosing closing = requireClosing(closingId);
    closing.markReview();
    return EasyClosingResponse.from(closing);
  }

  /**
   * 해당 귀속연도의 OPEN 검토 항목 — 마감 차단 대상.
   * 다른 연도 항목은 마감에 영향을 주지 않는다.
   */
  public EasyReviewSummaryResponse openReviewsForYear(Long businessId, int fiscalYear) {
    EasyBusiness business = businessRepository.findById(businessId)
        .orElseThrow(() -> new IllegalArgumentException(
            "사업체를 찾을 수 없습니다: " + businessId));
    long unclassified = 0L;
    long evidence = 0L;
    long businessCount = 0L;
    long withholding = 0L;
    long duplicate = 0L;
    long unconfirmed = 0L;
    long missingExpense = 0L;
    for (EasyReviewItem item : reviewRepository
        .findByBusiness_BusinessIdAndStatusOrderByReviewIdDesc(businessId,
            EasyReviewStatus.OPEN)) {
      if (!belongsToYear(item, fiscalYear)) {
        continue;
      }
      switch (item.getKind()) {
        case UNCLASSIFIED -> unclassified++;
        case EVIDENCE -> evidence++;
        case BUSINESS -> businessCount++;
        case WITHHOLDING -> withholding++;
        case DUPLICATE_SUSPECT -> duplicate++;
        case IMPORT_UNCONFIRMED -> unconfirmed++;
        case MISSING_EXPENSE -> missingExpense++;
      }
    }
    return new EasyReviewSummaryResponse(
        businessId, business.getName(),
        unclassified, evidence, businessCount, withholding, duplicate, unconfirmed,
        missingExpense,
        unclassified + evidence + businessCount + withholding + duplicate + unconfirmed
            + missingExpense);
  }

  /**
   * 검토 항목의 귀속연도 판별.
   * - 거래 연결: occurredOn 연도 (장부 귀속 기준)
   * - staging 행 연결: occurredOn 연도
   * - 배치 연결: 미확정 행들의 occurredOn 연도 합집합
   * - 날짜가 없어 귀속연도를 확정할 수 없으면 임의 연도에 배정하지 않고
   *   모든 연도 마감의 차단 사유로 둔다 (보수적 차단, 스냅샷에 undetermined로 기록).
   */
  boolean belongsToYear(EasyReviewItem item, int fiscalYear) {
    if (item.getTxn() != null) {
      LocalDate occurredOn = item.getTxn().getOccurredOn();
      return occurredOn == null || occurredOn.getYear() == fiscalYear;
    }
    if (item.getImportRow() != null) {
      LocalDate occurredOn = item.getImportRow().getOccurredOn();
      return occurredOn == null || occurredOn.getYear() == fiscalYear;
    }
    if (item.getBatch() != null) {
      java.util.Set<Integer> years = pendingRowYears(item.getBatch().getBatchId());
      return years.isEmpty() || years.contains(fiscalYear);
    }
    return true;
  }

  private boolean isUndetermined(EasyReviewItem item) {
    if (item.getTxn() != null) {
      return item.getTxn().getOccurredOn() == null;
    }
    if (item.getImportRow() != null) {
      return item.getImportRow().getOccurredOn() == null;
    }
    if (item.getBatch() != null) {
      return pendingRowYears(item.getBatch().getBatchId()).isEmpty();
    }
    return true;
  }

  private java.util.Set<Integer> pendingRowYears(Long batchId) {
    java.util.Set<Integer> years = new java.util.HashSet<>();
    if (batchId == null) {
      return years;
    }
    for (EasyImportRow row : rowRepository.findByBatch_BatchIdOrderByRowNoAsc(batchId)) {
      if (row.getStatus() == EasyImportRowStatus.IMPORTED) {
        continue;
      }
      if (row.getOccurredOn() != null) {
        years.add(row.getOccurredOn().getYear());
      }
    }
    return years;
  }

  /**
   * 연도 마감 — 해당 귀속연도의 OPEN 검토 항목이 있으면 강제 여부 없이 마감하지 않는다.
   * 다른 연도 항목은 차단하지 않는다. force=true면 사유를 스냅샷에 남기고 마감한다.
   * 마감 해제는 항상 가능하다.
   */
  @Transactional
  public EasyClosingResponse closeClosing(Long closingId, String note, boolean force) {
    EasyAnnualClosing closing = requireClosing(closingId);
    Long businessId = closing.getBusiness().getBusinessId();
    int fiscalYear = closing.getFiscalYear();
    EasyReviewSummaryResponse open = openReviewsForYear(businessId, fiscalYear);
    long undetermined = undeterminedCount(businessId, fiscalYear);
    String snapshot = snapshotJson(businessId, fiscalYear, open, undetermined, force);
    if (open.totalOpen() > 0 && !force) {
      throw new IllegalArgumentException(fiscalYear + "년 미확인 항목 " + open.totalOpen()
          + "건이 있습니다. 확인 후 마감하거나 사유와 함께 강제 마감해 주세요. (미분류 "
          + open.unclassified() + " / 증빙 " + open.evidence() + " / 사업장 " + open.business()
          + " / 원천징수 " + open.withholding() + " / 중복의심 " + open.duplicateSuspect()
          + " / 미입력 " + open.importUnconfirmed()
          + " / 대응비용없음 " + open.missingExpense()
          + (undetermined > 0 ? " / 귀속연도 미확정 " + undetermined + "건 포함" : "") + ")");
    }
    closing.close(snapshot, note);
    return EasyClosingResponse.from(closing);
  }

  private long undeterminedCount(Long businessId, int fiscalYear) {
    return reviewRepository
        .findByBusiness_BusinessIdAndStatusOrderByReviewIdDesc(businessId,
            EasyReviewStatus.OPEN)
        .stream()
        .filter(item -> belongsToYear(item, fiscalYear) && isUndetermined(item))
        .count();
  }

  @Transactional
  public EasyClosingResponse reopenClosing(Long closingId) {
    EasyAnnualClosing closing = requireClosing(closingId);
    closing.reopen();
    return EasyClosingResponse.from(closing);
  }

  // ================= 내부 =================

  private long count(Long businessId, EasyReviewKind kind) {
    if (businessId == null) {
      return reviewRepository.countByKindAndStatus(kind, EasyReviewStatus.OPEN);
    }
    return reviewRepository.countByBusiness_BusinessIdAndKindAndStatus(
        businessId, kind, EasyReviewStatus.OPEN);
  }

  private List<EasyBusiness> scopeBusinesses(Long businessId) {
    if (businessId != null) {
      EasyBusiness business = businessRepository.findById(businessId)
          .orElseThrow(() -> new IllegalArgumentException(
              "사업체를 찾을 수 없습니다: " + businessId));
      return List.of(business);
    }
    return businessRepository.findAll();
  }

  /**
   * 대응 없는 자금 유출 여부 — EASYSHOP_FUNDS 출금 중 적요/거래처가 있고 일정 금액 이상인데
   * 같은 사업장의 비용 장부(자금 제외)에 같은 금액 거래가 ±30일 안에 없는 경우.
   * 인명·용역 추정 등 임의 분류를 하지 않으며, 존재 여부만 확인 대상으로 올린다.
   */
  static final long MISSING_EXPENSE_MIN_AMOUNT = 500000L;
  static final int MISSING_EXPENSE_WINDOW_DAYS = 30;

  boolean isUnmatchedFundOutflow(Long businessId, EasyTransaction txn,
      List<EasyTransaction> scopeTxns) {
    if (txn.getDirection() != EasyTxnDirection.EXPENSE) {
      return false;
    }
    if (txn.isReversal()) {
      return false;
    }
    if (!"EASYSHOP_FUNDS".equals(txn.getSourceKind())) {
      return false;
    }
    if (txn.getAmount() < MISSING_EXPENSE_MIN_AMOUNT) {
      return false;
    }
    String who = (txn.getCounterparty() == null ? "" : txn.getCounterparty())
        + (txn.getDescription() == null ? "" : txn.getDescription())
        + (txn.getMemo() == null ? "" : txn.getMemo());
    if (who.isBlank()) {
      return false;
    }
    if (txn.getOccurredOn() == null) {
      return false;
    }
    for (EasyTransaction candidate : scopeTxns) {
      if (candidate.getTxnId() != null && candidate.getTxnId().equals(txn.getTxnId())) {
        continue;
      }
      if (candidate.getDirection() != EasyTxnDirection.EXPENSE) {
        continue;
      }
      if ("EASYSHOP_FUNDS".equals(candidate.getSourceKind())) {
        continue;
      }
      if (candidate.getAmount() != txn.getAmount()) {
        continue;
      }
      if (candidate.getOccurredOn() == null) {
        continue;
      }
      long gap = Math.abs(candidate.getOccurredOn().toEpochDay()
          - txn.getOccurredOn().toEpochDay());
      if (gap <= MISSING_EXPENSE_WINDOW_DAYS) {
        return false;
      }
    }
    return true;
  }

  private String describe(EasyTransaction txn) {
    String base = txn.getOccurredOn() + " "
        + String.format("%,d원", txn.getAmount())
        + " " + (txn.getDirection() == EasyTxnDirection.INCOME ? "수입" : "지출");
    String what = txn.getDescription();
    if (what == null || what.isBlank()) {
      what = txn.getCounterparty();
    }
    if (what != null && !what.isBlank()) {
      base += " " + what.trim();
    }
    return base;
  }

  private boolean containsAny(String text, List<String> keywords) {
    if (text == null) {
      return false;
    }
    for (String keyword : keywords) {
      if (text.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  private EasyReviewKind parseKind(String kind) {
    if (kind == null || kind.isBlank()) {
      return null;
    }
    try {
      return EasyReviewKind.valueOf(kind.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("검토 종류 값이 올바르지 않습니다.");
    }
  }

  private EasyReviewStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return EasyReviewStatus.OPEN;
    }
    try {
      return EasyReviewStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("검토 상태 값이 올바르지 않습니다.");
    }
  }

  private EasyAnnualClosing requireClosing(Long closingId) {
    return closingRepository.findById(closingId)
        .orElseThrow(() -> new IllegalArgumentException(
            "연도 마감을 찾을 수 없습니다: " + closingId));
  }

  private String snapshotJson(Long businessId, int fiscalYear, EasyReviewSummaryResponse open,
      long undetermined, boolean forced) {
    return "{\"businessId\":" + businessId
        + ",\"fiscalYear\":" + fiscalYear
        + ",\"open\":{\"UNCLASSIFIED\":" + open.unclassified()
        + ",\"EVIDENCE\":" + open.evidence()
        + ",\"BUSINESS\":" + open.business()
        + ",\"WITHHOLDING\":" + open.withholding()
        + ",\"DUPLICATE_SUSPECT\":" + open.duplicateSuspect()
        + ",\"IMPORT_UNCONFIRMED\":" + open.importUnconfirmed()
        + ",\"MISSING_EXPENSE\":" + open.missingExpense()
        + "},\"undetermined\":" + undetermined + ",\"forced\":" + forced + "}";
  }

  private EasyBusiness requireActiveBusiness(Long businessId) {
    if (businessId == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    EasyBusiness business = businessRepository.findById(businessId)
        .orElseThrow(() -> new IllegalArgumentException("사업체를 찾을 수 없습니다: " + businessId));
    if (!business.isActive()) {
      throw new IllegalArgumentException("비활성 사업체에는 등록할 수 없습니다.");
    }
    return business;
  }

  private Long requireBusinessId(EasyWithholdingRequest request) {
    if (request == null || request.businessId() == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    return request.businessId();
  }

  private String requirePayee(EasyWithholdingRequest request) {
    if (request == null || request.payee() == null || request.payee().isBlank()) {
      throw new IllegalArgumentException("지급 대상을 입력해 주세요.");
    }
    return request.payee();
  }

  private long requireGross(EasyWithholdingRequest request) {
    if (request == null || request.grossAmount() == null || request.grossAmount() < 0) {
      throw new IllegalArgumentException("지급총액은 0 이상이어야 합니다.");
    }
    return request.grossAmount();
  }

  private Long optionalNonNegative(Long amount, String label) {
    if (amount == null) {
      return null;
    }
    if (amount < 0) {
      throw new IllegalArgumentException(label + "은 0 이상이어야 합니다.");
    }
    return amount;
  }

  /** 연결 거래 — 같은 사업체 거래만 연결한다 (장부 섞임 방지). */
  private EasyTransaction requireLinkedTxn(EasyWithholdingRequest request,
      EasyBusiness business) {
    if (request == null || request.txnId() == null) {
      return null;
    }
    EasyTransaction txn = txnRepository.findById(request.txnId())
        .orElseThrow(() -> new IllegalArgumentException(
            "거래를 찾을 수 없습니다: txnId=" + request.txnId()));
    if (!txn.getBusiness().getBusinessId().equals(business.getBusinessId())) {
      throw new IllegalArgumentException("다른 사업체의 거래는 연결할 수 없습니다.");
    }
    return txn;
  }
}
