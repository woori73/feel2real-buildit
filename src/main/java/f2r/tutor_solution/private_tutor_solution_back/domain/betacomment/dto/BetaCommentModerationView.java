package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto;

import java.util.List;

/**
 * 어드민 모더레이션 화면의 한 번 조회 분량.
 *
 * @param approvedCount 승인 건수. 0건/1~4건/5건 이상에서 랜딩 렌더가 달라지므로 어드민이 현재 어느
 *     구간인지 알 수 있어야 한다.
 * @param animationMinCards 자동 스크롤이 켜지는 최소 승인 건수. 화면 안내 문구가 이 값을 쓴다.
 */
public record BetaCommentModerationView(
    List<BetaCommentAdminResponse> pending,
    List<BetaCommentAdminResponse> approved,
    List<BetaCommentAdminResponse> hidden,
    long approvedCount,
    int carouselMaxCards,
    int animationMinCards,
    List<BetaCommentHighlightOption> highlightOptions) {}
