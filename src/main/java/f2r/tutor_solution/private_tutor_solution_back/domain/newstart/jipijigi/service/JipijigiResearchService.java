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
import java.util.Comparator;
import java.util.List;

/**
 * 지피지기 Agent 강제 조사 사다리 (STEP1..5).
 *
 * <p>모델 자체 지식으로 답변하지 않는다: 모든 근거는 실제 NewStart DB 조회 결과만 사용하며,
 * 조회 결과 없는 단계는 "부족/미확인"으로 명시한다(추측·일반화 금지).
 *
 * <p>STEP1에는 NewStart 지식/사례와 함께 운영자가 승인한 축적 답변 세션
 * ({@link JipijigiAnswerSessionService})이 포함된다. 축적 DB는 외부 카페 검색(STEP2·STEP3)보다 먼저 조회하고,
 * 내부 근거가 충분하면 외부 카페 검색은 생략한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JipijigiResearchService {

    private static final int PER_STEP_LIMIT = 8;
    /** 이 미만이면 "내부 자료 부족" → 외부 사례조사 단계로 진행한다. */
    static final int INTERNAL_SUFFICIENT_THRESHOLD = 3;
    /** STEP1 ACTIVE 지식 후보 창. 117건 전량을 다 보지 않고 후보만 넓게 잡아 순위 매긴 뒤 {@value #PER_STEP_LIMIT}건만 쓴다. */
    static final int KNOWLEDGE_CANDIDATE_WINDOW = PER_STEP_LIMIT * 10;

    private final NewstartKnowledgeItemRepository knowledgeRepository;
    private final NewstartEmpiricalCaseRepository empiricalCaseRepository;
    private final NewStartOfficialUpdateRepository officialUpdateRepository;
    private final JipijigiAnswerSessionService answerSessions;

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
        /** 이 근거에서 실제로 일치한 질문 어휘(축약 후). 검색 감사용. */
        @Builder.Default
        private final List<String> matchedTerms = List.of();
        /** {@code NewstartKnowledgeService.computeWarnings}에서 파생된 위험 코드 — ACTIVE라고 신뢰도가 같지 않다. */
        @Builder.Default
        private final List<String> riskCodes = List.of();
        /** 질문 대비 적합도(어휘 가중치 + 슬롯 가점 + 결과/행동 기록 − 위험 감점). */
        private final int score;
    }

    @Getter
    @Builder
    public static class ResearchPack {
        private final String question;
        private final List<Evidence> internalHits;     // STEP1 NewStart 내부 DB/RAG + 축적 답변 세션
        private final List<Evidence> ccrsHits;         // STEP2 신복위 (우선)
        private final List<Evidence> borrowerHits;     // STEP3 부실차주 라이브러리 카페
        private final List<Evidence> officialHits;     // STEP4 공식 기준 검증
        private final List<Evidence> extraAgencyHits;  // STEP5 추가 공식기관 (V1: 수동 표기)
        private final List<String> gapNotes;           // 부족·실패·수동조사 필요 사유
        /** STEP2/STEP3 외부 카페 검색을 실제로 수행했는지 (생략 시 false). */
        private final boolean externalSearched;
        /** 질문 의도. {@code null}이면 미판정(기존 공식 우선 조립 유지). */
        private final JipijigiIntent intent;
        /** 의도·사건 해석 결과(감사 로그·답변 정책용). */
        private final JipijigiQuestionFrame frame;
        /** DB에서 "정확히 같은 사건" 사례를 찾았는지. false면 답변이 없는 사례를 지어내면 안 된다. */
        private final boolean exactExperienceFound;

        public boolean internalInsufficient() {
            return internalHits.size() < INTERNAL_SUFFICIENT_THRESHOLD;
        }
    }

    public ResearchPack research(String questionText) {
        List<String> keywords = keywordsOf(questionText);
        // 사건어는 문장 해석에서 복원한다 — keywordsOf의 토큰 12개 제한이 "기한이익상실/이자/연체"를
        // 잘라내 대표 질문의 실경험 근거가 질의 자체에서 사라졌던 문제가 여기 시작점에 있다.
        JipijigiQuestionFrame frame = JipijigiQuestionFrame.analyze(questionText, keywords);
        List<String> queryTerms = frame.queryTerms();
        List<String> gaps = new ArrayList<>();
        gaps.add("INTENT=" + frame.intent() + " | 사건어=" + frame.eventTerms()
                + " | 사건 질의=" + frame.searchQueries());

        // STEP 1 — NewStart 내부 DB/RAG + 축적된 운영자 승인 답변 세션 (항상 먼저)
        List<Evidence> internal = new ArrayList<>();
        boolean exactExperience = false;
        for (Evidence evidence : searchActiveKnowledge(frame, keywords)) {
            exactExperience |= frame.exactEventMatch(evidence.getTitle() + " " + nullToEmpty(evidence.getExcerpt()));
            internal.add(evidence);
        }
        for (Evidence session : answerSessions.searchAcceptedEvidence(keywords)) {
            internal.add(session);
            exactExperience |= frame.exactEventMatch(
                    nullToEmpty(session.getTitle()) + " " + nullToEmpty(session.getExcerpt()));
        }
        // 사례·경험 의도에서는 내부 ACTIVE 지식(대부분 공식·기준 글)이 3건을 채워도 외부 사례 조사를
        // 생략하지 않는다 — 생략되면 실경험 근거가 컨텍스트에서 통째로 빠진다.
        boolean needExternal = internal.size() < INTERNAL_SUFFICIENT_THRESHOLD || frame.experienceFirst();
        if (internal.size() < INTERNAL_SUFFICIENT_THRESHOLD) {
            gaps.add("STEP1 내부 ACTIVE 지식·축적 답변 근거 " + internal.size() + "건(기준 " + INTERNAL_SUFFICIENT_THRESHOLD
                    + "건 미만) → 외부 사례조사 단계 필수");
        }

        // STEP 2 — 외부 카페 검색. 내부 근거가 충분하면 생략한다(운영원칙 §5).
        // STEP 3·4는 우리 DB 조회이므로 생략 대상이 아니다 — STEP 4와 같은 이유로 항상 수행한다.
        List<Evidence> ccrs = new ArrayList<>();
        List<Evidence> borrower = new ArrayList<>();
        boolean externalSearched = false;
        if (needExternal) {
            externalSearched = true;
            // STEP 2 — 신복위: DB에 들어온 신복위 자료만 사용. 라이브 카페 자동조회는 V1 미구현.
            ccrs.addAll(searchOfficialUpdates(frame, keywords, NewStartOfficialUpdate.SourceType.CCRS,
                    "신복위 공식자료(수집·저장본)에서 검색"));
            ccrs.addAll(searchCcrsKnowledgeItems(frame, keywords));
            if (ccrs.isEmpty()) {
                gaps.add("STEP2 신복위: 저장된 근거 없음 — 신복위 카페(27453843) 라이브 조사는 운영자 승인된 브라우저/세션 조사 필요 (V1 서버 자동화 없음)");
            }
        } else {
            gaps.add("STEP2 신복위 외부 검색 생략 사유: 내부 근거 충분 (" + internal.size() + "건 ≥ 기준 "
                    + INTERNAL_SUFFICIENT_THRESHOLD + "건)");
        }

        // STEP 3 — 부실차주 라이브러리 카페 과거 글+댓글 (article_json 정본 수집 사례 베이스).
        // 내부 ACTIVE 근거가 충분해도 후순위 담보·임시안·납부 같은 실경험이 컨텍스트에서
        // 완전히 사라지지 않도록 항상 조회한다(2026-09-28 LIVE QA에서 생략 경로 실측).
        borrower = searchEmpiricalCases(frame, keywords);
        for (Evidence e : borrower) {
            exactExperience |= frame.exactEventMatch(e.getTitle() + " " + nullToEmpty(e.getExcerpt()));
        }
        if (borrower.isEmpty()) {
            gaps.add("STEP3 부실차주 카페: 키워드 일치 수집 사례 없음 — 질문게시판 원문 자체는 questions 테이블에 보관");
        }
        if (!exactExperience) {
            gaps.add("STEP3 정확히 같은 사건 사례 미발견 → 답변은 가장 가까운 사례의 유사점/차이점만 말하고 없는 사례를 지어내지 않는다");
        }

        // STEP 4 — 공식 기준 검증: 게시된 공식 업데이트 + 새출발기금/금감원/공공기관 저장 자료
        List<Evidence> official = searchOfficialUpdates(frame, keywords, null, "공식 기준 검증(게시 상태 공식 업데이트)");

        // STEP 5 — 질문 성격에 따른 추가 공식기관: V1은 자동 조회 없음, 필요 기관만 판정해 남긴다.
        List<Evidence> extra = extraAgencyMarkers(queryTerms.isEmpty() ? keywords : queryTerms, gaps);

        return ResearchPack.builder()
                .question(questionText)
                .internalHits(internal)
                .ccrsHits(ccrs)
                .borrowerHits(borrower)
                .officialHits(official)
                .extraAgencyHits(extra)
                .gapNotes(gaps)
                .externalSearched(externalSearched)
                .intent(frame.intent())
                .frame(frame)
                .exactExperienceFound(exactExperience)
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

    /**
     * STEP1 ACTIVE 지식 질의. OR LIKE 항은 사건 질의의 합집합(사건어 + 레거시 토큰) 하나로 내보내고,
    /**
     * STEP1 ACTIVE 지식 질의. OR LIKE 항은 사건 질의의 합집합(사건어 + 레거시 토큰) 하나로 내보내고,
     * 순위는 메모리에서 어휘 가중치·슬롯 가점·위험 감점으로 계산한다(1 단계 = 저장소 호출 1회 유지).
     */
    List<Evidence> searchActiveKnowledge(JipijigiQuestionFrame frame, List<String> keywords) {
        List<String> terms = termsOrKeywords(frame, keywords);
        if (terms.isEmpty()) return List.of();
        Specification<NewstartKnowledgeItem> spec = (root, query, cb) -> {
            List<Predicate> kws = new ArrayList<>();
            for (String kw : terms) {
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
        List<Ranked> ranked = new ArrayList<>();
        // 최신순 절취(옛 PER_STEP_LIMIT 상한) 대신 후보 창을 넓혀 가져온 뒤 적합도 순으로 다시 고른다.
        for (NewstartKnowledgeItem item : knowledgeRepository.findAll(spec,
                PageRequest.of(0, KNOWLEDGE_CANDIDATE_WINDOW)).getContent()) {
            SourceClass cls = classifyKnowledge(item);
            String text = knowledgeText(item);
            List<String> risks = JipijigiEvidenceScorer.riskCodes(item);
            ranked.add(new Ranked(Evidence.builder()
                    .stepNo(STEP_INTERNAL)
                    .sourceClass(cls)
                    .refType(RefType.KNOWLEDGE_ITEM)
                    .refId(String.valueOf(item.getKnowledgeId()))
                    .refUrl(item.getSourceUrl())
                    .title(item.getTitle())
                    .excerpt(firstChars(item.getSummary() != null ? item.getSummary() : item.getContent(), 300))
                    .note("sourceType=" + item.getSourceType() + ", authority=" + item.getAuthority())
                    .matchedTerms(JipijigiEvidenceScorer.matchedTerms(text))
                    .riskCodes(risks)
                    .score(evidenceScore(frame, keywords, text, risks, outcomeBonusOf(text, cls)))
                    .build(), keywordHits(keywords, text), item.getCreatedAt()));
        }
        return topRanked(ranked);
    }

    /** 커뮤니티 자료를 공식 규정으로 잘못 붙이지 않도록 출처별 분류를 강제한다. */
    static SourceClass classifyKnowledge(NewstartKnowledgeItem item) {
        NewstartKnowledgeItem.SourceType t = item.getSourceType();
        String url = item.getSourceUrl() == null ? "" : item.getSourceUrl();
        return switch (t) {
            case OFFICIAL_CURRENT, OFFICIAL_HISTORICAL, AUDIT, DERIVED_RULE -> SourceClass.OFFICIAL_RULE;
            case EBOOK, OPERATOR_KNOWLEDGE -> SourceClass.OPERATOR_MATERIAL;
            case CAFE_ARTICLE, CAFE_COMMENT -> url.contains("newhopero") ? SourceClass.BORROWER_CAFE_CASE : SourceClass.CCRS_CAFE_CASE;
            // 회원 채팅·질문요청에서 갈무리한 항목은 공식 규정이 아니라 내부 경험 자료다(RAG에서
            // "공식 안내에 따르면"으로 인용되는 것을 막기 위해 CASE와 같은 등급으로 둔다).
            case CASE, CHAT_DISCOVERY, REQUEST_DISCOVERY -> SourceClass.INTERNAL_CASE;
        };
    }

    List<Evidence> searchCcrsKnowledgeItems(JipijigiQuestionFrame frame, List<String> keywords) {
        List<String> terms = termsOrKeywords(frame, keywords);
        if (terms.isEmpty()) return List.of();
        Specification<NewstartKnowledgeItem> spec = (root, query, cb) -> {
            List<Predicate> kws = new ArrayList<>();
            for (String kw : terms) {
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
        List<Ranked> ranked = new ArrayList<>();
        for (NewstartKnowledgeItem item : knowledgeRepository.findAll(spec,
                PageRequest.of(0, KNOWLEDGE_CANDIDATE_WINDOW)).getContent()) {
            String text = nullToEmpty(item.getTitle()) + " " + nullToEmpty(item.getContent())
                    + " " + nullToEmpty(item.getSummary());
            List<String> risks = JipijigiEvidenceScorer.riskCodes(item);
            ranked.add(new Ranked(Evidence.builder()
                    .stepNo(STEP_CCRS)
                    .sourceClass(classifyKnowledge(item))
                    .refType(RefType.KNOWLEDGE_ITEM)
                    .refId(String.valueOf(item.getKnowledgeId()))
                    .refUrl(item.getSourceUrl())
                    .title(item.getTitle())
                    .excerpt(firstChars(item.getSummary(), 300))
                    .note("신복위 계열 저장 자료")
                    .matchedTerms(JipijigiEvidenceScorer.matchedTerms(text))
                    .riskCodes(risks)
                    .score(evidenceScore(frame, keywords, text, risks, 0))
                    .build(), keywordHits(keywords, text), item.getCreatedAt()));
        }
        return topRanked(ranked);
    }

    List<Evidence> searchOfficialUpdates(List<String> keywords, NewStartOfficialUpdate.SourceType typeRestriction, String note) {
        return searchOfficialUpdates(JipijigiQuestionFrame.legacy(keywords), keywords, typeRestriction, note);
    }

    List<Evidence> searchOfficialUpdates(JipijigiQuestionFrame frame, List<String> keywords,
                                         NewStartOfficialUpdate.SourceType typeRestriction, String note) {
        List<String> terms = termsOrKeywords(frame, keywords);
        if (terms.isEmpty()) return List.of();
        Specification<NewStartOfficialUpdate> spec = (root, query, cb) -> {
            List<Predicate> kws = new ArrayList<>();
            for (String kw : terms) {
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
        List<Ranked> ranked = new ArrayList<>();
        for (NewStartOfficialUpdate u : officialUpdateRepository.findAll(spec,
                PageRequest.of(0, CASE_CANDIDATE_WINDOW)).getContent()) {
            String text = nullToEmpty(u.getTitle()) + " " + nullToEmpty(u.getSummary())
                    + " " + nullToEmpty(u.getContent());
            // 공식 업데이트는 정책 원문이라 행동/결과 기록 가점을 주지 않는다(사례와 견주지 않게).
            List<String> risks = JipijigiEvidenceScorer.riskCodesForSource(u.getOriginalUrl());
            ranked.add(new Ranked(Evidence.builder()
                    .stepNo(STEP_OFFICIAL)
                    .sourceClass(SourceClass.OFFICIAL_RULE)
                    .refType(RefType.OFFICIAL_UPDATE)
                    .refId(String.valueOf(u.getUpdateId()))
                    .refUrl(u.getOriginalUrl())
                    .title("[" + u.getSourceName() + "] " + u.getTitle())
                    .excerpt(firstChars(u.getSummary(), 300))
                    .note(note)
                    .matchedTerms(JipijigiEvidenceScorer.matchedTerms(text))
                    .riskCodes(risks)
                    .score(evidenceScore(frame, keywords, text, risks, 0))
                    .build(), keywordHits(keywords, text), u.getCreatedAt()));
        }
        return topRanked(ranked);
    }

    /** 사례 후보 창. OR-LIKE 후보가 많아도 질문 키워드 일치 수로 순위 매긴 뒤 {@value #PER_STEP_LIMIT}건만 쓴다. */
    static final int CASE_CANDIDATE_WINDOW = PER_STEP_LIMIT * 20;

    /** 몇 개 키워드가 이 행의 텍스트에 실제로 나타나는지 (중복 키워드 없음 — keywordsOf가 distinct). */
    static int keywordHits(List<String> keywords, String... fields) {
        StringBuilder sb = new StringBuilder();
        for (String f : fields) {
            if (f != null) sb.append(f).append(' ');
        }
        String text = sb.toString();
        int hits = 0;
        for (String kw : keywords) {
            if (text.contains(kw)) hits++;
        }
        return hits;
    }

    List<Evidence> searchEmpiricalCases(List<String> keywords) {
        return searchEmpiricalCases(JipijigiQuestionFrame.legacy(keywords), keywords);
    }

    /**
     * STEP3 수집 사례. 레거시 프레임에서는 기존대로 키워드 일치 수 + 최신순,
     * 해석된 프레임에서는 같은 사건/채무 유형/기관/행동·결과 기록 가점과 위험 감점을 반영해
     * "가입인사"류 최신 무관 글이 실경험을 밀어내지 못하게 한다.
     */
    List<Evidence> searchEmpiricalCases(JipijigiQuestionFrame frame, List<String> keywords) {
        List<String> terms = termsOrKeywords(frame, keywords);
        if (terms.isEmpty()) return List.of();
        Specification<NewstartEmpiricalCase> spec = (root, query, cb) -> {
            List<Predicate> kws = new ArrayList<>();
            for (String kw : terms) {
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
        List<Ranked> ranked = new ArrayList<>();
        for (NewstartEmpiricalCase c : empiricalCaseRepository.findAll(spec,
                PageRequest.of(0, CASE_CANDIDATE_WINDOW)).getContent()) {
            String text = caseText(c);
            List<String> risks = JipijigiEvidenceScorer.riskCodesForSource(c.getSourceUrl());
            int outcome = (frame.hasActionRecord(text) ? JipijigiEvidenceScorer.ACTION_RECORD_BONUS : 0)
                    + (hasOutcomeRecord(c) ? JipijigiEvidenceScorer.RESULT_RECORD_BONUS : 0);
            ranked.add(new Ranked(Evidence.builder()
                    .stepNo(STEP_BORROWER)
                    .sourceClass(SourceClass.BORROWER_CAFE_CASE)
                    .refType(RefType.EMPIRICAL_CASE)
                    .refId(c.getArticleId())
                    .refUrl(c.getSourceUrl())
                    .title(c.getTitle())
                    .excerpt(firstChars(c.getKeyInsights() != null ? c.getKeyInsights() : c.getContentRaw(), 300))
                    .note("감면율 사례: 예측 " + c.getPredictedReductionRate() + " / 최종 " + c.getFinalReductionRate())
                    .matchedTerms(JipijigiEvidenceScorer.matchedTerms(text))
                    .riskCodes(risks)
                    .score(evidenceScore(frame, keywords, text, risks, outcome))
                    .build(), keywordHits(keywords, text), c.getWrittenAt()));
        }
        return topRanked(ranked);
    }

    /** 순위 계산용 중간값 — Evidence 점수 → 키워드 일치 수 → 최신순 순으로 자른다. */
    private record Ranked(Evidence evidence, int keywordHits, java.time.LocalDateTime recency) {
    }

    private static List<Evidence> topRanked(List<Ranked> ranked) {
        return ranked.stream()
                .sorted(Comparator.comparingInt((Ranked r) -> r.evidence().getScore()).reversed()
                        .thenComparingInt(r -> -r.keywordHits())
                        .thenComparing(Ranked::recency, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(PER_STEP_LIMIT)
                .map(Ranked::evidence)
                .toList();
    }

    /**
     * 근거 1건의 적합도 점수. 레거시 프레임은 {@code keywordHits}(기존 계약), 해석 프레임은
     * 어휘 가중치 합 + 질문 슬롯 일치 가점 + 기록 유형 가점에서 위험 감점을 뺀 값이다.
     */
    private static int evidenceScore(JipijigiQuestionFrame frame, List<String> keywords, String text,
                                     List<String> risks, int outcomeBonus) {
        int base = frame.legacy()
                ? keywordHits(keywords, text)
                : JipijigiEvidenceScorer.termScore(text) + frame.alignmentBonus(text) + outcomeBonus;
        return base - JipijigiEvidenceScorer.riskPenalty(risks);
    }

    /** 결과 기록(사후 경과·확정 값)이 있는 사례만 받는 가점. */
    private static boolean hasOutcomeRecord(NewstartEmpiricalCase c) {
        return notBlank(c.getAuthorFollowupResult()) || notBlank(c.getPredictionVsActual())
                || notBlank(c.getManagerPrediction()) || c.getFinalReductionRate() != null;
    }

    /** 지식 항목에서 "이 사람이 실제로 하고 결과를 남겼는지" 신호. */
    private static int outcomeBonusOf(String text, SourceClass cls) {
        if (cls == SourceClass.OFFICIAL_RULE) return 0;
        int bonus = 0;
        if (text.contains("확정") || text.contains("완료") || text.contains("결과")) bonus += JipijigiEvidenceScorer.RESULT_RECORD_BONUS;
        if (text.contains("방문") || text.contains("상담") || text.contains("접수") || text.contains("납부")) {
            bonus += JipijigiEvidenceScorer.ACTION_RECORD_BONUS;
        }
        return bonus;
    }

    private static String knowledgeText(NewstartKnowledgeItem item) {
        return String.join(" ", nullToEmpty(item.getTitle()), nullToEmpty(item.getSummary()),
                nullToEmpty(item.getContent()), nullToEmpty(item.getTags()), nullToEmpty(item.getCategory()));
    }

    private static String caseText(NewstartEmpiricalCase c) {
        return String.join(" ", nullToEmpty(c.getTitle()), nullToEmpty(c.getContentRaw()),
                nullToEmpty(c.getKeyInsights()), nullToEmpty(c.getSpecialCircumstances()),
                nullToEmpty(c.getAuthorFollowupResult()), nullToEmpty(c.getPredictionVsActual()));
    }

    /** 사건 질의어가 있으면 그것들만, 해석 실패(빈 질문 등)하면 레거시 키워드로 되돌린다. */
    private static List<String> termsOrKeywords(JipijigiQuestionFrame frame, List<String> keywords) {
        if (frame != null && !frame.queryTerms().isEmpty()) return frame.queryTerms();
        return keywords == null ? List.of() : keywords;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
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
