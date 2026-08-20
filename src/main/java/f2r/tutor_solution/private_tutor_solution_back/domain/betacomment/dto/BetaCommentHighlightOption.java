package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaCommentHighlight;
import java.util.List;

/** 어드민 뱃지 드롭다운 항목. 한글 문구를 서버가 내려 화면에 리터럴이 중복되지 않게 한다. */
public record BetaCommentHighlightOption(String value, String label) {

  public static List<BetaCommentHighlightOption> all() {
    return BetaCommentHighlight.displayOrder().stream()
        .map(highlight -> new BetaCommentHighlightOption(highlight.name(), highlight.getLabel()))
        .toList();
  }
}
