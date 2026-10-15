package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionType;
import java.util.List;

public record QuestionUpdateRequest(
    QuestionType questionType,
    String questionText,
    List<String> choices,
    String correctAnswer,
    String explanation,
    String questionLabel,
    Integer point,
    Long passageId,
    String passageTitle,
    String passageContent) {
}
