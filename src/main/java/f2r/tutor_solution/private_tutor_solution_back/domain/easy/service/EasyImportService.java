package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest.EasyImportRowInput;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportRowResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyEvidenceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRowStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountMappingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy import staging — 원본 행은 staging에만 두고, 검증·확정한 행만 장부로 옮긴다.
 * 실제 이지샵 데이터 import는 Work 자료 도착 후 별도 작업에서 수행한다.
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
    EasyImportBatch batch = batchRepository.save(EasyImportBatch.builder()
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
    } else if ((supplyAmount != null && supplyAmount < 0)
        || (vatAmount != null && vatAmount < 0)) {
      error = "공급가액/부가세는 0 이상이어야 합니다.";
    } else if (!isValidEvidence(input.evidenceType())) {
      error = "증빙 유형이 올바르지 않습니다.";
    }

    EasyImportRow row = EasyImportRow.builder()
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
    Optional<ClassificationMatch> match = accountService.classify(
        saved.getCounterparty(), saved.getDescription(), amount, direction);
    if (match.isPresent()) {
      saved.markReady(match.get().account(), match.get().confidence());
    } else {
      saved.markReady(null, 0.0);
    }
    return saved;
  }

  /**
   * 외부 계정명 → 내부 계정 확정 매핑. 공식 세무 계정코드를 추측하지 않으며,
   * 매핑 테이블에 없는 원본 계정명은 empty로 둔다 (review 대상).
   */
  public Optional<EasyAccount> resolveMapping(String externalAccount) {
    if (externalAccount == null || externalAccount.isBlank()) {
      return Optional.empty();
    }
    return mappingRepository.findFirstByExternalCode(externalAccount.trim())
        .map(mapping -> mapping.getAccount())
        .filter(EasyAccount::isActive);
  }

  /**
   * 확정 — READY 행만 장부 거래로 변환한다. 이미 확정/폐기된 배치는 거부한다.
   * rowIds 생략 시 READY 전체를 확정한다.
   */
  @Transactional
  public EasyImportBatchResponse confirm(Long batchId, List<Long> rowIds) {
    EasyImportBatch batch = requireBatch(batchId);
    requireStaged(batch);
    List<EasyImportRow> targets = rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(
        batchId, EasyImportRowStatus.READY);
    if (rowIds != null && !rowIds.isEmpty()) {
      targets = targets.stream().filter(row -> rowIds.contains(row.getRowId())).toList();
    }
    if (targets.isEmpty()) {
      throw new IllegalArgumentException("확정할 READY 행이 없습니다.");
    }
    int confirmed = 0;
    for (EasyImportRow row : targets) {
      String category = row.getMatchedAccount() == null
          ? "기타" : row.getMatchedAccount().getName();
      EasyTransaction txn = EasyTransaction.builder()
          .business(batch.getBusiness())
          .occurredOn(row.getOccurredOn())
          .direction(row.getDirection())
          .amount(row.getAmount())
          .category(category)
          .counterparty(row.getCounterparty())
          .description(row.getDescription())
          .payMethod(row.getPayMethod())
          .account(row.getMatchedAccount())
          .supplyAmount(row.getSupplyAmount())
          .vatAmount(row.getVatAmount())
          .evidenceType(row.getEvidenceType())
          .expenseReversal(row.isReversal() ? Boolean.TRUE : null)
          .sourceKind(batch.getSourceKind())
          .sourceRef(batch.getSourceName() + sourceRefSuffix(row))
          .fingerprint(EasyFingerprint.of(
              batch.getBusiness().getBusinessId(),
              row.getOccurredOn(), row.getDirection().name(), row.getAmount(),
              row.getCounterparty(), row.getDescription(), row.getPayMethod()))
          .build();
      EasyTransaction saved = txnRepository.save(txn);
      row.markImported(saved);
      journalService.repost(saved);
      confirmed++;
    }
    batch.markConfirmed(confirmed);
    return EasyImportBatchResponse.of(batch, rows(batchId));
  }

  /**
   * 확정 거래의 원본 추적 표기 — staging 시 rawData에 기록된
   * CSV 원본 행번호([srow:N])가 있으면 #orig-row-N,
   * 없으면 기존 #row-N(배치 내 순서)을 유지한다.
   * 기존 SALES 102건의 sourceRef는 변경하지 않는다.
   */
  private String sourceRefSuffix(EasyImportRow row) {
    Integer sourceRowNo = parseSourceRowNo(row.getRawData());
    if (sourceRowNo != null) {
      return "#orig-row-" + sourceRowNo;
    }
    return "#row-" + row.getRowNo();
  }

  private static final Pattern SOURCE_ROW_PATTERN =
      Pattern.compile("\\[srow:(\\d+)\\]");

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

  /** 폐기 — STAGED 배치만 폐기한다. 이미 장부로 간 행은 되돌리지 않는다. */
  @Transactional
  public void discard(Long batchId) {
    EasyImportBatch batch = requireBatch(batchId);
    requireStaged(batch);
    batch.markDiscarded();
  }

  private List<EasyImportRowResponse> rows(Long batchId) {
    return rowRepository.findByBatch_BatchIdOrderByRowNoAsc(batchId).stream()
        .map(EasyImportRowResponse::from)
        .toList();
  }

  private EasyImportBatch requireBatch(Long batchId) {
    return batchRepository.findById(batchId)
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
    EasyBusiness business = businessRepository.findById(businessId)
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
