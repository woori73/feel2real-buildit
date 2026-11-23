package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("입시상담 검색 — 물은 행이 상한에 잘리지 않게 구체적으로 맞는 행 먼저, 대학을 말한 제도 질문")
class AdmissionsRetrievalRankingTest {

    private static final Function<String, String> SELF = s -> s;

    @Test
    @DisplayName("given 국립한밭대 충원 일정 10행(1~5차 발표·등록) when '정시 전 전형 5차 충원합격자 발표'를 물으면 then 5차 발표 행이 맨 앞이고 상한 8건 안에 든다")
    void fifth_round_announcement_comes_first() {
        // given: DB 순서 — 5차 발표는 9번째라 앞에서 8건 자르면 빠졌다
        List<String> rows = new ArrayList<>();
        for (int n = 1; n <= 5; n++) {
            String round = n == 5 ? "5차(최종)" : n + "차";
            rows.add(round + " 충원합격자 발표 (정시 전 전형)");
            rows.add(round + " 충원합격자 등록 (정시 전 전형)");
        }
        String q = "2027학년도 국립한밭대 정시 정시 전 전형 5차 충원합격자 발표 일정 알려 주세요.";

        // when
        List<String> ranked = AdmissionsFactSearchService.mostSpecificFirst(q, rows, SELF, SELF);

        // then
        assertThat(ranked.get(0)).isEqualTo("5차(최종) 충원합격자 발표 (정시 전 전형)");
        assertThat(ranked.subList(0, AdmissionsFactSearchService.MAX_SCHEDULES)).contains("5차(최종) 충원합격자 발표 (정시 전 전형)");
    }

    @Test
    @DisplayName("given 극동대 실기 일정 여러 행 when 만화·애니메이션학과 실기고사를 물으면 then 그 학과 실기고사 행이 맨 앞에 온다")
    void department_exam_row_comes_first() {
        // given
        List<String> rows = List.of(
                "면접 예비 문제 발표 (면접/실기 전형)",
                "실기 예비주제 공지 (실기 위주-일반학생 전형(정원 내) — 만화·애니메이션학과)",
                "면접/실기 상세 안내 공지 (면접/실기 전형)",
                "실기고사 (실기 위주-일반학생 전형(정원 내) — 스포츠재활학과)",
                "실기고사 (실기 위주-일반학생 전형(정원 내) — 연극연기학과)",
                "실기고사 (실기 위주-일반학생 전형(정원 내) — 만화·애니메이션학과)");
        String q = "극동대학교 수시 실기 위주-일반학생 전형(정원 내) — 만화·애니메이션학과 실기고사 날짜가 궁금해요.";

        // when
        List<String> ranked = AdmissionsFactSearchService.mostSpecificFirst(q, rows, SELF, SELF);

        // then
        assertThat(ranked.get(0)).isEqualTo("실기고사 (실기 위주-일반학생 전형(정원 내) — 만화·애니메이션학과)");
    }

    @Test
    @DisplayName("given 다른 캠퍼스 행이 더 구체적으로 맞을 때 when 캠퍼스를 말하지 않고 물으면 then 본교 우선보다 구체성이 앞선다")
    void specificity_beats_main_campus_order() {
        // given: 다빈치캠퍼스 한국화 실기고사 행이 본교 실기고사 행들 뒤에 있다
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 9; i++) rows.add("실기고사 (정시 실기/실적(실기형)(가군) 공연영상창작학부 " + i + ")");
        rows.add("실기고사 (다빈치캠퍼스 정시 실기/실적(실기형)(가군) 미술학부(한국화))");
        String q = "2027학년도 중앙대 정시 정시 실기/실적(실기형)(가군) 미술학부(한국화) 실기고사 일정 알려 주세요.";

        // when
        List<String> ranked = AdmissionsFactSearchService.mostSpecificFirst(q, rows, SELF, SELF);

