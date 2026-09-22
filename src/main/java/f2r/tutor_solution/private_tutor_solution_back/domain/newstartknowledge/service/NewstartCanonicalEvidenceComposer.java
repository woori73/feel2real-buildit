package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartKnowledgeItemResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartCanonicalAnswerPolicy.CaseEvidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartCanonicalAnswerPolicy.EvidenceBundle;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartCanonicalAnswerPolicy.SourceLinkEvidence;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 질문 → canonical evidence block 조립 (retrieval → Answer Contract → askWithEvidence).
 *
 * <p>sourceType → 근거 층위 매핑은 canonical classification 어휘를 그대로 따른다:
 * OFFICIAL_* / AUDIT → 공식, OPERATOR_KNOWLEDGE / DERIVED_RULE → 운영자(공식 아님),
 * EBOOK → 전자책 정리, CAFE_* / CASE → 사례. 검색은 ACTIVE-only 게이트
 * ({@link NewstartKnowledgeRetrievalService#retrieveForQuestion})를 통과한 항목만 사용한다.
 * SOURCE_LINKS는 항목에 실재하는 sourceUrl만 전달하며 URL을 생성하지 않는다.
 *
 * <p>정책 재해석 금지 — 어긋남을 발견하면 수정·보고만 한다
 * (source of truth: {@link NewstartCanonicalAnswerPolicy#POLICY_DOC}).
 */
@Service
@RequiredArgsConstructor
public class NewstartCanonicalEvidenceComposer {

    static final int DEFAULT_ITEM_LIMIT = 10;

    private final NewstartKnowledgeRetrievalService retrievalService;

    /**
     * 질문에 대한 canonical 근거 블록. 근거가 없으면 빈 문자열 — 호출자(askWithEvidence)는
     * 그 경우 기존 ask()와 동일 동작한다. retrieval 예외는 답변 경로를 깨지 않도록 빈 블록으로
     * 축소한다 (AI chat은 DB 장애 시에도 무근거 모드로 계속 동작해야 한다).
     */
    public String composeForQuestion(String question) {
        List<NewstartKnowledgeItemResponse> items;
        try {
            items = retrievalService.retrieveForQuestion(question, DEFAULT_ITEM_LIMIT);
        } catch (RuntimeException e) {
            return "";
        }
        return composeFromItems(items);
    }

    /** 테스트 가능한 순수 조립: ACTIVE 통과 항목 목록 → evidence block. */
    public String composeFromItems(List<NewstartKnowledgeItemResponse> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        List<String> official = new ArrayList<>();
        List<String> operator = new ArrayList<>();
        List<String> ebook = new ArrayList<>();
        List<CaseEvidence> cases = new ArrayList<>();
        List<SourceLinkEvidence> links = new ArrayList<>();

        for (NewstartKnowledgeItemResponse item : items) {
            String body = item.getSummary() != null && !item.getSummary().isBlank()
                    ? item.getSummary() : item.getContent();
            String line = item.getTitle() + (body == null || body.isBlank() ? "" : " — " + body);
            String sourceType = item.getSourceType() == null ? "" : item.getSourceType();
            switch (sourceType) {
                case "OFFICIAL_CURRENT", "OFFICIAL_HISTORICAL", "AUDIT" -> official.add(line);
                case "OPERATOR_KNOWLEDGE", "DERIVED_RULE" -> operator.add(line);
                case "EBOOK" -> ebook.add(line);
                default -> cases.add(new CaseEvidence(
                        "K-" + item.getKnowledgeId(), line, null,
                        sourceType.startsWith("CHAT")
                                ? NewstartCanonicalAnswerPolicy.CASE_SOURCE_CHAT
                                : NewstartCanonicalAnswerPolicy.CASE_SOURCE_CAFE));
            }
            // URL은 provenance에 실재할 때만. 없는 항목에는 링크를 만들지 않는다.
            if (item.getSourceUrl() != null && !item.getSourceUrl().isBlank()) {
                links.add(new SourceLinkEvidence(
                        "K-" + item.getKnowledgeId(), item.getTitle(), item.getSourceUrl()));
            }
        }

        EvidenceBundle bundle = new EvidenceBundle(
                null, official, operator, ebook, cases, List.of(), List.of(), List.of(), links);
        return NewstartCanonicalAnswerPolicy.composeEvidenceBlock(bundle);
    }
}
