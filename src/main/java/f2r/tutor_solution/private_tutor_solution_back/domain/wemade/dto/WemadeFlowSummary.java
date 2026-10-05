package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.dto;

import java.time.LocalDate;

/** 수급 요약. 데이터가 없는 구간은 null로 내려 "데이터 없음"으로 표시한다. */
public record WemadeFlowSummary(
        LocalDate asOf,
        int daysAvailable,
        FlowNet d1,
        FlowNet d5,
        FlowNet d20,
        FlowNet d60
) {
    public record FlowNet(Long foreignNet, Long institutionNet, Long retailNet) {}
}
