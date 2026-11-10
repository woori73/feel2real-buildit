package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest.EasyImportRowInput;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportRowResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionApproval.ApprovalStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRunStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyEvidenceReview;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyEvidenceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRowStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTaxClassification;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountMappingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionApprovalRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportBatchRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportRowRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAccountService.ClassificationMatch;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy import staging — 원본 행은 staging에만 두고, 확정한 행은 장부로 옮긴다. 실제 거래는 분류 확정 여부와 무관하게 장부에 적재한다 (HOLD
 * 금지). 계정 미분류 행은 REVIEW 표시로 적재하고 분개는 생략한다. 실제 이지샵 데이터 import는 Work 자료 도착 후 별도 작업에서 수행한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyImportService {

  private final EasyImportBatchRepository batchRepository;
  private final EasyImportRowRepository rowRepository;
  private final EasyBusinessRepository businessRepository;
  private final EasyTransactionRepository txnRepository;
  private final EasyAccountService accountService;
  private final EasyAccountMappingRepository mappingRepository;
  private final EasyJournalService journalService;
  private final EasyCollectionRunRepository collectionRuns;
  private final EasyCollectionApprovalRepository collectionApprovals;
  private final EasyTutorAccessService collectionAccess;

  public List<EasyImportBatchResponse> listBatches(Long businessId) {
    if (businessId == null) {
      return batchRepository.findAll().stream()
          .map(batch -> EasyImportBatchResponse.of(batch, List.of()))
          .toList();
    }
    return batchRepository.findAllByBusiness_BusinessIdOrderByBatchIdDesc(businessId).stream()
        .map(batch -> EasyImportBatchResponse.of(batch, List.of()))
        .toList();
  }

  public EasyImportBatchResponse getBatch(Long batchId) {
    EasyImportBatch batch = requireBatch(batchId);
    return EasyImportBatchResponse.of(batch, rows(batchId));
  }

  /** staging 적재 — 행별 검증 + 규칙 자동제안. 실패 행이 있어도 배치는 만든다. */
  @Transactional
  public EasyImportBatchResponse stage(EasyImportBatchRequest request) {
    EasyBusiness business = requireActiveBusiness(request.businessId());
    if (request.rows() == null || request.rows().isEmpty()) {
      throw new IllegalArgumentException("가져올 행이 없습니다.");
    }
    if (request.rows().size() > 2000) {
      throw new IllegalArgumentException("한 번에 2000행까지 가져올 수 있습니다.");
    }
    EasyImportBatch batch =
        batchRepository.save(
            EasyImportBatch.builder()
                .business(business)
                .sourceName(request.sourceName())
                .sourceKind(request.sourceKind())
                .totalRows(request.rows().size())
                .build());

    List<EasyImportRowResponse> staged = new ArrayList<>();
    int rowNo = 0;
    for (EasyImportRowInput input : request.rows()) {
      rowNo++;
      staged.add(EasyImportRowResponse.from(stageOne(batch, rowNo, input)));
    }
    return EasyImportBatchResponse.of(batch, staged);
  }

  private EasyImportRow stageOne(EasyImportBatch batch, int rowNo, EasyImportRowInput input) {
    LocalDate occurredOn = parseDate(input.occurredOn());
    EasyTxnDirection direction = parseDirection(input.direction());
    Long amount = input.amount();
    Long supplyAmount = input.supplyAmount();
    Long vatAmount = input.vatAmount();
    EasyEvidenceType evidenceType = parseEvidence(input.evidenceType());

    String error = null;
    if (occurredOn == null) {
      error = "거래일(YYYY-MM-DD)이 올바르지 않습니다.";
    } else if (direction == null) {
      error = "수입/지출이 올바르지 않습니다.";
    } else if (amount == null || amount < 0) {
      error = "금액은 0 이상이어야 합니다.";
    } else if ((supplyAmount != null && supplyAmount < 0) || (vatAmount != null && vatAmount < 0)) {
      error = "공급가액/부가세는 0 이상이어야 합니다.";
    } else if (!isValidEvidence(input.evidenceType())) {
      error = "증빙 유형이 올바르지 않습니다.";
    }

    EasyImportRow row =
        EasyImportRow.builder()
            .batch(batch)
            .rowNo(rowNo)
            .occurredOn(occurredOn)
            .direction(direction)
            .amount(amount)
            .counterparty(input.counterparty())
            .description(input.description())
            .payMethod(input.payMethod())
            .supplyAmount(supplyAmount)
            .vatAmount(vatAmount)
            .evidenceType(evidenceType)
            .rawData(input.rawData())
            .externalAccount(input.externalAccount())
            .reversal(input.reversal())
            .build();
    EasyImportRow saved = rowRepository.save(row);
    if (error != null) {
      saved.markError(error);
      return saved;
    }
    // 확정 매핑 우선 (이지샵 원본 계정명 → 내부 계정), 불확실하면 규칙 → 미분류(review).
    Optional<EasyAccount> mapped = resolveMapping(saved.getExternalAccount());
    if (mapped.isPresent()) {
      saved.markReady(mapped.get(), 1.0);
      return saved;
    }
    Optional<ClassificationMatch> match =
        accountService.classify(saved.getCounterparty(), saved.getDescription(), amount, direction);
    if (match.isPresent()) {
      saved.markReady(match.get().account(), match.get().confidence());
    } else {
      saved.markReady(null, 0.0);
    }
    return saved;
  }

  /** 외부 계정명 → 내부 계정 확정 매핑. 공식 세무 계정코드를 추측하지 않으며, 매핑 테이블에 없는 원본 계정명은 empty로 둔다 (review 대상). */
  public Optional<EasyAccount> resolveMapping(String externalAccount) {
    if (externalAccount == null || externalAccount.isBlank()) {
      return Optional.empty();
    }
    return mappingRepository
        .findFirstByExternalCode(externalAccount.trim())
        .map(mapping -> mapping.getAccount())
        .filter(EasyAccount::isActive);
  }

  /**
   * 확정 — READY 행을 장부 거래로 변환한다. 이미 폐기된 배치는 거부한다. rowIds 생략 시 READY 전체를 확정한다. CONFIRMED 상태여도 READY 행을
   * 선택 확정할 수 있다 (partial confirm). 배치의 confirmedRows는 row-level IMPORTED 건수를 recount한 누적값이다. 이번
   * 호출분만 덮어쓰지 않으므로, CONFIRMED 배치의 추가 확정에서도 기존 누적(예: 500)이 손실되지 않는다 (예: 500 + 43 = 543).
   *
   * <p>EASY 운영 원칙 — 실제 거래를 HOLD하지 않는다: - PENDING 행은 같은 매핑→규칙 경로로 자동 준비한 뒤 함께 확정한다. - 계정 미분류(READY +
   * matchedAccount 없음) 행도 장부에 적재한다. account=NULL·category '미분류'·evidenceReview NEEDS_REVIEW·
   * taxClassification REVIEW_REQUIRED로 표시하고, 분개는 생략된다 (journal repost가 미분류를 건너뛰므로 P&L 분개는 분류 후에만
   * 생긴다). - 장부에 같은 fingerprint 거래가 있으면 DUPLICATE로 확정 제외한다 (판정·합치기가 아니라 제외 표시. 판단은 사용자가 한다). - 원천
   * 판정(raw_data primary_disposition)이 대출·계좌이체면 세무구분을 NOT_INCOME/NOT_EXPENSE로 두어 손익 왜곡을 막는다 (장부 목록에는
   * 보인다. 합계에서만 빠진다). - 원천에서 출판사로 확정된 행(business=PUBLISHING_CONFIRMED)은 배치 사업장과 달라도 출판사 장부에 적재한다
   * (사업장 혼합 금지).
   */
  @Transactional
  public EasyImportBatchResponse confirm(Long batchId, List<Long> rowIds) {
    EasyImportBatch batch = requireBatch(batchId);
    requireOrdinaryBatch(batchId);
    return confirmBatch(batch, rowIds);
  }

  /** Internal approval path: no HTTP mapping, no caller-supplied bypass flag. */
  @Transactional(propagation = Propagation.MANDATORY)
  public EasyImportBatchResponse confirmApprovedCollection(
      Long businessId, Long approvalId, AuthenticatedPrincipal principal) {
    collectionAccess.requireAllowedBusiness(principal, businessId);
    var approval =
        collectionApprovals
            .findWithLockByApprovalId(approvalId)
            .orElseThrow(() -> new AccessDeniedException("승인 요청이 없습니다."));
    var run = approval.getRun();
    if (run == null
        || run.getImportBatchId() == null
        || !businessId.equals(approval.getBusiness().getBusinessId())
        || !businessId.equals(run.getBusiness().getBusinessId())
        || !businessId.equals(run.getConnection().getBusiness().getBusinessId())) {
      throw new AccessDeniedException("수집 출처·사업체가 일치하지 않습니다.");
    }
    if (approval.getStatus() != ApprovalStatus.APPROVED
        && approval.getStatus() != ApprovalStatus.COMMIT_FAILED) {
      throw new AccessDeniedException("수집 승인 후에만 확정할 수 있습니다.");
    }
    EasyImportBatch batch = requireBatch(run.getImportBatchId());
    if (!businessId.equals(batch.getBusiness().getBusinessId())
        || !batch.getSourceKind().equals(run.getAdapterCode())
        || !batch.getSourceName().endsWith("#run:" + run.getRunId())) {
      throw new AccessDeniedException("수집 배치 소유 사업체가 일치하지 않습니다.");
    }
    requireCollectionReview(run.getImportBatchId(), approval.getDecisionNote());
    // 운영 원칙상 수집 행은 수집 시점에 이미 중앙 장부에 저장된다. 승인은 검토 확인 기록이며,
    // 남은 READY/PENDING 행(구버전 수집분)이 있을 때만 저장하고 없으면 그대로 반환한다(멱등).
    boolean remaining =
        !rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(
                batch.getBatchId(), EasyImportRowStatus.READY).isEmpty()
            || !rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(
                batch.getBatchId(), EasyImportRowStatus.PENDING).isEmpty();
    if (!remaining) {
      return EasyImportBatchResponse.of(batch, rows(batch.getBatchId()));
    }
    return confirmBatch(batch, null);
  }

  /** 수집 실행 1회분 장부 저장 결과 — 저장/검토/분류/중복 건수. */
  public record CollectedLedgerResult(int saved, int review, int classified, int duplicate,
      int error) {
    public static CollectedLedgerResult empty() {
      return new CollectedLedgerResult(0, 0, 0, 0, 0);
    }

    public CollectedLedgerResult plus(CollectedLedgerResult other) {
      return new CollectedLedgerResult(saved + other.saved, review + other.review,
          classified + other.classified, duplicate + other.duplicate, error + other.error);
    }
  }

  /**
   * 수집 실행 전용 중앙 장부 저장 — 운영 원칙 "수집 → 원본 보존 → 최소 중복 방지 → 장부 저장 → 자동분류 →
   * 예외 검토". 사람 승인을 기다리지 않는다. 계정이 불명확해도 미분류·REVIEW_REQUIRED로 저장하고(분개 생략),
   * 분류된 거래만 기존 분개 엔진(repost)을 탄다.
   *
   * <p>HTTP 매핑이 없고 호출자의 쓰기 트랜잭션 안에서만 실행된다(MANDATORY). 권한은 진입점(관리자 API의
   * 사업체 접근 검증·스케줄러의 연결 소유 사업체)에서 끝나 있어야 하며, 여기서는 출처를 다시 확인한다:
   * run이 RUNNING이고, 배치·run·연결이 같은 사업체이며, 배치가 이 run이 만든 것이어야 한다.
   *
   * @param identityByRowId import 행 id → 수집 원본 dedupe_key (원본 단위 1회 저장 보장)
   * @param originalKeyByRowId import 행 id → 취소 대상 원거래의 dedupe_key
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public CollectedLedgerResult saveCollectedBatch(
      f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun run,
      Long batchId,
      java.util.Map<Long, String> identityByRowId,
      java.util.Map<Long, String> originalKeyByRowId,
      java.util.function.Function<String, Long> txnIdByCollectionKey) {
    if (run == null || run.getRunId() == null) {
      throw new AccessDeniedException("수집 실행이 없습니다.");
    }
    Long runId = run.getRunId();
    EasyImportBatch batch = requireBatch(batchId);
    Long businessId = run.getBusiness().getBusinessId();
    if (run.getStatus() != EasyCollectionRunStatus.RUNNING
        || !businessId.equals(batch.getBusiness().getBusinessId())
        || !businessId.equals(run.getConnection().getBusiness().getBusinessId())
        || !batch.getSourceName().endsWith("#run:" + runId)
        || !batch.getSourceKind().equalsIgnoreCase(run.getAdapterCode())) {
      throw new AccessDeniedException("수집 출처·사업체가 일치하지 않습니다.");
    }
    if (batch.getStatus() != EasyImportStatus.STAGED
        && batch.getStatus() != EasyImportStatus.CONFIRMED) {
      throw new IllegalArgumentException("STAGED 또는 CONFIRMED 상태의 배치만 처리할 수 있습니다.");
    }
    preparePendingRows(batch, null);
    int saved = 0;
    int review = 0;
    int classified = 0;
    int duplicate = 0;
    int error = 0;
    for (EasyImportRow row : rowRepository.findByBatch_BatchIdOrderByRowNoAsc(batchId)) {
      if (row.getStatus() == EasyImportRowStatus.ERROR) {
        error++;
        continue;
      }
      if (row.getStatus() != EasyImportRowStatus.READY || row.getCreatedTxn() != null) {
        continue;
      }
      if (!isPostable(row)) {
        row.markError("확정에 필요한 거래일·수입/지출·금액이 없습니다.");
        error++;
        continue;
      }
      applyHistoricalClassification(businessId, row);
      String identity = identityByRowId == null ? null : identityByRowId.get(row.getRowId());
      String originalKey =
          originalKeyByRowId == null ? null : originalKeyByRowId.get(row.getRowId());
      Long originalTxnId = originalKey == null || txnIdByCollectionKey == null
          ? null : txnIdByCollectionKey.apply(originalKey);
      // 원천 검토 메모(카드대금·정산입금·정책자금·현금영수증 확인 등)는 저장을 막지 않고 검토 표시로 남긴다.
      String note = extractJsonField(row.getRawData(), "review_note");
      if (row.isReversal() && originalTxnId == null) {
        note = appendNote(note, "[검토] 취소 원거래를 아직 찾지 못했습니다. 원거래 수집 후 연결하세요.");
      }
      postRow(batch, row, identity, originalTxnId, note);
      if (row.getStatus() == EasyImportRowStatus.DUPLICATE) {
        duplicate++;
        continue;
      }
      saved++;
      EasyTransaction txn = row.getCreatedTxn();
      if (txn.getAccount() != null && txn.getEvidenceReview() == null) {
        classified++;
      } else {
        review++;
      }
    }
    long imported =
        rowRepository.countByBatch_BatchIdAndStatus(batchId, EasyImportRowStatus.IMPORTED);
    batch.markConfirmed((int) imported);
    return new CollectedLedgerResult(saved, review, classified, duplicate, error);
  }

  /**
   * 과거 장부(이지샵 이관분 포함)에서 같은 사업체·같은 방향·같은 거래처가 한 계정으로만 쓰였다면 그 계정을
   * 우선 적용한다. 원본 계정 매핑(confidence 1.0)은 덮어쓰지 않으며, 과거 계정이 둘 이상으로 갈리면
   * 판단하지 않고 기존 분류(규칙 또는 미분류)를 둔다.
   */
  void applyHistoricalClassification(Long businessId, EasyImportRow row) {
    if (row.getConfidence() != null && row.getConfidence() >= 1.0) {
      return;
    }
    String counterparty = row.getCounterparty() == null ? "" : row.getCounterparty().trim();
    if (counterparty.isEmpty() || row.getDirection() == null) {
      return;
    }
    List<EasyAccount> accounts =
        txnRepository
            .findByBusiness_BusinessIdAndDirectionAndCounterparty(
                businessId, row.getDirection(), counterparty)
            .stream()
            .map(EasyTransaction::getAccount)
            .filter(java.util.Objects::nonNull)
            .filter(EasyAccount::isActive)
            .toList();
    java.util.Set<Long> distinct =
        accounts.stream().map(EasyAccount::getAccountId).collect(java.util.stream.Collectors.toSet());
    if (distinct.size() == 1) {
      row.markReady(accounts.get(0), 0.95);
    }
  }

  /** 수집 원본 → import 행 → 장부 거래 역추적(취소 원거래 연결용). */
  public Optional<Long> createdTxnIdOfRow(Long rowId) {
    if (rowId == null) {
      return Optional.empty();
    }
    return rowRepository.findById(rowId)
        .map(EasyImportRow::getCreatedTxn)
        .map(EasyTransaction::getTxnId);
  }

  private static String appendNote(String memo, String note) {
    if (note == null) {
      return memo;
    }
    String joined = memo == null || memo.isBlank() ? note : memo + " " + note;
    return joined.length() <= 500 ? joined : joined.substring(0, 500);
  }

  /** The financial collector's reviewed hash is persisted in its existing server approval note. */
  void requireCollectionReview(Long batchId, String note) {
    if (batchId == null) throw new AccessDeniedException("수집 배치가 없습니다.");
    var batch = requireBatch(batchId);
    if ("FINANCIAL_FILE".equals(batch.getSourceKind())
        && !EasyImportReviewHash.matches(note, EasyImportBatchResponse.of(batch, rows(batchId))))
      throw new AccessDeniedException("REVIEW_CHANGED_OR_NO_BATCH");
  }

  /** Generic mutation endpoints must never alter a collection's reviewed draft. */
  public void requireOrdinaryBatch(Long batchId) {
    EasyImportBatch batch = requireBatch(batchId);
    if (collectionRuns.existsByImportBatchId(batchId)
        || java.util.Set.of("FILE_CSV", "FINANCIAL_FILE", "CARDSALES")
            .contains(batch.getSourceKind().toUpperCase(java.util.Locale.ROOT))) {
      throw new AccessDeniedException("수집 연결 배치는 수집 승인 전용 경로에서 처리하세요.");
    }
  }

  private EasyImportBatchResponse confirmBatch(EasyImportBatch batch, List<Long> rowIds) {
    Long batchId = batch.getBatchId();
    if (batch.getStatus() != EasyImportStatus.STAGED
        && batch.getStatus() != EasyImportStatus.CONFIRMED) {
      throw new IllegalArgumentException("STAGED 또는 CONFIRMED 상태의 배치만 처리할 수 있습니다.");
    }
    // PENDING 자동 준비 — 분류 대기라는 이유로 거래를 장부에서 숨기지 않는다.
    preparePendingRows(batch, rowIds);
    List<EasyImportRow> targets =
        rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(
            batchId, EasyImportRowStatus.READY);
    if (rowIds != null && !rowIds.isEmpty()) {
      targets = targets.stream().filter(row -> rowIds.contains(row.getRowId())).toList();
    }
    if (targets.isEmpty()) {
      throw new IllegalArgumentException("확정할 행이 없습니다(READY/PENDING 없음).");
    }
    for (EasyImportRow row : targets) {
      // 이미 확정된 행은 건너뜀 (상태/createdTxn 불일치 대비 멱등 가드)
      if (row.getCreatedTxn() != null) {
        continue;
      }
      if (!isPostable(row)) {
        row.markError("확정에 필요한 거래일·수입/지출·금액이 없습니다.");
        continue;
      }
      postRow(batch, row);
    }
    // 요약 카운터는 이번 호출분이 아닌 row-level IMPORTED 실측으로 갱신한다.
    // 동일 트랜잭션 내 확정분이 flush 후 집계에 포함되므로 누적 불변식이 유지된다.
    long imported =
        rowRepository.countByBatch_BatchIdAndStatus(batchId, EasyImportRowStatus.IMPORTED);
    batch.markConfirmed((int) imported);
    return EasyImportBatchResponse.of(batch, rows(batchId));
  }

  /**
   * PENDING 준비 — 직접 적재된 원자료 등 stage()를 거치지 않은 행을 확정 매핑→분류규칙 경로로 READY로 올린다. 확정 불가 행은 ERROR로 남긴다 (사유
   * 보존, 침묵 누락 금지). 확정과 분리 호출도 가능하다.
   */
  @Transactional
  public EasyImportBatchResponse prepare(Long batchId, List<Long> rowIds) {
    EasyImportBatch batch = requireBatch(batchId);
    requireOrdinaryBatch(batchId);
    if (batch.getStatus() != EasyImportStatus.STAGED
        && batch.getStatus() != EasyImportStatus.CONFIRMED) {
      throw new IllegalArgumentException("STAGED 또는 CONFIRMED 상태의 배치만 처리할 수 있습니다.");
    }
    preparePendingRows(batch, rowIds);
    return EasyImportBatchResponse.of(batch, rows(batchId));
  }

  private void preparePendingRows(EasyImportBatch batch, List<Long> rowIds) {
    List<EasyImportRow> pending =
        rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(
            batch.getBatchId(), EasyImportRowStatus.PENDING);
    for (EasyImportRow row : pending) {
      if (rowIds != null
          && !rowIds.isEmpty()
          && (row.getRowId() == null || !rowIds.contains(row.getRowId()))) {
        continue;
      }
      if (!isPostable(row)) {
        row.markError("확정에 필요한 거래일·수입/지출·금액이 없습니다.");
        continue;
      }
      // 확정 매핑 우선 (이지샵 원본 계정명 → 내부 계정), 불확실하면 규칙 → 미분류(review).
      Optional<EasyAccount> mapped = resolveMapping(row.getExternalAccount());
      if (mapped.isPresent()) {
        row.markReady(mapped.get(), 1.0);
        continue;
      }
      Optional<ClassificationMatch> match =
          accountService.classify(
              row.getCounterparty(), row.getDescription(), row.getAmount(), row.getDirection());
      if (match.isPresent()) {
        row.markReady(match.get().account(), match.get().confidence());
      } else {
        row.markReady(null, 0.0);
      }
    }
  }

  private boolean isPostable(EasyImportRow row) {
    return row.getOccurredOn() != null
        && row.getDirection() != null
        && row.getAmount() != null
        && row.getAmount() >= 0;
  }

  /** 단일 행 장부 적재 — 분류 확정 여부와 무관하게 실제 거래를 장부에 남긴다. 중복 의심은 DUPLICATE로 확정 제외한다 (자동합치기 없음). */
  private void postRow(EasyImportBatch batch, EasyImportRow row) {
    postRow(batch, row, null, null, null);
  }

  /**
   * @param collectionIdentity 수집 원본의 dedupe_key. 있으면 거래 fingerprint를 원본 단위로 만든다 —
   *     같은 원본은 한 번만 저장되고, 날짜·금액이 같은 서로 다른 실제 거래는 합쳐지지 않는다.
   * @param originalTxnId 취소·부분취소의 원거래(있으면 연결만 하고 원거래는 수정하지 않는다)
   * @param extraReviewNote 저장은 하되 검토가 필요한 사유(원거래 미확인 등)
   */
  private void postRow(EasyImportBatch batch, EasyImportRow row, String collectionIdentity,
      Long originalTxnId, String extraReviewNote) {
    EasyBusiness business = resolvePostingBusiness(batch, row);
    String fingerprintPay = collectionIdentity == null
        ? fingerprintPayMethod(row)
        : fingerprintPayMethod(row) + "|COLLECTED:" + collectionIdentity;
    String fingerprint =
        EasyFingerprint.of(
            business.getBusinessId(),
            row.getOccurredOn(),
            row.getDirection().name(),
            row.getAmount(),
            row.getCounterparty(),
            row.getDescription(),
            fingerprintPay);
    boolean duplicate =
        txnRepository
            .findByBusiness_BusinessIdAndFingerprintIsNotNull(business.getBusinessId())
            .stream()
            .anyMatch(txn -> fingerprint.equals(txn.getFingerprint()));
    if (duplicate) {
      row.markDuplicate("장부에 같은 거래가 이미 있습니다. (재입력 방지)");
      return;
    }
    EasyAccount matched = row.getMatchedAccount();
    EasyTaxClassification tax = deriveTaxClassification(row);
    boolean needsReview = matched == null || tax != null || extraReviewNote != null;
    String category = matched != null ? matched.getName() : reviewCategory(row, tax);
    EasyTransaction txn =
        EasyTransaction.builder()
            .business(business)
            .occurredOn(row.getOccurredOn())
            .direction(row.getDirection())
            .amount(row.getAmount())
            .category(category)
            .counterparty(row.getCounterparty())
            .description(row.getDescription())
            .payMethod(normalizePayMethod(row))
            .account(matched)
            .supplyAmount(row.getSupplyAmount())
            .vatAmount(row.getVatAmount())
            .evidenceType(row.getEvidenceType())
            .expenseReversal(row.isReversal() ? Boolean.TRUE : null)
            .sourceKind(batch.getSourceKind())
            .sourceRef(batch.getSourceName() + sourceRefSuffix(row))
            .fingerprint(fingerprint)
            .evidenceReview(needsReview ? EasyEvidenceReview.NEEDS_REVIEW : null)
            .taxClassification(tax)
            .memo(appendNote(reviewMemo(batch, row, business, tax, needsReview), extraReviewNote))
            .originalTxnId(originalTxnId)
            .build();
    EasyTransaction saved = txnRepository.save(txn);
    row.markImported(saved);
    journalService.repost(saved);
  }

  /**
   * 장부 귀속 — 원천에서 출판사로 확정된 행은 배치 사업장과 달라도 출판사 장부에 적재한다. 출판사 사업체가 없으면 배치 사업체에 두고 검토 표시한다 (귀속 추측 배정
   * 없음).
   */
  private EasyBusiness resolvePostingBusiness(EasyImportBatch batch, EasyImportRow row) {
    // Collection approval grants access to exactly its business; raw content cannot reroute it.
    if (collectionRuns.existsByImportBatchId(batch.getBatchId())) return batch.getBusiness();
    String marker = extractJsonField(row.getRawData(), "business");
    if (marker != null && "PUBLISHING_CONFIRMED".equalsIgnoreCase(marker.trim())) {
      Optional<EasyBusiness> publishing =
          businessRepository.findAll().stream()
              .filter(business -> "PUBLISHING".equalsIgnoreCase(business.getCode()))
              .filter(EasyBusiness::isActive)
              .findFirst();
      if (publishing.isPresent()) {
        return publishing.get();
      }
    }
    return batch.getBusiness();
  }

  /**
   * 세무구분 가드 — 원천 판정을 존중해 손익 왜곡을 막는다. - LOAN_INFLOW(입금): NOT_INCOME — 대출 입금은 매출이 아니다. -
   * LOAN_REPAYMENT*(출금): NOT_EXPENSE — 원금 상환은 비용이 아니다. - LOAN_INTEREST:
   * EXPENSE_DEDUCTIBLE_CANDIDATE (이자비용 후보, 확정 아님). - INTER_ACCOUNT_TRANSFER: 입금 NOT_INCOME / 출금
   * NOT_EXPENSE. - 계정 미확정 일반 행: REVIEW_REQUIRED (NULL 취급과 같은 의미, 명시 표기). - 계정 확정 + 판정 없음: null (기존
   * 동작 유지). 대출 키워드 fallback은 미분류 행에만 적용하고, 출금의 원금/이자 구분은 단정하지 않는다 (REVIEW_REQUIRED로 사용자 판단에 맡긴다).
   */
  private EasyTaxClassification deriveTaxClassification(EasyImportRow row) {
    String disposition = extractJsonField(row.getRawData(), "primary_disposition");
    if (disposition != null) {
      String normalized = disposition.trim().toUpperCase();
      if ("LOAN_INFLOW".equals(normalized)) {
        return EasyTaxClassification.NOT_INCOME;
      }
      if (normalized.startsWith("LOAN_REPAYMENT") || "LOAN_PRINCIPAL_OUT".equals(normalized)) {
        return EasyTaxClassification.NOT_EXPENSE;
      }
      if ("LOAN_INTEREST".equals(normalized)) {
        return EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE;
      }
      // 카드대금 은행 출금 = 카드미지급금 상환 → 비용 재계상 금지 (카드 사용 시점에 이미 비용).
      if ("CARD_PAYMENT".equals(normalized)) {
        return EasyTaxClassification.NOT_EXPENSE;
      }
      // 카드사 정산입금 = 기존 카드매출의 정산 → 매출 재계상 금지 (카드매출과 대사는 검토에서).
      if ("CARD_SETTLEMENT".equals(normalized)) {
        return EasyTaxClassification.NOT_INCOME;
      }
      // 고용지원금·장려금은 수입금액 산입 여부를 단정하지 않는다.
      if ("GOV_SUBSIDY".equals(normalized)) {
        return EasyTaxClassification.REVIEW_REQUIRED;
      }
      if ("INTER_ACCOUNT_TRANSFER".equals(normalized)) {
        return row.getDirection() == EasyTxnDirection.INCOME
            ? EasyTaxClassification.NOT_INCOME
            : EasyTaxClassification.NOT_EXPENSE;
      }
    }
    if (row.getMatchedAccount() == null
        && mentionsLoan(row.getCounterparty(), row.getDescription())) {
      if (row.getDirection() == EasyTxnDirection.INCOME) {
        return EasyTaxClassification.NOT_INCOME;
      }
      return EasyTaxClassification.REVIEW_REQUIRED;
    }
    if (row.getMatchedAccount() == null) {
      return EasyTaxClassification.REVIEW_REQUIRED;
    }
    return null;
  }

  private boolean mentionsLoan(String counterparty, String description) {
    String combined =
        ((counterparty == null ? "" : counterparty)
                + " "
                + (description == null ? "" : description))
            .toLowerCase();
    return combined.contains("대출") || combined.contains("차입") || combined.contains("loan");
  }

  /** 미분류 표시명 — 장부에서 거래 존재는 보이되 분류 상태가 드러나도록 한다. */
  private String reviewCategory(EasyImportRow row, EasyTaxClassification tax) {
    if (tax == EasyTaxClassification.NOT_INCOME) {
      return "차입금(검토)";
    }
    if (tax == EasyTaxClassification.NOT_EXPENSE) {
      return "차입상환·이체(검토)";
    }
    if (tax == EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE) {
      return "이자(검토)";
    }
    return "미분류";
  }

  /** 검토 메모 — 원천 판정과 귀속을 남긴다. 확정 행은 메모를 만들지 않는다. */
  private String reviewMemo(
      EasyImportBatch batch,
      EasyImportRow row,
      EasyBusiness business,
      EasyTaxClassification tax,
      boolean needsReview) {
    if (!needsReview) {
      return null;
    }
    StringBuilder memo = new StringBuilder("검토필요");
    String disposition = extractJsonField(row.getRawData(), "primary_disposition");
    if (disposition != null && !disposition.isBlank()) {
      memo.append("(원천판정:").append(disposition.trim()).append(")");
    } else if (tax == EasyTaxClassification.REVIEW_REQUIRED) {
      memo.append("(미분류)");
    }
    String marker = extractJsonField(row.getRawData(), "business");
    if (!java.util.Objects.equals(business.getBusinessId(), batch.getBusiness().getBusinessId())) {
      memo.append("[귀속:").append(business.getCode()).append("]");
    } else if (marker != null && "PUBLISHING_CONFIRMED".equalsIgnoreCase(marker.trim())) {
      memo.append("[귀속확인필요]");
    }
    String note = memo.toString();
    return note.length() > 500 ? note.substring(0, 500) : note;
  }

  /**
   * 결제수단 정규화 — 은행 수집대행이 시각("15:21:07")을 결제수단에 넣은 행은 raw JSON의 은행/계좌로 복원한다. 정보 없으면 비운다 (가짜 값 금지).
   */
  private String normalizePayMethod(EasyImportRow row) {
    String payMethod = row.getPayMethod() == null ? "" : row.getPayMethod();
    if (payMethod.matches("\\d{1,2}:\\d{2}(:\\d{2})?")) {
      String bank = extractJsonField(row.getRawData(), "bank");
      String acct = extractJsonField(row.getRawData(), "acct");
      if ((bank != null && !bank.isBlank()) || (acct != null && !acct.isBlank())) {
        String combined =
            ("은행 " + (bank == null ? "" : bank.trim()) + " " + (acct == null ? "" : acct.trim()))
                .trim()
                .replaceAll("\\s+", " ");
        return combined.length() > 30 ? combined.substring(0, 30) : combined;
      }
      return "";
    }
    return payMethod;
  }

  /**
   * fingerprint 입력용 결제수단 — 같은 날·같은 금액·같은 거래처의 서로 다른 시각 거래를 중복 오판하지 않도록 원천 시각을 함께 쓴다. 동일 원자료 재입력에는
   * 같은 값이 나와 재입력 방지가 유지된다.
   */
  private String fingerprintPayMethod(EasyImportRow row) {
    String payMethod = row.getPayMethod() == null ? "" : row.getPayMethod();
    String time = extractJsonField(row.getRawData(), "time");
    if (time != null && !time.isBlank()) {
      return payMethod + "|" + time.trim();
    }
    return payMethod;
  }

  /**
   * 원천 JSON 패러미터 추출 — 은행 수집대행 raw_data(JSON 문자열)에서 큰따옴표 문자열 값 하나를 읽는다. 파서 의존성 없이 동작하며, 형식이 다르면
   * null을 반환한다 (추측 없음).
   */
  static String extractJsonField(String rawData, String field) {
    if (rawData == null || field == null || field.isBlank()) {
      return null;
    }
    String key = "\"" + field + "\"";
    int keyAt = rawData.indexOf(key);
    if (keyAt < 0) {
      return null;
    }
    int colonAt = rawData.indexOf(':', keyAt + key.length());
    if (colonAt < 0) {
      return null;
    }
    int openAt = rawData.indexOf('"', colonAt + 1);
    if (openAt < 0) {
      return null;
    }
    int closeAt = rawData.indexOf('"', openAt + 1);
    if (closeAt < 0) {
      return null;
    }
    return rawData.substring(openAt + 1, closeAt);
  }

  /**
   * 확정 거래의 원본 추적 표기 — staging 시 rawData에 기록된 CSV 원본 행번호([srow:N])가 있으면 #orig-row-N, 없으면 기존
   * #row-N(배치 내 순서)을 유지한다. 기존 SALES 102건의 sourceRef는 변경하지 않는다.
   */
  private String sourceRefSuffix(EasyImportRow row) {
    Integer sourceRowNo = parseSourceRowNo(row.getRawData());
    if (sourceRowNo != null) {
      return "#orig-row-" + sourceRowNo;
    }
    return "#row-" + row.getRowNo();
  }

  private static final Pattern SOURCE_ROW_PATTERN = Pattern.compile("\\[srow:(\\d+)\\]");

  static Integer parseSourceRowNo(String rawData) {
    if (rawData == null) {
      return null;
    }
    Matcher matcher = SOURCE_ROW_PATTERN.matcher(rawData);
    if (!matcher.find()) {
      return null;
    }
    try {
      return Integer.parseInt(matcher.group(1));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * 대상 READY 행의 계정 매핑을 재계산한다. - batchId에 속한 rowIds만 허용 - READY 상태만 처리
   * (CONFIRMED/DUPLICATE/ERROR/IMPORTED 제외) - 이미 생성된 transaction은 절대 수정하지 않음 - 새 transaction 생성하지
   * 않음 - confirm을 수행하지 않음 - staging 시 사용한 공식 mapping resolution 로직 재사용: resolveMapping() ->
   * accountService.classify()
   */
  @Transactional
  public EasyImportBatchResponse reclassify(Long batchId, List<Long> rowIds) {
    EasyImportBatch batch = requireBatch(batchId);
    requireOrdinaryBatch(batchId);
    if (batch.getStatus() != EasyImportStatus.STAGED
        && batch.getStatus() != EasyImportStatus.CONFIRMED) {
      throw new IllegalArgumentException("STAGED 또는 CONFIRMED 상태의 배치만 처리할 수 있습니다.");
    }
    if (rowIds == null || rowIds.isEmpty()) {
      throw new IllegalArgumentException("재분류할 rowId를 지정해 주세요.");
    }

    List<EasyImportRow> targets = rowRepository.findByBatch_BatchIdAndRowIdIn(batchId, rowIds);
    if (targets.size() != rowIds.size()) {
      throw new IllegalArgumentException("일부 rowId가 이 배치에 속하지 않거나 존재하지 않습니다.");
    }

    int reclassified = 0;
    for (EasyImportRow row : targets) {
      if (row.getStatus() != EasyImportRowStatus.READY) {
        continue;
      }
      if (row.getCreatedTxn() != null) {
        continue;
      }

      Optional<EasyAccount> mapped = resolveMapping(row.getExternalAccount());
      if (mapped.isPresent()) {
        row.markReady(mapped.get(), 1.0);
        reclassified++;
        continue;
      }
      Optional<ClassificationMatch> match =
          accountService.classify(
              row.getCounterparty(), row.getDescription(), row.getAmount(), row.getDirection());
      if (match.isPresent()) {
        row.markReady(match.get().account(), match.get().confidence());
      } else {
        row.markReady(null, 0.0);
      }
      reclassified++;
    }

    return EasyImportBatchResponse.of(batch, rows(batchId));
  }

  /** 폐기 — STAGED 배치만 폐기한다. 이미 장부로 간 행은 되돌리지 않는다. */
  @Transactional
  public void discard(Long batchId) {
    EasyImportBatch batch = requireBatch(batchId);
    requireOrdinaryBatch(batchId);
    requireStaged(batch);
    batch.markDiscarded();
  }

  private List<EasyImportRowResponse> rows(Long batchId) {
    return rowRepository.findByBatch_BatchIdOrderByRowNoAsc(batchId).stream()
        .map(EasyImportRowResponse::from)
        .toList();
  }

  private EasyImportBatch requireBatch(Long batchId) {
    return batchRepository
        .findById(batchId)
        .orElseThrow(() -> new IllegalArgumentException("배치를 찾을 수 없습니다: " + batchId));
  }

  private void requireStaged(EasyImportBatch batch) {
    if (batch.getStatus() != EasyImportStatus.STAGED) {
      throw new IllegalArgumentException("STAGED 상태의 배치만 처리할 수 있습니다.");
    }
  }

  private EasyBusiness requireActiveBusiness(Long businessId) {
    if (businessId == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    EasyBusiness business =
        businessRepository
            .findById(businessId)
            .orElseThrow(() -> new IllegalArgumentException("사업체를 찾을 수 없습니다: " + businessId));
    if (!business.isActive()) {
      throw new IllegalArgumentException("비활성 사업체에는 등록할 수 없습니다.");
    }
    return business;
  }

  private LocalDate parseDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (RuntimeException e) {
      return null;
    }
  }

  private EasyTxnDirection parseDirection(String direction) {
    if (direction == null || direction.isBlank()) {
      return null;
    }
    try {
      return EasyTxnDirection.valueOf(direction.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private EasyEvidenceType parseEvidence(String evidenceType) {
    if (evidenceType == null || evidenceType.isBlank()) {
      return null;
    }
    try {
      return EasyEvidenceType.parse(evidenceType);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private boolean isValidEvidence(String evidenceType) {
    if (evidenceType == null || evidenceType.isBlank()) {
      return true;
    }
    try {
      EasyEvidenceType.parse(evidenceType);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
