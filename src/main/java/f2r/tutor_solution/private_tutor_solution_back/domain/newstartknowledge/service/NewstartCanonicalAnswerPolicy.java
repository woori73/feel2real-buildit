package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import java.util.List;

/**
 * NewStart Canonical Answer Policy — RAG prompt 조립 규칙.
 *
 * <p>Source of truth:
 * {@code docs/newstart/knowledge/NEWSTART_KNOWLEDGE_AND_ANSWER_POLICY.md} (canonical branch
 * {@code docs/newstart-canonical-integration-20260922}, HEAD {@code 4b807bd7}).
 * 이 클래스는 정책 문서의 기계적 표현이다. 정책을 다시 해석·재분류하지 않는다. 정책 문구와
 * 어긋나는 구조적 오류를 발견하면 수정하지 말고 보고한다.
 *
 * <p>핵심: CLASSIFY FIRST. INFORM SECOND. JUDGE ONLY WHEN ASKED.
 */
public final class NewstartCanonicalAnswerPolicy {

    private NewstartCanonicalAnswerPolicy() {
    }

    /** Canonical source 문서 위치. */
    public static final String POLICY_DOC =
            "docs/newstart/knowledge/NEWSTART_KNOWLEDGE_AND_ANSWER_POLICY.md";
    public static final String CANONICAL_BRANCH = "docs/newstart-canonical-integration-20260922";
    public static final String CANONICAL_HEAD = "4b807bd7";

    /** Wiki Router system prompt에 추가되는 canonical 지침. */
    public static final String SYSTEM_PROMPT_ADDENDUM =
            "너는 새출발기금 정보를 근거 성격별로 분류해서 제공하는 안내자다. "
                    + "원칙: 먼저 분류하고, 분류 상태 그대로 알리고, 판단·추측은 사용자가 명시적으로 요구할 때만 참고 분석으로 제공한다. "
                    + "근거 층위 OFFICIAL(공식자료 직접 확인) / OPERATOR(카페 운영자 가이드, 공식 규정 아님) / "
                    + "EBOOK(수집 전자책 정리) / CAFE_CASE(카페 게시글 실제 경험 진술) / "
                    + "REPEATED_CASE_PATTERN(여러 사례의 반복 관찰, 공식 규정 아님) / UNRESOLVED(질문·상황만 있고 결과 없음) / "
                    + "UNKNOWN(근거로 채울 수 없음)을 구분하고, 서로 다른 층위를 하나의 사실처럼 합성하지 않는다. "
                    + "공식자료에서 찾지 못한 것은 존재하지 않는다는 뜻이 아니다. "
                    + "이 경우 '현재 확보한 공식자료에서는 해당 세부기준을 확인하지 못했습니다.'라고 기록하고, "
                    + "관련 사례가 있으면 '다만 수집된 실제 차주 사례에서는 다음과 같은 처리가 보고되었습니다.' 형태로 이어서 제공한다. "
                    + "NOT_FOUND를 '그 기준이 존재하지 않는다'거나 '그렇게 처리되지 않는다'는 식의 단정 답변으로 바꾸지 않는다. "
                    + "실제 사례는 공식 규정이 아니다. 여러 사례에서 반복되어도 "
                    + "'수집된 여러 실제 사례에서 이러한 현상이 반복적으로 관찰됩니다.'로만 표현하고 "
                    + "제도가 그 처리를 보장한다는 규칙 단정으로 변환하지 않는다. "
                    + "결과가 확인되지 않은 사례(부분·진행중·미해결·제3자 진술)를 성공·실패 사례로 변환하지 않고, "
                    + "알 수 없는 부분은 UNKNOWN으로 답하며 채우지 않는다. "
                    + "사용자가 자신의 감면율·승인 여부 등 판단을 요구해도 먼저 근거를 분류해서 제공하고, "
                    + "사례 근거 참고 분석을 덧붙일 때만 "
                    + "'현재 확보한 공식 규정에서 확인된 사항은 아니며, 수집된 실제 사례에 근거한 참고 분석입니다.'라는 취지를 반드시 밝힌다. "
                    + "감면율 판정기·승인 가능/불가능 판정기로 동작하지 않는다. "
                    + "출처가 다른 내용이 충돌하면 한쪽을 삭제하지 않고 공식·운영자·사례를 병렬로 보여주고 차이를 명시한다. "
                    + "챗팅록(CHAT) 근거는 미검증(REVIEW_ONLY)이 원칙이며 확정 답변·확인된 사례처럼 제시하지 않는다.";

