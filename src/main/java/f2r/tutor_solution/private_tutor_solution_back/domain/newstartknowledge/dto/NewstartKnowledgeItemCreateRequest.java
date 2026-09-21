package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartKnowledgeItemCreateRequest {

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
    private String confidence;
    private String relatedCaseIds;
    private String relatedRuleIds;
}
