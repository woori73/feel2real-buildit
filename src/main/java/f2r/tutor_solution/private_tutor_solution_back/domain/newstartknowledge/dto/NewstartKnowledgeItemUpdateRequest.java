package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartKnowledgeItemUpdateRequest {

    private String sourceType;
    private String category;
    private String title;
    private String summary;
    private String content;
    private String authority;
    private String tags;
    private String sourceUrl;
    private String sourceDate;
    private String effectiveFrom;
    private String effectiveTo;
    private Integer version;
    private String status;
    private String confidence;
    private String relatedCaseIds;
    private String relatedRuleIds;
}