    /** CASE 근거 참고 분석 제공 시 의무 포함되는 취지의 문장. */
    public static final String JUDGMENT_LABEL_SENTENCE =
            "현재 확보한 공식 규정에서 확인된 사항은 아니며, 수집된 실제 사례에 근거한 참고 분석입니다.";

    /** OFFICIAL 부재 시 의무 사용되는 문장. */
    public static final String OFFICIAL_ABSENCE_SENTENCE =
            "현재 확보한 공식자료에서는 해당 세부기준을 확인하지 못했습니다.";

    /** OFFICIAL 부재 + CASE 존재 시 이어붙이는 문장. */
    public static final String CASE_BRIDGE_SENTENCE =
            "다만 수집된 실제 차주 사례에서는 다음과 같은 처리가 보고되었습니다.";

    /** 미해결 사례 표시 마커. */
    public static final String UNRESOLVED_MARKER =
            "결과 미확인 — 성공·실패로 단정하지 않습니다.";

    /** 반복 패턴 표시 마커 (officialRule=false 보존). */
    public static final String PATTERN_NOT_RULE_MARKER =
            "반복 관찰일 뿐 공식 규정이 아닙니다.";

    /** CHAT 출처 판별 값 (canonical ID prefix 규칙: CASE-CHAT-*). */
    public static final String CASE_SOURCE_CAFE = "CAFE_CASE";
    public static final String CASE_SOURCE_CHAT = "CHAT_CASE";

    /** CHAT(미검증) 사례 section 헤더 — GOLDEN-C/G 금지선: 확정 답변 제시 금지. */
    public static final String CHAT_UNVERIFIED_SECTION =
            "[미검증 대화록 사례 — REVIEW_ONLY, 확정 답변으로 제시 금지]";

    /** CHAT 미검증 마커. */
    public static final String CHAT_UNVERIFIED_MARKER =
            "미검증 대화록 — 확인된 사실로 단정하지 않습니다.";

    /**
     * 답변에 절대 등장하면 안 되는 단정·판정 표현.
     * 테스트가 정책 텍스트와 조립 블록을 이 목록으로 스캔한다.
     */
    public static final List<String> FORBIDDEN_VERDICT_PHRASES = List.of(
            "그런 기준은 없습니다",
            "그렇게 처리되지 않습니다",
            "제도상 반드시 이렇게 처리됩니다",
            "반드시 이렇게 처리됩니다",
            "확정 감면율",
            "승인이 확실",
            "100% 승인",
            "탈락이 확실");

    /** 근거가 없는 section을 억지로 만들지 않기 위한 빈 묶음 판별. */
    public record EvidenceBundle(
            String topic,
            List<String> official,
            List<String> operator,
            List<String> ebook,
            List<CaseEvidence> cases,
            List<PatternEvidence> patterns,
            List<DifferenceEvidence> differences,
            List<String> unknowns,
            List<SourceLinkEvidence> sourceLinks) {

        public EvidenceBundle {
            official = official == null ? List.of() : List.copyOf(official);
            operator = operator == null ? List.of() : List.copyOf(operator);
            ebook = ebook == null ? List.of() : List.copyOf(ebook);
            cases = cases == null ? List.of() : List.copyOf(cases);
            patterns = patterns == null ? List.of() : List.copyOf(patterns);
            differences = differences == null ? List.of() : List.copyOf(differences);
            unknowns = unknowns == null ? List.of() : List.copyOf(unknowns);
            sourceLinks = sourceLinks == null ? List.of() : List.copyOf(sourceLinks);
        }

        /** sourceLinks 추가 전 8-arg 호출 호환 (additive 변경). */
        public EvidenceBundle(
                String topic,
                List<String> official,
                List<String> operator,
                List<String> ebook,
                List<CaseEvidence> cases,
                List<PatternEvidence> patterns,
                List<DifferenceEvidence> differences,
                List<String> unknowns) {
            this(topic, official, operator, ebook, cases, patterns, differences, unknowns, List.of());
        }

        public static EvidenceBundle empty(String topic) {
            return new EvidenceBundle(topic, List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of());
        }
    }

    /**
     * 개별 사례 근거. status는 canonical caseStatus 그대로 보존한다.
     * caseSource는 CAFE_CASE/CHAT_CASE 구분 값 — provenance를 generic CASE로 뭉개지 않는다.
     * canonical ID prefix 규칙(NEWSTART_CANONICAL_TO_AGENT1_HANDOFF §1): CASE-CHAT-* 만 CHAT, 나머진 CAFE 계열.
     */
    public record CaseEvidence(String canonicalId, String summary, String caseStatus, String caseSource) {

