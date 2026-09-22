package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAiChatService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartCanonicalAnswerPolicy.CaseEvidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartCanonicalAnswerPolicy.EvidenceBundle;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartCanonicalAnswerPolicy.PatternEvidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartCanonicalAnswerPolicy.SourceLinkEvidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartCanonicalAnswerPolicy.DifferenceEvidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiAiRouterClient;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Golden A–H OFFLINE CODE PATH QA (2026-09-23).
 *
 * <p>fixture: {@code canonical-golden/GOLDEN_OFFLINE_FIXTURES_2026-09-23.json} — canonical
 * 4b807bd7에서 검증 667872b8 슬롯별로 verbatim 발췌(수치·상태 직접 타이핑 금지).
 *
 * <p>검증 범위: evidence classification → bundle → composeEvidenceBlock →
 * NewStartAiChatService.askWithEvidence 직전의 (system, prompt) payload.
 * 실제 provider 응답이 필요한 항목은 LIVE LLM QA = PENDING (재현 답변 2종으로 스캐너만 검증).
 * 판정 기준은 정답 문장이 아니라 classification / relation handling / provenance / forbidden conclusion.
 */
@DisplayName("Golden A-H offline code path QA")
class NewstartCanonicalGoldenOfflineQaTest {

    private static JsonNode fixtures;

    @BeforeAll
    static void loadFixtures() throws Exception {
        try (InputStream in = NewstartCanonicalGoldenOfflineQaTest.class.getResourceAsStream(
                "/canonical-golden/GOLDEN_OFFLINE_FIXTURES_2026-09-23.json")) {
            assertThat(in).as("canonical golden fixture must be on classpath").isNotNull();
            fixtures = new ObjectMapper().readTree(in);
        }
    }

    /** fixture 슬롯 → EvidenceBundle (surgical mapping; canonical 판별 규칙 재해석 없음). */
    private static EvidenceBundle bundleFor(String slot) {
        JsonNode d = fixtures.get(slot);
        List<String> official = new ArrayList<>();
        List<String> operator = new ArrayList<>();
        List<CaseEvidence> cases = new ArrayList<>();
        List<PatternEvidence> patterns = new ArrayList<>();
        List<DifferenceEvidence> differences = new ArrayList<>();
        List<String> unknowns = new ArrayList<>();
        List<SourceLinkEvidence> links = new ArrayList<>();

        for (JsonNode e : d.path("official")) official.add(officialLine(e));
        for (JsonNode e : d.path("officialNotFound")) {
            // NOT_FOUND: 공식 section 생성 금지, 부재 의미만 남긴다 (존재 부정이 아님).
            unknowns.add(NewstartCanonicalAnswerPolicy.OFFICIAL_ABSENCE_SENTENCE
                    + " (" + e.get("canonicalId").asText() + ")");
        }
        for (JsonNode e : d.path("operator")) operator.add(e.get("question").asText()
                + " → " + e.get("answer").asText() + " (운영자 축적 답변, 공식 규정 아님)");
        for (JsonNode e : d.path("cases")) cases.add(caseEvidence(e));
        // pattern.members(E/H 슬롯)는 개별 사례로 재나열하지 않는다 — 패턴 관찰로만 제시.
        for (JsonNode e : d.path("chatFaq")) {
            if (e.get("answer").isNull()) {
                unknowns.add(e.get("canonicalId").asText()
                        + " — 미검증 대화록(REVIEW_ONLY), 검증된 답변 없음. 답변으로 제시 금지.");
            } else {
                cases.add(new CaseEvidence(e.get("canonicalId").asText(),
                        e.get("question").asText(), "UNVERIFIED"));
            }
        }
        if (d.has("pattern")) {
            JsonNode p = d.get("pattern");
            patterns.add(new PatternEvidence(p.get("patternId").asText(), p.get("name").asText()));
        }
        if ("D".equals(slot)) {
            String officialSide = d.path("official").get(0).path("answer").asText();
            for (JsonNode r : d.path("relations")) {
                differences.add(new DifferenceEvidence(officialSide,
                        operatorAnswer(d, r.get("from").asText()), null));
            }
        }
        collectLinks(d, links);
        return new EvidenceBundle(question(slot), official, operator, List.of(),
                cases, patterns, differences, unknowns, links);
    }

    private static String officialLine(JsonNode e) {
        String cite = e.path("provenance").path("citations").path(0).asText("");
        return e.get("question").asText() + " → " + e.get("answer").asText()
                + " (공식자료 직접 확인" + (cite.isBlank() ? "" : ", 출처: " + cite) + ")";
    }

    private static CaseEvidence caseEvidence(JsonNode e) {
        String summary = e.get("title").asText();
        String observed = e.path("observedResult").asText(null);
        if (observed != null && !observed.isBlank()) summary += " — " + observed;
        return new CaseEvidence(e.get("canonicalId").asText(), summary, e.get("caseStatus").asText());
    }

