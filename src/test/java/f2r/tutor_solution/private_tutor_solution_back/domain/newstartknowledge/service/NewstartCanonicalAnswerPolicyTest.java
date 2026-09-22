package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Canonical answer policy 회귀 테스트 (CASE A~G + 자동 검증).
 *
 * <p>LLM 출력이 아니라 RAG가 모델에 전달하는 지침·근거 블록을 검증한다.
 * DB 없이 실행된다.
 */
@DisplayName("Canonical answer policy (A-G)")
class NewstartCanonicalAnswerPolicyTest {

    private NewstartCanonicalAnswerPolicy.EvidenceBundle bundleWith(
            List<String> official,
            List<NewstartCanonicalAnswerPolicy.CaseEvidence> cases) {
        return new NewstartCanonicalAnswerPolicy.EvidenceBundle(
                "감면율 문의",
                official,
                List.of(),
                List.of(),
                cases,
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("CASE A: 공식자료가 있으면 OFFICIAL section이 우선한다")
    void caseA_officialFirst() {
        // given: 공식 근거가 있는 질문
        var bundle = bundleWith(
                List.of("부실차주 자격: 총채무 15억 (출처: newstartfund FAQ-52)"),
                List.of());

        // when: 근거 블록 조립
        String block = NewstartCanonicalAnswerPolicy.composeEvidenceBlock(bundle);

        // then: OFFICIAL section이 있고 CASE 부재 문장이 없다
        assertThat(block).contains("[공식적으로 확인되는 내용]");
        assertThat(block).doesNotContain(
                NewstartCanonicalAnswerPolicy.OFFICIAL_ABSENCE_SENTENCE);
    }

    @Test
    @DisplayName("CASE B: OFFICIAL NOT_FOUND + CASE PRESENT면 '없다'고 답하지 않고 CASE를 제공한다")
    void caseB_officialAbsentCasePresent() {
        // given: 공식 부재 + 사례 존재 (canonical V-05/V-08/V-20 조합)
        var bundle = bundleWith(
                List.of(),
                List.of(new NewstartCanonicalAnswerPolicy.CaseEvidence(
                        "CASE-CAFE-5997", "Toss 알림으로 연체정보 삭제·기금 등록 확인", "RESULT_PARTIAL")));

        // when: 근거 블록 조립
        String block = NewstartCanonicalAnswerPolicy.composeEvidenceBlock(bundle);

        // then: 부재 문장 + 연결 문장 + CASE section, 단정 표현 없음
        assertThat(block).contains(NewstartCanonicalAnswerPolicy.OFFICIAL_ABSENCE_SENTENCE);
        assertThat(block).contains(NewstartCanonicalAnswerPolicy.CASE_BRIDGE_SENTENCE);
        assertThat(block).contains("[실제 차주 사례에서 확인되는 내용]");
        assertThat(NewstartCanonicalAnswerPolicy.containsForbiddenVerdictPhrase(block)).isFalse();
    }

    @Test
    @DisplayName("CASE C: 반복 패턴은 관찰로 표현하고 공식 규정으로 표현하지 않는다")
    void caseC_repeatedPatternNotRule() {
        // given: 반복 패턴 근거
        var bundle = new NewstartCanonicalAnswerPolicy.EvidenceBundle(
                "재산 초과 시 감면", List.of(), List.of(), List.of(), List.of(),
                List.of(new NewstartCanonicalAnswerPolicy.PatternEvidence(
                        "PAT-01", "재산가액≥채무 사례에서 0% 임시·확정안 후 체결 보류")),
                List.of(), List.of());

        // when: 근거 블록 조립
        String block = NewstartCanonicalAnswerPolicy.composeEvidenceBlock(bundle);

        // then: 패턴 마커 존재, 확정 표현 없음
        assertThat(block).contains(NewstartCanonicalAnswerPolicy.PATTERN_NOT_RULE_MARKER);
        assertThat(NewstartCanonicalAnswerPolicy.containsForbiddenVerdictPhrase(block)).isFalse();
    }

    @Test
    @DisplayName("CASE D: 공식자료와 CASE 충돌 시 양쪽을 표시하고 임의 판정하지 않는다")
    void caseD_conflictBothSides() {
        // given: 층위 간 차이
        var bundle = new NewstartCanonicalAnswerPolicy.EvidenceBundle(
                "감면율 수치", List.of("공식: 심사 후 확정"),
                List.of(), List.of(),
                List.of(new NewstartCanonicalAnswerPolicy.CaseEvidence(
                        "CASE-CAFE-5866", "90% 탕감 완료 보고", "RESULT_CONFIRMED")),
                List.of(),
                List.of(new NewstartCanonicalAnswerPolicy.DifferenceEvidence(
                        "심사 후 확정", null, "90% 보고 사례 존재")),
                List.of());

        // when: 근거 블록 조립
        String block = NewstartCanonicalAnswerPolicy.composeEvidenceBlock(bundle);

        // then: 양쪽 병렬 + 확정 금지 문구, 금지 표현 없음
        assertThat(block).contains("공식자료:").contains("실제 사례:");
        assertThat(block).contains("하나로 확정해 안내하지 않습니다");
        assertThat(NewstartCanonicalAnswerPolicy.containsForbiddenVerdictPhrase(block)).isFalse();
    }

    @Test
    @DisplayName("CASE E: UNRESOLVED 사례는 결과 확정 표현 없이 미확인으로 표시한다")
    void caseE_unresolvedNotPromoted() {
        // given: 미해결 사례
        var bundle = bundleWith(
                List.of(),
                List.of(new NewstartCanonicalAnswerPolicy.CaseEvidence(
                        "CASE-CAFE-5987", "지급명령 후 이의신청 주장, 이후 결과 없음", "RESULT_PARTIAL")));

        // when: 근거 블록 조립
        String block = NewstartCanonicalAnswerPolicy.composeEvidenceBlock(bundle);

        // then: 미확인 마커 + 상태 보존
        assertThat(block).contains(NewstartCanonicalAnswerPolicy.UNRESOLVED_MARKER);
        assertThat(block).contains("RESULT_PARTIAL");
    }

    @Test
    @DisplayName("CASE F: 판단 요청 시 CASE 기반 참고 분석은 라벨을 밝힌다")
    void caseF_judgmentLabel() {
        // given: 참고 분석 문장
        String analysis = "참고로 " + NewstartCanonicalAnswerPolicy.JUDGMENT_LABEL_SENTENCE;

        // when/then: 라벨 취지가 포함되고, 라벨 없는 분석은 검출된다
        assertThat(NewstartCanonicalAnswerPolicy.containsJudgmentLabel(analysis)).isTrue();
        assertThat(NewstartCanonicalAnswerPolicy.containsJudgmentLabel("90% 받을 수 있습니다")).isFalse();
    }

    @Test
    @DisplayName("CASE G: 근거 없음이면 UNKNOWN으로 답하고 hallucination하지 않는다")
    void caseG_unknownWhenNoEvidence() {
        // given: 빈 근거
        var bundle = NewstartCanonicalAnswerPolicy.EvidenceBundle.empty("HF 보증 연장 결과");

        // when: 근거 블록 조립
        String block = NewstartCanonicalAnswerPolicy.composeEvidenceBlock(bundle);

        // then: UNKNOWN section만 있고 수치·결과 section이 없다
        assertThat(block).contains("[현재 확인되지 않은 부분]");
        assertThat(block).contains("공식 확인이 필요합니다");
        assertThat(block).doesNotContain("[공식적으로 확인되는 내용]");
        assertThat(block).doesNotContain("[실제 차주 사례에서 확인되는 내용]");
    }

    @Test
    @DisplayName("자동 검증: 정책 지침에 금지 표현이 없고 의무 문장이 있다")
    void automated_policyTextGuards() {
        // OFFICIAL_NOT_FOUND_AS_FALSE = 0: 지침이 NOT_FOUND를 FALSE로 해석하지 않음
        assertThat(NewstartCanonicalAnswerPolicy.SYSTEM_PROMPT_ADDENDUM)
                .contains("존재하지 않는다는 뜻이 아니다");
        // CASE_PROMOTED_TO_OFFICIAL = 0, REPEATED_PATTERN_PROMOTED_TO_RULE = 0
        assertThat(NewstartCanonicalAnswerPolicy.containsForbiddenVerdictPhrase(
                NewstartCanonicalAnswerPolicy.SYSTEM_PROMPT_ADDENDUM)).isFalse();
        // CASE_SUPPRESSED_BY_OFFICIAL_ABSENCE = 0
        assertThat(NewstartCanonicalAnswerPolicy.SYSTEM_PROMPT_ADDENDUM)
                .contains("관련 사례가 있으면");
        // MISSING_EVIDENCE_LABEL_ON_CASE_ANALYSIS = 0: 라벨 문장이 의무 취지를 담음
        assertThat(NewstartCanonicalAnswerPolicy.containsJudgmentLabel(
                NewstartCanonicalAnswerPolicy.JUDGMENT_LABEL_SENTENCE)).isTrue();
    }

    @Test
    @DisplayName("자동 검증: 사례 상태 분류가 미확정을 확정으로 올리지 않는다")
    void automated_caseOutcomeNeverPromotes() {
        // UNRESOLVED_PROMOTED_TO_RESULT = 0
        assertThat(NewstartCanonicalAnswerPolicy.classifyCaseOutcome(null, null, null))
                .isEqualTo("UNRESOLVED_CASE");
        assertThat(NewstartCanonicalAnswerPolicy.classifyCaseOutcome("", "", "UNKNOWN"))
                .isEqualTo("UNRESOLVED_CASE");
        assertThat(NewstartCanonicalAnswerPolicy.classifyCaseOutcome(null, "추가 증빙 예정", null))
                .isEqualTo("RESULT_PARTIAL");
        assertThat(NewstartCanonicalAnswerPolicy.classifyCaseOutcome("90%", null, null))
                .isEqualTo("RESULT_CONFIRMED");
        // TRUE GAP 상태는 그대로 보존
        assertThat(NewstartCanonicalAnswerPolicy.isUnresolvedStatus("RESULT_PARTIAL")).isTrue();
        assertThat(NewstartCanonicalAnswerPolicy.isUnresolvedStatus("UNRESOLVED_CASE")).isTrue();
        assertThat(NewstartCanonicalAnswerPolicy.isUnresolvedStatus("ONGOING")).isTrue();
        assertThat(NewstartCanonicalAnswerPolicy.isUnresolvedStatus("THIRD_PARTY_RESULT")).isTrue();
        assertThat(NewstartCanonicalAnswerPolicy.isUnresolvedStatus("RESULT_CONFIRMED")).isFalse();
    }
}
