package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaParticipantType;

/**
 * 랜딩 폼의 의견 등록 요청.
 *
 * <p>길이·필수 검증은 {@code BetaComment} 팩토리가 담당한다 — 클라이언트 {@code maxlength}에
 * 의존하지 않는 단일 검증 지점을 두기 위함이다.
 */
public record BetaCommentCreateRequest(
    String nickname, BetaParticipantType participantType, String comment) {}
