package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsCounselingSessionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.ResearchPack;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.SourceClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("입시상담 답변 정책 — 근거 없는 답변·합격 판단·미발표 학년도 차단")
class AdmissionsChatServicePolicyTest {

    private static final List<String> UNIVS = List.of("서울대학교", "연세대학교", "인하대학교");

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
    }

    private Evidence fact(String id, String title, String excerpt, int year, String url) {
        return Evidence.builder().stepNo(0).sourceClass(year == 2027 ? SourceClass.OFFICIAL_CONFIRMED : SourceClass.OFFICIAL_PLANNED)
                .refId(id).refUrl(url).title(title).excerpt(excerpt).note("공식자료 DB").academicYear(year)
                .yearConfirmed(year == 2027).build();
    }

    private Evidence guide() {
        return Evidence.builder().stepNo(9).sourceClass(SourceClass.GUIDE_NOTE).refId("knowledge:1")
                .title("상담 지식: Q1").excerpt("질문 유형 안내").note("안내문").academicYear(2027).yearConfirmed(true).build();
    }

    private ResearchPack pack(List<Evidence> guides) {
        return ResearchPack.builder().question("q").confirmedHits(List.of()).plannedHits(List.of())
                .officialDocHits(List.of()).youtubeHits(List.of()).conflictHits(List.of())
                .guideHits(guides).gapNotes(List.of()).build();
    }

    private void givenFacts(String question, List<Evidence> evidence, Set<Integer> years, boolean unpublished) {
        var parsed = AdmissionsQueryParser.parse(question, UNIVS);
        when(facts.search(eq(question), any())).thenReturn(
                new AdmissionsFactSearchService.FactResult(evidence, parsed, years, unpublished));
    }

    @Test
    @DisplayName("given 합격 가능성·추천 질문 when 묻으면 then LLM을 호출하지 않고 안내 문구를 돌려준다")
    void admission_chance_question_is_refused_without_llm() {
        // given
        String q = "합격 가능성 높은 조합으로 6장 추천해 주세요.";
        givenFacts(q, List.of(), Set.of(2027), false);

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).isEqualTo(AdmissionsChatService.MSG_CHANCE);
        assertThat(answer.sources()).isEmpty();
        verify(llm, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("given 경쟁률 질문 when 묻으면 then 수집하지 않은 자료라고 답하고 LLM을 호출하지 않는다")
    void not_collected_topic_is_refused_without_llm() {
        // given
        String q = "작년 경쟁률 보면 2028도 감이 오나요?";
        givenFacts(q, List.of(), Set.of(2028), true);

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).isEqualTo(AdmissionsChatService.MSG_NOT_COLLECTED);
        verify(llm, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("given GUIDE 안내문만 검색된 질문 when 묻으면 then 사실 근거가 없으므로 LLM을 호출하지 않는다")
    void guide_only_hits_do_not_reach_llm() {
        // given
        String q = "반도체 전공 대학별 길이가 어떻게 돼요?";
        givenFacts(q, List.of(), Set.of(2027), false);
        when(research.research(eq(q), any())).thenReturn(pack(List.of(guide())));

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).isEqualTo(AdmissionsChatService.MSG_NO_EVIDENCE);
        assertThat(answer.sources()).isEmpty();
        verify(llm, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("given 2028 일정 질문 when 2028 근거가 없으면 then 2027 자료로 대신 답하지 않고 미발표로 답한다")
    void unpublished_2028_is_not_answered_from_2027() {
        // given
        String q = "2028 수시 원서접수는 몇 월인가요?";
        givenFacts(q, List.of(), Set.of(2028), true);
        when(research.research(eq(q), any())).thenReturn(ResearchPack.builder().question(q)
                .confirmedHits(List.of()).plannedHits(List.of())
                .officialDocHits(List.of(fact("material:1", "서울대 2027 모집요강", "서울대 (2026-09-01)", 2027, "https://x")))
                .youtubeHits(List.of()).conflictHits(List.of()).guideHits(List.of()).gapNotes(List.of()).build());

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).isEqualTo(AdmissionsChatService.MSG_UNPUBLISHED_2028);
        verify(llm, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("given 근거에 있는 수치로만 답한 LLM 응답 when 검증하면 then 통과하고 URL 없는 근거도 ID·제목으로 출처에 나온다")
    void grounded_answer_passes_and_sources_include_id_without_url() {
        // given
        String q = "인하대 컴퓨터공학과 몇 명 뽑나요";
        Evidence e = fact("dept:7", "인하대학교 2027학년도 컴퓨터공학과 모집인원", "전형별 모집인원: 합계 101명", 2027, null);
        givenFacts(q, List.of(e), Set.of(2027), false);
        when(research.research(eq(q), any())).thenReturn(pack(List.of(guide())));
        when(llm.complete(anyString(), anyString(), anyInt()))
                .thenReturn(NewStartLlmCompletion.success("2027학년도 기준 인하대 컴퓨터공학과는 101명을 모집합니다.", "t", "m"));

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).contains("101명");
        assertThat(answer.sources()).hasSize(1);
        assertThat(answer.sources().get(0).id()).isEqualTo("dept:7");
        assertThat(answer.sources().get(0).url()).isNull();
        assertThat(answer.sources()).noneMatch(s -> "knowledge:1".equals(s.id())); // GUIDE는 출처로 표시하지 않는다
    }

    @Test
    @DisplayName("given 근거에 없는 숫자를 만들어낸 LLM 응답 when 검증하면 then 폐기하고 안전 문구로 대체한다")
    void ungrounded_number_in_answer_is_rejected() {
        // given
        String q = "인하대 컴퓨터공학과 몇 명 뽑나요";
        Evidence e = fact("dept:7", "인하대학교 2027학년도 컴퓨터공학과 모집인원", "전형별 모집인원: 합계 101명", 2027, null);
        givenFacts(q, List.of(e), Set.of(2027), false);
        when(research.research(eq(q), any())).thenReturn(pack(List.of()));
        when(llm.complete(anyString(), anyString(), anyInt()))
                .thenReturn(NewStartLlmCompletion.success("2027학년도 인하대 컴퓨터공학과는 150명을 모집하고 경쟁률은 12대 1입니다.", "t", "m"));

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).isEqualTo(AdmissionsChatService.MSG_UNGROUNDED_NUMBER);
        assertThat(answer.sources()).isEmpty();
    }

    @Test
    @DisplayName("given 답변 숫자 when 근거와 비교하면 then 연도·한 자리·근거 숫자는 허용하고 나머지만 반환한다")
    void ungroundedNumbers_rules() {
        // given
        var e = fact("dept:1", "t", "합계 101명, 논술 11명", 2027, null);

        // when
        var bad = AdmissionsChatService.ungroundedNumbers("2027학년도 101명 중 논술 11명, 정시 7명, 면접 33명", "질문", List.of(e));

        // then
        assertThat(bad).containsExactly("33");
    }

    @Test
    @DisplayName("given 2028 일정 질문 when 2028 공식자료 제목만 검색되면 then 제목만으로는 일정을 답하지 않고 미발표로 답한다")
    void title_only_2028_material_is_not_enough_for_schedule() {
        // given
        String q = "2028 수시 원서접수는 몇 월인가요?";
        givenFacts(q, List.of(), Set.of(2028), true);
        when(research.research(eq(q), any())).thenReturn(ResearchPack.builder().question(q)
                .confirmedHits(List.of()).plannedHits(List.of())
                .officialDocHits(List.of(fact("material:9", "연세대 2028학년도 수시 시행계획", "연세대학교", 2028, "https://x")))
                .youtubeHits(List.of()).conflictHits(List.of()).guideHits(List.of()).gapNotes(List.of()).build());

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).isEqualTo(AdmissionsChatService.MSG_UNPUBLISHED_2028);
        verify(llm, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("given 2028 전형 행이 있는 질문 when 묻으면 then 2028 계획 근거로 LLM에 전달한다")
    void planned_2028_type_row_reaches_llm() {
        // given
        String q = "연세대 2028은 뭐가 달라지나요";
        Evidence e = fact("type:3", "연세대학교 2028학년도 활동우수형", "시행계획 방향: 변경 예정", 2028, null);
        givenFacts(q, List.of(e), Set.of(2028), false);
        when(research.research(eq(q), any())).thenReturn(pack(List.of()));
        when(llm.complete(anyString(), anyString(), anyInt()))
                .thenReturn(NewStartLlmCompletion.success("2028학년도는 시행계획 단계로 변경될 수 있습니다.", "t", "m"));

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).contains("시행계획");
        assertThat(answer.sources()).extracting(AdmissionsChatService.AnswerSource::id).containsExactly("type:3");
    }

    @Test
    @DisplayName("given 공식자료 제목만 검색된 질문(S15형) when 묻으면 then 본문이 없으므로 LLM을 호출하지 않는다")
    void title_only_materials_do_not_reach_llm() {
        // given
        String q = "2028 수능은 통합사회·통합과학을 본다면서요? 저는 2027 수능인데?";
        givenFacts(q, List.of(), Set.of(2028, 2027), false);
        when(research.research(eq(q), any())).thenReturn(ResearchPack.builder().question(q)
                .confirmedHits(List.of()).plannedHits(List.of())
                .officialDocHits(List.of(
                        fact("material:1", "서울대 2027학년도 수시 모집요강", "서울대학교", 2027, "https://a"),
                        fact("material:5", "연세대 2028학년도 시행계획", "연세대학교", 2028, "https://b")))
                .youtubeHits(List.of()).conflictHits(List.of()).guideHits(List.of()).gapNotes(List.of()).build());

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).isEqualTo(AdmissionsChatService.MSG_TITLE_ONLY);
        assertThat(answer.sources()).isEmpty();
        verify(llm, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("given 공식자료 제목과 학과 행이 함께 있는 질문 when 묻으면 then 정상 FACT 답변은 차단하지 않고 LLM에 전달한다")
    void material_plus_fact_row_still_reaches_llm() {
        // given
        String q = "인하대 컴퓨터공학과 몇 명 뽑나요";
        Evidence dept = fact("dept:7", "인하대학교 2027학년도 컴퓨터공학과 모집인원", "전형별 모집인원: 합계 101명", 2027, null);
        givenFacts(q, List.of(dept), Set.of(2027), false);
        when(research.research(eq(q), any())).thenReturn(ResearchPack.builder().question(q)
                .confirmedHits(List.of()).plannedHits(List.of())
                .officialDocHits(List.of(fact("material:7", "인하대 2027 수시 모집요강", "인하대학교", 2027, "https://x")))
                .youtubeHits(List.of()).conflictHits(List.of()).guideHits(List.of()).gapNotes(List.of()).build());
        when(llm.complete(anyString(), anyString(), anyInt()))
                .thenReturn(NewStartLlmCompletion.success("2027학년도 기준 101명을 모집합니다.", "t", "m"));

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).contains("101명");
        assertThat(answer.sources()).extracting(AdmissionsChatService.AnswerSource::id)
                .containsExactly("dept:7", "material:7");
    }

    @Test
    @DisplayName("given FACT 지식 본문만 있는 질문 when 묻으면 then 사실 근거로 인정해 LLM에 전달한다")
    void fact_knowledge_alone_reaches_llm() {
        // given
        String q = "연세대 2028 변화가 뭐예요";
        Evidence k = fact("knowledge:42", "연세대 2028 시행계획 요약", "활동우수형 신설 방향", 2028, null);
        givenFacts(q, List.of(), Set.of(2028), false);
        when(research.research(eq(q), any())).thenReturn(ResearchPack.builder().question(q)
                .confirmedHits(List.of()).plannedHits(List.of(k))
                .officialDocHits(List.of()).youtubeHits(List.of()).conflictHits(List.of()).guideHits(List.of()).gapNotes(List.of()).build());
        when(llm.complete(anyString(), anyString(), anyInt()))
                .thenReturn(NewStartLlmCompletion.success("2028학년도는 계획 단계로 변경될 수 있습니다.", "t", "m"));

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).contains("계획 단계");
        assertThat(answer.sources()).extracting(AdmissionsChatService.AnswerSource::id).containsExactly("knowledge:42");
    }

    @Test
    @DisplayName("given 전국 수시 비중 질문(S13) when 대학 없이 묻으면 then 미발표가 아니라 미수집 문구로 답하고 LLM을 호출하지 않는다")
    void aggregate_stats_question_gets_not_collected_message() {
        // given
        String q = "2028이면 수시 비중이 80% 좀 넘죠? 몇 퍼센트예요?";
        givenFacts(q, List.of(), Set.of(2028), true);

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).isEqualTo(AdmissionsChatService.MSG_AGGREGATE_NOT_COLLECTED);
        assertThat(answer.answer()).doesNotContain("아직 발표 전").contains("수집");
        assertThat(answer.answer()).doesNotContainPattern("\\d+\\s*%"); // 수치를 만들어내지 않는다
        verify(llm, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("given 대학별 2028 일정 질문 when 근거가 없으면 then 미수집이 아니라 미발표 문구를 쓴다")
    void university_2028_schedule_keeps_unpublished_message() {
        // given
        String q = "서울대 2028 수시 원서접수는 몇 월인가요?";
        givenFacts(q, List.of(), Set.of(2028), true);
        when(research.research(eq(q), any())).thenReturn(pack(List.of()));

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).isEqualTo(AdmissionsChatService.MSG_UNPUBLISHED_2028);
        assertThat(answer.answer()).contains("아직 발표 전").doesNotContain("수집된 공식 자료가 없어");
    }

    @Test
    @DisplayName("given 대학을 말한 수시 비중 질문 when 묻으면 then 총량 통계 차단에 걸리지 않고 정상 흐름으로 진행한다")
    void aggregate_filter_does_not_block_university_specific_questions() {
        // given
        String q = "서울대 수시 비중이 어떻게 되나요";
        Evidence e = fact("type:1", "서울대학교 2027학년도 지역균형", "모집인원: 414", 2027, null);
        givenFacts(q, List.of(e), Set.of(2027), false);
        when(research.research(eq(q), any())).thenReturn(pack(List.of()));
        when(llm.complete(anyString(), anyString(), anyInt()))
                .thenReturn(NewStartLlmCompletion.success("2027학년도 서울대 지역균형 모집인원은 414명입니다.", "t", "m"));

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).contains("414명");
        verify(llm).complete(anyString(), anyString(), anyInt());
    }

    private void givenScheduleFacts(String q, List<Evidence> evidence, List<String> covered, List<String> missing) {
        var parsed = AdmissionsQueryParser.parse(q, UNIVS);
        when(facts.search(eq(q), any())).thenReturn(new AdmissionsFactSearchService.FactResult(
                evidence, parsed, Set.of(2027), false, new AdmissionsFactSearchService.ScheduleCoverage(covered, missing)));
        when(research.research(eq(q), any())).thenReturn(pack(List.of()));
    }

    @Test
    @DisplayName("given 일부 대학 일정만 있는 전체 일정 질문 when 묻으면 then 답변을 포기하지 않고 LLM에 대학별 범위·미확인 대학을 전달하며 출처를 붙인다")
    void all_university_schedule_answers_with_partial_coverage() {
        // given
        String q = "2027학년도 수시 원서접수 일정 알려줘";
        givenScheduleFacts(q, List.of(
                fact("sched:1", "서울대학교 2027학년도 원서접수 (수시)", "일정 원문: 2026.9.8.~9.11.", 2027, "https://snu.test/a"),
                fact("sched:2", "연세대학교 2027학년도 원서접수 (수시)", "일정 원문: 2026.9.9.~9.12.", 2027, "https://yonsei.test/b")),
                List.of("서울대학교", "연세대학교"), List.of("인하대학교"));
        when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(NewStartLlmCompletion.success(
                "2027학년도 기준입니다. 서울대학교 2026.9.8.~9.11., 연세대학교 2026.9.9.~9.12. 인하대학교는 확인된 자료가 없습니다.", "t", "m"));

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.ok()).isTrue();
        assertThat(answer.answer()).contains("2027학년도").contains("서울대학교").contains("인하대학교");
        assertThat(answer.sources()).extracting(AdmissionsChatService.AnswerSource::url)
                .containsExactly("https://snu.test/a", "https://yonsei.test/b");
        var user = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(anyString(), user.capture(), anyInt());
        assertThat(user.getValue()).contains("서울대학교, 연세대학교").contains("일정이 확인되지 않은 대학: 인하대학교");
    }

    @Test
    @DisplayName("given 승인된 일정이 하나도 없는 전체 일정 질문 when 묻으면 then 자료 부족 문구로 답하고 LLM을 호출하지 않는다")
    void all_university_schedule_without_data_is_data_shortage_not_ai_failure() {
        // given
        String q = "2027학년도 수시 원서접수 일정 알려줘";
        givenScheduleFacts(q, List.of(), List.of(), UNIVS);

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.ok()).isTrue();
        assertThat(answer.answer()).isEqualTo(AdmissionsChatService.MSG_NO_EVIDENCE);
        assertThat(answer.answer()).doesNotContain("7개 대학");
        verify(llm, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("given 근거는 있으나 LLM 호출이 실패 when 묻으면 then 자료 부족과 다른 AI 연결 실패(ok=false)로 구분한다")
    void llm_failure_is_distinguished_from_data_shortage() {
        // given
        String q = "2027학년도 수시 원서접수 일정 알려줘";
        givenScheduleFacts(q, List.of(fact("sched:1", "서울대학교 2027학년도 원서접수", "일정 원문: 2026.9.8.", 2027, null)),
                List.of("서울대학교"), List.of("연세대학교", "인하대학교"));
        when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(NewStartLlmCompletion.failed("timeout", true));

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.ok()).isFalse();
        assertThat(answer.errorMessage()).contains("AI 서비스 연결에 실패");
        assertThat(answer.errorMessage()).isNotEqualTo(AdmissionsChatService.MSG_NO_EVIDENCE);
    }

    @Test
    @DisplayName("given 검색 단계 예외 when 묻으면 then 검색 오류(ok=false)로 구분하고 LLM을 호출하지 않는다")
    void search_failure_is_distinguished() {
        // given
        String q = "2027학년도 수시 원서접수 일정 알려줘";
        when(facts.search(eq(q), any())).thenThrow(new IllegalStateException("db down"));

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.ok()).isFalse();
        assertThat(answer.errorMessage()).isEqualTo(AdmissionsChatService.MSG_SEARCH_FAILED);
        verify(llm, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("given 캠퍼스가 표기된 근거 when 묻으면 then 시스템 프롬프트가 다른 캠퍼스 질문에 그 자료를 쓰지 말라고 지시한다")
    void campus_rule_is_in_system_prompt() {
        // given
        String q = "고려대 서울캠퍼스 수시 원서접수 일정";
        givenFacts(q, List.of(fact("sched:1", "고려대학교 2027학년도 원서접수 (수시(세종캠퍼스))", "일정 원문: 9.7.", 2027, "https://x")),
                Set.of(2027), false);
        when(research.research(eq(q), any())).thenReturn(pack(List.of()));
        when(llm.complete(anyString(), anyString(), anyInt()))
                .thenReturn(NewStartLlmCompletion.success("서울캠퍼스 자료는 없습니다.", "t", "m"));

        // when
        service.ask(q, null, null);

        // then
        var system = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(system.capture(), anyString(), anyInt());
        assertThat(system.getValue()).contains("[세종캠퍼스]").contains("다른 캠퍼스");
    }

    @Test
    @DisplayName("given 사실 근거와 사설 미러본이 함께 있는 질문 when 묻으면 then 미러본은 공식 자료로 표시되지 않고 프롬프트가 확정 답변을 금지한다")
    void mirror_material_is_not_presented_as_official() {
        // given
        String q = "중앙대 수시 정시 모집 알려줘";
        Evidence mirror = Evidence.builder().stepNo(3).sourceClass(SourceClass.UNVERIFIED_MIRROR)
                .refId("material:13").refUrl("https://mirror.test/13")
                .title("중앙대학교 2027학년도 수시모집요강 (사설 미러본 · 공식 원본 미확보)")
                .excerpt("공식 원본이 아닌 사설 미러본이며 내용이 검증되지 않았다.").note("사설 미러본 (공식 원본 아님)")
                .academicYear(2027).yearConfirmed(true).build();
        givenFacts(q, List.of(fact("sched:9", "중앙대학교 2027학년도 정시 원서접수", "일정 원문: 1.4.", 2027, "https://cau.test/jungsi"), mirror),
                Set.of(2027), false);
        when(research.research(eq(q), any())).thenReturn(pack(List.of()));
        when(llm.complete(anyString(), anyString(), anyInt()))
                .thenReturn(NewStartLlmCompletion.success("2027학년도 정시 원서접수는 1.4.입니다. 수시는 공식 원본이 아니라 확인이 필요합니다.", "t", "m"));

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.ok()).isTrue();
        assertThat(answer.sources()).filteredOn(s -> "material:13".equals(s.id()))
                .extracting(AdmissionsChatService.AnswerSource::kind)
                .containsExactly("사설 미러본 (공식 원본 아님)");
        var system = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(system.capture(), anyString(), anyInt());
        assertThat(system.getValue()).contains("사설 미러본").contains("확정적으로 말하지 않는다");
    }

    @Test
    @DisplayName("given 사설 미러본만 근거인 질문 when 묻으면 then 제목만 있는 자료로 보고 LLM을 호출하지 않는다")
    void mirror_only_question_is_title_only_refusal() {
        // given
        String q = "중앙대 수시 전형 알려줘";
        Evidence mirror = Evidence.builder().stepNo(3).sourceClass(SourceClass.UNVERIFIED_MIRROR)
                .refId("material:13").title("중앙대학교 2027학년도 수시모집요강 (사설 미러본 · 공식 원본 미확보)")
                .excerpt("사설 미러본").note("사설 미러본 (공식 원본 아님)").academicYear(2027).yearConfirmed(true).build();
        givenFacts(q, List.of(), Set.of(2027), false);
        when(research.research(eq(q), any())).thenReturn(ResearchPack.builder().question(q)
                .confirmedHits(List.of()).plannedHits(List.of()).officialDocHits(List.of(mirror))
                .youtubeHits(List.of()).conflictHits(List.of()).guideHits(List.of()).gapNotes(List.of()).build());

        // when
        var answer = service.ask(q, null, null);

        // then
        assertThat(answer.answer()).isEqualTo(AdmissionsChatService.MSG_TITLE_ONLY);
        verify(llm, never()).complete(anyString(), anyString(), anyInt());
    }
}
