package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

/** 선택함/문제집 → DesignCraft 전달 옵션. */
public record GrammarDesignCraftRequest(
    String template,           // ko-textbook-a4 | ko-workbook-b5
    Boolean splitAnswerSheet,  // true면 문제지 본문+정답·해설 분리 섹션
    String answerKeyLabel
) {
}
