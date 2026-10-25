package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import java.util.List;

/**
 * BuildIt Chart에 공개된 다른 교육자의 카드에서 온 <b>참고 전용</b> 사례.
 *
 * <p>의뢰 학원의 정보가 아니다. 프롬프트에 "[참고 사례]"로 주입되어 문장 구조·보여주는
 * 방식의 참고material로만 쓰이며, 이름·수치·실적·프로그램명이 최종 문구에 흘러나오면 안 된다
 * ({@code AcademyAiPromptBuilder}의 금지 조항 + ProposalService allowlist가 이중 방어).
 *
 * @param rankAsOf 사례 수집 시점의 순위 기준일(현재 순위 아님)
 * @param tags    Chart 카드 원문을 키워드 규칙으로 분류한 검색 태그(AI 분류, 인용 근거 아님)
 */
public record EducatorReference(
    long id,
    String name,
    String role,
    String academyName,
    String region,
    String rankAsOf,
    String philosophy,
    String curriculum,
    String teaching,
    List<String> tags,
    String sourceCardUrl) {}
