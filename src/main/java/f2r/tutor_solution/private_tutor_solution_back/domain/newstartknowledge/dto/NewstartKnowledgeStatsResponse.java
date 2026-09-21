package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartKnowledgeStatsResponse {

    private Long totalKnowledge;
    private Long draftCount;
    private Long reviewCount;
    private Long activeCount;
    private Long archivedCount;
    private Long officialCurrentCount;
    private Long officialHistoricalCount;
    private Long auditCount;
    private Long ebookCount;
    private Long cafeArticleCount;
    private Long cafeCommentCount;
    private Long caseCount;
    private Long operatorKnowledgeCount;
    private Long derivedRuleCount;
}
