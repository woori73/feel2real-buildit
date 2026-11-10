package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 은행 입출금 자동수집 — CODEF "수시입출 거래내역" 상품.
 *
 * <p>공식 개발가이드(developer.codef.io, 2026-11-10 확인)에서 확인한 계약만 쓴다:
 * <ul>
 *   <li>기업: {@code KR_BK_1_B_002} {@code POST /v1/kr/bank/b/account/transaction-list}</li>
 *   <li>개인: {@code KR_BK_1_P_002} {@code POST /v1/kr/bank/p/account/transaction-list}</li>
 *   <li>입력: organization, connectedId, account(숫자만), startDate·endDate(yyyyMMdd),
 *       orderBy("1" 과거순), inquiryType("0" 계좌상세 미포함)</li>
 *   <li>응답: data.resTrHistoryList[] — resAccountTrDate, resAccountTrTime, resAccountOut,
 *       resAccountIn, resAccountDesc1(보낸분/받는분)·Desc2(거래구분)·Desc3(적요)·Desc4(거래점),
 *       resAfterTranBalance. 거래 고유번호 필드는 없다.</li>
 *   <li>단건이면 객체, 다건이면 리스트로 온다. 5000건 단위 과금.</li>
 * </ul>
 * 고유번호가 없으므로 원본 키는 계좌·일자·시각·입출금액·거래 후 잔액·비고로 만든 해시에 같은 응답 안
 * 등장 순번을 붙인다(같은 내용의 서로 다른 거래를 합치지 않기 위함).
 *
 * <p>운영 전제: CODEF 정식 계약 + {@code CODEF_CLIENT_ID}/{@code CODEF_CLIENT_SECRET} 환경변수 +
 * 사용자가 CODEF 계정 등록으로 받은 connectedId. 계좌 비밀번호·인증서·OTP는 받지도 저장하지도 않는다.
 */
@Component
public class CodefBankTransactionAdapter implements EasyPeriodCollectionAdapter {

  public static final String ADAPTER_CODE = "CODEF_BANK";
  public static final String ADAPTER_VERSION = "v1-codef";
  public static final String BUSINESS_ENDPOINT = "/v1/kr/bank/b/account/transaction-list";
  public static final String PERSONAL_ENDPOINT = "/v1/kr/bank/p/account/transaction-list";

  /** CODEF 기관코드 — KB국민은행은 공식 샘플(codef-io/codef-python)에서 확인됨. */
  public static final String ORG_KB = "0004";

  private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;
  private static final int MAX_PERIOD_DAYS = 31;

  private final CodefApiClient client;
  private final CodefCredentialProvider credentials;
  private final ObjectMapper mapper = new ObjectMapper();
  private final int initialLookbackDays;

  @org.springframework.beans.factory.annotation.Autowired
  public CodefBankTransactionAdapter(
      @Value("${easy.collection.codef.base-url:" + CodefApiClient.PRODUCTION_BASE + "}")
      String baseUrl,
      @Value("${easy.collection.codef.initial-lookback-days:90}") int initialLookbackDays) {
    this(CodefApiClient.createDefault(baseUrl), CodefCredentialProvider.env(),
        initialLookbackDays);
  }

  CodefBankTransactionAdapter(CodefApiClient client, CodefCredentialProvider credentials,
      int initialLookbackDays) {
    this.client = client;
    this.credentials = credentials;
    this.initialLookbackDays = Math.max(0, initialLookbackDays);
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
    // 화면에서 받는 값은 비민감 조회 조건뿐이다. connectedId는 연결 참조(connectionRef)로 따로 둔다.
    return List.of("organization", "clientType", "account");
  }

  @Override
  public int maxPeriodDays() {
    return MAX_PERIOD_DAYS;
  }

  @Override
  public int initialLookbackDays() {
    return initialLookbackDays;
  }

