package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

import java.util.List;
import java.util.Map;

/**
 * 구조화 파일(JSON) 수입 요청.
 * records各行은 자유 키 허용 매핑(stem|question_text, choices, answer|correct_answer,
 * explanation|commentary, grammar_tags, school_level|grade, format, confidence 등).
 */
public record GrammarImportRequest(
    Long materialId,
    String defaultRightsStatus,
    List<Map<String, Object>> records
) {
}
