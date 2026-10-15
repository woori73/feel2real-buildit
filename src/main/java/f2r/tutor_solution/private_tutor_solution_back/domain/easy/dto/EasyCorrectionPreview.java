package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.util.List;

/** Easy 수정 미리보기 — 적용하지 않고 before/after만 보여준다 (AI 수정 계약용). */
public record EasyCorrectionPreview(
    Long txnId,
    String before,
    String after,
    List<String> changes,
    List<String> warnings
) {
}
