package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaComment;

/**
 * 랜딩 캐러셀 카드 1장의 렌더 데이터.
 *
 * @param decorative 무한 스크롤을 위한 복제본이면 true. 템플릿이 {@code aria-hidden="true"}를 붙여
 *     스크린 리더에 중복 낭독되지 않게 한다.
 * @param highlightLabel null이면 뱃지를 렌더하지 않는다.
 */
public record BetaCommentCardView(
    String avatarText,
    String nickname,
    String roleLabel,
    String highlightLabel,
    String comment,
    boolean decorative) {

  public static BetaCommentCardView from(BetaComment comment) {
    return new BetaCommentCardView(
        comment.avatarText(),
        comment.getNickname(),
        comment.getParticipantType().getLabel(),
        comment.getHighlightLabel() == null ? null : comment.getHighlightLabel().getLabel(),
        comment.getComment(),
        false);
  }

  /** 더미 카드 전용 생성자 — 뱃지 문구를 직접 지정한다. */
  public static BetaCommentCardView dummy(
      String avatarText, String nickname, String roleLabel, String highlightLabel, String comment) {
    return new BetaCommentCardView(avatarText, nickname, roleLabel, highlightLabel, comment, false);
  }

  public BetaCommentCardView asDecorative() {
    return new BetaCommentCardView(
        avatarText, nickname, roleLabel, highlightLabel, comment, true);
  }
}
