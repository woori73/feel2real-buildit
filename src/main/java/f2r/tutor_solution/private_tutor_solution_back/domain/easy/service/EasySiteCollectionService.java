package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionIngestResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasySiteCollectionCatalog.Dataset;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasySiteCollectionCatalog.Site;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 사용자가 기관 사이트에 직접 로그인해 내려받은 거래 자료를 MCP(AI)가 행 단위로 정리해 보내면 기존 수집 경로(원본 보존 → 중복 방지 →
 * 중앙 장부 저장 → 자동분류 → 예외 검토)로 저장한다. 유료 수집 API(CODEF 등) 없이 쓰는 경로다.
 *
 * <p>AI가 옮겨 적은 값이 틀리지 않았는지 사이트에 표시된 건수·합계(expectedCount·expectedTotal)와 맞춰 보고, 하나라도 어긋나면
 * 아무것도 저장하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class EasySiteCollectionService {

  static final int MAX_ROWS = 2000;
  private static final int MAX_PERIOD_DAYS = 366;
  /** 계좌·카드 전체 번호로 보이는 연속 숫자(6자리 이상). accountRef는 거절, 적요·거래처는 끝 4자리만 남긴다. */
  private static final Pattern LONG_DIGITS = Pattern.compile("\\d{6,}");
  private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,100}");
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final EasyCollectionIngestService ingestService;
  private final EasyTutorAccessService accessService;
  private final EasyBusinessRepository businessRepository;

  /** 기관 사이트에서 내려받은 한 행. direction은 IN(입금·매출·취소환불) 또는 OUT(출금·사용·매출취소). */
  public record SiteRow(String date, String direction, Long amount, String counterparty,
      String description, String approvalNo, Long balance, String cancelOfDate) {}

  public record SiteRowsRequest(Long businessId, String siteCode, String dataset,
      String accountRef, String from, String to, Integer expectedCount, Long expectedTotal,
      String idempotencyKey, List<SiteRow> rows) {}

  /** MCP 목록 도구 응답: 기관 링크·순서·안전 규칙과 이 사용자가 저장할 수 있는 사업체. */
  public Map<String, Object> catalog(AuthenticatedPrincipal principal) {
    requireAdmin(principal);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("purpose", "유료 수집 API 없이, 사용자가 직접 로그인한 기관 사이트에서 거래 파일을 내려받아 F2R 장부(복식부기)에 저장합니다.");
    out.put("safetyRules", EasySiteCollectionCatalog.safetyRules());
    out.put("sites", EasySiteCollectionCatalog.sites().stream().map(site -> {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("code", site.code());
      row.put("category", site.category().name());
      row.put("name", site.name());
      row.put("url", site.url());
      if (site.businessUrl() != null) {
        row.put("businessUrl", site.businessUrl());
      }
      row.put("datasets", site.datasets().stream().map(Enum::name).toList());
      if (site.note() != null) {
        row.put("note", site.note());
      }
      return row;
    }).toList());
    Map<String, Object> steps = new LinkedHashMap<>();
    for (EasySiteCollectionCatalog.Category category : EasySiteCollectionCatalog.Category.values()) {
      steps.put(category.name(), EasySiteCollectionCatalog.steps(category));
    }
    out.put("steps", steps);
    out.put("businesses", businessRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
        .map(b -> Map.<String, Object>of("businessId", b.getBusinessId(), "name", b.getName()))
        .toList());
    out.put("saveTool", "save_easy_site_rows");
    return out;
  }

  /** 검증을 모두 통과한 경우에만 기존 수집 경로로 저장한다. */
  public EasyCollectionIngestResponse save(AuthenticatedPrincipal principal, SiteRowsRequest request) {
    requireAdmin(principal);
    accessService.requireAllowedBusiness(principal, request.businessId());
    Site site = EasySiteCollectionCatalog.find(request.siteCode())
        .orElseThrow(() -> new IllegalArgumentException("목록에 없는 사이트입니다. easy_collection_sites의 code를 쓰세요."));
    Dataset dataset = parseDataset(request.dataset());
    if (!site.datasets().contains(dataset)) {
      throw new IllegalArgumentException(site.name() + "에서는 " + site.datasets() + " 자료만 저장할 수 있습니다.");
    }
    String accountRef = requireAccountRef(request.accountRef());
    LocalDate from = parseDate(request.from(), "from");
    LocalDate to = parseDate(request.to(), "to");
    if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_PERIOD_DAYS) {
      throw new IllegalArgumentException("조회 기간은 1년 이내로, 시작일이 종료일보다 앞서야 합니다.");
    }
    if (request.idempotencyKey() == null || !IDEMPOTENCY_KEY.matcher(request.idempotencyKey()).matches()) {
      throw new IllegalArgumentException("idempotencyKey는 영문·숫자 8~100자로 적어 주세요.");
    }
    List<SiteRow> rows = request.rows() == null ? List.of() : request.rows();
    if (rows.isEmpty() || rows.size() > MAX_ROWS) {
      throw new IllegalArgumentException("rows는 1~" + MAX_ROWS + "행이어야 합니다. 더 많으면 기간을 나눠 보내세요.");
    }
    verifyTotals(rows, request.expectedCount(), request.expectedTotal());

    String scope = "MS_" + sha256(site.code() + "|" + dataset + "|" + accountRef).substring(0, 24);
    List<CollectionParsedRow> parsed = new ArrayList<>();
    Map<String, Integer> occurrence = new HashMap<>();
    for (int i = 0; i < rows.size(); i++) {
      parsed.add(toParsedRow(i + 1, rows.get(i), site, dataset, accountRef, from, to, occurrence));
    }
    String keyHash = sha256(request.businessId() + "|" + request.idempotencyKey()).substring(0, 24);
    String contentHash = sha256(scope + "|" + from + "|" + to + "|" + rowsFingerprint(rows)).substring(0, 16);
    String prefix = "mcp-site-" + keyHash;
    return ingestService.ingestSiteRows(request.businessId(), scope, prefix,
        prefix + "-" + contentHash, parsed);
  }

  /** 행 수와 금액 합계(입출금을 서로 빼지 않은 합)가 사이트 표시값과 같은지 확인한다. */
  static void verifyTotals(List<SiteRow> rows, Integer expectedCount, Long expectedTotal) {
    if (expectedCount == null || expectedTotal == null) {
      throw new IllegalArgumentException("사이트에 표시된 조회 건수(expectedCount)와 금액 합계(expectedTotal)를 함께 보내 주세요.");
    }
    if (expectedCount != rows.size()) {
      throw new IllegalArgumentException("건수가 맞지 않습니다. 사이트 " + expectedCount + "건, 보낸 행 " + rows.size()
          + "건. 빠진 행이 없는지 다시 확인해 주세요. 저장하지 않았습니다.");
    }
    long sum = 0;
    for (SiteRow row : rows) {
      if (row.amount() == null || row.amount() <= 0) {
        throw new IllegalArgumentException("금액은 0보다 큰 원 단위 정수로 적어 주세요(입금·출금은 direction으로 구분).");
      }
      sum = Math.addExact(sum, row.amount());
    }
    if (sum != expectedTotal) {
      throw new IllegalArgumentException("금액 합계가 맞지 않습니다. 사이트 " + expectedTotal + "원, 보낸 행 합계 " + sum
          + "원. 저장하지 않았습니다.");
    }
  }

  static CollectionParsedRow toParsedRow(int rowNo, SiteRow row, Site site, Dataset dataset,
      String accountRef, LocalDate from, LocalDate to, Map<String, Integer> occurrence) {
    LocalDate occurredOn = parseDate(row.date(), "rows[" + rowNo + "].date");
    if (occurredOn.isBefore(from) || occurredOn.isAfter(to)) {
      throw new IllegalArgumentException(rowNo + "번째 행 날짜(" + occurredOn + ")가 조회 기간 밖입니다. 저장하지 않았습니다.");
    }
    String direction = row.direction() == null ? "" : row.direction().trim().toUpperCase();
    if (!direction.equals("IN") && !direction.equals("OUT")) {
      throw new IllegalArgumentException(rowNo + "번째 행 direction은 IN 또는 OUT이어야 합니다.");
    }
    boolean income = direction.equals("IN");
    // 적요·거래처의 계좌·카드 번호로 보이는 긴 숫자는 끝 4자리만 남긴다.
    String counterparty = maskLongDigits(clip(row.counterparty(), 100));
    String description = maskLongDigits(clip(row.description(), 200));
    String approvalNo = clip(row.approvalNo(), 40);

    String payMethod = switch (dataset) {
      case BANK_ACCOUNT -> "계좌";
      case CARD_PURCHASE, CARD_SALES -> "카드";
    };
    String evidenceType = dataset == Dataset.BANK_ACCOUNT ? null : "CREDIT_CARD";

    ObjectNode raw = MAPPER.createObjectNode();
    raw.put("provider", "MCP_SITE");
    raw.put("site", site.code());
    raw.put("institution", site.name());
    raw.put("dataset", dataset.name());
    raw.put("account_ref", accountRef);
    raw.put("date", occurredOn.toString());
    raw.put("direction", direction);
    raw.put("amount", row.amount());
    putIfPresent(raw, "counterparty", counterparty);
    putIfPresent(raw, "description", description);
    putIfPresent(raw, "approval_no", approvalNo);
    if (row.balance() != null) {
      raw.put("balance", row.balance());
    }
    if (dataset == Dataset.BANK_ACCOUNT) {
      EasyBankTransactionClassifier.Result judged = EasyBankTransactionClassifier.classify(
          income, row.amount(), counterparty == null ? "" : counterparty,
          description == null ? "" : description);
      putIfPresent(raw, "primary_disposition", judged.disposition());
      putIfPresent(raw, "review_note", judged.reviewNote());
    } else if (dataset == Dataset.CARD_PURCHASE && income) {
      raw.put("review_note", "카드 사용 취소(환불)입니다. 원거래와 연결되지 않았으니 원래 사용 건과 맞춰 확인하세요.");
    } else if (dataset == Dataset.CARD_SALES && !income) {
      raw.put("review_note", "카드 매출 취소입니다. 원거래와 연결되지 않았으니 원래 매출 건과 맞춰 확인하세요.");
    }

    // 카드 취소 행: 승인번호와 원거래 일자(cancelOfDate)가 있으면 원거래와 연결해 역분개로 저장한다(손익에서 원거래를 줄인다).
    boolean cardCancel = (dataset == Dataset.CARD_PURCHASE && income)
        || (dataset == Dataset.CARD_SALES && !income);
    String originalExternalId = null;
    if (cardCancel && approvalNo != null && row.cancelOfDate() != null) {
      LocalDate originalDate = parseDate(row.cancelOfDate(), "rows[" + rowNo + "].cancelOfDate");
      originalExternalId = approvalExternalBase(site, dataset, accountRef, approvalNo, originalDate) + "#1";
      raw.put("original_external_id", originalExternalId);
      raw.remove("review_note");
    }

    // 원본 행을 특정할 수 있으면(카드 승인번호, 통장 잔액) 외부 ID로 써서 같은 날 같은 금액 거래도 구분한다. 없으면 기존 tuple 규칙.
    String externalId = null;
    String base = null;
    if (approvalNo != null && !cardCancel && dataset != Dataset.BANK_ACCOUNT) {
      base = approvalExternalBase(site, dataset, accountRef, approvalNo, occurredOn);
    } else if (approvalNo != null || row.balance() != null) {
      String natural = String.join("|", site.code(), dataset.name(), accountRef,
          occurredOn.toString(), direction, String.valueOf(row.amount()),
          approvalNo == null ? "" : approvalNo,
          row.balance() == null ? "" : String.valueOf(row.balance()),
          counterparty == null ? "" : counterparty, description == null ? "" : description);
      base = "MS:" + sha256(natural).substring(0, 32);
    }
    if (base != null) {
      externalId = base + "#" + occurrence.merge(base, 1, Integer::sum);
    }
    // 연결된 취소는 원거래와 같은 방향의 역분개 행이다(기존 수집 경로 규칙).
    String ledgerDirection = originalExternalId != null ? (income ? "EXPENSE" : "INCOME")
        : (income ? "INCOME" : "EXPENSE");

    Map<String, String> original = new LinkedHashMap<>();
    original.put("date", occurredOn.toString());
    original.put("direction", direction);
    original.put("amount", String.valueOf(row.amount()));
    original.put("counterparty", counterparty == null ? "" : counterparty);
    original.put("description", description == null ? "" : description);
    return new CollectionParsedRow(rowNo, original, raw.toString(), occurredOn,
        ledgerDirection, row.amount(), counterparty, description, payMethod,
        null, null, evidenceType, null, externalId, null);
  }

  private static String approvalExternalBase(Site site, Dataset dataset, String accountRef,
      String approvalNo, LocalDate approvedOn) {
    return "MS:" + sha256(String.join("|", site.code(), dataset.name(), accountRef, "APPROVAL",
        approvalNo, approvedOn.toString())).substring(0, 32);
  }

  private static void requireAdmin(AuthenticatedPrincipal principal) {
    if (principal == null || !"ADMIN".equals(principal.role())) {
      throw new org.springframework.security.access.AccessDeniedException("관리자만 사용할 수 있습니다.");
    }
  }

  static Dataset parseDataset(String value) {
    try {
      return Dataset.valueOf(value == null ? "" : value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("dataset은 BANK_ACCOUNT, CARD_PURCHASE, CARD_SALES 중 하나입니다.");
    }
  }

  static String requireAccountRef(String value) {
    String ref = value == null ? "" : value.trim();
    if (ref.isEmpty() || ref.length() > 40) {
      throw new IllegalArgumentException("accountRef(예: 국민-운영통장)를 40자 이내로 적어 주세요.");
    }
    if (LONG_DIGITS.matcher(ref.replaceAll("[\\s-]", "")).find()) {
      throw new IllegalArgumentException("accountRef에 계좌·카드 전체 번호를 넣지 마세요. 끝 4자리 정도만 쓰세요.");
    }
    return ref;
  }

  private static LocalDate parseDate(String value, String field) {
    try {
      return LocalDate.parse(value == null ? "" : value.trim());
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(field + " 날짜는 2026-01-31 형식으로 적어 주세요.");
    }
  }

  private static String rowsFingerprint(List<SiteRow> rows) {
    StringBuilder sb = new StringBuilder();
    for (SiteRow row : rows) {
      sb.append(row).append('\n');
    }
    return sb.toString();
  }

  static String maskLongDigits(String value) {
    if (value == null) {
      return null;
    }
    return LONG_DIGITS.matcher(value).replaceAll(m -> "*".repeat(m.group().length() - 4)
        + m.group().substring(m.group().length() - 4));
  }

  private static void putIfPresent(ObjectNode node, String key, String value) {
    if (value != null && !value.isBlank()) {
      node.put(key, value);
    }
  }

  private static String clip(String value, int max) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim().replaceAll("\\s+", " ");
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
