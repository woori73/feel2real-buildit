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

        public boolean internalInsufficient() {
            return internalHits.size() < INTERNAL_SUFFICIENT_THRESHOLD;
        }
    }

    public ResearchPack research(String questionText) {
        List<String> keywords = keywordsOf(questionText);
        List<String> gaps = new ArrayList<>();

        // STEP 1 — NewStart 내부 DB/RAG + 축적된 운영자 승인 답변 세션 (항상 먼저)
        List<Evidence> internal = new ArrayList<>();
        for (NewstartKnowledgeItem item : searchActiveKnowledge(keywords)) {
            internal.add(toKnowledgeEvidence(item, keywords));
        }
        internal.addAll(answerSessions.searchAcceptedEvidence(keywords));
        boolean needExternal = internal.size() < INTERNAL_SUFFICIENT_THRESHOLD;
        if (needExternal) {
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
            ccrs.addAll(searchOfficialUpdates(keywords, NewStartOfficialUpdate.SourceType.CCRS,
                    "신복위 공식자료(수집·저장본)에서 검색"));
            ccrs.addAll(searchCcrsKnowledgeItems(keywords));
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
        borrower = searchEmpiricalCases(keywords);
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
                .externalSearched(externalSearched)
                .build();
    }

    // ── internal query helpers ───────────────────────────────────────────

    /** 질문 의도와 무관하게 거의 모든 글에 나오는 말 — OR-LIKE 후보를 무관 자료로 채우는 원인이라 키워드에서 뺀다. */
    private static final java.util.Set<String> STOPWORDS = java.util.Set.of(
            "어떻게", "어떤", "무엇", "언제", "어디", "어디서", "얼마", "하나", "하나요", "해야", "할까", "될까", "되나", "되나요",
            "있나", "있나요", "있는", "있는데", "없는", "없나", "받았", "받았는데", "했는데", "하는데", "인데", "이런", "그런",
            "계속", "진행", "질문", "궁금", "궁금합니다", "알려", "알려주세요", "관련", "경우", "때문", "정말", "혹시", "지금", "현재",
            "이후", "이전", "그리고", "하지만", "합니다", "입니다", "있습니다", "대한", "위해", "통해", "가능", "가능한가", "가능한지",
            "온다", "왔는데", "했나", "했는지", "됐는지", "되는지");

    private static final String[] PARTICLES = {
            "으로부터", "에서는", "에게서", "이라고", "라고", "으로", "에서", "에게", "부터", "까지", "처럼", "한테", "이나", "이면", "라면",
            "이며", "이고", "하면", "하고", "은요", "는요", "를", "을", "은", "는", "이", "가", "의", "에", "도", "만", "로", "과", "와", "고", "며"};

    /** 질문 표현이 자료 본문의 용어와 달라 LIKE에 걸리지 않는 흔한 경우 — 질문 문장에 단서가 있을 때만 붙는다. */
    private static final String[][] SYNONYMS = {
            // {단서(|구분), 붙일 키워드...}
            {"차량|자동차|자가용|승용차|차를|차가|차는", "차량", "자동차"},
            {"전화|독촉|문자가", "추심", "독촉"},
            {"기한이익", "기한이익"},
            {"지급명령", "전자소송", "지급명령"},
            {"장기연체|연체 등록|연체등록", "장기연체", "Credit4U", "신용정보원"},
            {"신복위", "신용회복위원회"},
            {"부실우려", "부실우려", "실효"},
            {"후순위", "후순위", "담보"}};

    List<String> keywordsOf(String text) {
        if (text == null) return List.of();
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String raw : text.replaceAll("[^가-힣a-zA-Z0-9]", " ").split("\\s+")) {
            String t = stripParticle(raw);
            if (t.length() < 2 || STOPWORDS.contains(t) || STOPWORDS.contains(raw)) continue;
            out.add(t);
        }
        for (String[] syn : SYNONYMS) {
            for (String trigger : syn[0].split("\\|")) {
                if (text.contains(trigger)) {
                    for (int i = 1; i < syn.length; i++) out.add(syn[i]);
                    break;
                }
            }
        }
        return out.stream().limit(16).toList();
    }

    /** 조사(을/를/에서 등) 한 겹만 벗긴다 — 벗긴 뒤 2글자 미만이면 원형 유지. */
    static String stripParticle(String token) {
        for (String p : PARTICLES) {
            if (token.length() - p.length() >= 2 && token.endsWith(p)) return token.substring(0, token.length() - p.length());
        }
        return token;
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
        // 최신순 8건만 자르면 흔한 단어가 걸린 무관 자료가 질문과 직접 맞는 자료를 밀어낸다 — 후보 창에서 키워드 일치 수로 순위.
        return knowledgeRepository.findAll(spec, PageRequest.of(0, CASE_CANDIDATE_WINDOW)).getContent().stream()
                .sorted(Comparator.comparingInt((NewstartKnowledgeItem k) ->
                        keywordHits(keywords, k.getTitle(), k.getTags(), k.getCategory()) * 2
                                + keywordHits(keywords, k.getContent())).reversed())
                .limit(PER_STEP_LIMIT)
                .toList();
    }

    Evidence toKnowledgeEvidence(NewstartKnowledgeItem item) {
        return toKnowledgeEvidence(item, List.of());
    }

    Evidence toKnowledgeEvidence(NewstartKnowledgeItem item, List<String> keywords) {
        SourceClass cls = classifyKnowledge(item);
        return Evidence.builder()
                .stepNo(STEP_INTERNAL)
                .sourceClass(cls)
                .refType(RefType.KNOWLEDGE_ITEM)
                .refId(String.valueOf(item.getKnowledgeId()))
                .refUrl(item.getSourceUrl())
                .title(item.getTitle())
                .excerpt(caseExcerpt(item.getSummary(), item.getContent(), keywords))
                .note("sourceType=" + item.getSourceType() + ", authority=" + item.getAuthority()
                        + (item.getSourceDate() != null ? ", 자료시점=" + item.getSourceDate() : ""))
                .build();
    }

    /** 커뮤니티 자료를 공식 규정으로 잘못 붙이지 않도록 출처별 분류를 강제한다. */
    static SourceClass classifyKnowledge(NewstartKnowledgeItem item) {
        NewstartKnowledgeItem.SourceType t = item.getSourceType();
        return switch (t) {
            case OFFICIAL_CURRENT, OFFICIAL_HISTORICAL, AUDIT, DERIVED_RULE -> SourceClass.OFFICIAL_RULE;
            case EBOOK, OPERATOR_KNOWLEDGE -> SourceClass.OPERATOR_MATERIAL;
            case CAFE_ARTICLE, CAFE_COMMENT -> classifyCafeCase(item);
            // 회원 채팅·질문요청에서 갈무리한 항목은 공식 규정이 아니라 내부 경험 자료다(RAG에서
            // "공식 안내에 따르면"으로 인용되는 것을 막기 위해 CASE와 같은 등급으로 둔다).
            case CASE, CHAT_DISCOVERY, REQUEST_DISCOVERY -> SourceClass.INTERNAL_CASE;
        };
    }

    /**
     * sourceUrl이 비어 있어도(카페 원문 URL 미확보 등록 지침) authority/category로 부실차주
     * 카페 자료를 신복위 카페로 오분류하지 않는다. 판별 불가 시 기존 기본값(CCRS)을 유지한다.
     */
    private static SourceClass classifyCafeCase(NewstartKnowledgeItem item) {
        String url = item.getSourceUrl() == null ? "" : item.getSourceUrl();
        if (url.contains("newhopero")) return SourceClass.BORROWER_CAFE_CASE;
        String hay = url + " " + (item.getAuthority() == null ? "" : item.getAuthority())
                + " " + (item.getCategory() == null ? "" : item.getCategory());
        if (hay.contains("ccrs") || hay.contains("신복위") || hay.contains("신용회복")) return SourceClass.CCRS_CAFE_CASE;
        if (hay.contains("부실차주")) return SourceClass.BORROWER_CAFE_CASE;
        return SourceClass.CCRS_CAFE_CASE;
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
        return empiricalCaseRepository.findAll(spec, PageRequest.of(0, CASE_CANDIDATE_WINDOW)).stream()
                // 무순위 최신순이면 "가입인사" 같은 무관 글이 8건을 채워 실경험 근거가 사라진다(LOCAL E2E 실측).
                .sorted(Comparator.comparingInt((NewstartEmpiricalCase c) ->
                        keywordHits(keywords, c.getTitle(), c.getContentRaw(), c.getKeyInsights(), c.getSpecialCircumstances())).reversed()
                        .thenComparing(NewstartEmpiricalCase::getWrittenAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(PER_STEP_LIMIT)
                .map(c -> Evidence.builder()
                        .stepNo(STEP_BORROWER)
                        .sourceClass(SourceClass.BORROWER_CAFE_CASE)
                        .refType(RefType.EMPIRICAL_CASE)
                        .refId(c.getArticleId())
                        .refUrl(c.getSourceUrl())
                        .title(c.getTitle())
                        .excerpt(caseExcerpt(c.getKeyInsights(), c.getContentRaw(), keywords))
                        .note(caseNote(c))
                        .build())
                .toList();
    }

    private static String caseNote(NewstartEmpiricalCase c) {
        StringBuilder sb = new StringBuilder("카페 실증 사례");
        if (c.getWrittenAt() != null) sb.append(", 작성 ").append(c.getWrittenAt().toLocalDate());
        if (c.getFinalReductionRate() != null || c.getPredictedReductionRate() != null) {
            sb.append(", 감면율 예측 ").append(c.getPredictedReductionRate()).append(" / 최종 ").append(c.getFinalReductionRate());
        }
        return sb.toString();
    }

    static final int CASE_EXCERPT_CHARS = 420;

    /**
     * 사례 발췌: 정리된 요약(있으면)을 앞에 두고 본문에서는 질문 키워드가 가장 많이 몰린 구간을 잘라 붙인다.
     * 앞 300자만 쓰면 "실제로 어떻게 진행·대응했는가"가 있는 뒷부분과 운영자 답변 댓글이 잘려 나간다.
     */
    static String caseExcerpt(String summary, String body, List<String> keywords) {
        String head = summary == null ? "" : summary.strip();
        String headPart = head.length() > 160 ? head.substring(0, 160) + "…" : head;
        if (body == null || body.isBlank()) return firstChars(head, CASE_EXCERPT_CHARS);
        String b = body.strip();
        int budget = CASE_EXCERPT_CHARS - headPart.length();
        String bodyPart;
        if (b.length() <= budget) {
            bodyPart = b;
        } else {
            int bestStart = 0;
            int bestHits = -1;
            int step = Math.max(40, budget / 4);
            for (int st = 0; st < b.length(); st += step) {
                int hits = keywordHits(keywords, b.substring(st, Math.min(b.length(), st + budget)));
                if (hits > bestHits) {
                    bestHits = hits;
                    bestStart = st;
                }
            }
            int end = Math.min(b.length(), bestStart + budget);
            bodyPart = (bestStart > 0 ? "…" : "") + b.substring(bestStart, end) + (end < b.length() ? "…" : "");
        }
        if (headPart.isEmpty() || bodyPart.contains(head)) return bodyPart;
        return headPart + " / " + bodyPart;
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
