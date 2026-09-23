package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto.JipijigiInventoryResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiDraftRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiQuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.repository.NewStartOfficialUpdateRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartEmpiricalCase;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartEmpiricalCaseRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent가 의존할 수 있는 지식 자원의 실제 상태. 추측("모아놨을 것")을 배제하기 위해
 * 모두 조회 가능한 카운트와 코드 실측 capability 플래그로만 채운다.
 */
@Service
@RequiredArgsConstructor
public class JipijigiInventoryService {

    private final NewstartKnowledgeItemRepository knowledgeRepository;
    private final NewstartEmpiricalCaseRepository empiricalCaseRepository;
    private final NewStartOfficialUpdateRepository officialUpdateRepository;
    private final JipijigiQuestionRepository questionRepository;
    private final JipijigiDraftRepository draftRepository;

    @Transactional(readOnly = true)
    public JipijigiInventoryResponse build() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (NewstartKnowledgeItem.KnowledgeStatus s : NewstartKnowledgeItem.KnowledgeStatus.values()) {
            byStatus.put(s.name(), knowledgeRepository.countByStatus(s));
        }
        Map<String, Long> bySourceType = new LinkedHashMap<>();
        for (NewstartKnowledgeItem.SourceType t : NewstartKnowledgeItem.SourceType.values()) {
            bySourceType.put(t.name(), knowledgeRepository.countBySourceType(t));
        }
        Map<String, Long> officialBySource = new LinkedHashMap<>();
        for (NewStartOfficialUpdate.SourceType t : NewStartOfficialUpdate.SourceType.values()) {
            officialBySource.put(t.name(), officialUpdateRepository.count(sourceTypeSpec(t)));
        }

        List<String> gaps = new ArrayList<>();
        gaps.add("벡터/embedding 검색 없음 — NewStart 지식 검색은 title/content/tags/category LIKE 조회(NewstartKnowledgeRetrievalService)가 유일");
        gaps.add("FULLTEXT 인덱스 미사용 — 한국어 형태·동의어 확장은 코드 사전을 통한 keyword 확장에 의존");
        gaps.add("신복위 카페 라이브 자동 크롤링 없음 — 저장된 수집본(official_updates CCRS, knowledge CAFE_* )만 STEP2 근거로 사용");
        gaps.add("chat_messages는 RAG 용도가 아님(별도 채팅 저장) — Agent 근거로 읽지 않는다");
        if (byStatus.getOrDefault("ACTIVE", 0L) == 0L) {
            gaps.add("ACTIVE 지식 0건 —승인된 내부 근거가 없어 STEP1이 비어 있음");
        }

        return JipijigiInventoryResponse.builder()
                .knowledgeTotal(knowledgeRepository.count())
                .knowledgeByStatus(byStatus)
                .knowledgeBySourceType(bySourceType)
                .empiricalCaseTotal(empiricalCaseRepository.count())
                .empiricalCaseWithFinalRate(empiricalCaseRepository.countByFinalReductionRateIsNotNull())
                .officialUpdateTotal(officialUpdateRepository.count())
                .officialUpdatePublished(officialUpdateRepository.count(
                        spec(NewStartOfficialUpdate.ReviewStatus.PUBLISHED)))
                .officialUpdateBySource(officialBySource)
                .questionTotal(questionRepository.count())
                .draftTotal(draftRepository.count())
                .keywordLikeRetrieval(true)
                .vectorSearch(false)
                .fullTextIndex(false)
                .embeddingPipeline(false)
                .retrievalNote("JipijigiResearchService STEP1~4 = newstart_knowledge_items(ACTIVE) / newstart_empirical_cases / newstart_official_updates(PUBLISHED) LIKE 조회")
                .gaps(gaps)
                .build();
    }

    private static Specification<NewStartOfficialUpdate> sourceTypeSpec(NewStartOfficialUpdate.SourceType type) {
        return (root, query, cb) -> cb.equal(root.get("sourceType"), type);
    }

    private static Specification<NewStartOfficialUpdate> spec(NewStartOfficialUpdate.ReviewStatus status) {
        return (root, query, cb) -> cb.equal(root.get("reviewStatus"), status);
    }
}
