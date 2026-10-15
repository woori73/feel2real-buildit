package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartKnowledgeItemResponse {

    private Long knowledgeId;
    private String sourceType;
    private String category;
    private String title;
    private String summary;
    private String content;
    private String authority;
    private String tags;
    private String sourceUrl;
    private LocalDate sourceDate;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Integer version;
    private String status;
    private String confidence;
    private String relatedCaseIds;
    private String relatedRuleIds;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