        public CaseEvidence {
            if (caseSource == null || caseSource.isBlank()) {
                caseSource = inferCaseSource(canonicalId);
            }
        }

        /** caseSource 생략 생성자 — canonicalId prefix로 deterministic하게 추론. */
        public CaseEvidence(String canonicalId, String summary, String caseStatus) {
            this(canonicalId, summary, caseStatus, inferCaseSource(canonicalId));
        }

        public boolean isChatSource() {
            return CASE_SOURCE_CHAT.equals(caseSource);
        }

        private static String inferCaseSource(String canonicalId) {
            return canonicalId != null && canonicalId.startsWith("CASE-CHAT")
                    ? CASE_SOURCE_CHAT : CASE_SOURCE_CAFE;
        }
    }

    /**
     * 출처 링크 근거. canonical provenance에 실재하는 URL/reference만 사용한다 —
     * source가 없는 evidence에는 URL을 생성하지 않는다 (url null 허용, 가짜 URL 금지).
     */
    public record SourceLinkEvidence(String evidenceId, String label, String url) {
    }

    /** 반복 패턴 근거. officialRule은 항상 false로 보존한다. */
    public record PatternEvidence(String patternId, String observation) {
    }

    /** 층위 간 차이. 어느 한쪽도 삭제·판정하지 않는다. */
    public record DifferenceEvidence(String officialSide, String operatorSide, String caseSide) {
    }

    /**
     * 근거 묶음을 고정 순서의 section 블록으로 조립한다.
     * 근거가 없는 section은 생성하지 않는다. 근거가 전부 비면 UNKNOWN section만 둔다.
     */
    public static String composeEvidenceBlock(EvidenceBundle bundle) {
        StringBuilder sb = new StringBuilder();
        if (bundle.topic() != null && !bundle.topic().isBlank()) {
            sb.append("[주제]\n").append(bundle.topic().strip()).append("\n");
        }
        boolean hasOfficial = !bundle.official().isEmpty();
        if (hasOfficial) {
            sb.append("\n[공식적으로 확인되는 내용]\n");
            bundle.official().forEach(line -> sb.append("- ").append(line).append("\n"));
        }
        if (!bundle.operator().isEmpty()) {
            sb.append("\n[운영자 가이드 — 공식 규정이 아님]\n");
            bundle.operator().forEach(line -> sb.append("- ").append(line).append("\n"));
        }
        if (!bundle.ebook().isEmpty()) {
            sb.append("\n[전자책 정리]\n");
            bundle.ebook().forEach(line -> sb.append("- ").append(line).append("\n"));
        }
        List<CaseEvidence> cafeCases = bundle.cases().stream().filter(c -> !c.isChatSource()).toList();
        List<CaseEvidence> chatCases = bundle.cases().stream().filter(CaseEvidence::isChatSource).toList();
        if (!cafeCases.isEmpty()) {
            if (!hasOfficial) {
                sb.append("\n").append(OFFICIAL_ABSENCE_SENTENCE).append("\n");
                sb.append(CASE_BRIDGE_SENTENCE).append("\n");
            }
            sb.append("\n[실제 차주 사례에서 확인되는 내용]\n");
            for (CaseEvidence c : cafeCases) {
                sb.append("- [").append(c.canonicalId()).append("] ").append(c.summary());
                if (c.caseStatus() != null && !c.caseStatus().isBlank()) {
                    sb.append(" (").append(c.caseStatus()).append(")");
                }
                if (isUnresolvedStatus(c.caseStatus())) {
                    sb.append(" — ").append(UNRESOLVED_MARKER);
                }
                sb.append("\n");
            }
        }
        if (!chatCases.isEmpty()) {
            if (!hasOfficial && cafeCases.isEmpty()) {
                sb.append("\n").append(OFFICIAL_ABSENCE_SENTENCE).append("\n");
            }
            sb.append("\n").append(CHAT_UNVERIFIED_SECTION).append("\n");
            for (CaseEvidence c : chatCases) {
                sb.append("- [").append(c.canonicalId()).append("] ").append(c.summary())
                        .append(" (").append(c.caseStatus() == null || c.caseStatus().isBlank()
                                ? "UNVERIFIED" : c.caseStatus()).append(")")
                        .append(" — ").append(CHAT_UNVERIFIED_MARKER).append("\n");
            }
        }
        if (!bundle.patterns().isEmpty()) {
            sb.append("\n[반복적으로 관찰된 사례]\n");
            for (PatternEvidence p : bundle.patterns()) {
                sb.append("- [").append(p.patternId()).append("] ").append(p.observation())
                        .append(" (").append(PATTERN_NOT_RULE_MARKER).append(")\n");
            }
        }
        if (!bundle.differences().isEmpty()) {
            sb.append("\n[자료 사이의 차이]\n");
            for (DifferenceEvidence d : bundle.differences()) {
                if (d.officialSide() != null) {
                    sb.append("- 공식자료: ").append(d.officialSide()).append("\n");
                }
                if (d.operatorSide() != null) {
                    sb.append("- 운영자 가이드: ").append(d.operatorSide()).append("\n");
                }
                if (d.caseSide() != null) {
                    sb.append("- 실제 사례: ").append(d.caseSide()).append("\n");
                }
                sb.append("  (출처별 적용 시점·조건·사례별 결과가 다를 수 있습니다. 하나로 확정해 안내하지 않습니다.)\n");
            }
        }
        boolean hasAnyEvidence = hasOfficial
                || !bundle.operator().isEmpty()
                || !bundle.ebook().isEmpty()
                || !bundle.cases().isEmpty()
                || !bundle.patterns().isEmpty()
                || !bundle.differences().isEmpty();
        if (!bundle.sourceLinks().isEmpty()) {
            sb.append("\n[출처 링크]\n");
            for (SourceLinkEvidence l : bundle.sourceLinks()) {
                sb.append("- [").append(l.evidenceId()).append("] ").append(l.label());
                // URL은 provenance에 실재할 때만 — 없으면 붙이지 않는다(가짜 URL 생성 금지).
                if (l.url() != null && !l.url().isBlank()) {
                    sb.append(" (").append(l.url()).append(")");
                }
                sb.append("\n");
            }
        }
        if (!bundle.unknowns().isEmpty() || !hasAnyEvidence) {
            sb.append("\n[현재 확인되지 않은 부분]\n");
            if (!hasAnyEvidence) {
                sb.append("- 현재 확인된 근거 자료가 없어 확인해 드릴 수 없습니다. 공식 확인이 필요합니다.\n");
            }
            bundle.unknowns().forEach(line -> sb.append("- ").append(line).append("\n"));
        }
        return sb.toString();
    }

