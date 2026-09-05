package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiTaskType;

/**
 * AI가 만든 수정안. 절대 {@code builderDraft}를 직접 바꾸지 않는다 — 이 값을 사용자가 Builder
 * 화면에서 "적용" 버튼으로 눌러야만 기존 인라인 편집과 같은 경로(commitDraftText →
 * 발행/미리보기)로 반영된다.
 *
 * <p>상태(PENDING/APPLIED/DISMISSED)를 두지 않는다 — 서버는 완전히 stateless하다. Suggestion의
 * 생명주기는 정확히 "한 번의 Builder 편집 세션" 범위이고, 그 상태는 브라우저의 모달 하나가 들고
 * 있는 것으로 충분하다(§13 참고: 이유는 이 필드 도입 결정을 담은 PR 설명에 상세히 적는다).
 */
public record AcademyAiSuggestion(
    AcademyAiTaskType task,
    String target,
    String currentValue,
    String suggestedValue) {}
