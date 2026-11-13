package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsCounselingSessionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.ResearchPack;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.SourceClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("입시상담 — 지난해 입시결과 답변 정책(지난해 표시·원문 지표명·판정 금지·성적 나란히 보기)")
class AdmissionsPriorResultChatTest {

    private static final List<String> UNIVS = List.of("테스트대학교", "예시대학교", "연세대학교");

    private AdmissionsLlmClient llm;
    private AdmissionsResearchService research;
    private AdmissionsFactSearchService facts;
    private AdmissionsChatService service;

    @BeforeEach
    void setUp() {
        llm = mock(AdmissionsLlmClient.class);
        research = mock(AdmissionsResearchService.class);
        facts = mock(AdmissionsFactSearchService.class);
        service = new AdmissionsChatService(llm, research, facts, mock(AdmissionsCounselingSessionRepository.class));
        when(research.research(anyString(), any())).thenReturn(ResearchPack.builder().question("q")
                .confirmedHits(List.of()).plannedHits(List.of()).officialDocHits(List.of()).youtubeHits(List.of())
                .conflictHits(List.of()).guideHits(List.of()).gapNotes(List.of()).build());
    }

    private static Evidence prior(long id, String univ, String round, String unit, String excerpt) {
        return Evidence.builder().stepNo(0).sourceClass(SourceClass.OFFICIAL_PRIOR_RESULT).refId("prior:" + id)
                .refUrl("https://example.test/" + id + ".pdf")
                .title(univ + " 2026학년도(지난해) " + round + " 입시결과: " + unit)
                .excerpt(excerpt).note("대학 입학처 공식 입시결과(2026학년도, 지난해 — 올해 결과 아님)")
                .academicYear(2026).yearConfirmed(true).build();
    }

    private static final Evidence TST = prior(1, "테스트대학교", "수시", "학생부교과(지역균형) 컴퓨터공학과",
            "모집인원 10명, 지원자 125명, 경쟁률 12.5:1, 충원(추가합격) 7명\n성적 지표(대학 원문 표기 그대로, 지표끼리 합치거나 환산하지 않음): "
                    + "최종등록자 교과등급 70%컷 = 2.31");
    private static final Evidence EXA = prior(2, "예시대학교", "수시", "논술전형 컴퓨터공학과",
            "모집인원 5명, 경쟁률 40.2:1\n성적 지표(대학 원문 표기 그대로, 지표끼리 합치거나 환산하지 않음): 최종등록자 논술 환산점수 평균 = 81.4");

    private void givenFacts(String question, List<Evidence> evidence) {
        var parsed = AdmissionsQueryParser.parse(question, UNIVS);
        when(facts.search(eq(question), any())).thenReturn(
                new AdmissionsFactSearchService.FactResult(evidence, parsed, Set.of(2027), false));
    }

    private void givenLlmAnswers(String text) {
        when(llm.complete(anyString(), anyString(), anyInt()))
                .thenReturn(NewStartLlmCompletion.success(text, "test", "test-model"));
    }

    @Test
    @DisplayName("given 지난해 결과 근거 when 질문하면 then 근거에 없는 값을 '공개되지 않음'으로 단정하지 말라는 규칙을 LLM에 넘긴다")
    void priorQuestion_promptForbidsCallingMissingValuesUndisclosed() {
        // given
        String q = "테스트대 컴퓨터공학과 충원 몇 명 돌았어요?";
        givenFacts(q, List.of(TST));
        givenLlmAnswers("테스트대학교 컴퓨터공학과 지난해 자료에 충원 인원 값이 없습니다.");

        // when
        service.ask(q, null, null);

        // then
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(anyString(), user.capture(), anyInt());
        assertThat(user.getValue()).contains(AdmissionsChatService.PRIOR_MISSING_RULE)
                .doesNotContain("'공개되지 않음'이라고 하라");
    }

