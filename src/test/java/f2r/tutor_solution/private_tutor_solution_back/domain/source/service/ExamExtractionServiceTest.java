package f2r.tutor_solution.private_tutor_solution_back.domain.source.service;

import com.anthropic.client.AnthropicClient;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.ExamType;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.ExamAnalysisResult;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Step 2 — ExamExtractionService는 LLM 호출 + JSON 파싱만 책임.
 * 묶음 LLM 호출(extractFromBatch) 자체의 통신은 통합 테스트(별도)에서 검증한다.
 * 여기서는 LLM 응답 텍스트를 파싱하는 로직만 단위로 검증한다.
 */
class ExamExtractionServiceTest {

  private ExamExtractionService service;

  @BeforeEach
  void setUp() {
    service = new ExamExtractionService(
        mock(AnthropicClient.class),
        new ObjectMapper(),
        mock(FileStorage.class),
        "claude-sonnet-4-6");
  }

  @Test
  @DisplayName("extractFromBatch — 데모(anthropic.enabled=false)면 DemoFeatureDisabledException")
  void throws_when_anthropic_disabled() {
    // given — anthropicEnabled=false로 생성 (fromEnv 미호출)
    ExamExtractionService demoService =
        new ExamExtractionService(false, new ObjectMapper(), mock(FileStorage.class));

    // when / then
    assertThatThrownBy(() -> demoService.extractFromBatch(java.util.List.of()))
        .isInstanceOf(f2r.tutor_solution.private_tutor_solution_back.config.DemoFeatureDisabledException.class)
        .hasMessageContaining("데모 버전");
  }

  @Test
  @DisplayName("parse — meta + 객관식/단답형 혼합 questions 정상 파싱")
  void parses_meta_and_questions() {
    // given
    String json = """
        {
          "meta": {
            "school_name": "00중학교",
            "grade": 3,
            "exam_type": "MIDTERM",
            "exam_year": 2026,
            "exam_semester": 1
          },
          "questions": [
            {"question_no": 1, "question_text": "단어 뜻은?",
             "question_type": "MULTIPLE_CHOICE",
             "choices": ["①apple","②book","③cat","④dog"],
             "correct_answer": "②", "student_answer": "②", "is_correct": true},
            {"question_no": 2, "question_text": "빈칸을 채우시오",
             "question_type": "SHORT_ANSWER", "choices": [],
             "correct_answer": "apple", "student_answer": "aple", "is_correct": false}
          ]
        }
        """;

    // when
    ExamAnalysisResult result = service.parse(json);

    // then
    assertThat(result.meta().schoolName()).isEqualTo("00중학교");
    assertThat(result.meta().grade()).isEqualTo((byte) 3);
    assertThat(result.meta().examType()).isEqualTo(ExamType.MIDTERM);
    assertThat(result.questions()).hasSize(2);
    assertThat(result.questions().get(1).isCorrect()).isFalse();
  }

  @Test
  @DisplayName("parse — 코드펜스/부가설명 섞여도 JSON만 추출")
  void parses_with_codefence() {
    // given
    String text = """
        ```json
        {"meta": {"school_name": "X", "grade": 1, "exam_type": "FINAL",
                  "exam_year": null, "exam_semester": null},
         "questions": []}
        ```
        """;

    // when
    ExamAnalysisResult result = service.parse(text);

    // then
    assertThat(result.meta().examType()).isEqualTo(ExamType.FINAL);
    assertThat(result.questions()).isEmpty();
  }

  @Test
  @DisplayName("parse — questions 배열이 없으면 IllegalStateException")
  void rejects_missing_questions() {
    // given
    String json = "{\"meta\": {\"grade\": 3, \"exam_type\": \"MIDTERM\"}}";

    // when & then
    assertThatThrownBy(() -> service.parse(json))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("questions 배열이 없습니다");
  }