        // then
        assertThat(ranked.get(0)).contains("미술학부(한국화)");
    }

    @Test
    @DisplayName("given 점수가 같은 행들 when 정렬하면 then 원래 순서를 지킨다")
    void ties_keep_original_order() {
        // given
        List<String> rows = List.of("원서접수 (수시 A)", "원서접수 (수시 B)", "원서접수 (수시 C)");

        // when
        List<String> ranked = AdmissionsFactSearchService.mostSpecificFirst("가대 수시 원서접수 언제예요?", rows, SELF, SELF);

        // then
        assertThat(ranked).containsExactlyElementsOf(rows);
    }

    @Test
    @DisplayName("given 질문의 차수와 다른 차수 행 when 점수를 매기면 then 같은 차수보다 낮다")
    void different_round_scores_lower() {
        // given
        String q = "3차 충원합격자 발표 언제예요?";

        // when
        int same = AdmissionsFactSearchService.specificity(q, "3차 충원합격자 발표 (정시 전 전형)");
        int other = AdmissionsFactSearchService.specificity(q, "1차 충원합격자 발표 (정시 전 전형)");

        // then
        assertThat(same).isGreaterThan(other);
    }

    private static AdmissionsKnowledgeItem policy(String dedupKey, String title, String tags, String content) {
        return AdmissionsKnowledgeItem.builder().dedupKey(dedupKey).title(title).tags(tags).content(content).academicYear(2028).build();
    }

    @Test
    @DisplayName("given 서강대가 본문에 나오는 2028 권장과목 제도 항목 when 서강대를 말한 질문이면 then 근거 후보로 남긴다")
    void policy_item_naming_university_is_kept() {
        // given
        AdmissionsKnowledgeItem stats = policy("official:R2028-RECOMMENDED-STATS", "2028학년도 대학 권장과목 현황",
                "권장과목, 서강대, 성균관대", "상위 20개 대학 가운데 15곳이 권장과목을 발표했다. 서강대와 성균관대는 권장과목이 없고");

        // when / then
        assertThat(AdmissionsResearchService.policyNamesUniversity(stats, List.of("서강대학교"))).isTrue();
    }

    @Test
    @DisplayName("given 다른 대학만 나오는 제도 항목·대학 원문 항목 when 서강대를 말한 질문이면 then 근거 후보로 남기지 않는다")
    void other_university_or_sourced_items_are_not_kept() {
        // given
        AdmissionsKnowledgeItem snu = policy("official:R2028-SNU-COURSES", "서울대 2028학년도 전공 연계 과목 선택 안내",
                "서울대, 권장과목", "서울대는 2025-06-30 안내를 냈다.");
        AdmissionsKnowledgeItem notOfficial = policy("type:123", "서강대 학생부종합", "서강대", "서강대 전형");

        // when / then
        assertThat(AdmissionsResearchService.policyNamesUniversity(snu, List.of("서강대학교"))).isFalse();
        assertThat(AdmissionsResearchService.policyNamesUniversity(notOfficial, List.of("서강대학교"))).isFalse();
        assertThat(AdmissionsResearchService.policyNamesUniversity(snu, List.of())).isFalse();
    }

    @Test
    @DisplayName("given 기회균형 전형(정원 내) 질문 when 지난해 결과 전형명과 맞추면 then '정원내'만 같은 교과우수자전형은 맞지 않고 기회균형전형만 맞는다")
    void quota_marker_alone_does_not_match_type() {
        // given
        String q = "내신 2.8등급인데 극동대 학생부교과 위주-기회균형 전형(정원 내) 합격할 수 있을까요?";

        // when / then
        assertThat(AdmissionsFactSearchService.priorTypeMentioned(q, "교과우수자전형(정원내)")).isFalse();
        assertThat(AdmissionsFactSearchService.priorTypeMentioned(q, "일반학생전형(정원내)")).isFalse();
        assertThat(AdmissionsFactSearchService.priorTypeMentioned(q, "기회균형전형(정원내)")).isTrue();
    }
}