    @Test
    @DisplayName("given 승인된 지난해 결과 when 경쟁률을 물으면 then LLM이 답하고 지난해 안내·출처 종류 '공식 입시결과 (지난해)'가 붙는다")
    void priorQuestion_withData_isAnsweredWithLastYearLabel() {
        // given
        String q = "테스트대 컴퓨터공학과 경쟁률 알려줘";
        givenFacts(q, List.of(TST));
        givenLlmAnswers("테스트대학교 학생부교과(지역균형) 컴퓨터공학과의 지난해 경쟁률은 12.5:1입니다.");

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).startsWith(AdmissionsChatService.PRIOR_DISCLAIMER).contains("12.5:1");
        assertThat(answer.sources()).anySatisfy(s -> {
            assertThat(s.kind()).isEqualTo("공식 입시결과 (지난해)");
            assertThat(s.url()).isEqualTo("https://example.test/1.pdf");
        });
    }

    private static final Evidence CUR = Evidence.builder().stepNo(0).sourceClass(SourceClass.OFFICIAL_PRIOR_RESULT).refId("prior:9")
            .refUrl("https://example.test/9")
            .title("테스트대학교 2027학년도" + AdmissionsFactSearchService.CURRENT_YEAR_TAG + " 수시 경쟁률: 학생부교과(지역균형) 컴퓨터공학과")
            .excerpt("모집인원 10명, 지원자 83명, 경쟁률 8.3:1")
            .note("대학 입학처 공식 원서접수 최종 경쟁률(2027학년도, 올해 — 합격·등록 결과 아님)")
            .academicYear(2027).yearConfirmed(true).build();

    @Test
    @DisplayName("given 올해 원서접수 경쟁률 근거만 있음 when 경쟁률을 물으면 then 지난해 안내 대신 올해 원서접수 안내·출처 종류가 붙는다")
    void currentRatioOnly_isNotCalledLastYear() {
        // given
        String q = "테스트대 컴퓨터공학과 경쟁률 알려줘";
        givenFacts(q, List.of(CUR));
        givenLlmAnswers("테스트대학교 학생부교과(지역균형) 컴퓨터공학과 경쟁률은 8.3:1입니다.");

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).startsWith(AdmissionsChatService.CURRENT_RATIO_NOTICE).contains("8.3:1")
                .doesNotContain(AdmissionsChatService.PRIOR_DISCLAIMER);
        assertThat(answer.sources()).anySatisfy(s -> assertThat(s.kind()).isEqualTo("공식 경쟁률 (올해 원서접수)"));
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(system.capture(), user.capture(), anyInt());
        assertThat(system.getValue()).contains("올해 원서접수 최종 경쟁률 — 지난해 결과가 아니며").doesNotContain("지난해 입시결과 — 올해 모집요강이 아니며");
        assertThat(user.getValue()).contains("[올해 원서접수 경쟁률]").doesNotContain("[지난해 입시결과]");
    }

    @Test
    @DisplayName("given 올해 경쟁률과 지난해 결과가 함께 있음 when 질문하면 then 두 학년도 함께 안내가 한 번 붙고 출처 종류가 구분된다")
    void currentRatioAndLastYear_bothNoticesAndKinds() {
        // given
        String q = "테스트대 컴퓨터공학과 경쟁률 알려줘";
        givenFacts(q, List.of(CUR, TST));
        givenLlmAnswers("테스트대학교 컴퓨터공학과 2027학년도 경쟁률 8.3:1, 2026학년도 경쟁률 12.5:1입니다.");

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).startsWith(AdmissionsChatService.BOTH_YEARS_NOTICE)
                .doesNotContain(AdmissionsChatService.CURRENT_RATIO_NOTICE).doesNotContain(AdmissionsChatService.PRIOR_DISCLAIMER);
        assertThat(answer.sources()).extracting(s -> s.kind())
                .contains("공식 경쟁률 (올해 원서접수)", "공식 입시결과 (지난해)");
    }

    @Test
    @DisplayName("given 그 대학 지난해 결과가 없음 when 입결을 물으면 then LLM 없이 그 대학은 아직 수집되지 않았다고 답한다")
    void priorQuestion_withoutData_isRefusedPerUniversity() {
        // given
        String q = "연세대 입결 알려줘";
        givenFacts(q, List.of());

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).isEqualTo(AdmissionsChatService.msgPriorNotCollected(List.of("연세대학교")));
        verify(llm, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("given 권장 이수 과목 질문 when 지난해 결과가 있어도 then 여전히 수집하지 않은 자료로 답한다")
    void recommendedCourses_stayNotCollected() {
        // given
        String q = "테스트대 컴퓨터공학과 권장과목이랑 경쟁률 알려줘";
        givenFacts(q, List.of(TST));

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).isEqualTo(AdmissionsChatService.MSG_NOT_COLLECTED);
        verify(llm, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("given 지표가 다른 두 대학 when 함께 물으면 then 프롬프트에 두 지표명이 원문 그대로 따로 들어가고 합치지 말라는 규칙이 있다")
    void twoUniversities_metricsAreNotMerged() {
        // given
        String q = "테스트대랑 예시대 컴퓨터공학과 입결 비교해줘";
        givenFacts(q, List.of(TST, EXA));
        givenLlmAnswers("2026학년도(지난해) 결과이며 올해와 다를 수 있습니다. 테스트대학교 최종등록자 교과등급 70%컷 2.31, "
                + "예시대학교 최종등록자 논술 환산점수 평균 81.4입니다.");
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);

        // when
        var answer = service.ask(q, null, null);

        // then
        verify(llm).complete(system.capture(), user.capture(), anyInt());
        assertThat(system.getValue()).contains("최종등록자 교과등급 70%컷 = 2.31", "최종등록자 논술 환산점수 평균 = 81.4",
                "결과 학년도: 2026학년도 (지난해 입시결과");
        assertThat(user.getValue()).contains("서로 다른 지표나 서로 다른 대학의 지표를 하나로 합치거나 평균내거나 환산하지 마라");
        assertThat(answer.answer()).doesNotStartWith(AdmissionsChatService.PRIOR_DISCLAIMER + "\n2026"); // 이미 안내가 있으면 중복으로 붙이지 않는다
    }

    @Test
    @DisplayName("given LLM이 합격 판정 문장을 씀 when 답하면 then 그 답변을 버리고 근거를 그대로 옮긴 답변으로 바꾼다")
    void verdictPhrase_isReplacedWithEvidenceOnlyAnswer() {
        // given
        String q = "내신 2.5등급인데 테스트대 컴퓨터공학과 교과 합격 가능성 있나요?";
        givenFacts(q, List.of(TST));
        givenLlmAnswers("지난해 70%컷이 2.31이라 2.5등급이면 합격권에 가깝고 충분히 합격할 수 있습니다.");

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).startsWith(AdmissionsChatService.PRIOR_DISCLAIMER)
                .contains("최종등록자 교과등급 70%컷 = 2.31")
                .contains("지난해 최종등록자 교과등급 70%컷 2.31, 입력 성적 2.5등급")
                .doesNotContain("합격권").doesNotContain("합격할 수 있");
        assertThat(AdmissionsChatService.containsVerdict(answer.answer())).isFalse();
    }

    @Test
    @DisplayName("given 판정 표현 목록 when 검사하면 then 판정 문장은 잡고 '판단해 드릴 수 없다' 안내 문장은 통과시킨다")
    void containsVerdict_distinguishesVerdictFromNotice() {
        // given
        List<String> verdicts = List.of("합격 가능성이 높습니다.", "안정권입니다.", "충분히 합격할 수 있어요.", "불합격 가능성이 큽니다.",
                "이 성적이면 유리합니다.", "합격은 어렵습니다.");
        String notice = "합격 가능성은 판단해 드릴 수 없지만, 지원 판단에 참고할 공식 정보를 정리해 드립니다.";

        // when / then
        assertThat(verdicts).allMatch(AdmissionsChatService::containsVerdict);
        assertThat(AdmissionsChatService.containsVerdict(notice)).isFalse();
    }

    @Test
    @DisplayName("given 내신 등급과 논술 점수 지표 when 나란히 보기를 만들면 then 같은 체계만 나란히 놓고 다른 체계는 비교하지 않음이라 적는다")
    void comparisonLines_onlySameKind() {
        // given
        var scores = AdmissionsQueryParser.studentScores("내신 2.5등급이에요");

        // when
        List<String> lines = AdmissionsChatService.priorComparisonLines(List.of(TST, EXA), scores);

        // then
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("지난해 최종등록자 교과등급 70%컷 2.31, 입력 성적 2.5등급");
        assertThat(lines.get(1)).contains("예시대학교").contains("비교하지 않음");
        assertThat(lines).noneMatch(AdmissionsChatService::containsVerdict);
    }

    @Test
    @DisplayName("given 괄호가 섞인 원문 전형명 when 질문이 세부 이름만 말하면 then 그 전형으로 알아본다")
    void priorTypeMentioned_matchesBracketedNames() {
        // given / when / then
        assertThat(AdmissionsFactSearchService.priorTypeMentioned("연세대 경영학과 활동우수형 70%컷", "학생부종합[활동우수형]")).isTrue();
        assertThat(AdmissionsFactSearchService.priorTypeMentioned("경희대 지역균형 입결", "학생부교과[지역균형전형]")).isTrue();
        assertThat(AdmissionsFactSearchService.priorTypeMentioned("한양대 교과 경쟁률", "학생부교과 (추천형)")).isTrue();
        assertThat(AdmissionsFactSearchService.priorTypeMentioned("연세대 경영학과 활동우수형 70%컷", "학생부교과[추천형]")).isFalse();
    }

    @Test
    @DisplayName("given '농생명계열 제외'가 붙은 전형명 when 질문이 농생명계열만 말하면 then 제외 전형으로 맞추지 않는다")
    void priorTypeMentioned_ignoresExcludedPiece() {
        // given
        String excluded = "기회균형특별전형(사회통합) 전체(농생명계열 제외)";
        String included = "기회균형특별전형(사회통합) 농생명계열";

        // when / then
        assertThat(AdmissionsFactSearchService.priorTypeMentioned("서울대 기회균형 농생명계열 작년 충원 인원", excluded)).isFalse();
        assertThat(AdmissionsFactSearchService.priorTypeMentioned("서울대 기회균형 농생명계열 작년 충원 인원", included)).isTrue();
        assertThat(AdmissionsFactSearchService.priorTypeMentioned("서울대 농생명계열 제외 기회균형 충원", excluded)).isTrue();
    }

    @Test
    @DisplayName("given 대학 없이 가능성만 물음 when 성적을 적어도 then 대학·전형을 알려 달라고 안내한다")
    void chanceWithoutUniversity_stillAsksForTarget() {
        // given
        String q = "내신 2.5등급이면 합격 가능성 있나요?";
        givenFacts(q, List.of());

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).isEqualTo(AdmissionsChatService.MSG_CHANCE_NEED_TARGET);
        verify(llm, never()).complete(anyString(), anyString(), anyInt());
    }
}
