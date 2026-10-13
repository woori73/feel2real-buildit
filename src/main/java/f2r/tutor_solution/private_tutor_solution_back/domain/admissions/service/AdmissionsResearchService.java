package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.config.AdmissionsEmbeddingProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsConflict;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsOfficialMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsConflictRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsOfficialMaterialRepository;
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
 * 입시상담 가이드 강제 조사 사다리 (STEP1..5).
 *
 * <p>지피지기의 {@code JipijigiResearchService} 구조를 입시 도메인으로 재구성:
 * <ul>
 *   <li>STEP1: 입시 DB — ACTIVE + OFFICIAL + 확정(2027) 자료 우선</li>
 *   <li>STEP2: 입시 DB — ACTIVE + OFFICIAL + 계획(2028) 자료 ("계획 단계" 명시)</li>
 *   <li>STEP3: 공식자료 원문 (admissions_official_materials)</li>
 *   <li>STEP4: 유튜브 의견 — 별도 섹션으로만 표시, 확정 사실 답변에 사용 금지</li>
 *   <li>STEP5: 충돌대장 — OPEN 항목은 "확인 중" 안내</li>
 * </ul>
 *
 * <p>모델 자체 지식으로 답변하지 않는다: 모든 근거는 실제 입시 DB 조회 결과만 사용.
 * NewStart 지피지기 DB를 검색하거나 NewStart 상담 데이터를 참조하지 않는다.
 *
 * <p>검증 보류·충돌·출처 미확인 자료가 확정 사실로 답변에 사용되지 않도록 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdmissionsResearchService {

    private static final int PER_STEP_LIMIT = 8;
    private static final int CASE_CANDIDATE_WINDOW = PER_STEP_LIMIT * 20;

    // 조사 단계 번호
    static final int STEP_CONFIRMED = 1;   // 2027 확정 공식자료
    static final int STEP_PLANNED = 2;     // 2028 계획 공식자료
    static final int STEP_OFFICIAL_DOC = 3; // 공식자료 원문
    static final int STEP_YOUTUBE = 4;     // 유튜브 의견 (별도 분류)
    static final int STEP_CONFLICT = 5;    // 충돌대장

    private final AdmissionsKnowledgeItemRepository knowledgeRepository;
    private final AdmissionsOfficialMaterialRepository materialRepository;
    private final AdmissionsConflictRepository conflictRepository;
    private final AdmissionsEmbeddingProperties embeddingProps;
    private final AdmissionsEmbeddingClient embeddingClient;
    private final AdmissionsVectorIndex vectorIndex;

    @Getter
    @Builder
    public static class Evidence {
        private final int stepNo;
        private final SourceClass sourceClass;
        private final String refId;
        private final String refUrl;
        private final String title;
        private final String excerpt;
        private final String note;
        /** 적용 학년도 (2027/2028). */
        private final Integer academicYear;
        /** true=확정자료, false=계획자료. */
        private final Boolean yearConfirmed;
    }

    public enum SourceClass {
        /** 공식 모집요강·입학처 자료 (확정) */
        OFFICIAL_CONFIRMED,
        /** 공식 자료 (계획 단계 — 2028) */
        OFFICIAL_PLANNED,
        /** 공식자료 원문 */
        OFFICIAL_DOCUMENT,
        /** 유튜브 의견 — 확정 사실 답변에 사용 금지 */
        YOUTUBE_OPINION,
        /** 충돌 중인 정보 — "확인 중" 안내 */
        CONFLICT_INFO
    }

    @Getter
    @Builder
    public static class ResearchPack {
        private final String question;
        private final List<Evidence> confirmedHits;  // STEP1 2027 확정
        private final List<Evidence> plannedHits;    // STEP2 2028 계획
        private final List<Evidence> officialDocHits; // STEP3 원문
        private final List<Evidence> youtubeHits;    // STEP4 유튜브 (별도)
        private final List<Evidence> conflictHits;   // STEP5 충돌
        private final List<String> gapNotes;
        /** 확정 자료가 있는지 (없으면 "모른다"고 답변). */
        public boolean hasConfirmedEvidence() {
            return !confirmedHits.isEmpty();
        }
    }

    public ResearchPack research(String questionText) {
        return research(questionText, null);
    }

    /**
     * @param academicYearFilter null이면 전체, 2027 또는 2028로 필터.
     */
    public ResearchPack research(String questionText, Integer academicYearFilter) {
        List<String> keywords = keywordsOf(questionText);
        List<String> gaps = new ArrayList<>();

        // STEP1 — 2027 확정 공식자료 (ACTIVE + OFFICIAL + year_confirmed=TRUE)
        List<Evidence> confirmed = searchConfirmedKnowledge(questionText, keywords, academicYearFilter);
        if (confirmed.isEmpty()) {
            gaps.add("STEP1 2027 확정 공식자료: 키워드 일치 ACTIVE 자료 없음");
        }

        // STEP2 — 2028 계획 공식자료 (ACTIVE + OFFICIAL + year_confirmed=FALSE)
        List<Evidence> planned = searchPlannedKnowledge(questionText, keywords, academicYearFilter);
        if (!planned.isEmpty()) {
            gaps.add("STEP2 2028 계획자료 " + planned.size() + "건 — 답변 시 '계획 단계' 명시 필수");
        }

        // STEP3 — 공식자료 원문
        List<Evidence> officialDocs = searchOfficialMaterials(keywords, academicYearFilter);

        // STEP4 — 유튜브 의견 (별도 분류, 확정 답변에 사용 금지)
        List<Evidence> youtube = searchYoutubeKnowledge(keywords, academicYearFilter);
        if (!youtube.isEmpty()) {
            gaps.add("STEP4 유튜브 의견 " + youtube.size() + "건 — 보조 설명으로만 사용, 확정 사실 인용 금지");
        }

        // STEP5 — 충돌대장 (OPEN)
        List<Evidence> conflicts = searchOpenConflicts(keywords, academicYearFilter);
        if (!conflicts.isEmpty()) {
            gaps.add("STEP5 충돌 " + conflicts.size() + "건 — 관련 주제는 확정 답변 금지, '확인 중' 안내");
        }

        return ResearchPack.builder()
                .question(questionText)
                .confirmedHits(confirmed)
                .plannedHits(planned)
                .officialDocHits(officialDocs)
                .youtubeHits(youtube)
                .conflictHits(conflicts)
                .gapNotes(gaps)
                .build();
    }

    // ── keyword helpers (지피지기 패턴) ──────────────────────────────

    private static final java.util.Set<String> STOPWORDS = java.util.Set.of(
            "어떻게", "어떤", "무엇", "언제", "어디", "얼마", "하나요", "해야", "할까",
            "있나", "있나요", "있는", "없나", "궁금", "궁금합니다", "알려", "알려주세요",
            "관련", "경우", "정말", "혹시", "지금", "현재", "그리고", "하지만", "합니다",
            "입니다", "있습니다", "대한", "위해", "통해", "가능", "입시", "대입");

    private static final String[] PARTICLES = {
            "으로부터", "에서는", "에게서", "이라고", "라고", "으로", "에서", "에게",
            "부터", "까지", "처럼", "한테", "이나", "이면", "라면", "이며", "이고",
            "하면", "하고", "은요", "는요", "를", "을", "은", "는", "이", "가",
            "의", "에", "도", "만", "로", "과", "와", "고", "며"};

    /** 입시 도메인 동의어 — 질문 표현과 자료 용어가 다를 때 검색어 확장. */
    private static final String[][] SYNONYMS = {
            {"수시", "수시모집"},
            {"정시", "정시모집"},
            {"학종", "학생부종합", "학생부종합전형"},
            {"교과", "학생부교과", "학생부교과전형"},
            {"논술", "논술전형"},
            {"수능최저", "수능", "최저학력기준"},
            {"원서접수", "원서", "접수"},
            {"모집요강", "요강", "모집"},
            {"경쟁률", "경쟁"},
    };

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

    static String stripParticle(String token) {
        for (String p : PARTICLES) {
            if (token.length() - p.length() >= 2 && token.endsWith(p)) {
                return token.substring(0, token.length() - p.length());
            }
        }
        return token;
    }

    // ── STEP1: 2027 확정 공식자료 ────────────────────────────────────

    List<AdmissionsKnowledgeItem> searchConfirmedKnowledgeItems(String questionText, List<String> keywords,
                                                               Integer yearFilter) {
        if (embeddingProps.isEnabled() && vectorIndex.isReady()) {
            List<AdmissionsKnowledgeItem> hybrid = searchHybrid(questionText, keywords, true, yearFilter);
            if (!hybrid.isEmpty()) return hybrid;
        }
        return searchLike(keywords, true, yearFilter);
    }

    /** LIKE 검색 — ACTIVE + OFFICIAL + 확정/계획 구분 + 학년도 필터. */
    List<AdmissionsKnowledgeItem> searchLike(List<String> keywords, boolean confirmedOnly, Integer yearFilter) {
        if (keywords.isEmpty()) return List.of();
        Specification<AdmissionsKnowledgeItem> spec = (root, query, cb) -> {
            List<Predicate> kws = new ArrayList<>();
            for (String kw : keywords) {
                String p = "%" + kw + "%";
                kws.add(cb.or(
                        cb.like(root.get("title"), p),
                        cb.like(root.get("content"), p),
                        cb.like(root.get("tags"), p),
                        cb.like(root.get("category"), p)));
            }
            List<Predicate> base = new ArrayList<>();
            base.add(cb.equal(root.get("status"), AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE));
            base.add(cb.equal(root.get("sourceClass"), AdmissionsKnowledgeItem.SourceClass.OFFICIAL));
            base.add(cb.equal(root.get("yearConfirmed"), confirmedOnly));
            if (yearFilter != null) {
                base.add(cb.equal(root.get("academicYear"), yearFilter));
            }
            base.add(cb.or(kws.toArray(new Predicate[0])));
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(base.toArray(new Predicate[0]));
        };
        return knowledgeRepository.findAll(spec, PageRequest.of(0, CASE_CANDIDATE_WINDOW)).getContent().stream()
                .sorted(Comparator.comparingInt((AdmissionsKnowledgeItem k) ->
                        keywordHits(keywords, k.getTitle(), k.getTags(), k.getCategory()) * 2
                                + keywordHits(keywords, k.getContent())).reversed())
                .limit(PER_STEP_LIMIT)
                .toList();
    }

    /** 하이브리드 검색 (LIKE + 벡터). */
    List<AdmissionsKnowledgeItem> searchHybrid(String questionText, List<String> keywords,
                                              boolean confirmedOnly, Integer yearFilter) {
        float[] queryVec = embeddingClient.embed(questionText);
        if (queryVec == null) return List.of();
        List<AdmissionsKnowledgeItem> likeCandidates = searchLike(keywords, confirmedOnly, yearFilter);
        java.util.Map<Long, Double> likeScores = new java.util.HashMap<>();
        double maxLikeTmp = 1.0;
        for (AdmissionsKnowledgeItem k : likeCandidates) {
            double s = keywordHits(keywords, k.getTitle(), k.getTags(), k.getCategory()) * 2.0
                    + keywordHits(keywords, k.getContent());
            likeScores.put(k.getKnowledgeId(), s);
            if (s > maxLikeTmp) maxLikeTmp = s;
        }
        final double maxLike = maxLikeTmp;
        List<AdmissionsVectorIndex.VectorHit> vecHits = vectorIndex.search(queryVec, embeddingProps.getVectorLimit());
        java.util.Map<Long, Double> vecScores = new java.util.HashMap<>();
        for (AdmissionsVectorIndex.VectorHit h : vecHits) {
            vecScores.put(h.getKnowledgeId(), h.getSimilarity());
        }
        java.util.Set<Long> ids = new java.util.HashSet<>(likeScores.keySet());
        ids.addAll(vecScores.keySet());
        if (ids.isEmpty()) return List.of();
        List<AdmissionsKnowledgeItem> candidates = knowledgeRepository.findAllById(ids);
        double likeW = embeddingProps.getLikeWeight();
        double vecW = embeddingProps.getVectorWeight();
        double officialBonus = embeddingProps.getOfficialBonus();
        return candidates.stream()
                .filter(k -> k.getStatus() == AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE)
                .filter(k -> k.getSourceClass() == AdmissionsKnowledgeItem.SourceClass.OFFICIAL)
                .filter(k -> Boolean.valueOf(confirmedOnly).equals(k.getYearConfirmed()))
                .filter(k -> yearFilter == null || yearFilter.equals(k.getAcademicYear()))
                .sorted(Comparator.comparingDouble((AdmissionsKnowledgeItem k) -> {
                    double likeNorm = likeScores.getOrDefault(k.getKnowledgeId(), 0.0) / maxLike;
                    double vecNorm = (vecScores.getOrDefault(k.getKnowledgeId(), 0.0) + 1.0) / 2.0;
                    return likeW * likeNorm + vecW * vecNorm + officialBonus;
                }).reversed())
                .limit(PER_STEP_LIMIT)
                .toList();
    }

    private List<Evidence> searchConfirmedKnowledge(String questionText, List<String> keywords, Integer yearFilter) {
        return searchConfirmedKnowledgeItems(questionText, keywords, yearFilter).stream()
                .map(k -> toEvidence(k, STEP_CONFIRMED, SourceClass.OFFICIAL_CONFIRMED))
                .toList();
    }

    private List<Evidence> searchPlannedKnowledge(String questionText, List<String> keywords, Integer yearFilter) {
        List<AdmissionsKnowledgeItem> items;
        if (embeddingProps.isEnabled() && vectorIndex.isReady()) {
            items = searchHybrid(questionText, keywords, false, yearFilter);
            if (items.isEmpty()) items = searchLike(keywords, false, yearFilter);
        } else {
            items = searchLike(keywords, false, yearFilter);
        }
        return items.stream()
                .map(k -> toEvidence(k, STEP_PLANNED, SourceClass.OFFICIAL_PLANNED))
                .toList();
    }

    private Evidence toEvidence(AdmissionsKnowledgeItem k, int stepNo, SourceClass cls) {
        String sourceInfo = k.getSource() != null
                ? k.getSource().getIssuer() + " / " + k.getSource().getTitle()
                : (k.getSourceUrl() != null ? k.getSourceUrl() : "출처 미확인");
        return Evidence.builder()
                .stepNo(stepNo)
                .sourceClass(cls)
                .refId(String.valueOf(k.getKnowledgeId()))
                .refUrl(k.getSource() != null ? k.getSource().getDocumentUrl() : k.getSourceUrl())
                .title(k.getTitle())
                .excerpt(firstChars(k.getContent(), 400))
                .note(sourceInfo + (k.getYearConfirmed() ? " (2027 확정)" : " (2028 계획)"))
                .academicYear(k.getAcademicYear())
                .yearConfirmed(k.getYearConfirmed())
                .build();
    }

    // ── STEP3: 공식자료 원문 ─────────────────────────────────────────

    List<Evidence> searchOfficialMaterials(List<String> keywords, Integer yearFilter) {
        if (keywords.isEmpty()) return List.of();
        Specification<AdmissionsOfficialMaterial> spec = (root, query, cb) -> {
            List<Predicate> kws = new ArrayList<>();
            for (String kw : keywords) {
                String p = "%" + kw + "%";
                kws.add(cb.or(
                        cb.like(root.get("title"), p),
                        cb.like(root.get("issuer"), p)));
            }
            List<Predicate> base = new ArrayList<>();
            if (yearFilter != null) {
                base.add(cb.equal(root.get("academicYear"), yearFilter));
            }
            base.add(cb.or(kws.toArray(new Predicate[0])));
            query.orderBy(cb.desc(root.get("publishDate")));
            return cb.and(base.toArray(new Predicate[0]));
        };
        return materialRepository.findAll(spec, PageRequest.of(0, PER_STEP_LIMIT)).stream()
                .map(m -> Evidence.builder()
                        .stepNo(STEP_OFFICIAL_DOC)
                        .sourceClass(m.getAcademicYear() != null && m.getAcademicYear() == 2028
                                ? SourceClass.OFFICIAL_PLANNED : SourceClass.OFFICIAL_CONFIRMED)
                        .refId(String.valueOf(m.getMaterialId()))
                        .refUrl(m.getDocumentUrl())
                        .title(m.getTitle())
                        .excerpt(m.getIssuer() + (m.getPublishDate() != null ? " (" + m.getPublishDate() + ")" : ""))
                        .note("공식자료 원문")
                        .academicYear(m.getAcademicYear())
                        .yearConfirmed(m.getAcademicYear() != null && m.getAcademicYear() == 2027)
                        .build())
                .toList();
    }

    // ── STEP4: 유튜브 의견 (별도 분류) ────────────────────────────────

    List<Evidence> searchYoutubeKnowledge(List<String> keywords, Integer yearFilter) {
        if (keywords.isEmpty()) return List.of();
        Specification<AdmissionsKnowledgeItem> spec = (root, query, cb) -> {
            List<Predicate> kws = new ArrayList<>();
            for (String kw : keywords) {
                String p = "%" + kw + "%";
                kws.add(cb.or(
                        cb.like(root.get("title"), p),
                        cb.like(root.get("content"), p),
                        cb.like(root.get("tags"), p)));
            }
            List<Predicate> base = new ArrayList<>();
            base.add(cb.equal(root.get("status"), AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE));
            base.add(cb.equal(root.get("sourceClass"), AdmissionsKnowledgeItem.SourceClass.YOUTUBE));
            if (yearFilter != null) {
                base.add(cb.equal(root.get("academicYear"), yearFilter));
            }
            base.add(cb.or(kws.toArray(new Predicate[0])));
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(base.toArray(new Predicate[0]));
        };
        return knowledgeRepository.findAll(spec, PageRequest.of(0, PER_STEP_LIMIT)).stream()
                .map(k -> Evidence.builder()
                        .stepNo(STEP_YOUTUBE)
                        .sourceClass(SourceClass.YOUTUBE_OPINION)
                        .refId(String.valueOf(k.getKnowledgeId()))
                        .refUrl(k.getSourceUrl())
                        .title("[유튜브 의견] " + k.getTitle())
                        .excerpt(firstChars(k.getContent(), 300))
                        .note("유튜브 분석 의견 — 확정 사실이 아님")
                        .academicYear(k.getAcademicYear())
                        .yearConfirmed(k.getYearConfirmed())
                        .build())
                .toList();
    }

    // ── STEP5: 충돌대장 ──────────────────────────────────────────────

    List<Evidence> searchOpenConflicts(List<String> keywords, Integer yearFilter) {
        if (keywords.isEmpty()) return List.of();
        Specification<AdmissionsConflict> spec = (root, query, cb) -> {
            List<Predicate> kws = new ArrayList<>();
            for (String kw : keywords) {
                String p = "%" + kw + "%";
                kws.add(cb.or(
                        cb.like(root.get("title"), p),
                        cb.like(root.get("description"), p)));
            }
            List<Predicate> base = new ArrayList<>();
            base.add(cb.equal(root.get("status"), AdmissionsConflict.ConflictStatus.OPEN));
            if (yearFilter != null) {
                base.add(cb.equal(root.get("academicYear"), yearFilter));
            }
            base.add(cb.or(kws.toArray(new Predicate[0])));
            return cb.and(base.toArray(new Predicate[0]));
        };
        return conflictRepository.findAll(spec, PageRequest.of(0, PER_STEP_LIMIT)).stream()
                .map(c -> Evidence.builder()
                        .stepNo(STEP_CONFLICT)
                        .sourceClass(SourceClass.CONFLICT_INFO)
                        .refId(String.valueOf(c.getConflictId()))
                        .refUrl(null)
                        .title("[확인 중] " + c.getTitle())
                        .excerpt(firstChars(c.getDescription(), 300))
                        .note("충돌 중인 정보 — 확정 답변 불가")
                        .academicYear(c.getAcademicYear())
                        .yearConfirmed(false)
                        .build())
                .toList();
    }

    // ── helpers ──────────────────────────────────────────────────────

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

    static String firstChars(String s, int n) {
        if (s == null) return null;
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
