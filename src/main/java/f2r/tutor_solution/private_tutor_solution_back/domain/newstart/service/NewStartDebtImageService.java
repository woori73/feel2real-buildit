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
 * NewStart 채무 이미지 분석 서비스 — Credit4U 채무내역 스크린샷을 OpenRouter Qwen Vision으로
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

  private static final String SYSTEM_PROMPT =
      "당신은 한국 신용정보원(Credit4U) 채무내역 화면을 분석하는 전문가입니다. "
          + "이미지에서 채무 정보를 추출해 JSON으로 반환하세요.";

  private static final String USER_PROMPT =
      "이 Credit4U 채무내역 이미지에서 모든 채무를 추출해 아래 JSON 형식으로만 응답하세요. "
          + "다른 설명 없이 JSON만 출력하세요.\n"
          + "{\n"
          + "  \"debts\": [\n"
          + "    {\"creditor\": \"채권기관명\", \"debtType\": \"채무종류\", "
          + "\"balance\": \"잔액(예: 7,500,000원)\", \"overdueInfo\": \"연체정보(없으면 빈 문자열)\", "
          + "\"creditorChange\": \"채권자변동(없으면 빈 문자열)\"},\n"
          + "    ...\n"
          + "  ]\n"
          + "}\n"
          + "규칙:\n"
          + "- creditor: 은행/카드/캐피탈/저축은행 등 기관명 (예: 신한은행, 삼성카드)\n"
          + "- debtType: 일반신용대출, 주택담보대출, 카드론, 현금서비스 등\n"
          + "- balance: 채무잔액 우선, 없으면 대출금액. 숫자+원 형식\n"
          + "- overdueInfo: 연체/장기연체 관련 내용, 없으면 \"\"\n"
          + "- creditorChange: 채권자 변동 내용, 없으면 \"\"\n"
          + "- 주민등록번호·계좌번호는 절대 포함하지 마세요";

  private final NewStartLlmProperties.OpenRouter config;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public NewStartDebtImageService(NewStartLlmProperties properties) {
    this.config = properties.getOpenrouter();
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  /** 채무 항목 DTO. */
  public record DebtImageItem(
      String creditor, String debtType, String balance, String overdueInfo, String creditorChange) {}

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

  /** PDF 첫 페이지를 PNG 이미지로 렌더링한다. 텍스트가 충분하면 텍스트 기반 분석용으로 별도 처리. */
  private byte[] pdfFirstPageToImage(byte[] pdfBytes) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      if (document.getNumberOfPages() == 0) {
        throw new IllegalArgumentException("PDF에 페이지가 없습니다.");
      }
      // 첫 페이지 텍스트 추출 시도
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setStartPage(1);
      stripper.setEndPage(1);
      String text = stripper.getText(document);
      // 텍스트가 충분하면 Vision 대신 텍스트 분석 (더 정확하고 저렴)
      // 여기서는 일단 이미지 렌더링으로 통일 — 텍스트 추출은 프론트에서 이미 처리됨
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

  /** OpenRouter Qwen Vision API 호출. */
  private String callVisionApi(String dataUrl) throws IOException {
    if (config.getApiKey() == null || config.getApiKey().isBlank()) {
      throw new IOException("AI 서비스 설정이 없습니다.");
    }

    // OpenRouter vision 형식: content 배열에 text + image_url 포함
    List<Map<String, Object>> contentParts = new ArrayList<>();
    contentParts.add(Map.of("type", "text", "text", USER_PROMPT));
    contentParts.add(Map.of("type", "image_url",
        "image_url", Map.of("url", dataUrl)));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", config.getModel());
    body.put("messages", List.of(
        Map.of("role", "system", "content", SYSTEM_PROMPT),
        Map.of("role", "user", "content", contentParts)));
    body.put("temperature", 0.1);
    body.put("max_tokens", 4000);

    try {
      HttpRequest request = HttpRequest
          .newBuilder(URI.create(config.getBaseUrl() + "/chat/completions"))
          .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + config.getApiKey())
          .header("HTTP-Referer", "https://feel2real.co.kr")
          .header("X-Title", "BuildIt NewStart Debt Image")
          .POST(HttpRequest.BodyPublishers.ofString(
              objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
          .build();

      HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) {
        log.warn("debt image vision api failed: status={}", response.statusCode());
        throw new IOException("이미지 분석에 실패했습니다.");
      }

      JsonNode root = objectMapper.readTree(response.body());
      JsonNode choices = root.path("choices");
      if (!choices.isArray() || choices.isEmpty()) {
        throw new IOException("이미지 분석에 실패했습니다.");
      }
      String content = choices.get(0).path("message").path("content").asText("");
      if (content.isBlank()) {
        throw new IOException("이미지 분석에 실패했습니다.");
      }
      return content.strip();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("이미지 분석이 중단되었습니다.");
    }
  }

  /** Vision API의 JSON 응답을 파싱해 채무 목록으로 변환한다. */
  List<DebtImageItem> parseDebts(String jsonResponse) {
    List<DebtImageItem> items = new ArrayList<>();
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
        return items;
      }

      for (JsonNode debt : debts) {
        if (items.size() >= MAX_DEBTS) break;
        items.add(new DebtImageItem(
            mask(debt.path("creditor").asText("")),
            mask(debt.path("debtType").asText("")),
            mask(debt.path("balance").asText("")),
            mask(debt.path("overdueInfo").asText("")),
            mask(debt.path("creditorChange").asText(""))));
      }
    } catch (Exception e) {
      log.warn("debt image json parse failed: {}", e.getMessage());
    }
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
