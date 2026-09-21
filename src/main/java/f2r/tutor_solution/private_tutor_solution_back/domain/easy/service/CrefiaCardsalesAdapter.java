package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 여신금융협회 매통조 OpenAPI 기반 가맹점 카드매출 수집 어댑터 (PHASE 2).
 *
 * <p>필드 근거 — 협회 공식 공개 자료(crefia.or.kr 신용정보활용체제 공시 + 가맹점 카드매출 조회 안내):
 * <ul>
 *   <li>승인: 승인번호·카드구분·카드종류·승인금액·거래일자·거래시간·할부기간</li>
 *   <li>매입: 카드사명·제휴사·가맹점번호·승인번호·매출금액·수수료·지급금액·거래일자·매입일자·지급예정일자·부가세대리납부금액</li>
 *   <li>입금: 카드사명·매출금액·수수료·보류금액·입금예정금액·거래건수·대금지급은행·입금(예정)일·부가세대리납부금액</li>
 * </ul>
 * 와이어 포맷의 세부 키명은 가입 후 자료실 샘플 기준으로 확정하므로 관용적(tolerant) 조회를 사용한다.
 *
 * <p>운영 전제 (미충족 시 fetch 차단, 조용히 빈 결과로 속이지 않는다):
 * <ol>
 *   <li>협회 이용기관 승인 + {@code CREFIA_CLIENT_ID}/{@code CREFIA_CLIENT_SECRET} 등록</li>
 *   <li>가맹점별 "정보 전송 이용 동의" 완료 (connectionRef = {@code crefia:<사업자번호 10자리>})</li>
 * </ol>
 * 수집 방향은 가맹점 매출이므로 전부 INCOME이다. confirm은 기존 경계(관리자 수동) 유지 —
 * 자동 collect ≠ 자동 confirm. easy_transactions 직접 INSERT 금지.
 */
@Service
public class CrefiaCardsalesAdapter implements EasyCollectionAdapter {

  public static final String ADAPTER_CODE = "CARDSALES";
  public static final String ADAPTER_VERSION = "v1-crefia";
  public static final String PROVIDER = "CREFIA";

  private static final int PAGE_SIZE = 500;
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final CrefiaCardsalesClient client;
  private final CrefiaCredentialProvider credentialProvider;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public CrefiaCardsalesAdapter() {
    this(CrefiaCardsalesClient.createDefault(), CrefiaCredentialProvider.env());
  }

  CrefiaCardsalesAdapter(CrefiaCardsalesClient client,
      CrefiaCredentialProvider credentialProvider) {
    this.client = client;
    this.credentialProvider = credentialProvider;
  }

  @Override
  public String adapterCode() {
    return ADAPTER_CODE;
  }

  @Override
  public String adapterVersion() {
    return ADAPTER_VERSION;
  }

  @Override
  public List<String> configKeys() {
    // client_secret은 환경변수로만 주입 (입력 폼에 노출 금지)
    return List.of("merchantNo");
  }

  @Override
  public List<CollectionParsedRow> fetch(String connectionRef, LocalDateTime since, int limit) {
    String merchantNo = requireMerchantNo(connectionRef);
    if (limit <= 0 || limit > 5000) {
      throw new IllegalArgumentException("limit은 1~5000 사이여야 합니다.");
    }
    CrefiaCredentialProvider.Credentials credentials = credentialProvider.credentials();
    if (credentials == null || !credentials.present()) {
      throw new IllegalStateException(
          "매통조 이용기관 자격증명이 없습니다. 협회 이용기관 승인 후 운영 환경에 등록하세요 (구현 BLOCKED).");
    }
    LocalDate from = since == null ? null : since.toLocalDate();
    CrefiaCardsalesClient.DatasetQuery query =
        new CrefiaCardsalesClient.DatasetQuery(merchantNo, from, null);

    List<CollectionParsedRow> rows = new ArrayList<>();
    int budget = limit;
    budget -= collectDataset(rows, "APPROVAL",
        client.fetchApprovals(credentials, query, PAGE_SIZE), merchantNo, budget);
    if (budget > 0) {
      budget -= collectDataset(rows, "PURCHASE",
          client.fetchPurchases(credentials, query, PAGE_SIZE), merchantNo, budget);
    }
    if (budget > 0) {
      collectDataset(rows, "DEPOSIT",
          client.fetchDeposits(credentials, query, PAGE_SIZE), merchantNo, budget);
    }
    if (since == null) {
      return rows.size() <= limit ? rows : rows.subList(0, limit);
    }
    return rows.stream()
        .filter(r -> r.occurredOn() != null && r.occurredOn().atStartOfDay().isAfter(since))
        .limit(limit)
        .toList();
  }

