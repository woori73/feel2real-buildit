package f2r.tutor_solution.private_tutor_solution_back.domain.source.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import f2r.tutor_solution.private_tutor_solution_back.config.DemoFeatureDisabledException;
import com.anthropic.models.messages.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionType;
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
import java.util.Map;

/**
 * MVP §5.0 4단계 — 시험지 묶음 전체 이미지를 LLM에 한 번에 전달해 메타 + 문제 + 학생 답안을 추출한다.
 *
 * <p>책임은 LLM 호출 + JSON 파싱까지로 한정한다. exams dedup, questions INSERT,
 * EXAM_BLANK/EXAM_GRADED 판별, analysis_result 저장은 호출자({@link SourceAnalysisService})가 담당한다.
 */
@Slf4j
@Service
public class ExamExtractionService {

  private static final String EXTRACTION_SYSTEM_PROMPT = """
      당신은 한국 중·고등학교 학생을 돕는 교육용 영어 시험지 분석 도구입니다.

      [컨텍스트]
      - 입력은 학생이 학교에서 받은 정규 시험지(고사 원안 또는 학생 답안지)입니다.
      - 분석 결과는 학생 본인의 학습 점검 및 오답 노트 작성 목적으로만 사용됩니다.
      - 영어 시험지에는 한국 교과 과정에 따라 다양한 주제의 독해 지문(역사·사회·과학·문학·시사 등)이 포함될 수 있습니다. 이는 정규 교육과정의 일부입니다.
      - 당신의 역할은 시험지의 내용을 평가하거나 수정하는 것이 아니라, 학생이 본 시험지의 문항·정답·학생 답안을 그대로 구조화해 추출하는 것입니다.

      첨부된 시험지 이미지(들)를 보고 메타데이터와 모든 문항, 그리고 공유 지문을 추출해 다음 JSON 형식으로만 응답하세요.

      {
        "meta": {
          "school_name": "학교명 또는 null",
          "grade": 학년 숫자(1~6) 또는 null,
          "exam_type": "MIDTERM" | "FINAL" | "MOCK" | "OTHER",
          "exam_year": 연도 숫자 또는 null,
          "exam_semester": 1 | 2 | null
        },
        "passages": [
          { "id": "P1", "content": "지문 본문 전체" }
        ],
        "questions": [
          {
            "question_no": 1,
            "question_label": "1",
            "question_text": "문제 본문 (지문은 포함하지 말고, 발문/선택지 안내 등 문제 자체만)",
            "question_type": "MULTIPLE_CHOICE" | "SHORT_ANSWER",
            "choices": ["①...", "②...", "③...", "④...", "⑤..."],
            "correct_answer": "정답 (객관식은 정수 문자열 '1'~'10', 단답형은 자유 텍스트)",
            "student_answer": "학생이 표기한 답 (객관식은 정수 문자열 '1'~'10', 단답형은 자유 텍스트) 또는 null",
            "is_correct": true | false | null,
            "point": 3,
            "category": "vocabulary" | "grammar" | "memorization" | "reading" | "other" | null,
            "passage_ref": "P1" 또는 null
          }
        ]
      }

      규칙:
      - 학생 답안이나 정/오답 표기가 명확하지 않으면 student_answer와 is_correct는 null.
      - 정답을 모르겠으면 correct_answer는 null.
      - 단답형 문제는 choices를 빈 배열 [].
      - **객관식 답안 표기**: correct_answer와 student_answer는 반드시 정수 문자열 "1","2","3","4","5"... 로 응답 (원문자 ①②③④⑤가 아니라). choices 배열 안에는 표시용으로 ① 같은 원문자를 포함해도 됨.
      - **question_no**: 시험지 전체에서의 정렬 순번 (1부터 시작, 객관식·서답형 통틀어 순서대로 증가).
      - **question_label**: 시험지에 실제로 적힌 문항 라벨을 그대로 옮긴 짧은 표시 문자열. 객관식은 "1", "2"… 서답형은 "[서답형 1] (1)", "[서답형 2] (2)" 같은 형태. 잘 모르면 question_no를 문자열로 변환 ("1", "2"…).
      - exam_type 추정 단서: "중간고사"=MIDTERM, "기말고사"=FINAL, "모의고사"=MOCK, 그 외=OTHER.

      **배점 (point 필드)**:
      - 시험지 문항 옆에 "(3점)", "[3점]", "3점" 등으로 표시된 배점을 정수로 추출하세요. 예: "(3점)" → 3, "[5점]" → 5.
      - 배점 표기가 없거나 찾을 수 없으면 0으로 응답하세요 (null이 아닌 0). 0은 "배점 미명시"로 처리되어 점수 합산에서 제외됩니다.
      - 시험지 상단에 "각 문항 3점" 같은 일괄 안내가 있으면 모든 문항에 그 값을 적용하세요.
      - 추측하지 마세요. 명시되지 않았으면 0.

      **카테고리 분류 (category 필드)**:
      - **vocabulary** — 어휘, 단어 뜻, 동의어/반의어, 단어 형태(파생어/품사) 문제
      - **grammar** — 어법, 시제, 수일치, 어순, 가정법, 분사구문 등 문법 문제
      - **memorization** — 본문 빈칸 채우기, 본문 영작, 본문 문장 순서/내용 일치 등 교과서 본문 암기 의존 문제
      - **reading** — 지문 주제/요지/제목/심경/추론/문장 삽입/순서/요약 등 독해 문제
      - **other** — 위 4종에 명확히 들어가지 않는 문제(듣기, 자유 작문, 말하기 등)
      - 정확히 판단하기 어려우면 null로 응답하세요.

      **공유 지문 (passages)**:
      - 여러 문제가 같은 지문을 공유하면 그 지문을 passages 배열에 한 번만 넣고 각 문제에서 passage_ref로 참조하세요.
        예: 23~25번이 같은 글에 대한 문제 → passages에 "P1" 1건, 각 question에 "passage_ref": "P1".
      - 문제 1개에만 딸린 짧은 본문(한 문장이나 두세 줄)은 passages에 넣지 말고 question_text 안에 그냥 포함하세요.
      - 충분히 긴 본문(예: 5~6줄 이상)이거나 여러 문제가 공유하면 passages로 분리하세요.
      - id는 "P1", "P2", … 순으로 부여하고 passage_ref와 정확히 일치시키세요.
      - 지문이 전혀 없으면 passages는 빈 배열 [] 또는 생략 가능.

      다른 텍스트나 마크다운 코드펜스 없이 JSON으로만 응답하세요.

      ⚠️ JSON 문자열 escape 규칙 (반드시 준수):
      - 문제 본문(question_text)이나 선택지(choices) 안에 큰따옴표(")가 들어가면 반드시 \\"로 escape하세요.
        예: "the so-called \\"cycling as a lifestyle\\" craze"
      - 줄바꿈은 \\n으로 표현하세요.
      - 백슬래시는 \\\\로 표현하세요.
      - escape 누락은 응답 전체를 무효로 만들기 때문에 매우 중요합니다.
      """;

