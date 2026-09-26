package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

/** 초안 상세: 본문 + 조사 추적(STEP별 근거) + 상태 이력 + 근거 건수 집계. Agent가 우리 DB를 보고 썼는지 감사용. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiDraftDetailResponse {
    private JipijigiDraftResponse draft;
    private List<JipijigiResearchTraceResponse> traces;
    private List<JipijigiStatusLogResponse> statusLogs;
    /** INTERNAL_DB_HITS / CCRS_CAFE_HITS / BORROWER_CAFE_HITS / OFFICIAL_SOURCES 등 STEP별 근거 건수. */
    private Map<String, Long> hitCounts;
}
