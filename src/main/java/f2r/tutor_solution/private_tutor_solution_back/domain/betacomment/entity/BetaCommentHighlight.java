package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 승인된 의견 카드 우측에 붙는 뱃지(`feedback-live`).
 *
 * <p>어드민이 개별 의견에 지정하며 미지정(null)이면 뱃지를 렌더하지 않는다. 자유 입력이 아니라
 * 고정 집합인 이유는 뱃지 폭이 카드 레이아웃을 넘기지 않도록 보장하기 위함이다. 표시 문구를 enum이
 * 들고 있어 템플릿에 한글 리터럴이 흩어지지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum BetaCommentHighlight {
  NEW("신규"),
  REVIEWING("검토 중"),
  APPLYING("반영 중"),
  APPLIED("반영 완료");

  private final String label;

  public static List<BetaCommentHighlight> displayOrder() {
    return Arrays.asList(values());
  }
}