  @Test
  @DisplayName("parse — 객관식 원문자 답안(①~⑩)은 정수 문자열로 정규화된다 (correct_answer/student_answer 모두)")
  void normalizes_circled_digit_answers_for_multiple_choice() {
    // given — LLM이 안전망을 못 지키고 원문자로 응답한 경우
    String json = """
        {
          "meta": {"school_name": null, "grade": null, "exam_type": "OTHER",
                   "exam_year": null, "exam_semester": null},
          "questions": [
            {"question_no": 1, "question_text": "Q1",
             "question_type": "MULTIPLE_CHOICE",
             "choices": ["①a","②b","③c","④d","⑤e"],
             "correct_answer": "②", "student_answer": "⑤", "is_correct": false},
            {"question_no": 2, "question_text": "Q2",
             "question_type": "MULTIPLE_CHOICE",
             "choices": ["①a","②b"],
             "correct_answer": "⑩", "student_answer": null, "is_correct": null},
            {"question_no": 3, "question_text": "단답",
             "question_type": "SHORT_ANSWER", "choices": [],
             "correct_answer": "apple", "student_answer": "aple", "is_correct": false}
          ]
        }
        """;

    // when
    ExamAnalysisResult result = service.parse(json);

    // then — 객관식 답안은 정수 문자열로 변환, 단답형은 그대로
    assertThat(result.questions().get(0).correctAnswer()).isEqualTo("2");
    assertThat(result.questions().get(0).studentAnswer()).isEqualTo("5");
    assertThat(result.questions().get(1).correctAnswer()).isEqualTo("10");
    assertThat(result.questions().get(1).studentAnswer()).isNull();
    assertThat(result.questions().get(2).correctAnswer()).isEqualTo("apple");
    assertThat(result.questions().get(2).studentAnswer()).isEqualTo("aple");

    // choices 배열은 표시용이라 원문 유지
    assertThat(result.questions().get(0).choices()).containsExactly("①a","②b","③c","④d","⑤e");
  }

  @Test
  @DisplayName("parse — question_label이 응답에 포함되면 그대로 보존 (객관식/서답형 모두)")
  void preserves_question_label() {
    // given
    String json = """
        {
          "meta": {"school_name": null, "grade": null, "exam_type": "OTHER",
                   "exam_year": null, "exam_semester": null},
          "questions": [
            {"question_no": 1, "question_label": "1",
             "question_text": "Q1", "question_type": "MULTIPLE_CHOICE",
             "choices": ["①","②"], "correct_answer": "②",
             "student_answer": null, "is_correct": null},
            {"question_no": 26, "question_label": "[서답형 1] (1)",
             "question_text": "Q26", "question_type": "SHORT_ANSWER",
             "choices": [], "correct_answer": "apple",
             "student_answer": null, "is_correct": null}
          ]
        }
        """;

    // when
    ExamAnalysisResult result = service.parse(json);

    // then
    assertThat(result.questions().get(0).questionLabel()).isEqualTo("1");
    assertThat(result.questions().get(1).questionLabel()).isEqualTo("[서답형 1] (1)");
  }

  @Test
  @DisplayName("parse — Phase 11: category + passage_ref + passages 배열 보존")
  void preserves_category_and_passages_phase11() {
    // given — LLM이 새 스펙대로 카테고리와 공유 지문을 응답한 경우
    String json = """
        {
          "meta": {"school_name": null, "grade": null, "exam_type": "OTHER",
                   "exam_year": null, "exam_semester": null},
          "passages": [
            {"id": "P1", "content": "Environmental protection has become critical."},
            {"id": "P2", "content": "AI is transforming education."}
          ],
          "questions": [
            {"question_no": 1, "question_text": "지문 주제는?",
             "question_type": "MULTIPLE_CHOICE", "choices": ["①","②"],
             "correct_answer": "1", "student_answer": null, "is_correct": null,
             "category": "reading", "passage_ref": "P1"},
            {"question_no": 2, "question_text": "동의어는?",
             "question_type": "MULTIPLE_CHOICE", "choices": ["①","②"],
             "correct_answer": "2", "student_answer": null, "is_correct": null,
             "category": "vocabulary", "passage_ref": null},
            {"question_no": 3, "question_text": "본문 빈칸",
             "question_type": "SHORT_ANSWER", "choices": [],
             "correct_answer": "answer", "student_answer": null, "is_correct": null,
             "category": null, "passage_ref": null}
          ]
        }
        """;

    // when
    ExamAnalysisResult result = service.parse(json);

    // then
    assertThat(result.passages()).hasSize(2);
    assertThat(result.passages().get(0).id()).isEqualTo("P1");
    assertThat(result.passages().get(0).content()).startsWith("Environmental");
    assertThat(result.passages().get(1).id()).isEqualTo("P2");

    assertThat(result.questions()).hasSize(3);
    assertThat(result.questions().get(0).category()).isEqualTo("reading");
    assertThat(result.questions().get(0).passageRef()).isEqualTo("P1");
    assertThat(result.questions().get(1).category()).isEqualTo("vocabulary");
    assertThat(result.questions().get(1).passageRef()).isNull();
    assertThat(result.questions().get(2).category()).isNull();
  }

