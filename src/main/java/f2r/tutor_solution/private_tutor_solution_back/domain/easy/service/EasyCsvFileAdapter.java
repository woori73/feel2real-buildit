package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * PHASE 1 파일 어댑터 — canonical CSV(UTF-8/CP949)를 파싱해 CollectionParsedRow를 만든다.
 * 헤더는 한/영 별칭을 허용한다. 특정 은행/카드사 전용 포맷 변환은 이 클래스가 아니라
 * 향후 전용 어댑터의 책임이다(이 어댑터는 공통 계약 증명용).
 * XLSX는 미지원(의존성 추가 없이 진행) — .xlsx 입력은 명시적 오류로 거부한다.
 */
@Service
public class EasyCsvFileAdapter {

  public static final String ADAPTER_CODE = "FILE_CSV";
  public static final String ADAPTER_VERSION = "v1";
  private static final Map<String, String> HEADER_ALIASES = buildAliases();
  private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
      DateTimeFormatter.ofPattern("yyyy-MM-dd"),
      DateTimeFormatter.ofPattern("yyyy.MM.dd"),
      DateTimeFormatter.ofPattern("yyyy/MM/dd"),
      DateTimeFormatter.ofPattern("yyyyMMdd"));
  private final ObjectMapper objectMapper = new ObjectMapper();

  public List<CollectionParsedRow> parse(byte[] content, String filename) {
    if (filename != null && filename.toLowerCase().endsWith(".xlsx")) {
      throw new IllegalArgumentException("PHASE 1은 CSV만 지원합니다. XLSX는 CSV로 저장해 업로드하세요.");
    }
    String text = decode(content);
    List<List<String>> lines = parseCsv(text);
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("CSV에 내용이 없습니다.");
    }
    Map<String, Integer> columnIndex = mapHeaders(lines.get(0));
    if (!columnIndex.containsKey("date") || !columnIndex.containsKey("amount")) {
      throw new IllegalArgumentException("필수 헤더(거래일/date, 금액/amount)가 없습니다.");
    }
    List<CollectionParsedRow> rows = new ArrayList<>();
    for (int i = 1; i < lines.size(); i++) {
      List<String> cells = lines.get(i);
      if (cells.stream().allMatch(c -> c == null || c.isBlank())) {
        continue;
      }
      rows.add(parseRow(i, cells, columnIndex));
    }
    return rows;
  }

  private CollectionParsedRow parseRow(int lineIndex, List<String> cells,
      Map<String, Integer> columnIndex) {
    Map<String, String> original = new LinkedHashMap<>();
    String occurredOn = cell(cells, columnIndex, "date");
    String directionRaw = cell(cells, columnIndex, "direction");
    String amountRaw = cell(cells, columnIndex, "amount");
    String counterparty = cell(cells, columnIndex, "counterparty");
    String description = cell(cells, columnIndex, "description");
    String payMethod = cell(cells, columnIndex, "paymethod");
    String supplyRaw = cell(cells, columnIndex, "supplyamount");
    String vatRaw = cell(cells, columnIndex, "vat");
    String evidenceType = cell(cells, columnIndex, "evidencetype");
    String externalAccount = cell(cells, columnIndex, "account");
    String externalId = cell(cells, columnIndex, "externalid");
    original.put("date", occurredOn);
    original.put("direction", directionRaw);
    original.put("amount", amountRaw);
    original.put("counterparty", counterparty);
    original.put("description", description);
    original.put("payMethod", payMethod);
    original.put("supplyAmount", supplyRaw);
    original.put("vat", vatRaw);
    original.put("evidenceType", evidenceType);
    original.put("account", externalAccount);
    original.put("externalId", externalId);

    String error = null;
    LocalDate date = null;
    Long amount = null;
    String direction = null;
    Long supplyAmount = null;
    Long vatAmount = null;
    try {
      date = parseDate(occurredOn);
      amount = parseAmount(amountRaw);
      direction = parseDirection(directionRaw);
      supplyAmount = parseAmount(supplyRaw);
      vatAmount = parseAmount(vatRaw);
      if (date == null) {
        error = "거래일을 해석할 수 없습니다: " + occurredOn;
      } else if (amount == null) {
        error = "금액을 해석할 수 없습니다: " + amountRaw;
      } else if (amount < 0) {
        error = "금액은 음수가 될 수 없습니다: " + amountRaw;
      } else if (direction == null) {
        error = "구분(수입/지출, INCOME/EXPENSE)을 해석할 수 없습니다: " + directionRaw;
      }
    } catch (RuntimeException e) {
      error = e.getMessage();
    }
    String rawJson;
    try {
      rawJson = objectMapper.writeValueAsString(original);
    } catch (IOException e) {
      rawJson = String.valueOf(original);
    }
    return new CollectionParsedRow(lineIndex, Map.copyOf(original), rawJson,
        date, direction, amount, blankToNull(counterparty), blankToNull(description),
        blankToNull(payMethod), supplyAmount, vatAmount, blankToNull(evidenceType),
        blankToNull(externalAccount), blankToNull(externalId), error);
  }

  private static String cell(List<String> cells, Map<String, Integer> index, String field) {
    Integer i = index.get(field);
    if (i == null || i >= cells.size()) {
      return "";
    }
    return cells.get(i) == null ? "" : cells.get(i).trim();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  static LocalDate parseDate(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String value = raw.trim().replace(".", "-").replace("/", "-");
    for (DateTimeFormatter format : DATE_FORMATS) {
      try {
        return LocalDate.parse(value, format);
      } catch (RuntimeException ignored) {
        // next format
      }
    }
    return null;
  }

  /** "123,456원", "₩1,000", "-500" 등 허용. 소수점 금액은 미지원(null). */
  static Long parseAmount(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String value = raw.trim()
        .replace(",", "")
        .replace("₩", "")
        .replace("원", "")
        .replaceAll("\\s+", "");
    boolean negative = value.startsWith("-");
    if (negative) {
      value = value.substring(1);
    }
    try {
      BigDecimal decimal = new BigDecimal(value);
      if (decimal.stripTrailingZeros().scale() > 0) {
        return null;
      }
      return negative ? decimal.longValueExact() * -1 : decimal.longValueExact();
    } catch (RuntimeException e) {
      return null;
    }
  }

  static String parseDirection(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String value = EasyCollectionDedupeKeys.normalizeText(raw);
    return switch (value) {
      case "income", "수입", "입금", "수금" -> "INCOME";
      case "expense", "지출", "출금", "지급" -> "EXPENSE";
      default -> null;
    };
  }

  private static Map<String, Integer> mapHeaders(List<String> headerLine) {
    Map<String, Integer> index = new HashMap<>();
    for (int i = 0; i < headerLine.size(); i++) {
      String normalized = EasyCollectionDedupeKeys.normalizeText(headerLine.get(i))
          .replace("-", "").replace("_", "").replace(" ", "");
      String field = HEADER_ALIASES.get(normalized);
      if (field != null && !index.containsKey(field)) {
        index.put(field, i);
      }
    }
    return index;
  }

  private static Map<String, String> buildAliases() {
    Map<String, String> aliases = new HashMap<>();
    put(aliases, "date", "거래일", "거래일자", "거래일시", "일자");
    put(aliases, "direction", "구분", "입출구분", "수입지출", "입출금구분");
    put(aliases, "amount", "금액", "거래금액", "지출금액", "입금금액");
    put(aliases, "counterparty", "거래처", "거래처명", "상대방", "vendor");
    put(aliases, "description", "적요", "요약", "메모", "memo", "description");
    put(aliases, "paymethod", "결제수단", "지급수단", "수단");
    put(aliases, "supplyamount", "공급가액", "공급액");
    put(aliases, "vat", "부가세", "세액");
    put(aliases, "evidencetype", "증빙", "증빙유형");
    put(aliases, "account", "계좌명", "계정과목원문", "원본계정", "externalaccount");
    put(aliases, "externalid", "승인번호", "거래번호", "externalid", "refno", "거래수정번호");
    return Map.copyOf(aliases);
  }

  private static void put(Map<String, String> map, String field, String... headers) {
    map.put(field, field);
    for (String header : headers) {
      map.put(EasyCollectionDedupeKeys.normalizeText(header).replace("_", ""), field);
    }
  }

  /** UTF-8(BOM 허용) 우선, 실패 시 CP949. */
  static String decode(byte[] content) {
    byte[] body = content;
    if (body.length >= 3 && (body[0] & 0xFF) == 0xEF && (body[1] & 0xFF) == 0xBB
        && (body[2] & 0xFF) == 0xBF) {
      body = new byte[content.length - 3];
      System.arraycopy(content, 3, body, 0, body.length);
    }
    try {
      CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT);
      return decoder.decode(ByteBuffer.wrap(body)).toString();
    } catch (CharacterCodingException e) {
      Charset cp949 = Charset.forName("MS949");
      return new String(body, cp949);
    }
  }

  /** 최소 RFC4180 파서: 따옴표·쉼표·개행 포함 필드와 "" 이스케이프를 처리한다. */
  static List<List<String>> parseCsv(String text) {
    List<List<String>> result = new ArrayList<>();
    List<String> current = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean inQuotes = false;
    boolean fieldStarted = false;
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (inQuotes) {
        if (c == '"') {
          if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
            field.append('"');
            i += 2;
            continue;
          }
          inQuotes = false;
          i++;
          continue;
        }
        field.append(c);
        i++;
        continue;
      }
      switch (c) {
        case '"' -> {
          inQuotes = true;
          fieldStarted = true;
          i++;
        }
        case ',' -> {
          current.add(field.toString());
          field.setLength(0);
          fieldStarted = false;
          i++;
        }
        case '\r' -> i++;
        case '\n' -> {
          current.add(field.toString());
          field.setLength(0);
          fieldStarted = false;
          result.add(current);
          current = new ArrayList<>();
          i++;
        }
        default -> {
          field.append(c);
          i++;
        }
      }
    }
    if (fieldStarted || field.length() > 0 || !current.isEmpty()) {
      current.add(field.toString());
      result.add(current);
    }
    return result;
  }

  /** MultipartFile/stream 전용 convenience. */
  public List<CollectionParsedRow> parse(InputStream input, String filename) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    input.transferTo(buffer);
    return parse(buffer.toByteArray(), filename);
  }
}
