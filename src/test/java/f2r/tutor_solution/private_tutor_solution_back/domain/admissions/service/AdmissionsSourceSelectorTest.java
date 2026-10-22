package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.SourceClass;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdmissionsSourceSelectorTest {

    private static final String GUIDE = "https://admission.yonsei.ac.kr/guide.asp";

    private static Evidence ev(String refId, String title, String excerpt, SourceClass c) {
        return Evidence.builder().stepNo(1).sourceClass(c).refId(refId).refUrl(GUIDE)
                .title(title).excerpt(excerpt).academicYear(2027).yearConfirmed(true).build();
    }

    private static Evidence ev(String refId, String title, String excerpt) {
        return ev(refId, title, excerpt, SourceClass.OFFICIAL_CONFIRMED);
    }

    private static List<String> ids(List<Evidence> list) {
        return list.stream().map(Evidence::getRefId).toList();
    }

    @Test
    @DisplayName("질문과 무관한 논술·실기·정시 일정은 출처에서 빠지고 추천형만 남는다")
    void dropsUnrelatedSources() {
        // given
        List<Evidence> evidence = List.of(
                ev("type:1", "연세대학교 2027학년도 학생부교과전형[추천형]", "지원자격: 확인되지 않음"),
                ev("type:2", "연세대학교 2027학년도 논술전형", "논술 100%"),
                ev("type:3", "연세대학교 2027학년도 실기/실적위주 일반전형 (예능계열, 나군)", "실기 반영"),
                ev("sched:9", "연세대학교 2027학년도 일정: 원서접수 (정시)", "2027. 1. 4."));

        // when
        List<Evidence> picked = AdmissionsSourceSelector.select(evidence,
                "연세대학교 2027학년도 학생부교과 추천형 지원자격 알려주세요", 6);

        // then
        assertThat(ids(picked)).containsExactly("type:1");
    }

    @Test
    @DisplayName("제목·URL이 같은 전형 행과 지식 행은 하나로 합친다")
    void mergesSameTitleSameUrl() {
        // given
        List<Evidence> evidence = List.of(
                ev("type:1", "연세대학교 2027학년도 학생부교과전형[추천형]", "수능최저 적용"),
                ev("knowledge:7", "연세대학교 2027학년도 학생부교과전형[추천형]", "수능최저 적용"),
                ev("type:1", "연세대학교 2027학년도 학생부교과전형[추천형]", "수능최저 적용"));

        // when
        List<Evidence> picked = AdmissionsSourceSelector.select(evidence, "연대 교과전형 수능최저 있어?", 6);

        // then
        assertThat(ids(picked)).containsExactly("type:1");
    }

    @Test
    @DisplayName("수시만 물으면 정시 제목은 빠지고, 정시만 물으면 수시 제목이 빠진다")
    void dropsOppositeTrack() {
        // given
        List<Evidence> evidence = List.of(
                ev("sched:1", "성균관대학교 2027학년도 일정: 원서접수 (수시)", "2026. 9. 8."),
                ev("sched:2", "성균관대학교 2027학년도 일정: 원서접수 (정시)", "2027. 1. 4."));

        // when / then
        assertThat(ids(AdmissionsSourceSelector.select(evidence, "성균관대 수시 원서접수 일정", 6)))
                .containsExactly("sched:1");
        assertThat(ids(AdmissionsSourceSelector.select(evidence, "성균관대 정시 원서접수 일정", 6)))
                .containsExactly("sched:2");
    }

    @Test
    @DisplayName("관련도가 높은 순으로 상위 N개만 남기고, 같은 관련도면 공식 자료가 사설 미러본보다 앞선다")
    void ranksByRelevanceThenOfficialClassAndLimits() {
        // given
        List<Evidence> evidence = List.of(
                ev("material:5", "중앙대학교 2027학년도 수시모집요강 사본", "수능최저 모집요강", SourceClass.UNVERIFIED_MIRROR),
                ev("type:2", "중앙대학교 2027학년도 학생부교과전형", "수능최저 적용"),
                ev("type:3", "중앙대학교 2027학년도 학생부교과전형 지역균형", "수능최저 적용"),
                ev("type:4", "중앙대학교 2027학년도 학생부교과전형 농어촌", "수능최저 적용"));

        // when
        List<Evidence> picked = AdmissionsSourceSelector.select(evidence, "중앙대 교과전형 수능최저", 2);

        // then
        assertThat(ids(picked)).containsExactly("type:2", "type:3");
    }

    @Test
    @DisplayName("공식 자료 확정 근거가 있으면 그 밖의 약한 관련 근거보다 먼저 나온다")
    void confirmedOfficialComesBeforeMirrorOnTie() {
        // given
        List<Evidence> evidence = List.of(
                ev("mirror:1", "서울대학교 2027학년도 지역균형전형 미러", "수능최저 3개 영역", SourceClass.UNVERIFIED_MIRROR),
                ev("type:1", "서울대학교 2027학년도 지역균형전형", "수능최저 3개 영역 7등급"));

        // when
        List<Evidence> picked = AdmissionsSourceSelector.select(evidence, "서울대 지역균형 수능최저", 6);

        // then
        assertThat(ids(picked)).containsExactly("type:1", "mirror:1");
    }

    @Test
    @DisplayName("주제어가 어떤 근거와도 맞지 않으면 원래 순서의 앞 3건을 그대로 보여준다")
    void fallsBackToFirstFewWhenNothingMatches() {
        // given
        List<Evidence> evidence = List.of(
                ev("type:1", "한양대학교 2027학년도 A전형", "가"),
                ev("type:2", "한양대학교 2027학년도 B전형", "나"),
                ev("type:3", "한양대학교 2027학년도 C전형", "다"),
                ev("type:4", "한양대학교 2027학년도 D전형", "라"));

        // when
        List<Evidence> picked = AdmissionsSourceSelector.select(evidence, "한양대 기숙사 식단표", 6);

        // then
        assertThat(ids(picked)).containsExactly("type:1", "type:2", "type:3");
    }

    @Test
    @DisplayName("GUIDE 안내문은 출처에 나오지 않는다")
    void excludesGuideNotes() {
        // given
        List<Evidence> evidence = List.of(
                ev("guide:1", "답변 형식 안내", "수능최저 설명", SourceClass.GUIDE_NOTE),
                ev("type:1", "연세대학교 2027학년도 학생부교과전형[추천형]", "수능최저 적용"));

        // when
        List<Evidence> picked = AdmissionsSourceSelector.select(evidence, "연세대 교과 수능최저", 6);

        // then
        assertThat(ids(picked)).containsExactly("type:1");
    }

    @Test
    @DisplayName("주제어는 대학명·학년도·질문 잡음을 빼고 전형 접미사를 줄여 만든다")
    void topicTermsStripNoise() {
        // given
        String question = "연세대학교 2027학년도 학생부교과전형 수능최저 알려주세요";

        // when
        var terms = AdmissionsSourceSelector.topicTerms(question);

        // then
        assertThat(terms).containsExactly("학생부교과", "수능최저");
    }

    @Test
    @DisplayName("공식 문서 링크는 주제어가 없어도 최대 2건 남고, 사설 미러본은 예외가 아니다")
    void keepsBareOfficialMaterialsUpToTwo() {
        // given
        List<Evidence> evidence = List.of(
                ev("dept:7", "인하대학교 2027학년도 컴퓨터공학과 모집인원", "전형별 모집인원: 합계 101명"),
                ev("material:1", "인하대 2027 수시 모집요강", "인하대학교", SourceClass.OFFICIAL_DOCUMENT),
                ev("material:2", "인하대 2027 정시 모집요강", "인하대학교", SourceClass.OFFICIAL_DOCUMENT),
                ev("material:3", "인하대 2027 편입 모집요강", "인하대학교", SourceClass.OFFICIAL_DOCUMENT),
                ev("material:9", "인하대 2027 모집요강 사본", "인하대학교", SourceClass.UNVERIFIED_MIRROR));

        // when
        List<Evidence> picked = AdmissionsSourceSelector.select(evidence, "인하대 컴퓨터공학과 몇 명 뽑나요", 6);

        // then
        assertThat(ids(picked)).containsExactly("dept:7", "material:1", "material:2");
    }
}
