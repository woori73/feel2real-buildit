package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsPriorResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("입시결과 근거 — 질문이 가리킨 학년도(작년/올해)만 남기기")
class AdmissionsPriorYearIntentTest {

    private static List<AdmissionsPriorResult> unit(int resultYear) {
        return List.of(AdmissionsPriorResult.builder().academicYear(2027).resultYear(resultYear)
                .roundType("SUSI").typeName("일반전형").departmentName("신학부").metricLabel("").build());
    }

    private static final List<List<AdmissionsPriorResult>> BOTH = List.of(unit(2026), unit(2027));

    @Test
    @DisplayName("given 2026·2027 단위 when '작년 경쟁률'을 물으면 then 2026 단위만 남는다")
    void lastYearQuestion_keepsLastYearOnly() {
        // given / when
        var kept = AdmissionsFactSearchService.byYearIntent("감신대 작년 일반전형 경쟁률은?", BOTH);

        // then
        assertThat(kept).extracting(rows -> rows.get(0).getResultYear()).containsExactly(2026);
    }

    @Test
    @DisplayName("given 2026·2027 단위 when '올해 경쟁률'을 물으면 then 2027 단위만 남는다")
    void thisYearQuestion_keepsCurrentOnly() {
        // given / when
        var kept = AdmissionsFactSearchService.byYearIntent("감신대 올해 수시 경쟁률 알려줘", BOTH);

        // then
        assertThat(kept).extracting(rows -> rows.get(0).getResultYear()).containsExactly(2027);
    }

    @Test
    @DisplayName("given 2026·2027 단위 when 학년도를 말하지 않으면 then 둘 다 남는다")
    void noYearQuestion_keepsBoth() {
        // given / when
        var kept = AdmissionsFactSearchService.byYearIntent("감신대 신학부 수시 경쟁률 알려주세요", BOTH);

        // then
        assertThat(kept).hasSize(2);
    }

    @Test
    @DisplayName("given 2026 단위만 있음 when '올해 경쟁률'을 물으면 then 빈 근거 대신 2026 단위를 그대로 둔다")
    void thisYearQuestion_withoutCurrentRows_keepsLastYear() {
        // given / when
        var kept = AdmissionsFactSearchService.byYearIntent("서울기독대 올해 경쟁률", List.of(unit(2026)));

        // then
        assertThat(kept).hasSize(1);
    }

    private static List<List<AdmissionsPriorResult>> units(int resultYear, int n) {
        List<List<AdmissionsPriorResult>> out = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) out.add(List.of(AdmissionsPriorResult.builder().academicYear(2027).resultYear(resultYear)
                .roundType("SUSI").typeName("전형" + i).departmentName("신학부").metricLabel("").build()));
        return out;
    }

    @Test
    @DisplayName("given 2027 7개·2026 7개 단위 when 상한 12로 자르면 then 학년도마다 8개까지라 14개가 모두 남는다")
    void twoYears_capPerYear_keepsAll() {
        // given
        List<List<AdmissionsPriorResult>> chosen = new java.util.ArrayList<>(units(2027, 7));
        chosen.addAll(units(2026, 7));

        // when
        var kept = AdmissionsFactSearchService.capUnits(chosen, AdmissionsFactSearchService.MAX_PRIOR_DEPARTMENT_UNITS);

        // then
        assertThat(kept).hasSize(14);
    }

    @Test
    @DisplayName("given 2027 10개·2026 10개 단위 when 자르면 then 학년도마다 8개씩 16개")
    void twoYears_capPerYear_limitsEachYear() {
        // given
        List<List<AdmissionsPriorResult>> chosen = new java.util.ArrayList<>(units(2027, 10));
        chosen.addAll(units(2026, 10));

        // when
        var kept = AdmissionsFactSearchService.capUnits(chosen, AdmissionsFactSearchService.MAX_PRIOR_DEPARTMENT_UNITS);

        // then
        assertThat(kept).hasSize(16);
        assertThat(kept).filteredOn(rows -> rows.get(0).getResultYear() == 2027).hasSize(8);
    }

    @Test
    @DisplayName("given 한 학년도 20개 단위 when 자르면 then 기존 상한 그대로")
    void oneYear_keepsOldCap() {
        // given / when
        var kept = AdmissionsFactSearchService.capUnits(units(2026, 20), AdmissionsFactSearchService.MAX_PRIOR_DEPARTMENT_UNITS);

        // then
        assertThat(kept).hasSize(AdmissionsFactSearchService.MAX_PRIOR_DEPARTMENT_UNITS);
    }

    @Test
    @DisplayName("given 원문 주소·기관명 when 출처 종류를 정하면 then 대행사·대학어디가·사설은 이름, 입학처 원문은 null")
    void priorSourceLabel_byUrlAndInstitution() {
        // given
        AdmissionsPriorResult uway = AdmissionsPriorResult.builder().sourceUrl("https://ratio.uwayapply.com/abc").institutionName("장로회신학대학교 입학처").build();
        AdmissionsPriorResult jinhak = AdmissionsPriorResult.builder().institutionName("○○대학교 입학처(진학어플라이 경쟁률 서비스)").build();
        AdmissionsPriorResult adiga = AdmissionsPriorResult.builder().sourceUrl("https://www.adiga.kr/x").build();
        AdmissionsPriorResult academy = AdmissionsPriorResult.builder().institutionName("○○학원 배치표").build();
        AdmissionsPriorResult official = AdmissionsPriorResult.builder().sourceUrl("https://ipsi.example.ac.kr/a.pdf").institutionName("예시대학교 입학처").build();

        // when / then
        assertThat(AdmissionsFactSearchService.priorSourceLabel(uway)).isEqualTo(AdmissionsFactSearchService.SOURCE_UWAY);
        assertThat(AdmissionsFactSearchService.priorSourceLabel(jinhak)).isEqualTo(AdmissionsFactSearchService.SOURCE_JINHAK);
        assertThat(AdmissionsFactSearchService.priorSourceLabel(adiga)).isEqualTo(AdmissionsFactSearchService.SOURCE_ADIGA);
        assertThat(AdmissionsFactSearchService.priorSourceLabel(academy)).isEqualTo(AdmissionsFactSearchService.SOURCE_PRIVATE);
        assertThat(AdmissionsFactSearchService.priorSourceLabel(official)).isNull();
    }
}
