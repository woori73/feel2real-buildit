package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.repository.NewStartOfficialUpdateRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.RefType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.SourceClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartEmpiricalCase;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartEmpiricalCaseRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 지피지기 Agent 강제 조사 사다리 (STEP1..5).
 *
 * <p>모델 자체 지식으로 답변하지 않는다: 모든 근거는 실제 NewStart DB 조회 결과만 사용하며,
 * 조회 결과 없는 단계는 "부족/미확인"으로 명시한다(추측·일반화 금지).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JipijigiResearchService {

    private static final int PER_STEP_LIMIT = 8;
    /** 이 미만이면 "내부 자료 부족" → 외부 사례조사 단계로 진행한다. */
    static final int INTERNAL_SUFFICIENT_THRESHOLD = 3;

    private final NewstartKnowledgeItemRepository knowledgeRepository;
    private final NewstartEmpiricalCaseRepository empiricalCaseRepository;
    private final NewStartOfficialUpdateRepository officialUpdateRepository;

    @Getter
    @Builder
    public static class Evidence {
        private final int stepNo;
        private final SourceClass sourceClass;
        private final RefType refType;
        private final String refId;
        private final String refUrl;
        private final String title;
        private final String excerpt;
        private final String note;
    }

    @Getter
    @Builder
    public static class ResearchPack {
        private final String question;
        private final List<Evidence> internalHits;     // STEP1 NewStart 내부 DB/RAG
        private final List<Evidence> ccrsHits;         // STEP2 신복위 (우선)
        private final List<Evidence> borrowerHits;     // STEP3 부실차주 라이브러리 카페
        private final List<Evidence> officialHits;     // STEP4 공식 기준 검증
        private final List<Evidence> extraAgencyHits;  // STEP5 추가 공식기관 (V1: 수동 표기)
        private final List<String> gapNotes;           // 부족·실패·수동조사 필요 사유

        public boolean internalInsufficient() {
            return internalHits.size() < INTERNAL_SUFFICIENT_THRESHOLD;
        }
    }

    public ResearchPack research(String questionText) {
        List<String> keywords = keywordsOf(questionText);
        List<String> gaps = new ArrayList<>();

        // STEP 1 — NewStart 내부 DB/RAG 검색 (항상 먼저)
        List<Evidence> internal = new ArrayList<>();
        for (NewstartKnowledgeItem item : searchActiveKnowledge(keywords)) {
            internal.add(toKnowledgeEvidence(item));
        }
        boolean needExternal = internal.size() < INTERNAL_SUFFICIENT_THRESHOLD;
        if (needExternal) {
            gaps.add("STEP1 내부 ACTIVE 지식 근거 " + internal.size() + "건(기준 " + INTERNAL_SUFFICIENT_THRESHOLD
                    + "건 미만) → 외부 사례조사 단계 필수");
        }

        // STEP 2 — 신복위: DB에 들어온 신복위 자료만 사용. 라이브 카페 자동조회는 V1 미구현.
        List<Evidence> ccrs = new ArrayList<>();
        ccrs.addAll(searchOfficialUpdates(keywords, NewStartOfficialUpdate.SourceType.CCRS,
                "신복위 공식자료(수집·저장본)에서 검색"));
        ccrs.addAll(searchCcrsKnowledgeItems(keywords));
        if (ccrs.isEmpty()) {
            gaps.add("STEP2 신복위: 저장된 근거 없음 — 신복위 카페(27453843) 라이브 조사는 운영자 승인된 브라우저/세션 조사 필요 (V1 서버 자동화 없음)");
        }

        // STEP 3 — 부실차주 라이브러리 카페 과거 글+댓글 (수집 사례 베이스)
        List<Evidence> borrower = searchEmpiricalCases(keywords);
        if (borrower.isEmpty()) {
            gaps.add("STEP3 부실차주 카페: 키워드 일치 수집 사례 없음 — 질문게시판 원문 자체는 questions 테이블에 보관");
        }

        // STEP 4 — 공식 기준 검증: 게시된 공식 업데이트 + 새출발기금/금감원/공공기관 저장 자료
        List<Evidence> official = searchOfficialUpdates(keywords, null, "공식 기준 검증(게시 상태 공식 업데이트)");

        // STEP 5 — 질문 성격에 따른 추가 공식기관: V1은 자동 조회 없음, 필요 기관만 판정해 남긴다.
        List<Evidence> extra = extraAgencyMarkers(keywords, gaps);

        return ResearchPack.builder()
                .question(questionText)
                .internalHits(internal)
                .ccrsHits(ccrs)
                .borrowerHits(borrower)
                .officialHits(official)
                .extraAgencyHits(extra)
                .gapNotes(gaps)
                .build();
    }

    // ── internal query helpers ───────────────────────────────────────────

    List<String> keywordsOf(String text) {
        if (text == null) return List.of();
        return java.util.Arrays.stream(text.replaceAll("[^가-힣a-zA-Z0-9]", " ").split("\\s+"))
                .filter(s -> s.length() >= 2)
                .distinct()
                .limit(12)
                .toList();
    }

    List<NewstartKnowledgeItem> searchActiveKnowledge(List<String> keywords) {
        if (keywords.isEmpty()) return List.of();
        Specification<NewstartKnowledgeItem> spec = (root, query, cb) -> {
            List<Predicate> kws = new ArrayList<>();
            for (String kw : keywords) {
                String p = "%" + kw + "%";
                kws.add(cb.or(
                        cb.like(root.get("title"), p),
                        cb.like(root.get("content"), p),
                        cb.like(root.get("tags"), p),
                        cb.like(root.get("category"), p)));
            }
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(cb.equal(root.get("status"), NewstartKnowledgeItem.KnowledgeStatus.ACTIVE),
                    cb.or(kws.toArray(new Predicate[0])));
        };
        return knowledgeRepository.findAll(spec, PageRequest.of(0, PER_STEP_LIMIT)).getContent();
    }

    Evidence toKnowledgeEvidence(NewstartKnowledgeItem item) {
        SourceClass cls = classifyKnowledge(item);
        return Evidence.builder()
                .stepNo(STEP_INTERNAL)
                .sourceClass(cls)
                .refType(RefType.KNOWLEDGE_ITEM)
                .refId(String.valueOf(item.getKnowledgeId()))
                .refUrl(item.getSourceUrl())
                .title(item.getTitle())
                .excerpt(firstChars(item.getSummary() != null ? item.getSummary() : item.getContent(), 300))
                .note("sourceType=" + item.getSourceType() + ", authority=" + item.getAuthority())
                .build();
    }

    /** 커뮤니티 자료를 공식 규정으로 잘못 붙이지 않도록 출처별 분류를 강제한다. */
    static SourceClass classifyKnowledge(NewstartKnowledgeItem item) {
        NewstartKnowledgeItem.SourceType t = item.getSourceType();
        String url = item.getSourceUrl() == null ? "" : item.getSourceUrl();
        return switch (t) {
            case OFFICIAL_CURRENT, OFFICIAL_HISTORICAL, AUDIT, DERIVED_RULE -> SourceClass.OFFICIAL_RULE;
            case EBOOK, OPERATOR_KNOWLEDGE -> SourceClass.OPERATOR_MATERIAL;
            case CAFE_ARTICLE, CAFE_COMMENT -> url.contains("newhopero") ? SourceClass.BORROWER_CAFE_CASE : SourceClass.CCRS_CAFE_CASE;
            case CASE -> SourceClass.INTERNAL_CASE;
        };
    }

    List<Evidence> searchCcrsKnowledgeItems(List<String> keywords) {
        if (keywords.isEmpty()) return List.of();
        Specification<NewstartKnowledgeItem> spec = (root, query, cb) -> {
            List<Predicate> kws = new ArrayList<>();
            for (String kw : keywords) {
                String p = "%" + kw + "%";
                kws.add(cb.or(cb.like(root.get("title"), p), cb.like(root.get("content"), p)));
            }
            Predicate ccrs = cb.or(
                    cb.like(root.get("authority"), "%신용회복%"),
                    cb.like(root.get("sourceUrl"), "%ccrs%"),
                    cb.like(root.get("category"), "%신복위%"));
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(cb.equal(root.get("status"), NewstartKnowledgeItem.KnowledgeStatus.ACTIVE),
                    ccrs, cb.or(kws.toArray(new Predicate[0])));
        };
        return knowledgeRepository.findAll(spec, PageRequest.of(0, PER_STEP_LIMIT)).stream()
                .map(item -> Evidence.builder()
                        .stepNo(STEP_CCRS)
                        .sourceClass(classifyKnowledge(item))
                        .refType(RefType.KNOWLEDGE_ITEM)
                        .refId(String.valueOf(item.getKnowledgeId()))
                        .refUrl(item.getSourceUrl())
                        .title(item.getTitle())
                        .excerpt(firstChars(item.getSummary(), 300))
                        .note("신복위 계열 저장 자료")
                        .build())
                .toList();
    }

    List<Evidence> searchOfficialUpdates(List<String> keywords, NewStartOfficialUpdate.SourceType typeRestriction, String note) {
        if (keywords.isEmpty()) return List.of();
        Specification<NewStartOfficialUpdate> spec = (root, query, cb) -> {
            List<Predicate> kws = new ArrayList<>();
            for (String kw : keywords) {
                String p = "%" + kw + "%";
                kws.add(cb.or(cb.like(root.get("title"), p), cb.like(root.get("summary"), p), cb.like(root.get("content"), p)));
            }
            List<Predicate> base = new ArrayList<>();
            base.add(cb.equal(root.get("reviewStatus"), NewStartOfficialUpdate.ReviewStatus.PUBLISHED));
            if (typeRestriction != null) {
                base.add(cb.equal(root.get("sourceType"), typeRestriction));
            }
            query.orderBy(cb.desc(root.get("createdAt")));
            base.add(cb.or(kws.toArray(new Predicate[0])));
            return cb.and(base.toArray(new Predicate[0]));
        };
        return officialUpdateRepository.findAll(spec, PageRequest.of(0, PER_STEP_LIMIT)).stream()
                .map(u -> Evidence.builder()
                        .stepNo(STEP_OFFICIAL)
                        .sourceClass(SourceClass.OFFICIAL_RULE)
                        .refType(RefType.OFFICIAL_UPDATE)
                        .refId(String.valueOf(u.getUpdateId()))
                        .refUrl(u.getOriginalUrl())
                        .title("[" + u.getSourceName() + "] " + u.getTitle())
                        .excerpt(firstChars(u.getSummary(), 300))
                        .note(note)
                        .build())
                .toList();
    }

    List<Evidence> searchEmpiricalCases(List<String> keywords) {
        if (keywords.isEmpty()) return List.of();
        Specification<NewstartEmpiricalCase> spec = (root, query, cb) -> {
            List<Predicate> kws = new ArrayList<>();
            for (String kw : keywords) {
                String p = "%" + kw + "%";
                kws.add(cb.or(
                        cb.like(root.get("title"), p),
                        cb.like(root.get("contentRaw"), p),
                        cb.like(root.get("keyInsights"), p),
                        cb.like(root.get("specialCircumstances"), p)));
            }
            query.orderBy(cb.desc(root.get("writtenAt")));
            return cb.or(kws.toArray(new Predicate[0]));
        };
        return empiricalCaseRepository.findAll(spec, PageRequest.of(0, PER_STEP_LIMIT)).stream()
                .map(c -> Evidence.builder()
                        .stepNo(STEP_BORROWER)
                        .sourceClass(SourceClass.BORROWER_CAFE_CASE)
                        .refType(RefType.EMPIRICAL_CASE)
                        .refId(c.getArticleId())
                        .refUrl(c.getSourceUrl())
                        .title(c.getTitle())
                        .excerpt(firstChars(c.getKeyInsights() != null ? c.getKeyInsights() : c.getContentRaw(), 300))
                        .note("감면율 사례: 예측 " + c.getPredictedReductionRate() + " / 최종 " + c.getFinalReductionRate())
                        .build())
                .toList();
    }

    /** STEP5: V1 자동화 없음 — 질문이 가리키는 추가 공식기관을 기록만 남긴다. */
    List<Evidence> extraAgencyMarkers(List<String> keywords, List<String> gaps) {
        List<Evidence> out = new ArrayList<>();
        if (anyContains(keywords, "금감원", "금융감독원", "민원")) {
            out.add(marker("금융감독원(공식) 수동 확인 필요"));
        }
        if (anyContains(keywords, "법원", "지급명령", "추심", "개인회생", "파산")) {
            out.add(marker(" 대한민국 법원 전자소송포털 수동 확인 필요"));
        }
        if (anyContains(keywords, "신용정보", "credit4u", "Credit4U", "연체기록")) {
            out.add(marker("한국신용정보원/Credit4U 수동 확인 필요"));
        }
        if (anyContains(keywords, "금융위", "위원회")) {
            out.add(marker("금융위원회(공식) 수동 확인 필요"));
        }
        if (!out.isEmpty()) {
            gaps.add("STEP5 추가 공식기관: V1 서버 자동 조회 없음 — 게시 전 운영자 확인");
        }
        return out;
    }

    private Evidence marker(String note) {
        return Evidence.builder()
                .stepNo(STEP_EXTRA)
                .sourceClass(SourceClass.OTHER)
                .refType(RefType.NONE)
                .title("추가 공식기관 확인 필요")
                .excerpt(null)
                .note(note)
                .build();
    }

    private static boolean anyContains(List<String> keywords, String... needles) {
        for (String kw : keywords) {
            for (String n : needles) {
                if (kw.equalsIgnoreCase(n)) return true;
            }
        }
        return false;
    }

    static String firstChars(String s, int n) {
        if (s == null) return null;
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    // 조사 단계 번호 (JipijigiResearchTrace의 STEP 상수와 동일 값)
    static final int STEP_INTERNAL = JipijigiResearchTrace.STEP_INTERNAL;
    static final int STEP_CCRS = JipijigiResearchTrace.STEP_CCRS;
    static final int STEP_BORROWER = JipijigiResearchTrace.STEP_BORROWER;
    static final int STEP_OFFICIAL = JipijigiResearchTrace.STEP_OFFICIAL;
    static final int STEP_EXTRA = JipijigiResearchTrace.STEP_EXTRA_AGENCIES;
}
