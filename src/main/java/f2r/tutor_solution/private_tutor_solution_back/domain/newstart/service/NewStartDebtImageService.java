package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.NewStartLlmProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * NewStart 채무 이미지 분석 서비스 — Credit4U 채무내역 스크린샷을 NVIDIA Vision (nemotron-3-nano-omni)으로
 * 분석해 채무 목록을 추출한다.
 *
 * <p>브라우저 Tesseract.js OCR의 인식률 문제를 해결하기 위해 서버 사이드 Vision 분석으로 교체한다.
 * 이미지 원본은 저장하지 않고 분석 후 즉시 버린다. 개인정보(주민번호·계좌번호)는 마스킹한다.
 */
@Service
public class NewStartDebtImageService {

  private static final Logger log = LoggerFactory.getLogger(NewStartDebtImageService.class);

  private static final long MAX_BYTES = 20 * 1024 * 1024;
  private static final int MAX_DEBTS = 50;

  // 개인정보 마스킹 패턴 (프론트 clean()과 동일)
  private static final Pattern RRN_RE = Pattern.compile("\\b\\d{6}[- ]?[1-4]\\d{6}\\b");
  private static final Pattern ACCOUNT_RE = Pattern.compile("\\b\\d{10,16}\\b(?!\\s*원)");

  private static final String VISION_MODEL = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning";

  private static final String SYSTEM_PROMPT =
      "당신은 한국 금융문서에서 채무 정보를 추출하는 전문가입니다. "
          + "Credit4U 채무내역 화면, 채권양도통지서 등 한글 금융문서를 분석해 채무 목록을 JSON으로 반환하세요.";

  private static final String USER_PROMPT =
      "이 이미지(한글 금융문서: Credit4U 채무내역, 채권양도통지서 등)에서 모든 채무를 추출해 "
          + "아래 JSON 형식으로만 응답하세요. 다른 설명 없이 JSON만 출력하세요.\n"
          + "{\n"
          + "  \"debts\": [\n"
          + "    {\"creditor\": \"금융회사명(예: 신한은행)\", "
          + "\"debtType\": \"채무종류(예: 일반신용대출, 카드론, 현금서비스)\", "
          + "\"balance\": 7500000, "
          + "\"loanAmount\": 10000000, "
          + "\"overdueInfo\": \"연체정보(없으면 빈 문자열)\", "
          + "\"creditorChange\": \"채권자변동(없으면 빈 문자열)\", "
          + "\"confidence\": \"high|medium|low\", "
          + "\"needsVerification\": false},\n"
          + "    ...\n"
          + "  ]\n"
          + "}\n"
          + "핵심 규칙 (반드시 준수):\n"
          + "1. 추측 금지: 이미지에 없는 정보는 null로 반환하고 needsVerification을 true로 설정. "
          + "절대 추측해서 채우지 마세요.\n"
          + "2. balance는 현재 남은 채무잔액, loanAmount는 최초 대출원금. 두 값을 혼동하지 마세요. "
          + "잔액만 보이면 loanAmount는 null.\n"
          + "3. balance/loanAmount는 쉼표 없는 정수로(예: 7500000), 없으면 null. "
          + "금액에 '원' 표기나 쉼표를 붙이지 마세요.\n"
          + "4. creditor는 은행/카드/캐피탈/저축은행 등 금융회사명, "
          + "debtType은 일반신용대출/주택담보대출/카드론/현금서비스 등.\n"
          + "5. confidence: 값이 이미지에서 명확히 읽히면 high, 일부 불확실하면 medium, "
          + "추측이 필요하면 low.\n"
          + "6. 주민등록번호·계좌번호는 절대 포함하지 마세요.";

