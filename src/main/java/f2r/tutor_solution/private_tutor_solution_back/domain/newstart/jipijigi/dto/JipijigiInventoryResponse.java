package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * NewStart 지식 DB/RAG 실제 상태(inventory). "들었다"가 아니라 조회 가능한 수치와 capability 플래그로
 * Agent가 어떤 검색 수단에 의존할 수 있는지 증명한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiInventoryResponse {
    private long knowledgeTotal;
    private Map<String, Long> knowledgeByStatus;
    private Map<String, Long> knowledgeBySourceType;
    private long empiricalCaseTotal;
    private long empiricalCaseWithFinalRate;
    private long officialUpdateTotal;
    private long officialUpdatePublished;
    private Map<String, Long> officialUpdateBySource;
    private long questionTotal;
    private long draftTotal;

    /** 검색 수단 실측: V1 NewStart DB는 키워드 LIKE 조회만 존재. */
    private boolean keywordLikeRetrieval;
    private boolean vectorSearch;
    private boolean fullTextIndex;
    private boolean embeddingPipeline;
    private String retrievalNote;
    private List<String> gaps;
}
