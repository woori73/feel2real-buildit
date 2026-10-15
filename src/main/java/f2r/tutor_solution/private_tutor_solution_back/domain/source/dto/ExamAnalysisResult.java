package f2r.tutor_solution.private_tutor_solution_back.domain.source.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.ExamType;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 시험지 분석(LLM 2차 패스) 결과 — MVP §5.0 4단계.
 * sources.analysis_result JSON 컬럼에 직렬화되어 저장되며, Phase 4 검증 화면이 이 JSON을 그대로 보여준다.
 * 학생이 [전체 확정]하면 이 JSON을 questions / student_answers / student_scores / exams에 풀어서 INSERT한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExamAnalysisResult(
    ExamMeta meta,
    @JsonProperty("matched_exam_id") Long matchedExamId,
    List<QuestionEntry> questions,
    List<PassageEntry> passages,
    @JsonProperty("total_questions") Integer totalQuestions,
    @JsonProperty("correct_count") Integer correctCount
) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ExamMeta(
      @JsonProperty("school_name") String schoolName,
      Byte grade,
      @JsonProperty("exam_type") ExamType examType,
      @JsonProperty("exam_year") Short examYear,
      @JsonProperty("exam_semester") Byte examSemester,
      @JsonProperty("subject_id") Integer subjectId) {
  }

  /**
   * LLM이 추출한 문제 1건.
   * Phase 11 추가:
   * <ul>
   *   <li>{@code category} — vocabulary/grammar/memorization/reading/other 5종 중 하나 (못 고르면 null).
   *       DB의 {@code question_categories.category_name_en} 키로 lookup된다.</li>
   *   <li>{@code passageRef} — passages 배열의 {@code id}를 가리키는 참조. 지문이 없는 문제는 null.</li>
   * </ul>
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record QuestionEntry(
      @JsonProperty("question_no") Integer questionNo,
      @JsonProperty("question_label") String questionLabel,
      @JsonProperty("question_text") String questionText,
      @JsonProperty("question_type") QuestionType questionType,
      List<String> choices,
      @JsonProperty("correct_answer") String correctAnswer,
      @JsonProperty("student_answer") String studentAnswer,
      @JsonProperty("is_correct") Boolean isCorrect,
      /**
       * 문항 배점. 시험지의 "(3점)" 같은 표기에서 LLM이 추출.
       * 표기가 없거나 LLM이 찾지 못하면 0 (= 점수 합산에서 제외, 정답률만 표시).
       */
      Integer point,
      String category,
      @JsonProperty("passage_ref") String passageRef) {
  }

  /**
   * LLM이 추출한 지문(공유 본문). 한 시험 내에서 여러 문제가 참조할 수 있다.
   * {@code id}는 LLM이 임의로 부여한 임시 식별자(예: "P1", "P2"). 같은 응답 안의 QuestionEntry.passageRef와
   * 매핑되어 DB INSERT 시 진짜 passage_id로 변환된다.
   * title / sourceOrigin은 코드에서 채우므로 LLM은 content만 추출하면 된다 (응답 부담 감소).
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PassageEntry(
      String id,
      String content) {
  }

  public String toJson(ObjectMapper mapper) {
    try {
      return mapper.writeValueAsString(this);
    } catch (JacksonException e) {
      throw new IllegalStateException("ExamAnalysisResult 직렬화 실패", e);
    }
  }

  public static ExamAnalysisResult fromJson(String json, ObjectMapper mapper) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return mapper.readValue(json, ExamAnalysisResult.class);
    } catch (JacksonException e) {
      throw new IllegalStateException("ExamAnalysisResult 파싱 실패", e);
    }
  }
}