  private final NewStartLlmProperties.NvidiaNim config;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public NewStartDebtImageService(NewStartLlmProperties properties) {
    this.config = properties.getNvidia();
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  /** 채무 항목 DTO. */
  public record DebtImageItem(
      String creditor,
      String debtType,
      String balance,
      String loanAmount,
      String overdueInfo,
      String creditorChange,
      String confidence,
      boolean needsVerification) {}

  /**
   * 업로드된 파일을 분석해 채무 목록을 반환한다.
   *
   * @param fileBytes 파일 바이트
   * @param contentType MIME 타입 (image/jpeg, image/png, image/webp, application/pdf)
   * @param originalFilename 원본 파일명
   * @return 추출된 채무 목록 (최대 50건)
   * @throws IllegalArgumentException 파일이 유효하지 않을 때
   * @throws IOException 분석 중 오류 발생 시
   */
  public List<DebtImageItem> analyze(byte[] fileBytes, String contentType, String originalFilename)
      throws IOException {
    if (fileBytes == null || fileBytes.length == 0) {
      throw new IllegalArgumentException("파일이 비어 있습니다.");
    }
    if (fileBytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("파일은 20MB 이하만 가능합니다.");
    }

    String type = (contentType == null ? "" : contentType.toLowerCase());
    String name = (originalFilename == null ? "" : originalFilename.toLowerCase());

    byte[] imageBytes;
    String imageMime;

    if (type.equals("application/pdf") || name.endsWith(".pdf")) {
      // PDF: 첫 페이지 텍스트 추출 시도 → 부족하면 이미지 렌더링
      imageBytes = pdfFirstPageToImage(fileBytes);
      imageMime = "image/png";
    } else if (type.equals("image/jpeg") || type.equals("image/png") || type.equals("image/webp")
        || name.endsWith(".jpg") || name.endsWith(".jpeg")
        || name.endsWith(".png") || name.endsWith(".webp")) {
      imageBytes = fileBytes;
      imageMime = type.startsWith("image/") ? type : guessMime(name);
    } else {
      throw new IllegalArgumentException("JPG, PNG, WebP, PDF만 가능합니다.");
    }

    String base64Image = Base64.getEncoder().encodeToString(imageBytes);
    String dataUrl = "data:" + imageMime + ";base64," + base64Image;

    String jsonResponse = callVisionApi(dataUrl);
    return parseDebts(jsonResponse);
  }

  /** PDF 첫 페이지를 PNG 이미지로 렌더링한다. */
  private byte[] pdfFirstPageToImage(byte[] pdfBytes) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      if (document.getNumberOfPages() == 0) {
        throw new IllegalArgumentException("PDF에 페이지가 없습니다.");
      }
      PDFRenderer renderer = new PDFRenderer(document);
      // 150 DPI 렌더링
      BufferedImage image = renderer.renderImageWithDPI(0, 150);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ImageIO.write(image, "png", baos);
      return baos.toByteArray();
    }
  }

  private String guessMime(String filename) {
    if (filename.endsWith(".png")) return "image/png";
    if (filename.endsWith(".webp")) return "image/webp";
    return "image/jpeg";
  }

  /** NVIDIA Vision API 호출 (429/5xx 시 재시도).
   *
   * <p>진단 전용 로그(prefix: {@code DEBT_VISION_DIAG})만 남기며, 요청/재시도/파싱 동작은
   * 변경하지 않는다. 로그에는 API key·개인정보·이미지 원문을 절대 포함하지 않는다.
   */
  private String callVisionApi(String dataUrl) throws IOException {
    if (config.getApiKey() == null || config.getApiKey().isBlank()) {
      throw new IOException("AI 서비스 설정이 없습니다.");
    }

    long startMs = System.currentTimeMillis();
    String model = VISION_MODEL;
    log.info("DEBT_VISION_DIAG | start | model={} | time={}", model, Instant.now().toString());

    // NVIDIA vision 형식: content 배열에 text + image_url 포함
    List<Map<String, Object>> contentParts = new ArrayList<>();
    contentParts.add(Map.of("type", "text", "text", USER_PROMPT));
    contentParts.add(Map.of("type", "image_url",
        "image_url", Map.of("url", dataUrl)));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("messages", List.of(
        Map.of("role", "system", "content", SYSTEM_PROMPT),
        Map.of("role", "user", "content", contentParts)));
    body.put("temperature", 0.1);
    body.put("max_tokens", 4000);

    String requestBody;
    try {
      requestBody = objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new IOException("요청 생성에 실패했습니다.");
    }

    // 429(속도 제한)/5xx 시 최대 3회 재시도 (exponential backoff)
    int maxAttempts = 3;
    IOException lastError = null;
    List<Integer> retryStatuses = new ArrayList<>();
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      long attemptStartMs = System.currentTimeMillis();
      try {
        HttpRequest request = HttpRequest
            .newBuilder(URI.create(config.getBaseUrl() + "/chat/completions"))
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + config.getApiKey())
            .POST(HttpRequest.BodyPublishers.ofString(
                requestBody, StandardCharsets.UTF_8))
            .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        long latencyMs = System.currentTimeMillis() - attemptStartMs;
        retryStatuses.add(status);
        log.info("DEBT_VISION_DIAG | attempt={} | status={} | latency_ms={}",
            attempt, status, latencyMs);

        if (status == 200) {
          JsonNode root = objectMapper.readTree(response.body());
          String generationId = root.path("id").asText("");
          if (generationId.isBlank()) generationId = "N/A";
          String provider = extractProvider(root, response);
          JsonNode choices = root.path("choices");
          boolean hasChoices = choices.isArray() && !choices.isEmpty();
          log.info("DEBT_VISION_DIAG | response | has_choices={} | generation_id={} | provider={}",
              hasChoices, generationId, provider);
          if (!hasChoices) {
            logDone(false, startMs, retryStatuses);
            throw new IOException("이미지 분석에 실패했습니다.");
          }
          String content = choices.get(0).path("message").path("content").asText("");
          if (content.isBlank()) {
            logDone(false, startMs, retryStatuses);
            throw new IOException("이미지 분석에 실패했습니다.");
          }
          logDone(true, startMs, retryStatuses);
          return content.strip();
        }

        // 실패 응답의 안전한 진단 정보 기록
        String safeMsg = extractSafeErrorMessage(response.body());
        String errorType = classifyErrorType(status, safeMsg);
        log.warn("DEBT_VISION_DIAG | error | status={} | error_type={} | message={}",
            status, errorType, safeMsg);

        // 429 또는 5xx면 재시도
        if (status == 429 || (status >= 500 && status < 600)) {
          log.warn("debt image vision api retryable: status={} attempt={}/{}", status, attempt, maxAttempts);
          lastError = new IOException(
              status == 429 ? "요청이 많아 잠시 후 다시 시도해 주세요." : "이미지 분석에 실패했습니다.");
          if (attempt < maxAttempts) {
            try {
              Thread.sleep(2000L * attempt); // 2s, 4s backoff
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
              logDone(false, startMs, retryStatuses);
              throw new IOException("이미지 분석이 중단되었습니다.");
            }
            continue;
          }
          logDone(false, startMs, retryStatuses);
          throw lastError;
        }

        // 4xx (429 제외)는 재시도 없이 즉시 실패
        log.warn("debt image vision api failed: status={}", status);
        logDone(false, startMs, retryStatuses);
        if (status == 401 || status == 403) {
          throw new IOException("AI 서비스 인증에 실패했습니다.");
        }
        throw new IOException("이미지 분석에 실패했습니다.");

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logDone(false, startMs, retryStatuses);
        throw new IOException("이미지 분석이 중단되었습니다.");
      } catch (IOException e) {
        // 재시도 가능한 에러면 루프 계속, 아니면 즉시 throw
        if (lastError == e || e.getMessage().contains("다시 시도")) {
          // 이미 위에서 처리됨
          throw e;
        }
        logDone(false, startMs, retryStatuses);
        throw e;
      }
    }
    logDone(false, startMs, retryStatuses);
    throw lastError != null ? lastError : new IOException("이미지 분석에 실패했습니다.");
  }

  /** 진단 로그: 최종 결과 (성공/실패, 총 소요시간, 재시도 횟수·상태 목록). */
  private void logDone(boolean success, long startMs, List<Integer> retryStatuses) {
    long totalMs = System.currentTimeMillis() - startMs;
    int retries = Math.max(0, retryStatuses.size() - 1);
    log.info("DEBT_VISION_DIAG | done | success={} | total_latency_ms={} | retries={} | retry_statuses={}",
        success, totalMs, retries, retryStatuses);
  }

  /** NVIDIA 응답에서 provider 식별 (없으면 "N/A"). */
  private String extractProvider(JsonNode root, HttpResponse<byte[]> response) {
    String provider = root.path("provider").path("name").asText("");
    if (!provider.isBlank()) return provider;
    provider = root.path("provider").asText("");
    if (!provider.isBlank()) return provider;
    List<String> headerVals = response.headers().allValues("x-openrouter-provider");
    if (!headerVals.isEmpty() && !headerVals.get(0).isBlank()) return headerVals.get(0).trim();
    headerVals = response.headers().allValues("x-provider");
    if (!headerVals.isEmpty() && !headerVals.get(0).isBlank()) return headerVals.get(0).trim();
    return "N/A";
  }

  /** HTTP status + (안전 처리된) 메시지로 오류 유형 분류. */
  private String classifyErrorType(int status, String safeMessage) {
    if (status == 429) return "rate_limit";
    if (status == 401 || status == 403) return "auth_error";
    if (status == 400 || status == 404 || status == 422) {
      String lower = safeMessage.toLowerCase();
      if (lower.contains("model")) return "invalid_model";
      if (lower.contains("image") || lower.contains("vision") || lower.contains("unsupported")) {
        return "vision_unsupported";
      }
      return "invalid_request";
    }
    if (status >= 500) return "server_error";
    return "unknown";
  }

  /**
   * Vision 오류 응답에서 안전한 범위의 메시지만 추출 (최대 200자).
   * API key·토큰·base64 이미지·개인정보 패턴은 마스킹한다.
   */
  private String extractSafeErrorMessage(byte[] responseBody) {
    String msg = "";
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      msg = root.path("error").path("message").asText("");
      if (msg.isBlank()) msg = root.path("message").asText("");
      if (msg.isBlank()) msg = root.path("error").asText("");
    } catch (Exception e) {
      msg = "";
    }
    if (msg.isBlank()) return "N/A";
    if (msg.length() > 200) msg = msg.substring(0, 200);
    return sanitizeForLog(msg);
  }

  /** 로그 출력 전 민감 패턴 마스킹 (키·토큰·이미지·개인정보). */
  private String sanitizeForLog(String text) {
    if (text == null) return "";
    String s = text;
    s = s.replaceAll("data:image/[^;]+;base64,[A-Za-z0-9+/=\\s]+", "[IMAGE_MASKED]");
    s = s.replaceAll("(?i)bearer\\s+[A-Za-z0-9._~\\-]+", "Bearer [MASKED]");
    s = s.replaceAll("(?i)(api[_-]?key|secret)[\"'\\s:=]+[A-Za-z0-9._~\\-]{8,}", "$1=[MASKED]");
    s = RRN_RE.matcher(s).replaceAll("[개인정보 제거]");
    s = ACCOUNT_RE.matcher(s).replaceAll("[개인정보 제거]");
    return s.replaceAll("[ \\t]+", " ").trim();
  }

  /** Vision API의 JSON 응답을 파싱해 채무 목록으로 변환한다. */
  List<DebtImageItem> parseDebts(String jsonResponse) {
    List<DebtImageItem> items = new ArrayList<>();
    boolean success = false;
    try {
      // JSON 코드블록 제거 (```json ... ```)
      String json = jsonResponse.trim();
      if (json.startsWith("```")) {
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
          json = json.substring(start, end + 1);
        }
      } else {
        // 순수 JSON이 아니면 { } 구간 추출 시도
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
          json = json.substring(start, end + 1);
        }
      }

      JsonNode root = objectMapper.readTree(json);
      JsonNode debts = root.path("debts");
      if (!debts.isArray()) {
        log.info("DEBT_VISION_DIAG | parse | success=false | debts_count=0");
        return items;
      }

      for (JsonNode debt : debts) {
        if (items.size() >= MAX_DEBTS) break;
        String confidence = debt.path("confidence").asText("medium");
        if (!confidence.equals("high") && !confidence.equals("medium") && !confidence.equals("low")) {
          confidence = "medium";
        }
        // loanAmount가 없으면 null (추측 금지)
        JsonNode loanNode = debt.path("loanAmount");
        String loanAmount = (loanNode.isMissingNode() || loanNode.isNull())
            ? null : mask(loanNode.asText(""));
        if (loanAmount != null && loanAmount.isBlank()) loanAmount = null;
        items.add(new DebtImageItem(
            mask(debt.path("creditor").asText("")),
            mask(debt.path("debtType").asText("")),
            mask(debt.path("balance").asText("")),
            loanAmount,
            mask(debt.path("overdueInfo").asText("")),
            mask(debt.path("creditorChange").asText("")),
            confidence,
            debt.path("needsVerification").asBoolean(false)));
      }
      success = true;
    } catch (Exception e) {
      log.warn("debt image json parse failed: {}", e.getMessage());
    }
    log.info("DEBT_VISION_DIAG | parse | success={} | debts_count={}", success, items.size());
    return items;
  }

  /** 개인정보 마스킹 (프론트 clean()과 동일 규칙). */
  static String mask(String text) {
    if (text == null) return "";
    String masked = RRN_RE.matcher(text).replaceAll("[개인정보 제거]");
    masked = ACCOUNT_RE.matcher(masked).replaceAll("[개인정보 제거]");
    return masked.replaceAll("[ \\t]+", " ").trim();
  }
}