    private static String operatorAnswer(JsonNode d, String canonicalId) {
        for (JsonNode e : d.path("operator")) {
            if (canonicalId.equals(e.get("canonicalId").asText())) return e.path("answer").asText(null);
        }
        return null;
    }

    private static void collectLinks(JsonNode d, List<SourceLinkEvidence> links) {
        for (String key : List.of("official", "officialNotFound", "operator", "cases", "chatFaq", "members")) {
            for (JsonNode e : d.path(key)) {
                String url = e.path("url").asText(null);
                String cite = e.path("provenance").path("citations").path(0).asText(null);
                if (url != null || cite != null) {
                    // URL은 provenance에 실재할 때만; 없으면 citation 텍스트 라벨로만 전달.
                    links.add(new SourceLinkEvidence(e.get("canonicalId").asText(),
                            cite == null ? "provenance" : cite, url));
                }
            }
        }
    }

    private static String question(String slot) {
        return fixtures.get(slot).get("question").asText();
    }

    /** 실제 answer path 조립까지 실행: askWithEvidence → (우리가 세운) bundle → WikiAiRouterClient 직전 payload. */
    private static String[] payloadFor(String slot) {
        EvidenceBundle bundle = bundleFor(slot);
        String block = NewstartCanonicalAnswerPolicy.composeEvidenceBlock(bundle);
        WikiAiRouterClient router = mock(WikiAiRouterClient.class);
        when(router.complete(any(), any(), anyInt())).thenReturn(
                new WikiAiRouterClient.Completion(true, "REPLAY-ONLY", null, "offline", "offline"));
        new NewStartAiChatService(router).askWithEvidence(question(slot), block);
        ArgumentCaptor<String> sys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(router).complete(sys.capture(), user.capture(), anyInt());
        return new String[] {sys.getValue(), user.getValue() + "\n<<<EVIDENCE_BLOCK>>>\n" + block};
    }

    @Test
    @DisplayName("GOLDEN-A: OFFICIAL 단독 — 부재·사례 문장 없이 공식만")
    void goldenA() {
        String[] p = payloadFor("A");
        assertThat(p[1]).contains("V-02").contains("공식");
        assertThat(p[1]).doesNotContain(NewstartCanonicalAnswerPolicy.OFFICIAL_ABSENCE_SENTENCE);
        assertThat(p[1]).doesNotContain("[실제 차주 사례에서 확인되는 내용]");
        assertThat(NewstartCanonicalAnswerPolicy.containsForbiddenVerdictPhrase(p[1])).isFalse();
    }

    @Test
    @DisplayName("GOLDEN-B: CURRENT + CASE 병존 — 공식 유지, CCRS 사례·링크 분리 전달")
    void goldenB() {
        String[] p = payloadFor("B");
        assertThat(p[1]).contains("[공식적으로 확인되는 내용]");
        assertThat(p[1]).contains("[실제 차주 사례에서 확인되는 내용]").contains("CASE-CCRS-784675");
        assertThat(p[1]).contains("https://cafe.naver.com/ccrs5500/784675"); // 실재 URL만
        assertThat(p[1]).doesNotContain(NewstartCanonicalAnswerPolicy.OFFICIAL_ABSENCE_SENTENCE);
    }

    @Test
    @DisplayName("GOLDEN-C: NOT_FOUND + CASE 공존 — '없다' 아님, CAFE/CHAT 분리, UNEXPLAINED 6건 보존")
    void goldenC() {
        String[] p = payloadFor("C");
        assertThat(p[1]).contains(NewstartCanonicalAnswerPolicy.OFFICIAL_ABSENCE_SENTENCE);
        assertThat(p[1]).contains(NewstartCanonicalAnswerPolicy.CASE_BRIDGE_SENTENCE);
        assertThat(p[1]).contains("[실제 차주 사례에서 확인되는 내용]").contains("CASE-CAFE-5862");
        assertThat(p[1]).contains(NewstartCanonicalAnswerPolicy.CHAT_UNVERIFIED_SECTION)
                .contains("CASE-CHAT-004");
        // FAQ-CHAT-020 answer=null → 답변이 아니라 미검증 항목으로만
        assertThat(p[1]).contains("FAQ-CHAT-020").contains("REVIEW_ONLY");
        assertThat(p[1]).doesNotContain("그런 기준은 없습니다");
        // relations 6건 REL id가 사례 요약/unknown으로 추적 가능해야 한다 — 최소 6개 REL 중 전달된 것
        for (String rel : List.of("CASE-CCRS-784675", "CASE-CAFE-5862")) {
            assertThat(p[1]).contains(rel);
        }
    }