    /**
     * EmpiricalCase 행의 확정 결과 보유 여부를 canonical 상태명으로 분류한다 (표시용).
     * DB에 caseStatus 컬럼이 없으므로 finalReductionRate/authorFollowupResult/predictionVsActual으로 판별한다.
     * 미확정은 절대 확정으로 올리지 않는다.
     */
    public static String classifyCaseOutcome(String finalReductionRate,
                                             String authorFollowupResult,
                                             String predictionVsActual) {
        if (isKnownValue(finalReductionRate)) {
            return "RESULT_CONFIRMED";
        }
        if (authorFollowupResult != null && !authorFollowupResult.isBlank()) {
            return "RESULT_PARTIAL";
        }
        if (isKnownValue(predictionVsActual)) {
            return "RESULT_PARTIAL";
        }
        return "UNRESOLVED_CASE";
    }

    /** canonical HANDOFF §1: "UNKNOWN" 접두 변형(추적필요 등)은 전부 UNKNOWN으로 취급. */
    private static boolean isKnownValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return !value.strip().toUpperCase().startsWith("UNKNOWN");
    }

    /** TRUE GAP 상태(부분·진행중·미해결·제3자) 여부. */
    public static boolean isUnresolvedStatus(String caseStatus) {
        if (caseStatus == null) {
            return true;
        }
        return switch (caseStatus.strip()) {
            case "RESULT_PARTIAL", "UNRESOLVED_CASE", "ONGOING", "THIRD_PARTY_RESULT",
                 "RESULT_PARTIAL_OR_ONGOING", "NOT_CHECKED" -> true;
            default -> false;
        };
    }

    /** 참고 분석 라벨 의무 문장의 취지가 포함됐는지 검사 (기계적 동일 문장 강제 아님). */
    public static boolean containsJudgmentLabel(String text) {
        if (text == null) {
            return false;
        }
        return text.contains("공식 규정에서 확인된 사항은 아니며")
                && text.contains("실제 사례에 근거한 참고 분석");
    }

    /** 금지된 단정·판정 표현이 포함됐는지 검사. */
    public static boolean containsForbiddenVerdictPhrase(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return FORBIDDEN_VERDICT_PHRASES.stream().anyMatch(text::contains);
    }
}