  @Override
  public void validateConnection(String connectionRef) {
    // 네트워크 호출 없음 — 형식 검증만 수행한다 (가벼운 ping 원칙 유지)
    requireMerchantNo(connectionRef);
  }

  private int collectDataset(List<CollectionParsedRow> out, String dataset,
      List<Map<String, String>> records, String merchantNo, int budget) {
    int added = 0;
    for (Map<String, String> record : records) {
      if (added >= budget) {
        break;
      }
      out.add(toRow(dataset, record, merchantNo));
      added++;
    }
    return added;
  }

  private CollectionParsedRow toRow(String dataset, Map<String, String> record,
      String merchantNo) {
    String occurredOn = firstText(record, dateKeys(dataset));
    String amountText = firstText(record, amountKeys(dataset));
    String approvalNo = firstText(record, "승인번호", "approvalNo", "apprNo", "approval_no");
    String cardCompany = firstText(record, "카드사명", "카드사", "cardCompany", "cardCo", "매입카드사");
    String merchantName = firstText(record, "가맹점명", "merchantName", "merName", "상호명");
    String fee = firstText(record, "수수료", "fee", "commission");
    String payExpectedDate = firstText(record, "지급예정일자", "지급예정일", "payExpectedDate");
    String payAmount = firstText(record, "지급금액", "payAmount", "지급액");

    LocalDate date = parseDate(occurredOn);
    Long amount = parseAmount(amountText);
    String error = null;
    if (date == null) {
      error = "거래일자를 해석할 수 없습니다: " + occurredOn;
    } else if (amount == null || amount < 0) {
      error = "금액이 유효하지 않습니다: " + amountText;
    }

    String externalId = buildExternalId(dataset, record, merchantNo, approvalNo, date, amount);
    String counterparty = cardCompany != null && !cardCompany.isBlank() ? cardCompany
        : (merchantName != null && !merchantName.isBlank() ? merchantName : "카드매출");
    String description = buildDescription(dataset, approvalNo, merchantName, cardCompany, fee,
        payAmount, payExpectedDate);
    String externalAccount = merchantName != null && !merchantName.isBlank() ? merchantName
        : "CARDSALES:" + merchantNo;

    Map<String, String> original = new LinkedHashMap<>();
    original.put("provider", PROVIDER);
    original.put("recordType", dataset);
    original.put("merchantNo", merchantNo);
    original.put("externalId", externalId);
    original.put("occurredOn", date == null ? "" : date.format(DATE_FMT));
    original.put("direction", "INCOME");
    original.put("amount", amount == null ? "" : String.valueOf(amount));
    original.put("counterparty", counterparty);
    original.put("description", description);
    original.put("record", record.toString());

    String rawJson = "{\"provider\":\"" + PROVIDER
        + "\",\"sourceType\":\"CARDSALES_" + dataset
        + "\",\"providerAccountId\":\"" + escape(merchantNo)
        + "\",\"externalTransactionId\":\"" + escape(externalId)
        + "\",\"occurredAt\":\"" + (date == null ? "" : date.format(DATE_FMT))
        + "\",\"amount\":\"" + (amount == null ? "" : amount)
        + "\",\"counterparty\":\"" + escape(counterparty)
        + "\",\"description\":\"" + escape(description)
        + "\",\"collectedAt\":\"" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        + "\",\"record\":" + toJson(record) + "}";

    return new CollectionParsedRow(
        0,
        Map.copyOf(original),
        rawJson,
        date,
        "INCOME",
        amount == null ? 0L : amount,
        counterparty,
        description,
        "DEPOSIT".equals(dataset) ? "ACCOUNT_TRANSFER" : "CARD",
        null,
        null,
        "DEPOSIT".equals(dataset) ? "BANK_STATEMENT" : "CARD_RECEIPT",
        externalAccount,
        externalId,
        error);
  }

  private String buildExternalId(String dataset, Map<String, String> record, String merchantNo,
      String approvalNo, LocalDate date, Long amount) {
    String merchantPart = firstText(record, "가맹점번호", "merchantId", "merNo");
    if (merchantPart == null || merchantPart.isBlank()) {
      merchantPart = merchantNo;
    }
    if (approvalNo != null && !approvalNo.isBlank()) {
      return ("APPROVAL".equals(dataset) ? "APPR:" : "PUR:") + merchantPart + ":" + approvalNo.trim();
    }
    if ("DEPOSIT".equals(dataset)) {
      String cardCompany = firstText(record, "카드사명", "카드사", "cardCompany", "cardCo");
      return "DEP:" + (cardCompany == null ? "" : cardCompany.trim())
          + ":" + (date == null ? "" : date) + ":" + (amount == null ? "" : amount);
    }
    // 매입에서 승인번호가 없을 때 — 매입일자+금액 deterministic fingerprint
    String purchaseDate = firstText(record, "매입일자", "purchaseDate", "매입일");
    return "PUR:" + merchantPart + ":" + (purchaseDate == null ? "" : purchaseDate.trim())
        + ":" + (amount == null ? "" : amount);
  }