  private static final String EXTRACTION_USER_PROMPT =
      "위 시험지를 분석해 JSON으로만 응답하세요.";

  /**
   * claude-sonnet-4-6 기준 max output tokens 64000 중 절반 수준으로 잡는다.
   * 30~40문항 시험지가 평균 4~8k tokens이므로 32k면 충분한 여유.
   * 응답이 이 한도에 도달하면 stop_reason=MAX_TOKENS로 잘려서 들어오므로 호출 후 검사한다.
   */
  private static final long MAX_TOKENS_EXTRACTION = 32768L;

  private final AnthropicClient client;
  private final boolean anthropicEnabled;
  private final ObjectMapper objectMapper;
  private final FileStorage fileStorage;

  @Value("${anthropic.model}")
  private String model;

  @Autowired
  public ExamExtractionService(
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
  ExamExtractionService(AnthropicClient client, ObjectMapper objectMapper, FileStorage fileStorage, String model) {
    this.client = client;
    this.anthropicEnabled = true;
    this.objectMapper = objectMapper;
    this.fileStorage = fileStorage;
    this.model = model;
  }

  /**
   * 묶음(한 시험 단위)의 여러 source 이미지를 한 번에 전달해 메타 + 문제 + 학생 답안을 추출한다.
   * 시험지가 여러 페이지로 나뉘어 있어도 LLM이 전체를 보고 일관된 문항 번호로 응답하도록 한 번에 던진다.
   */
  public ExamAnalysisResult extractFromBatch(List<Source> sources) throws IOException {
    if (!anthropicEnabled) {
      throw new DemoFeatureDisabledException(
          "데모 버전에서는 시험지 분석(문항 추출) 기능이 비활성화되어 있습니다. (Claude API 미연결)");
    }
    if (sources == null || sources.isEmpty()) {
      throw new IllegalArgumentException("추출 대상 source가 비어 있습니다.");
    }
    List<ContentBlockParam> blocks = new ArrayList<>();
    for (Source src : sources) {
      blocks.addAll(toImageBlocks(src));
    }
    blocks.add(ContentBlockParam.ofText(
        TextBlockParam.builder().text(EXTRACTION_USER_PROMPT).build()));

    Message response = client.messages().create(
        MessageCreateParams.builder()
            .model(model)
            .maxTokens(MAX_TOKENS_EXTRACTION)
            .systemOfTextBlockParams(List.of(
                TextBlockParam.builder().text(EXTRACTION_SYSTEM_PROMPT).build()))
            .addUserMessageOfBlockParams(blocks)
            .build());

    // stop_reason=MAX_TOKENS면 응답 JSON이 잘려서 파싱 실패가 확정적 — 미리 명시적 예외로 알린다
    boolean truncated = response.stopReason()
        .map(reason -> reason == StopReason.MAX_TOKENS)
        .orElse(false);
    if (truncated) {
      throw new IllegalStateException(
          "Claude 응답이 max_tokens(" + MAX_TOKENS_EXTRACTION + ") 한도에서 잘렸습니다. "
              + "시험지 페이지 수가 너무 많거나 문항 수가 많습니다. 묶음을 줄여서 다시 시도하세요. "
              + "sources count=" + sources.size());
    }

    String text = response.content().stream()
        .flatMap(block -> block.text().stream())
        .map(TextBlock::text)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Claude 응답에 텍스트 블록이 없습니다"));

    return parse(text);
  }

  /** LLM JSON 응답을 record로 파싱하고 객관식 답안을 정수 문자열로 정규화한다. */
  ExamAnalysisResult parse(String text) {
    String json = extractJson(text);
    ExamAnalysisResult parsed;
    try {
      parsed = objectMapper.readValue(json, ExamAnalysisResult.class);
    } catch (Exception e) {
      throw new IllegalStateException("시험 분석 응답 파싱 실패: " + text, e);
    }
    if (parsed.questions() == null) {
      throw new IllegalStateException("시험 분석 응답에 questions 배열이 없습니다: " + text);
    }
    return normalizeChoiceAnswers(parsed);
  }

  /**
   * 객관식(MULTIPLE_CHOICE) 문항의 correct_answer / student_answer 에 들어온 원문자(①~⑩)를
   * 정수 문자열("1"~"10")로 통일한다. choices 배열은 학생 검증 화면 표시용이라 원문 유지.
   * 단답형(SHORT_ANSWER)은 그대로 둔다.
   */
  ExamAnalysisResult normalizeChoiceAnswers(ExamAnalysisResult parsed) {
    List<ExamAnalysisResult.QuestionEntry> normalized = parsed.questions().stream()
        .map(this::normalizeIfMultipleChoice)
        .toList();
    return new ExamAnalysisResult(
        parsed.meta(),
        parsed.matchedExamId(),
        normalized,
        parsed.passages(),
        parsed.totalQuestions(),
        parsed.correctCount());
  }

  private ExamAnalysisResult.QuestionEntry normalizeIfMultipleChoice(ExamAnalysisResult.QuestionEntry q) {
    if (q.questionType() != QuestionType.MULTIPLE_CHOICE) return q;
    return new ExamAnalysisResult.QuestionEntry(
        q.questionNo(),
        q.questionLabel(),
        q.questionText(),
        q.questionType(),
        q.choices(),
        normalizeCircledDigit(q.correctAnswer()),
        normalizeCircledDigit(q.studentAnswer()),
        q.isCorrect(),
        q.point(),
        q.category(),
        q.passageRef());
  }

  /** 원문자 1글자(①~⑩)면 정수 문자열로 변환. 그 외는 trim 후 그대로 반환. */
  static String normalizeCircledDigit(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return null;
    if (trimmed.length() == 1) {
      String mapped = CIRCLED_DIGIT_MAP.get(trimmed.charAt(0));
      if (mapped != null) return mapped;
    }
    return trimmed;
  }

  private static final Map<Character, String> CIRCLED_DIGIT_MAP = Map.ofEntries(
      Map.entry('①', "1"), Map.entry('②', "2"), Map.entry('③', "3"),
      Map.entry('④', "4"), Map.entry('⑤', "5"), Map.entry('⑥', "6"),
      Map.entry('⑦', "7"), Map.entry('⑧', "8"), Map.entry('⑨', "9"),
      Map.entry('⑩', "10"));

  private String extractJson(String text) {
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start < 0 || end < 0 || end < start) {
      throw new IllegalStateException("응답에서 JSON 객체를 찾지 못했습니다: " + text);
    }
    return text.substring(start, end + 1);
  }

  /** PDF/IMAGE 어느 쪽이든 FileStorage가 페이지 단위로 추상화해 돌려준다. */
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