  @Test
  @DisplayName("parse — point 필드가 정수로 응답되면 그대로 보존, 없으면 null")
  void preserves_point_field() {
    // given — 1번은 3점, 2번은 5점, 3번은 point 키 자체가 없음
    String json = """
        {
          "meta": {"school_name": null, "grade": null, "exam_type": "OTHER",
                   "exam_year": null, "exam_semester": null},
          "questions": [
            {"question_no": 1, "question_text": "Q1", "question_type": "MULTIPLE_CHOICE",
             "choices": ["①","②"], "correct_answer": "1",
             "student_answer": null, "is_correct": null, "point": 3},
            {"question_no": 2, "question_text": "Q2", "question_type": "MULTIPLE_CHOICE",
             "choices": ["①","②"], "correct_answer": "2",
             "student_answer": null, "is_correct": null, "point": 5},
            {"question_no": 3, "question_text": "Q3", "question_type": "SHORT_ANSWER",
             "choices": [], "correct_answer": "x",
             "student_answer": null, "is_correct": null}
          ]
        }
        """;

    // when
    ExamAnalysisResult result = service.parse(json);

    // then
    assertThat(result.questions().get(0).point()).isEqualTo(3);
    assertThat(result.questions().get(1).point()).isEqualTo(5);
    assertThat(result.questions().get(2).point()).isNull(); // 키 없으면 null — service에서 0으로 normalize
  }

  @Test
  @DisplayName("parse — Phase 11: passages 배열 없어도 정상 파싱 (지문 없는 시험지)")
  void parses_without_passages_phase11() {
    // given — passages 키 자체가 없는 경우
    String json = """
        {
          "meta": {"school_name": null, "grade": null, "exam_type": "OTHER",
                   "exam_year": null, "exam_semester": null},
          "questions": [
            {"question_no": 1, "question_text": "Q1",
             "question_type": "MULTIPLE_CHOICE", "choices": ["①","②"],
             "correct_answer": "1", "student_answer": null, "is_correct": null,
             "category": "grammar", "passage_ref": null}
          ]
        }
        """;

    // when
    ExamAnalysisResult result = service.parse(json);

    // then
    assertThat(result.passages()).isNull();
    assertThat(result.questions().get(0).category()).isEqualTo("grammar");
  }

  @Test
  @DisplayName("parse — 객관식 답안이 이미 정수 문자열이면 그대로 유지 (멱등성)")
  void normalize_is_idempotent_for_already_numeric_answers() {
    // given
    String json = """
        {
          "meta": {"school_name": null, "grade": null, "exam_type": "OTHER",
                   "exam_year": null, "exam_semester": null},
          "questions": [
            {"question_no": 1, "question_text": "Q1",
             "question_type": "MULTIPLE_CHOICE", "choices": ["①","②"],
             "correct_answer": "2", "student_answer": "1", "is_correct": false}
          ]
        }
        """;

    // when
    ExamAnalysisResult result = service.parse(json);

    // then
    assertThat(result.questions().get(0).correctAnswer()).isEqualTo("2");
    assertThat(result.questions().get(0).studentAnswer()).isEqualTo("1");
  }
}
