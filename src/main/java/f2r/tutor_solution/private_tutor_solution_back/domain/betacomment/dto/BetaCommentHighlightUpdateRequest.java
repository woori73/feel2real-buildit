package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaCommentHighlight;

/**
 * 뱃지 지정/해제 요청.
 *
 * <p>{@code highlightLabel}이 null이면 뱃지 해제다 — 여기서 null을 거르면 해제 경로가 사라진다.
 */
public record BetaCommentHighlightUpdateRequest(BetaCommentHighlight highlightLabel) {}
