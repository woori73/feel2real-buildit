package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest.EasyImportRowInput;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportRowResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyShopImportRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyShopImportRequest.EasyShopRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyEvidenceType;
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
 * EasyShop 실데이터 importer — source_kind=EASYSHOP(매출/비용),
 * EASYSHOP_FUNDS(입출금 자금 이동).
 *
 * - 원본 row는 staging에 보존하고 source_ref/fingerprint로 추적한다.
 * - 입출금은 매출/비용과 별개 원천자료로 보존하며 P&L에 합산하지 않는다
 *   (이중 계상 방지 — 카드매출과 카드값입금은 별개로 둔다).
 * - 음수 비용(환불)은 누락·절댓값 변환 없이 역분개(INCOME+플래그)로 보존한다.
 * - 사업장 분리·연도 혼합·재입력 중복 방지를 staging 단계에서 강제한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyShopImportService {

  static final String SOURCE_KIND = "EASYSHOP";
  static final String SOURCE_KIND_FUNDS = "EASYSHOP_FUNDS";
  static final String SALES_ACCOUNT_KEY = "EASYSHOP:매출";
  static final int MAX_ROWS = 2000;

  private final EasyBusinessRepository businessRepository;
  private final EasyTransactionRepository txnRepository;
  private final EasyImportRowRepository rowRepository;
  private final EasyImportService importService;

  /** EasyShop 파일 staging — (사업장, 출처)별로 분리된 배치 목록을 반환한다. */
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

    // 정규화 + (사업장, 출처) 그룹핑 — 코드 없으면 요청 사업체, 둘 다 없으면 전체 거부.
    // staging 전에 검증한다 (일부만 적재된 채 실패하지 않도록).
    Map<GroupKey, List<EasyImportRowInput>> grouped = new LinkedHashMap<>();
    List<Integer> homeless = new ArrayList<>();
    int rowNo = 0;
    for (EasyShopRow row : request.rows()) {
      rowNo++;
      Long businessId = resolveBusinessId(row, request.businessId());
      if (businessId == null) {
        homeless.add(rowNo);
        continue;
      }
      for (Normalized normalized : normalize(row, rowNo)) {
        grouped.computeIfAbsent(new GroupKey(businessId, normalized.sourceKind()),
            key -> new ArrayList<>()).add(normalized.input());
      }
    }
    if (!homeless.isEmpty()) {
      throw new IllegalArgumentException(
          "사업체를 확정할 수 없는 행이 " + homeless.size() + "건 있습니다. "
              + "사업체를 선택하거나 사업체 코드를 입력해 주세요. (첫 행: "
              + homeless.get(0) + ")");
    }
    if (grouped.isEmpty()) {
      throw new IllegalArgumentException(
          "사업체를 확정할 수 없습니다. 사업체를 선택하거나 사업체 코드를 입력해 주세요.");
    }

    List<EasyImportBatchResponse> staged = new ArrayList<>();
    for (Map.Entry<GroupKey, List<EasyImportRowInput>> group : grouped.entrySet()) {
      EasyBusiness business = requireActiveBusiness(group.getKey().businessId());
      EasyImportBatchResponse batch = importService.stage(new EasyImportBatchRequest(
          business.getBusinessId(), sourceName, group.getKey().sourceKind(),
          group.getValue()));
      markLedgerDuplicates(batch);
      staged.add(importService.getBatch(batch.batchId()));
    }
    return staged;
  }

  private record GroupKey(Long businessId, String sourceKind) {
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

  /**
   * 사업장 확정 — 명시 코드 우선, 없으면 사업자번호·상호명 완전일치, 없으면 요청 사업체.
   * 추측 배정 없음 (일치 없으면 null → 전체 거부).
   */
  Long resolveBusinessId(EasyShopRow row, Long defaultBusinessId) {
    if (row != null) {
      if (hasText(row.businessCode())) {
        Long matched = matchBusiness(row.businessCode().trim(), true);
        if (matched != null) {
          return matched;
        }
        return null;
      }
      if (hasText(row.bizRegNo()) || hasText(row.business())) {
        String key = hasText(row.bizRegNo()) ? row.bizRegNo().trim() : row.business().trim();
        Long matched = matchBusiness(key, false);
        if (matched != null) {
          return matched;
        }
        return null;
      }
    }
    return defaultBusinessId;
  }

  private Long matchBusiness(String key, boolean codeOnly) {
    for (EasyBusiness business : businessRepository.findAll()) {
      if (!business.isActive()) {
        continue;
      }
      if (business.getCode() != null && business.getCode().equalsIgnoreCase(key)) {
        return business.getBusinessId();
      }
      if (!codeOnly && business.getName() != null && business.getName().equals(key.trim())) {
        return business.getBusinessId();
      }
    }
    return null;
  }

  private EasyBusiness requireActiveBusiness(Long businessId) {
    EasyBusiness business = businessRepository.findById(businessId)
        .orElseThrow(() -> new IllegalArgumentException("사업체를 찾을 수 없습니다: " + businessId));
    if (!business.isActive()) {
      throw new IllegalArgumentException("비활성 사업체에는 등록할 수 없습니다.");
    }
    return business;
  }

  /** 정규화 결과 — 검증 실패 시 error에 사유를 담는다 (행 보존, ERROR 적재). */
  record Normalized(
      LocalDate occurredOn,
      EasyTxnDirection direction,
      Long amount,
      String counterparty,
      String description,
      String payMethod,
      String accountName,
      Long supplyAmount,
      Long vatAmount,
      EasyEvidenceType evidenceType,
      String raw,
      String sourceKind,
      Boolean reversal,
      String error) {
    EasyImportRowInput input() {
      return new EasyImportRowInput(
          occurredOn == null ? null : occurredOn.toString(),
          direction == null ? null : direction.name(),
          amount,
          counterparty,
          description,
          payMethod,
          supplyAmount,
          vatAmount,
          evidenceType == null ? null : evidenceType.name(),
          raw,
          accountName,
          reversal);
    }
  }

  List<Normalized> normalize(EasyShopRow row, int rowNo) {
    if (row == null || !hasText(row.ledgerType())) {
      return List.of(fail(row, rowNo, "원장 종류(ledger_type)가 없습니다."));
    }
    String ledgerType = row.ledgerType().trim().toLowerCase();
    return switch (ledgerType) {
      case "sales" -> List.of(normalizeSales(row, rowNo));
      case "expense" -> List.of(normalizeExpense(row, rowNo));
      case "deposit_withdrawal" -> normalizeFunds(row, rowNo);
      default -> List.of(fail(row, rowNo, "원장 종류가 올바르지 않습니다: " + row.ledgerType()));
    };
  }

  private Normalized normalizeSales(EasyShopRow row, int rowNo) {
    LocalDate date = parseDate(row.date());
    if (date == null) {
      return fail(row, rowNo, "날짜가 올바르지 않습니다: " + row.date());
    }
    Long amount = parseSigned(row.totalAmount());
    if (amount == null || amount < 0) {
      return fail(row, rowNo, "매출 합계가 올바르지 않습니다: " + row.totalAmount());
    }
    return new Normalized(date, EasyTxnDirection.INCOME, amount,
        trim(row.vendor()), trim(row.itemDesc()), trim(row.receiptMethod()),
        SALES_ACCOUNT_KEY,
        parseSigned(row.supplyAmount()), parseSigned(row.vat()),
        evidenceOfReceipt(trim(row.receiptMethod())),
        preservedRaw(row), SOURCE_KIND, null, null);
  }

  private Normalized normalizeExpense(EasyShopRow row, int rowNo) {
    LocalDate date = parseDate(row.date());
    if (date == null) {
      return fail(row, rowNo, "날짜가 올바르지 않습니다: " + row.date());
    }
    Long signed = parseSigned(row.totalAmount());
    if (signed == null) {
      return fail(row, rowNo, "비용 합계가 올바르지 않습니다: " + row.totalAmount());
    }
    // 음수 비용(환불) — 누락·절댓값 변환 없이 역분개로 보존한다.
    boolean reversal = signed < 0;
    EasyTxnDirection direction = reversal ? EasyTxnDirection.INCOME : EasyTxnDirection.EXPENSE;
    return new Normalized(date, direction, Math.abs(signed),
        trim(row.vendor()), trim(row.itemDesc()), trim(row.payMethod()),
        trimToNull(row.accountCategory()),
        abs(parseSigned(row.supplyAmount())), abs(parseSigned(row.vat())),
        evidenceOfPay(trim(row.payMethod())),
        preservedRaw(row) + (reversal ? " [원본합계:" + row.totalAmount().trim() + "]" : ""),
        SOURCE_KIND, reversal ? Boolean.TRUE : null, null);
  }

  private List<Normalized> normalizeFunds(EasyShopRow row, int rowNo) {
    LocalDate date = parseDate(row.date());
    if (date == null) {
      return List.of(fail(row, rowNo, "날짜가 올바르지 않습니다: " + row.date()));
    }
    Long deposit = parseSigned(row.depositAmount());
    Long withdrawal = parseSigned(row.withdrawalAmount());
    if ((deposit == null || deposit == 0) && (withdrawal == null || withdrawal == 0)) {
      return List.of(fail(row, rowNo, "입금·출금이 모두 0원입니다."));
    }
    String who = trim(row.vendor());
    String what = trim(row.memo())
        + (hasText(row.itemType()) ? " [" + row.itemType().trim() + "]" : "");
    String via = trim(row.account());
    String raw = preservedRaw(row)
        + (hasText(row.fee()) && !"0".equals(row.fee().trim())
            ? " [수수료:" + row.fee().trim() + "]" : "");
    List<Normalized> outputs = new ArrayList<>();
    // 음수 면(예: 출금 -330,000원)도 누락 없이 반대 방향+역분개로 보존한다.
    // 자금 합계는 원래 면 기준으로 네팅한다 (EasyReportService.fundsTotals).
    if (deposit != null && deposit != 0) {
      boolean reversal = deposit < 0;
      outputs.add(new Normalized(date,
          reversal ? EasyTxnDirection.EXPENSE : EasyTxnDirection.INCOME, Math.abs(deposit),
          who, what, via, null, null, null, null,
          raw + (reversal ? " [원본입금:" + row.depositAmount().trim() + "]" : ""),
          SOURCE_KIND_FUNDS, reversal ? Boolean.TRUE : null, null));
    }
    if (withdrawal != null && withdrawal != 0) {
      boolean reversal = withdrawal < 0;
      outputs.add(new Normalized(date,
          reversal ? EasyTxnDirection.INCOME : EasyTxnDirection.EXPENSE, Math.abs(withdrawal),
          who, what, via, null, null, null, null,
          raw + (reversal ? " [원본출금:" + row.withdrawalAmount().trim() + "]" : ""),
          SOURCE_KIND_FUNDS, reversal ? Boolean.TRUE : null, null));
    }
    return outputs;
  }

  private Normalized fail(EasyShopRow row, int rowNo, String error) {
    return new Normalized(null, null, null, "", "", "", null, null, null, null,
        row == null ? null : row.raw(), SOURCE_KIND, null, rowNo + "행: " + error);
  }

  private String preservedRaw(EasyShopRow row) {
    StringBuilder raw = new StringBuilder();
    // CSV provenance용 원본 행번호 — 없으면 생략하고 기존 동작을 유지한다.
    // 헤더를 제외한 첫 data row = 1. confirm()이 [srow:N]을 보고
    // sourceRef를 #orig-row-N으로 만든다 (DB 변경 없음).
    if (row != null && row.sourceRowNo() != null) {
      if (row.sourceRowNo() <= 0) {
        throw new IllegalArgumentException(
            "원본 행번호(sourceRowNo)는 1 이상이어야 합니다: " + row.sourceRowNo());
      }
      raw.append("[srow:").append(row.sourceRowNo()).append("]");
    }
    if (hasText(row.refNo())) {
      raw.append("[ref:").append(row.refNo().trim()).append("]");
    }
    if (hasText(row.raw())) {
      if (raw.length() > 0) {
        raw.append(" ");
      }
      raw.append(row.raw());
    }
    if (hasText(row.menuPath())) {
      if (raw.length() > 0) {
        raw.append(" ");
      }
      raw.append("[menu:").append(row.menuPath().trim()).append("]");
    }
    return raw.toString();
  }

  private EasyEvidenceType evidenceOfReceipt(String receiptMethod) {
    if (receiptMethod == null) {
      return null;
    }
    if (receiptMethod.contains("카드")) {
      return EasyEvidenceType.CREDIT_CARD;
    }
    if (receiptMethod.contains("현금영수증")) {
      return EasyEvidenceType.CASH_RECEIPT;
    }
    return null;
  }

  private EasyEvidenceType evidenceOfPay(String payMethod) {
    if (payMethod == null) {
      return null;
    }
    if (payMethod.startsWith("카드")) {
      return EasyEvidenceType.CREDIT_CARD;
    }
    return null;
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

  /** 부호 보존 파싱 — 쉼표·원 제거, 선행 - 유지. */
  Long parseSigned(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String cleaned = value.trim().replaceAll("[^0-9-]", "");
    if (cleaned.isBlank() || cleaned.equals("-")) {
      return null;
    }
    try {
      return Long.parseLong(cleaned);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private Long abs(Long value) {
    return value == null ? null : Math.abs(value);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String trim(String value) {
    return value == null ? "" : value.trim();
  }

  private static String trimToNull(String value) {
    return hasText(value) ? value.trim() : null;
  }
}
