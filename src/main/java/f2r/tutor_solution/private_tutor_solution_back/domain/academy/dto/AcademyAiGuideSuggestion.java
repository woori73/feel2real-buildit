package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import java.util.List;

/**
 * AI Guided Academy Builder(Phase 5B)의 한 단계에서 만든 문구 후보 묶음.
 *
 * <p>{@link AcademyAiSuggestion}(단일 후보)과 같은 절대 원칙을 따른다 — 이 값은 어떤 경우에도
 * {@code builderDraft}나 Production을 바꾸지 않는다. 사용자가 Guide 패널에서 후보 하나의
 * "적용"을 눌러야만, 기존 인라인 편집과 완전히 같은 경로(builderDraft 갱신 → 미리보기 반영,
 * Publish는 별도)로 반영된다. 서버에는 "적용" 엔드포인트가 없다.
 *
 * @param target 이 후보들이 대상으로 하는 builderDraft key(또는 top-level {@code intro}).
 *     {@code AcademyAiGuideService}의 allowlist를 통과한 값만 담긴다.
 * @param candidates 문구 후보(최대 3개). provider 응답에서 파싱·검증을 통과한 것만 담고,
 *     하나도 남지 않으면 서비스가 {@code AcademyAiGenerationException}을 던진다(빈 배열을
 *     "성공"으로 돌려주지 않는다).
 */
public record AcademyAiGuideSuggestion(String target, List<String> candidates) {}
