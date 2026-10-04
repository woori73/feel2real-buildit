package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

/**
 * 세무구분 지정 요청 — 회계 분류(방향·계정)와 분개는 그대로 두고
 * 손익 반영 여부만 확정한다. 미확정으로 되돌리려면 REVIEW_REQUIRED를 보낸다.
 */
public record EasyTaxClassificationRequest(
    String taxClassification
) {
}
