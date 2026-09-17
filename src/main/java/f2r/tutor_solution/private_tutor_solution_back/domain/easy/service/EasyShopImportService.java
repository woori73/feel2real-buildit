package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest.EasyImportRowInput;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportRowResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyShopImportRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyShopImportRequest.EasyShopRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRowStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportRowRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EasyShop 실데이터 importer — source_kind=EASYSHOP.
 * 원본 row는 staging에 보존하고 source_ref/fingerprint로 추적한다.
 * 사업장 분리·연도 혼합·재입력 중복 방지를 staging 단계에서 강제한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyShopImportService {

  static final String SOURCE_KIND = "EASYSHOP";
  static final int MAX_ROWS = 2000;

  private final EasyBusinessRepository businessRepository;
  private final EasyTransactionRepository txnRepository;
  private final EasyImportRowRepository rowRepository;
  private final EasyImportService importService;

  /** EasyShop 파일 staging — 사업장별로 분리된 배치 목록을 반환한다. */
  @Transactional
  public List<EasyImportBatchResponse> stage(EasyShopImportRequest request) {
    if (request == null || request.rows() == null || request.rows().isEmpty()) {
      throw new IllegalArgumentException("가져올 행이 없습니다.");
    }
    if (request.rows().size() > MAX_ROWS) {
      throw new IllegalArgumentException("한 번에 2000행까지 가져올 수 있습니다.");
    }
    String sourceName = request.sourceName() == null || request.sourceName().isBlank()
        ? "easyshop.csv" : request.sourceName().trim();

    // 사업장별 그룹핑 — 코드 없으면 요청 사업체, 둘 다 없으면 전체 거부.
    // staging 전에 검증한다 (일부만 적재된 채 실패하지 않도록).
    Map<Long, List<IndexedInput>> grouped = new LinkedHashMap<>();
    List<IndexedInput> homeless = new ArrayList<>();
    int rowNo = 0;
    for (EasyShopRow row : request.rows()) {
      rowNo++;
      Normalized normalized = normalize(row, rowNo);
      Long businessId = resolveBusinessId(row.businessCode(), request.businessId());
      if (businessId == null) {
        homeless.add(new IndexedInput(rowNo, normalized, null));
      } else {
        grouped.computeIfAbsent(businessId, key -> new ArrayList<>())
            .add(new IndexedInput(rowNo, normalized, businessId));
      }
    }
    if (!homeless.isEmpty()) {
      throw new IllegalArgumentException(
          "사업체를 확정할 수 없는 행이 " + homeless.size() + "건 있습니다. "
              + "사업체를 선택하거나 사업체 코드를 입력해 주세요. (첫 행: "
              + homeless.get(0).rowNo() + ")");
    }
    if (grouped.isEmpty()) {
      throw new IllegalArgumentException(
          "사업체를 확정할 수 없습니다. 사업체를 선택하거나 사업체 코드를 입력해 주세요.");
    }

    List<EasyImportBatchResponse> staged = new ArrayList<>();
    for (Map.Entry<Long, List<IndexedInput>> group : grouped.entrySet()) {
      EasyBusiness business = requireActiveBusiness(group.getKey());
      List<EasyImportRowInput> inputs = new ArrayList<>();
      for (IndexedInput indexed : group.getValue()) {
        inputs.add(indexed.normalized().input());
      }
      EasyImportBatchResponse batch = importService.stage(new EasyImportBatchRequest(
          business.getBusinessId(), sourceName, SOURCE_KIND, inputs));
      markLedgerDuplicates(batch);
      staged.add(importService.getBatch(batch.batchId()));
    }
    return staged;
  }

  /** 장부 중복 검사 — 같은 사업장·fingerprint 거래가 있으면 확정 제외한다. */
  private void markLedgerDuplicates(EasyImportBatchResponse batch) {
    for (EasyImportRowResponse response : batch.rows()) {
      if (!EasyImportRowStatus.READY.name().equals(response.status())) {
        continue;
      }
      EasyImportRow row = rowRepository.findById(response.rowId()).orElse(null);
      if (row == null || row.getOccurredOn() == null || row.getDirection() == null
          || row.getAmount() == null) {
        continue;
      }
      String fingerprint = EasyFingerprint.of(
          batch.businessId(), row.getOccurredOn(), row.getDirection().name(), row.getAmount(),
          row.getCounterparty(), row.getDescription(), row.getPayMethod());
      boolean duplicate = txnRepository
          .findByBusiness_BusinessIdAndFingerprintIsNotNull(batch.businessId()).stream()
          .anyMatch(txn -> fingerprint.equals(txn.getFingerprint()));
      if (duplicate) {
        row.markDuplicate("장부에 같은 거래가 이미 있습니다. (재입력 방지)");
      }
    }
  }

  private Long resolveBusinessId(String businessCode, Long defaultBusinessId) {
    if (businessCode != null && !businessCode.isBlank()) {
      String code = businessCode.trim().toUpperCase();
      return businessRepository.findAll().stream()
          .filter(business -> business.getCode().equalsIgnoreCase(code)
              && business.isActive())
          .map(EasyBusiness::getBusinessId)
          .findFirst()
          .orElse(null);
    }
    return defaultBusinessId;
  }

  private EasyBusiness requireActiveBusiness(Long businessId) {
    EasyBusiness business = businessRepository.findById(businessId)
        .orElseThrow(() -> new IllegalArgumentException("사업체를 찾을 수 없습니다: " + businessId));
    if (!business.isActive()) {
      throw new IllegalArgumentException("비활성 사업체에는 등록할 수 없습니다.");
    }
    return business;
  }

  private record IndexedInput(int rowNo, Normalized normalized, Long businessId) {
  }

  /** 정규화 결과 — 검증 실패 시 error에 사유를 담는다. */
  record Normalized(
      LocalDate occurredOn,
      EasyTxnDirection direction,
      Long amount,
      String counterparty,
      String description,
      String payMethod,
      String accountName,
      String refNo,
      String raw,
      String error) {
    EasyImportRowInput input() {
      // 이지샵 거래번호는 원본 보존(raw_data)에 함께 남긴다.
      String preserved = raw == null ? "" : raw;
      if (refNo != null && !refNo.isBlank()) {
        preserved = (preserved.isBlank() ? "" : preserved + " ") + "[ref:" + refNo.trim() + "]";
      }
      return new EasyImportRowInput(
          occurredOn == null ? null : occurredOn.toString(),
          direction == null ? null : direction.name(),
          amount,
          counterparty,
          description,
          payMethod,
          null,
          null,
          null,
          preserved.isBlank() ? null : preserved,
          accountName);
    }
  }

  Normalized normalize(EasyShopRow row, int rowNo) {
    if (row == null) {
      return new Normalized(null, null, null, "", "", "", null, null, null,
          rowNo + "행: 빈 행입니다.");
    }
    LocalDate date = parseDate(row.date());
    if (date == null) {
      return fail(row, "날짜(YYYY-MM-DD)가 올바르지 않습니다: " + row.date());
    }
    EasyTxnDirection direction = parseDirection(row.direction());
    if (direction == null) {
      return fail(row, "수입/지출 구분이 올바르지 않습니다: " + row.direction());
    }
    Long amount = parseAmount(row.amount());
    if (amount == null || amount < 0) {
      return fail(row, "금액은 0 이상이어야 합니다: " + row.amount());
    }
    return new Normalized(date, direction, amount,
        row.counterparty() == null ? "" : row.counterparty().trim(),
        row.description() == null ? "" : row.description().trim(),
        row.payMethod() == null ? "" : row.payMethod().trim(),
        row.accountName() == null || row.accountName().isBlank() ? null
            : row.accountName().trim(),
        row.refNo(),
        row.raw(),
        null);
  }

  private Normalized fail(EasyShopRow row, String error) {
    return new Normalized(null, null, null,
        row.counterparty() == null ? "" : row.counterparty(),
        row.description() == null ? "" : row.description(),
        row.payMethod() == null ? "" : row.payMethod(),
        row.accountName(), row.refNo(), row.raw(), error);
  }

  LocalDate parseDate(String value) {
    if (value == null) {
      return null;
    }
    String digits = value.trim().replaceAll("[^0-9]", "");
    try {
      if (digits.length() == 8) {
        return LocalDate.of(
            Integer.parseInt(digits.substring(0, 4)),
            Integer.parseInt(digits.substring(4, 6)),
            Integer.parseInt(digits.substring(6, 8)));
      }
    } catch (RuntimeException e) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (RuntimeException e) {
      return null;
    }
  }

  EasyTxnDirection parseDirection(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toUpperCase();
    if (normalized.contains("INCOME")) {
      return EasyTxnDirection.INCOME;
    }
    if (normalized.contains("EXPENSE")) {
      return EasyTxnDirection.EXPENSE;
    }
    for (String income : List.of("수입", "입금", "매출", "수익")) {
      if (value.contains(income)) {
        return EasyTxnDirection.INCOME;
      }
    }
    for (String expense : List.of("지출", "출금", "비용", "매입")) {
      if (value.contains(expense)) {
        return EasyTxnDirection.EXPENSE;
      }
    }
    return null;
  }

  Long parseAmount(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String digits = value.trim().replaceAll("[^0-9-]", "");
    if (digits.isBlank() || digits.equals("-")) {
      return null;
    }
    try {
      return Long.parseLong(digits);
    } catch (NumberFormatException e) {
      return null;
    }
  }

}
