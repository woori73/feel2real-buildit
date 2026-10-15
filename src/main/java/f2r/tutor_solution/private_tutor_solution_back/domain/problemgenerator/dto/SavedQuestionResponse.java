package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Passage;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionType;
import java.util.List;

public record SavedQuestionResponse(
    Long questionId,
    Long passageId,
    String passageTitle,
    String passageContent,
    QuestionType questionType,
    String questionText,
    List<String> choices,
    String correctAnswer,
    String explanation,
    String questionLabel,
    Integer point,
    Integer orderNum) {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  public static SavedQuestionResponse of(Question question, Integer orderNum) {
    Passage passage = question.getPassage();
    return new SavedQuestionResponse(
        question.getQuestionId(),
        passage != null ? passage.getPassageId() : null,
        passage != null ? passage.getTitle() : null,
        passage != null ? passage.getContent() : null,
        question.getQuestionType(),
        question.getQuestionText(),
        parseChoices(question.getChoices()),
        question.getCorrectAnswer(),
        question.getExplanation(),
        question.getQuestionLabel(),
        question.getPoint(),
        orderNum);
  }

  private static List<String> parseChoices(String choices) {
    if (choices == null || choices.isBlank()) return List.of();
    try {
      return OBJECT_MAPPER.readValue(choices, STRING_LIST);
    } catch (Exception ignored) {
      return List.of();
    }
  }
}
