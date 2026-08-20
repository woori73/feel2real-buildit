package f2r.tutor_solution.private_tutor_solution_back.domain.source.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import f2r.tutor_solution.private_tutor_solution_back.config.DemoFeatureDisabledException;
import com.anthropic.models.messages.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.ExamAnalysisResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.VisionMediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Phase 9 — 시험지 이미지를 보고 메타데이터만 유추한다.
 *
 * <p>학생이 업로드 폼에서 일부 메타 필드를 비워둔 경우, 빈 필드를 채우기 위한 가벼운 LLM 호출이다.
 * 기존 {@code ExamExtractionService}와 달리 questions 추출을 하지 않으므로 응답 토큰이 작고
 * 파싱 실패 위험이 낮다 (문제 본문이 없어 escape 이슈가 없음).
 *
 * <p>응답 형식:
 * <pre>{"meta": { "school_name": ..., "grade": ..., "exam_type": ..., "exam_year": ..., "exam_semester": ... }}</pre>
 */
@Slf4j
@Service
public class ExamMetaInferenceService {

  private static final String META_INFERENCE_SYSTEM_PROMPT = """
      당신은 한국 중·고등학교 시험지의 메타데이터를 추출하는 도우미입니다.
      첨부된 시험지 이미지를 보고 다음 JSON 형식으로만 응답하세요.
      값을 확신할 수 없으면 null을 반환합니다.

      {
        "meta": {
          "school_name": "학교명 또는 null",
          "grade": 학년 숫자(1~6) 또는 null,
          "exam_type": "MIDTERM" | "FINAL" | "MOCK" | "OTHER" | null,
          "exam_year": 4자리 연도 또는 null,
          "exam_semester": 1 | 2 | null
        }
      }

      판단 단서:
      - "중간고사" → MIDTERM, "기말고사" → FINAL, "모의고사" → MOCK, 그 외는 OTHER
      - 학년 숫자가 시험지에 명확히 보이지 않으면 null
      - 다른 텍스트나 마크다운 코드펜스 없이 JSON으로만 응답하세요.
      """;

  private static final String META_INFERENCE_USER_PROMPT =
      "위 시험지의 메타데이터를 JSON으로만 응답하세요.";

  private static final long MAX_TOKENS_INFERENCE = 256L;

  private final AnthropicClient client;
  private final boolean anthropicEnabled;
  private final ObjectMapper objectMapper;
  private final FileStorage fileStorage;

  @Value("${anthropic.model}")
  private String model;

  @Autowired
  public ExamMetaInferenceService(
      @Value("${anthropic.enabled:true}") boolean anthropicEnabled,
      ObjectMapper objectMapper,
      FileStorage fileStorage) {
    this.anthropicEnabled = anthropicEnabled;
    // 데모(anthropic.enabled=false)에서는 클라이언트 미생성 → ANTHROPIC_API_KEY 없이 부팅.
    this.client = anthropicEnabled ? AnthropicOkHttpClient.fromEnv() : null;
    this.objectMapper = objectMapper;
    this.fileStorage = fileStorage;
  }

  /** 테스트용 생성자 */
  ExamMetaInferenceService(AnthropicClient client, ObjectMapper objectMapper, FileStorage fileStorage, String model) {
    this.client = client;
    this.anthropicEnabled = true;
    this.objectMapper = objectMapper;
    this.fileStorage = fileStorage;
    this.model = model;
  }

  /**
   * Phase 9 — 한 묶음의 시험지 source들(PDF 1개 또는 이미지 N장)에서 메타만 1회 LLM 호출로 유추한다.
   * 모든 이미지를 한 번에 전달해 첫 페이지에 없는 메타 정보(예: 마지막 페이지 학년/학기 표기)도 잡는다.
   * 학생이 채운 메타와 합치는 책임은 호출자에 있다.
   */
  public ExamAnalysisResult.ExamMeta inferMetaFromBatch(List<Source> sources) throws IOException {
    if (!anthropicEnabled) {
      throw new DemoFeatureDisabledException(
          "데모 버전에서는 시험지 분석(메타 추론) 기능이 비활성화되어 있습니다. (Claude API 미연결)");
    }
    if (sources == null || sources.isEmpty()) {
      return new ExamAnalysisResult.ExamMeta(null, null, null, null, null, null);
    }
    List<ContentBlockParam> blocks = new ArrayList<>();
    for (Source src : sources) {
      blocks.addAll(toImageBlocks(src));
    }
    blocks.add(ContentBlockParam.ofText(
        TextBlockParam.builder().text(META_INFERENCE_USER_PROMPT).build()));

    Message response = client.messages().create(
        MessageCreateParams.builder()
            .model(model)
            .maxTokens(MAX_TOKENS_INFERENCE)
            .systemOfTextBlockParams(List.of(
                TextBlockParam.builder().text(META_INFERENCE_SYSTEM_PROMPT).build()))
            .addUserMessageOfBlockParams(blocks)
            .build());

    String text = response.content().stream()
        .flatMap(block -> block.text().stream())
        .map(TextBlock::text)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Claude 응답에 텍스트 블록이 없습니다"));

    return parse(text);
  }

  /** LLM 응답에서 meta 필드만 추출. questions 응답은 무시. */
  ExamAnalysisResult.ExamMeta parse(String text) {
    String json = extractJson(text);
    try {
      var root = objectMapper.readTree(json);
      var metaNode = root.get("meta");
      if (metaNode == null || metaNode.isNull()) {
        return new ExamAnalysisResult.ExamMeta(null, null, null, null, null, null);
      }
      return objectMapper.treeToValue(metaNode, ExamAnalysisResult.ExamMeta.class);
    } catch (Exception e) {
      throw new IllegalStateException("메타 유추 응답 파싱 실패: " + text, e);
    }
  }

  private String extractJson(String text) {
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start < 0 || end < 0 || end < start) {
      throw new IllegalStateException("응답에서 JSON 객체를 찾지 못했습니다: " + text);
    }
    return text.substring(start, end + 1);
  }

  private List<ContentBlockParam> toImageBlocks(Source src) throws IOException {
    List<byte[]> pages = fileStorage.readAllPages(src.getStorageKey(), src.getFileType());
    List<ContentBlockParam> blocks = new ArrayList<>(pages.size());
    for (byte[] page : pages) {
      blocks.add(imageBlock(page, src.getStorageKey()));
    }
    return blocks;
  }

  private ContentBlockParam imageBlock(byte[] bytes, String storageKey) {
    String base64 = Base64.getEncoder().encodeToString(bytes);
    return ContentBlockParam.ofImage(
        ImageBlockParam.builder()
            .source(Base64ImageSource.builder()
                .mediaType(VisionMediaType.forStorageKey(storageKey))
                .data(base64)
                .build())
            .build());
  }
}
