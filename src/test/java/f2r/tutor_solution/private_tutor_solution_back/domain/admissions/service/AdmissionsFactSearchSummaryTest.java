package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.Evidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("대학을 정하지 않은 전체 일정 질문 — 예로 들 대학 수 줄이기")
class AdmissionsFactSearchSummaryTest {

    private static Evidence sched(String univ, int n) {
        return Evidence.builder().stepNo(0).refId("sched:" + univ + n).title(univ + " 2027학년도 원서접수 " + n)
                .excerpt("일정 원문: 2026.9.8.~9.11.").academicYear(2027).yearConfirmed(true).build();
    }

    @Test
    @DisplayName("given 일정이 있는 대학 11곳(대학마다 2행) when 요약용으로 고르면 then 앞의 4곳 8행만 남고 일정이 있는 대학 목록은 11곳 그대로다")
    void summary_keeps_first_four_universities() {
        // given
        Map<String, List<Evidence>> byUniversity = new LinkedHashMap<>();
        for (int i = 1; i <= 11; i++) {
            String u = "가" + i + "대학교";
            byUniversity.put(u, new ArrayList<>(List.of(sched(u, 1), sched(u, 2))));
        }

        // when
        List<Evidence> shown = AdmissionsFactSearchService.firstUniversities(byUniversity, AdmissionsFactSearchService.MAX_SUMMARY_UNIVERSITIES);

        // then
        assertThat(AdmissionsFactSearchService.MAX_SUMMARY_UNIVERSITIES).isEqualTo(4);
        assertThat(shown).hasSize(8);
        assertThat(shown).extracting(Evidence::getTitle).allMatch(t -> t.startsWith("가1대학교") || t.startsWith("가2대학교")
                || t.startsWith("가3대학교") || t.startsWith("가4대학교"));
    }

    @Test
    @DisplayName("given 일정이 있는 대학이 2곳뿐 when 요약용으로 고르면 then 모두 남는다")
    void summary_keeps_all_when_few() {
        // given
        Map<String, List<Evidence>> byUniversity = new LinkedHashMap<>();
        byUniversity.put("가대학교", List.of(sched("가대학교", 1)));
        byUniversity.put("나대학교", List.of(sched("나대학교", 1)));

        // when
        List<Evidence> shown = AdmissionsFactSearchService.firstUniversities(byUniversity, 4);

        // then
        assertThat(shown).hasSize(2);
    }
}
