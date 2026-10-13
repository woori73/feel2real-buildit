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
}
