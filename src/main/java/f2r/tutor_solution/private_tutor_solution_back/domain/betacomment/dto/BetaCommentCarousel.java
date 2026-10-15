package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 랜딩 "베타 참여자들의 목소리" 캐러셀의 완성된 렌더 상태.
 *
 * <p>애니메이션 on/off와 복제본 생성을 서버에서 끝내고 템플릿은 {@link #cards()}를 한 번만 순회한다.
 * 뷰에 건수 조건문을 흩지 않기 위함이다.
 *
 * @param cards 렌더할 카드 전체. {@code animated}면 원본 N장 + 복제본 N장으로 2N장이다.
 * @param animated 자동 스크롤 여부. false면 템플릿이 트랙을 가운데 정렬 정적 배치로 바꾼다.
 */
public record BetaCommentCarousel(List<BetaCommentCardView> cards, boolean animated) {

  /**
   * {@code landing.css}의 {@code translateX(calc(-50% - 8px))}는 트랙이 **정확히 동일한 두 절반**임을
   * 전제한다. 애니메이션을 켤 때 복제본 수가 원본과 어긋나면 이음매가 튄다.
   */
  public static BetaCommentCarousel of(List<BetaCommentCardView> base, boolean animated) {
    if (base == null || base.isEmpty()) {
      throw new IllegalArgumentException("캐러셀 카드가 비어 있습니다.");
    }
    if (!animated) {
      return new BetaCommentCarousel(List.copyOf(base), false);
    }
    List<BetaCommentCardView> cards = new ArrayList<>(base.size() * 2);
    cards.addAll(base);
    base.forEach(card -> cards.add(card.asDecorative()));
    return new BetaCommentCarousel(List.copyOf(cards), true);
  }
}