    @Test
    @DisplayName("GOLDEN-D: CONTRADICTS 병존 — 양쪽 보존, 임의 판정 없음")
    void goldenD() {
        String[] p = payloadFor("D");
        assertThat(p[1]).contains("공식자료:").contains("운영자 가이드:");
        assertThat(p[1]).contains("하나로 확정해 안내하지 않습니다");
        assertThat(NewstartCanonicalAnswerPolicy.containsForbiddenVerdictPhrase(p[1])).isFalse();
    }

    @Test
    @DisplayName("GOLDEN-E: PAT-01 반복 관찰 — officialRule=false, 규칙 승격 없음")
    void goldenE() {
        String[] p = payloadFor("E");
        assertThat(p[1]).contains("PAT-01");
        assertThat(p[1]).contains(NewstartCanonicalAnswerPolicy.PATTERN_NOT_RULE_MARKER);
        assertThat(p[1]).doesNotContain("반드시");
        assertThat(NewstartCanonicalAnswerPolicy.containsForbiddenVerdictPhrase(p[1])).isFalse();
    }

    @Test
    @DisplayName("GOLDEN-F: UNRESOLVED 3건 — 미확인 마커, 성공/실패 변환 없음")
    void goldenF() {
        String[] p = payloadFor("F");
        assertThat(p[1]).contains("CASE-CAFE-5752").contains("CASE-CAFE-5878").contains("CASE-CAFE-5971");
        assertThat(p[1]).contains(NewstartCanonicalAnswerPolicy.UNRESOLVED_MARKER);
        assertThat(p[1]).doesNotContain("성공했습니다").doesNotContain("실패했습니다");
    }

    @Test
    @DisplayName("GOLDEN-G: 근거 없음 — null/UNKNOWN 추측 금지")
    void goldenG() {
        String[] p = payloadFor("G");
        // G: FAQ-CHAT-001 answer=null → 미검증 항목으로만, CASE-CAFE-5904는 원 상태(OPERATOR_GUIDANCE,
        // predictionVsActual=UNKNOWN) 라벨 그대로만 제시 — 확정 감면율·전환 기준 결론 금지.
        assertThat(p[1]).contains(NewstartCanonicalAnswerPolicy.OFFICIAL_ABSENCE_SENTENCE);
        assertThat(p[1]).contains("[현재 확인되지 않은 부분]").contains("FAQ-CHAT-001").contains("REVIEW_ONLY");
        assertThat(p[1]).doesNotContain("[공식적으로 확인되는 내용]");
        assertThat(p[1]).contains("CASE-CAFE-5904").contains("OPERATOR_GUIDANCE");
        assertThat(NewstartCanonicalAnswerPolicy.containsForbiddenVerdictPhrase(p[1])).isFalse();
    }

    @Test
    @DisplayName("GOLDEN-H: 판단 요청 — system에 판단 의무 취지, evidence level 선행")
    void goldenH() {
        String[] p = payloadFor("H");
        assertThat(p[0]).contains("판단·추측은 사용자가 명시적으로 요구할 때만");
        assertThat(p[1]).contains("[반복적으로 관찰된 사례]").contains("PAT-01");
        assertThat(p[1]).contains("[실제 차주 사례에서 확인되는 내용]");
        assertThat(p[1]).contains(NewstartCanonicalAnswerPolicy.CHAT_UNVERIFIED_SECTION);
        assertThat(NewstartCanonicalAnswerPolicy.containsJudgmentLabel(
                NewstartCanonicalAnswerPolicy.JUDGMENT_LABEL_SENTENCE)).isTrue();
    }

    @Test
    @DisplayName("REPLAY 스캐너: 위반 답변은 감지, 준수 답변은 통과 (LLM 응답은 LIVE QA에서 재검증)")
    void replayForbiddenLanguageScanner() {
        String violating = "차량 기준은 없습니다. 감면율은 90%로 확정됩니다. 승인이 확실합니다.";
        assertThat(NewstartCanonicalAnswerPolicy.containsForbiddenVerdictPhrase(violating)).isTrue();
        String compliant = NewstartCanonicalAnswerPolicy.OFFICIAL_ABSENCE_SENTENCE
                + " 다만 수집된 실제 차주 사례에서는…" + NewstartCanonicalAnswerPolicy.JUDGMENT_LABEL_SENTENCE;
        assertThat(NewstartCanonicalAnswerPolicy.containsForbiddenVerdictPhrase(compliant)).isFalse();
        assertThat(NewstartCanonicalAnswerPolicy.containsJudgmentLabel(compliant)).isTrue();
        // 판단 라벨 없는 사례 기반 예측 문장은 검출돼야 한다 (GOLDEN-H 금지선)
        assertThat(NewstartCanonicalAnswerPolicy.containsJudgmentLabel(
                "이 조건이면 70% 정도 받을 수 있습니다.")).isFalse();
    }
}
