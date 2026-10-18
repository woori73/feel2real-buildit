package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

import java.util.List;

/**
 * AI 검색 결과. questions는 반드시 실제 DB 검색 결과만 담는다(AI 창작 문항 금지).
 * usedAi=false면 결정적 키워드 매핑으로 필터를 추출한 것이다.
 */
public record GrammarAiFindResponse(
    boolean usedAi,
    String extractedFilters,
    long totalFound,
    List<GrammarQuestionItemResponse> questions,
    String answer
) {
}
