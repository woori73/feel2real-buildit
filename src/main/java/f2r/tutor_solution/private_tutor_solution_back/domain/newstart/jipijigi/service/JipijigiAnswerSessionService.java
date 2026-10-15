package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiAnswerSession;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiAnswerSession.Confidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiAnswerSession.PublishedAs;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiAnswerSession.ReviewState;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiQuestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.RefType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.SourceClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiAnswerSessionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiQuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.ResearchPack;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * 답변 세션 자동 축적 — 질문 1건 → 최종 답변 → 근거 세트를 한 행으로 저장하고, 다음 질문의 STEP1 RAG에 우선 사용한다.
 *
 * <p>규칙:
 * <ul>
 *   <li>답변 생성에 성공하면 게시 여부와 무관하게 저장한다 (INTRO/패키지 없는 초안은 저장 대상 아님)</li>
 *   <li>fingerprint(정규화 질문 + 근거 ref 세트)로 dedupe — 동일 질문/동일 근거는 중복 저장하지 않는다</li>
 *   <li>운영자 승인(APPROVED) 전 CANDIDATE는 RAG 검색 대상이 아니다 — Agent 답변을 임의로 사실화하지 않는다</li>
 *   <li>재사용 시 출처 분류는 INTERNAL_CASE + ANSWER_SESSION 고정 (OFFICIAL_RULE로 승격 경로 없음)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JipijigiAnswerSessionService {

    private static final int RAG_LIMIT = 5;
    /** 운영원칙 §3의 축적 분야 9개 — 첫 일치 순서대로 판정한다. */
    private static final Map<String, String[]> TOPICS = orderedTopics();

    private final JipijigiAnswerSessionRepository sessionRepository;
    private final JipijigiQuestionRepository questionRepository;
    private final ObjectMapper json = new ObjectMapper();

    /** 저장 결과: 세션 행 + 이 행이 dedupe로 재사용된 것인지. */
    public record RecordResult(JipijigiAnswerSession session, boolean duplicate) {
    }

    // ── write paths ───────────────────────────────────────────────────

    @Transactional
    public RecordResult recordFromDraft(JipijigiDraft draft, ResearchPack pack) {
        if (pack == null || pack.getQuestion() == null || pack.getQuestion().isBlank()
                || draft.getDraftType() == JipijigiDraft.DraftType.INTRO) {
            return null;
        }
        String fingerprint = fingerprintOf(pack.getQuestion(), allRefs(pack));
        Optional<JipijigiAnswerSession> existing = sessionRepository.findByFingerprint(fingerprint);
        if (existing.isPresent()) {
            log.info("jipijigi answer session deduped draftId={} sessionId={}", draft.getDraftId(),
                    existing.get().getSessionId());
            return new RecordResult(existing.get(), true);
        }

        JipijigiAnswerSession session = JipijigiAnswerSession.builder()
                .fingerprint(fingerprint)
                .draftId(draft.getDraftId())
                .questionId(draft.getQuestionId())
                .commandId(draft.getCommandId())
                .questionType(draft.getDraftType().name())
                .questionTopic(topicOf(pack.getQuestion()))
                .questionText(pack.getQuestion())
                .answerTitle(draft.getTitle())
                .answerBody(draft.getBody())
                .answeredAt(LocalDateTime.now())
                .evidenceInternal(writeJson(pack.getInternalHits()))
                .evidenceCcrs(writeJson(pack.getCcrsHits()))
                .evidenceBorrower(writeJson(pack.getBorrowerHits()))
                .evidenceOfficial(writeJson(pack.getOfficialHits()))
                .dbHits(writeJson(hitsOf(pack)))
                .externalSearched(pack.isExternalSearched())
                .confidence(confidenceOf(pack))
                .operatorEdited(false)
                .publishedAs(PublishedAs.NONE)
                .reviewState(ReviewState.CANDIDATE)
                .build();
        applyQuestionSource(session, draft.getQuestionId());
        return new RecordResult(sessionRepository.save(session), false);
    }

    /** 운영자가 초안 본문을 고치면 저장된 최종 답변도 갱신하고 수정 표시를 남긴다. */
    @Transactional
    public void markEdited(Long draftId, String title, String body) {
        sessionRepository.findByDraftId(draftId).ifPresent(s -> {
            if (title != null && !title.isBlank()) s.setAnswerTitle(title);
            if (body != null && !body.isBlank()) s.setAnswerBody(body);
            s.setOperatorEdited(true);
            sessionRepository.save(s);
        });
    }

    /** 승인 = 운영자 검토 통과. 이 시점부터 다음 질문의 STEP1 RAG에서 검색된다. */
    @Transactional
    public void markAccepted(Long draftId) {
        sessionRepository.findByDraftId(draftId).ifPresent(s -> {
            s.setReviewState(ReviewState.ACCEPTED);
            sessionRepository.save(s);
        });
    }

    @Transactional
    public void markRejected(Long draftId) {
        sessionRepository.findByDraftId(draftId).ifPresent(s -> {
            s.setReviewState(ReviewState.REJECTED);
            sessionRepository.save(s);
        });
    }

    /** V1은 NAVER 게시 클라이언트가 없다 — 운영자가 실제 게시/댓글 결과를 보고하면 그 값만 저장한다. */
    @Transactional
    public JipijigiAnswerSession reportExposure(Long draftId, PublishedAs publishedAs, String publishedUrl,
                                                String commentId, String followUpRef) {
        JipijigiAnswerSession s = sessionRepository.findByDraftId(draftId)
                .orElseThrow(() -> new IllegalArgumentException("이 초안에 저장된 답변 세션이 없습니다: " + draftId));
        if (publishedAs != null) s.setPublishedAs(publishedAs);
        if (publishedUrl != null && !publishedUrl.isBlank()) s.setPublishedUrl(publishedUrl.trim());
        if (commentId != null && !commentId.isBlank()) s.setSourceCommentId(commentId.trim());
        if (followUpRef != null && !followUpRef.isBlank()) s.setFollowUpRef(followUpRef.trim());
        return sessionRepository.save(s);
    }

    @Transactional
    public JipijigiAnswerSession attachFollowUp(Long sessionId, String followUpRef) {
        JipijigiAnswerSession s = get(sessionId);
        s.setFollowUpRef(followUpRef);
        return sessionRepository.save(s);
    }

    // ── read paths ────────────────────────────────────────────────────

    /** STEP1 RAG: ACCEPTED 세션만 외부 카페 검색보다 먼저 조회한다. 분류는 INTERNAL_CASE로 고정. */
    @Transactional(readOnly = true)
    public List<Evidence> searchAcceptedEvidence(List<String> keywords) {
        return searchAccepted(keywords).stream().map(this::toEvidence).toList();
    }

    @Transactional(readOnly = true)
    public List<JipijigiAnswerSession> searchAccepted(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return List.of();
        Specification<JipijigiAnswerSession> spec = (root, query, cb) -> {
            List<Predicate> kws = new ArrayList<>();
            for (String kw : keywords) {
                String p = "%" + kw + "%";
                kws.add(cb.or(
                        cb.like(root.get("questionText"), p),
                        cb.like(root.get("answerBody"), p),
                        cb.like(root.get("answerTitle"), p),
                        cb.like(root.get("questionTopic"), p)));
            }
            query.orderBy(cb.desc(root.get("answeredAt")));
            return cb.and(cb.equal(root.get("reviewState"), ReviewState.ACCEPTED),
                    cb.or(kws.toArray(new Predicate[0])));
        };
        return sessionRepository.findAll(spec, PageRequest.of(0, RAG_LIMIT)).getContent();
    }

    @Transactional(readOnly = true)
    public Page<JipijigiAnswerSession> recent(int page, int size) {
        return sessionRepository.findAllByOrderByAnsweredAtDesc(
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    @Transactional(readOnly = true)
    public Optional<JipijigiAnswerSession> byDraft(Long draftId) {
        return sessionRepository.findByDraftId(draftId);
    }

    @Transactional(readOnly = true)
    public JipijigiAnswerSession get(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("답변 세션 없음: " + sessionId));
    }

    // ── derivation ────────────────────────────────────────────────────

    private Evidence toEvidence(JipijigiAnswerSession s) {
        return Evidence.builder()
                .stepNo(JipijigiResearchService.STEP_INTERNAL)
                .sourceClass(SourceClass.INTERNAL_CASE)
                .refType(RefType.ANSWER_SESSION)
                .refId(String.valueOf(s.getSessionId()))
                .refUrl(s.getPublishedUrl())
                .title("[축적 답변] " + s.getAnswerTitle())
                .excerpt(JipijigiResearchService.firstChars(s.getAnswerBody(), 300))
                .note("운영자 승인 답변 세션 재사용 · 주제=" + s.getQuestionTopic()
                        + " · 신뢰도=" + s.getConfidence() + " · 게시=" + s.getPublishedAs())
                .build();
    }

    private void applyQuestionSource(JipijigiAnswerSession session, Long questionId) {
        if (questionId == null) return;
        questionRepository.findById(questionId)
                .ifPresent(q -> {
                    session.setSourceClubId(q.getClubId());
                    session.setSourceArticleId(q.getArticleId());
                });
    }

    static Confidence confidenceOf(ResearchPack pack) {
        boolean official = !pack.getOfficialHits().isEmpty();
        boolean caseBacked = !pack.getInternalHits().isEmpty() || !pack.getCcrsHits().isEmpty()
                || !pack.getBorrowerHits().isEmpty();
        if (official && caseBacked) return Confidence.HIGH;
        if (official || caseBacked) return Confidence.MEDIUM;
        return Confidence.LOW;
    }

    static String topicOf(String questionText) {
        String text = questionText == null ? "" : questionText.toLowerCase();
        for (Map.Entry<String, String[]> entry : TOPICS.entrySet()) {
            for (String needle : entry.getValue()) {
                if (text.contains(needle.toLowerCase())) return entry.getKey();
            }
        }
        return "기타/미분류";
    }

    private static Map<String, String[]> orderedTopics() {
        Map<String, String[]> m = new java.util.LinkedHashMap<>();
        m.put("담보채무", new String[]{"담보", "차량", "자동차", "부동산", "아파트", "경매", "유효담보", "홈택스"});
        m.put("감면율", new String[]{"감면", "깎", "이자율", "연체이자", "변제율"});
        m.put("법원·지급명령", new String[]{"법원", "지급명령", "개인회생", "파산", "전자소송", "면책", "회생"});
        m.put("추심", new String[]{"추심", "독촉", "채권자", "대부업체", "압류"});
        m.put("신용정보", new String[]{"신용", "연체기록", "credit4u", "등급", "불량"});
        m.put("부실차주 자격", new String[]{"자격", "요건", "대상", "부실우려", "전환", "취급기관"});
        m.put("신청·심사", new String[]{"신청", "심사", "접수", "서류", "배정"});
        m.put("약정", new String[]{"약정", "상환", "분할", "이행", "변제계획"});
        m.put("실제 처리결과", new String[]{"결과", "승인", "반려", "확정", "후기"});
        return m;
    }

    private static List<String> allRefs(ResearchPack pack) {
        List<String> refs = new ArrayList<>();
        collectRefs(pack.getInternalHits(), refs);
        collectRefs(pack.getCcrsHits(), refs);
        collectRefs(pack.getBorrowerHits(), refs);
        collectRefs(pack.getOfficialHits(), refs);
        return refs;
    }

    private static void collectRefs(List<Evidence> list, List<String> out) {
        for (Evidence e : list) {
            out.add(e.getRefType() + ":" + e.getRefId() + ":" + (e.getRefUrl() == null ? "" : e.getRefUrl()));
        }
    }

    /** 동일 질문 + 동일 근거 세트는 같은 지문. 근거가 바뀌어야 새 세션이 된다. */
    static String fingerprintOf(String question, List<String> refs) {
        String normalized = question == null ? "" : question.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "");
        TreeSet<String> sorted = new TreeSet<>(refs);
        String payload = normalized + "|" + String.join(",", sorted);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static Map<String, Object> hitsOf(ResearchPack pack) {
        Map<String, Object> hits = new LinkedHashMap<>();
        hits.put("step1_internal", pack.getInternalHits().size());
        hits.put("step2_ccrs", pack.getCcrsHits().size());
        hits.put("step3_borrower", pack.getBorrowerHits().size());
        hits.put("step4_official", pack.getOfficialHits().size());
        hits.put("step5_extra_agency", pack.getExtraAgencyHits().size());
        hits.put("gap_notes", pack.getGapNotes().size());
        hits.put("total", pack.getInternalHits().size() + pack.getCcrsHits().size()
                + pack.getBorrowerHits().size() + pack.getOfficialHits().size());
        return hits;
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("jipijigi answer session json failed", e);
            return "[]";
        }
    }
}