  @Override
  public void validateConnection(String connectionRef) {
    requireConnectedId(connectionRef);
    if (!credentials.credentials().present()) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "CODEF client credentials are not configured.");
    }
  }

  /** 연결 설정 JSON 검증·정규화. 화면 저장 시에도 같은 검증을 쓴다. */
  public static BankConfig parseConfig(String providerConfig) {
    if (providerConfig == null || providerConfig.isBlank()) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "Bank connection settings are missing.");
    }
    JsonNode node;
    try {
      node = new ObjectMapper().readTree(providerConfig);
    } catch (Exception e) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "Bank connection settings are not valid JSON.");
    }
    String organization = node.path("organization").asText("").trim();
    String clientType = node.path("clientType").asText("B").trim().toUpperCase();
    String account = node.path("account").asText("").replaceAll("[^0-9]", "");
    if (!organization.matches("\\d{4}")) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "Bank organization code must be 4 digits.");
    }
    if (!clientType.equals("B") && !clientType.equals("P")) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "Client type must be B(기업) or P(개인).");
    }
    if (!account.matches("\\d{6,20}")) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "Account number must be 6-20 digits.");
    }
    return new BankConfig(organization, clientType, account);
  }

  /** 비민감 조회 조건. toString은 계좌번호를 가린다. */
  public record BankConfig(String organization, String clientType, String account) {
    public String maskedAccount() {
      return mask(account);
    }

    public String toJson() {
      return "{\"organization\":\"" + organization + "\",\"clientType\":\"" + clientType
          + "\",\"account\":\"" + account + "\"}";
    }

    @Override
    public String toString() {
      return "BankConfig[" + organization + "," + clientType + "," + maskedAccount() + "]";
    }
  }

  @Override
  public List<CollectionParsedRow> fetchPeriod(String connectionRef, String providerConfig,
      LocalDate from, LocalDate to, int limit) {
    String connectedId = requireConnectedId(connectionRef);
    BankConfig config = parseConfig(providerConfig);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("organization", config.organization());
    body.put("connectedId", connectedId);
    body.put("account", config.account());
    body.put("startDate", from.format(YMD));
    body.put("endDate", to.format(YMD));
    body.put("orderBy", "1");
    body.put("inquiryType", "0");
    String endpoint = "P".equals(config.clientType()) ? PERSONAL_ENDPOINT : BUSINESS_ENDPOINT;
    JsonNode data = client.call(credentials.credentials(), endpoint, body);
    return mapResponse(data, config);
  }

  /** 응답 → 수집 행. 해석하지 못한 행은 error로 남겨 원문만 보존한다(임의 값 저장 금지). */
  List<CollectionParsedRow> mapResponse(JsonNode data, BankConfig config) {
    JsonNode list = data == null ? null : data.path("resTrHistoryList");
    List<JsonNode> items = new ArrayList<>();
    if (list != null && list.isArray()) {
      list.forEach(items::add);
    } else if (list != null && list.isObject()) {
      items.add(list);
    }
    Map<String, Integer> occurrence = new HashMap<>();
    List<CollectionParsedRow> rows = new ArrayList<>();
    int rowNo = 0;
    for (JsonNode item : items) {
      rowNo++;
      rows.add(mapItem(rowNo, item, config, occurrence));
    }
    return rows;
  }

  private CollectionParsedRow mapItem(int rowNo, JsonNode item, BankConfig config,
      Map<String, Integer> occurrence) {
    String date = text(item, "resAccountTrDate");
    String time = text(item, "resAccountTrTime");
    String outText = text(item, "resAccountOut");
    String inText = text(item, "resAccountIn");
    String desc1 = text(item, "resAccountDesc1");
    String desc2 = text(item, "resAccountDesc2");
    String desc3 = text(item, "resAccountDesc3");
    String desc4 = text(item, "resAccountDesc4");
    String balance = text(item, "resAfterTranBalance");

    Map<String, String> original = new LinkedHashMap<>();
    original.put("resAccountTrDate", date);
    original.put("resAccountTrTime", time);
    original.put("resAccountOut", outText);
    original.put("resAccountIn", inText);
    original.put("resAccountDesc1", desc1);
    original.put("resAccountDesc2", desc2);
    original.put("resAccountDesc3", desc3);
    original.put("resAccountDesc4", desc4);
    original.put("resAfterTranBalance", balance);

    String natural = String.join("|", config.organization(), config.account(), date, time,
        outText, inText, balance, desc1, desc2, desc3);
    String base = "TR:" + sha256(natural).substring(0, 32);
    int seq = occurrence.merge(base, 1, Integer::sum);
    String externalId = base + "#" + seq;

    LocalDate occurredOn = parseDate(date);
    Long out = parseAmount(outText);
    Long in = parseAmount(inText);
    String error = null;
    if (occurredOn == null) {
      error = "거래일자를 해석하지 못했습니다.";
    } else if (out == null || in == null) {
      error = "입출금 금액을 해석하지 못했습니다.";
    } else if ((out > 0) == (in > 0)) {
      error = "입금·출금 중 하나만 있어야 합니다.";
    }
    boolean income = in != null && in > 0;
    long amount = income ? in : (out == null ? 0L : out);
    String counterparty = clip(desc1, 100);
    String description = clip(firstNonBlank(desc3, desc2), 200);
    String payMethod = clip(firstNonBlank(desc2, "계좌"), 30);

    EasyBankTransactionClassifier.Result judged = error == null
        ? EasyBankTransactionClassifier.classify(income, amount, desc1,
            String.join(" ", desc2, desc3))
        : new EasyBankTransactionClassifier.Result(null, null);

    ObjectNode raw = mapper.createObjectNode();
    raw.put("provider", "CODEF");
    raw.put("product", "P".equals(config.clientType()) ? "KR_BK_1_P_002" : "KR_BK_1_B_002");
    raw.put("organization", config.organization());
    raw.put("account_masked", config.maskedAccount());
    original.forEach(raw::put);
    if (judged.disposition() != null) {
      raw.put("primary_disposition", judged.disposition());
    }
    if (judged.reviewNote() != null) {
      raw.put("review_note", judged.reviewNote());
    }
    String rawJson = raw.toString();

    return new CollectionParsedRow(rowNo, original, rawJson,
        error == null ? occurredOn : null,
        error == null ? (income ? "INCOME" : "EXPENSE") : null,
        error == null ? amount : null,
        counterparty, description, payMethod, null, null, null,
        null, externalId, error);
  }

  private static String requireConnectedId(String connectionRef) {
    if (connectionRef == null || connectionRef.isBlank()) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "CODEF connectedId is not registered.");
    }
    return connectionRef.trim();
  }

  private static LocalDate parseDate(String value) {
    if (value == null || !value.matches("\\d{8}")) {
      return null;
    }
    try {
      return LocalDate.parse(value, YMD);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** 정수 원화 금액만 받는다(소수점·문자는 해석 실패). 빈 값은 0. */
  static Long parseAmount(String value) {
    if (value == null || value.isBlank()) {
      return 0L;
    }
    String cleaned = value.replace(",", "").trim();
    if (!cleaned.matches("\\d{1,15}")) {
      return null;
    }
    return Long.parseLong(cleaned);
  }

  static String mask(String account) {
    if (account == null || account.length() <= 4) {
      return "****";
    }
    return "*".repeat(account.length() - 4) + account.substring(account.length() - 4);
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || value.isNull() ? "" : value.asText("").trim();
  }

  private static String firstNonBlank(String a, String b) {
    return a != null && !a.isBlank() ? a : (b == null ? "" : b);
  }

  private static String clip(String value, int max) {
    if (value == null) {
      return "";
    }
    return value.length() <= max ? value : value.substring(0, max);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