  private String buildDescription(String dataset, String approvalNo, String merchantName,
      String cardCompany, String fee, String payAmount, String payExpectedDate) {
    StringBuilder sb = new StringBuilder(switch (dataset) {
      case "APPROVAL" -> "카드승인";
      case "PURCHASE" -> "카드매입";
      default -> "카드정산입금";
    });
    if (merchantName != null && !merchantName.isBlank()) {
      sb.append(" ").append(merchantName.trim());
    }
    if (cardCompany != null && !cardCompany.isBlank()) {
      sb.append(" (").append(cardCompany.trim()).append(")");
    }
    if (approvalNo != null && !approvalNo.isBlank()) {
      sb.append(" 승인번호:").append(approvalNo.trim());
    }
    if (fee != null && !fee.isBlank()) {
      sb.append(" 수수료:").append(fee.trim());
    }
    if (payAmount != null && !payAmount.isBlank()) {
      sb.append(" 지급금액:").append(payAmount.trim());
    }
    if (payExpectedDate != null && !payExpectedDate.isBlank()) {
      sb.append(" 지급예정:").append(payExpectedDate.trim());
    }
    return sb.toString();
  }

  private static String[] dateKeys(String dataset) {
    return switch (dataset) {
      case "PURCHASE" -> new String[]{"거래일자", "매입일자", "tradeDate", "purchaseDate", "dealDate", "date"};
      case "DEPOSIT" -> new String[]{"입금일자", "입금예정일", "거래일자", "depositDate", "payDate", "date"};
      default -> new String[]{"거래일자", "tradeDate", "dealDate", "approvalDate", "date"};
    };
  }

  private static String[] amountKeys(String dataset) {
    return switch (dataset) {
      case "PURCHASE" -> new String[]{"매출금액", "지급금액", "salesAmount", "payAmount", "승인금액", "amount"};
      case "DEPOSIT" -> new String[]{"입금예정금액", "실입금액", "입금액", "매출금액", "depositAmount", "amount"};
      default -> new String[]{"승인금액", "매출금액", "approvalAmount", "amount"};
    };
  }

  static String firstText(Map<String, String> record, String... keys) {
    for (String key : keys) {
      String value = record.get(key);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    // 대소문자 변형까지 관용 조회
    for (Map.Entry<String, String> entry : record.entrySet()) {
      for (String key : keys) {
        if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)
            && entry.getValue() != null && !entry.getValue().isBlank()) {
          return entry.getValue();
        }
      }
    }
    return null;
  }

  static LocalDate parseDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String v = value.trim();
    // yyyy-MM-dd(시간 접미 허용) → yyyyMMdd 순으로 시도
    if (v.length() >= 10 && v.charAt(4) == '-') {
      try {
        return LocalDate.parse(v.substring(0, 10), DATE_FMT);
      } catch (DateTimeParseException e) {
        return null;
      }
    }
    String digits = v.length() >= 8 ? v.substring(0, 8) : v;
    if (digits.length() == 8 && digits.chars().allMatch(Character::isDigit)) {
      try {
        return LocalDate.parse(digits, DateTimeFormatter.BASIC_ISO_DATE);
      } catch (DateTimeParseException e) {
        return null;
      }
    }
    return null;
  }

  static Long parseAmount(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String digits = value.trim().replaceAll("[,\\s원₩]", "");
    if (digits.isEmpty()) {
      return null;
    }
    try {
      return Long.parseLong(digits);
    } catch (NumberFormatException e) {
      try {
        return (long) Double.parseDouble(digits);
      } catch (NumberFormatException e2) {
        return null;
      }
    }
  }

  private String toJson(Map<String, String> record) {
    try {
      return objectMapper.writeValueAsString(record);
    } catch (Exception e) {
      return "{}";
    }
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String requireMerchantNo(String connectionRef) {
    if (connectionRef == null || connectionRef.isBlank()) {
      throw new IllegalArgumentException("connectionRef가 비어 있습니다. 연결 설정을 확인하세요.");
    }
    String ref = connectionRef.trim();
    if (!ref.matches("(?i)crefia:\\d{10}")) {
      throw new IllegalStateException(
          "알 수 없는 connectionRef 형식: crefia:<사업자번호 10자리> 형태여야 합니다.");
    }
    return ref.substring(ref.indexOf(':') + 1);
  }
}
