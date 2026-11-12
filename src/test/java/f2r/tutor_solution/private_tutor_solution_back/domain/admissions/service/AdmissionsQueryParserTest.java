package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("입시 질문 분석기 — 대학·학년도·의도·답변 금지 주제")
class AdmissionsQueryParserTest {

    private static final List<String> UNIVS = List.of(
            "서울대학교", "연세대학교", "성균관대학교", "고려대학교(서울)", "한양대학교(서울)", "경희대학교(서울)", "인하대학교");

    @Test
    @DisplayName("given 대학 약칭 when 분석하면 then 괄호 부기가 있는 정식 이름과 매칭된다")
    void matches_university_aliases() {
        // given
        String q = "컴퓨터공학 하려고 하는데 인하·고려·연세대 중 어디가 많이 뽑아요? 고려대 기준으로요";

        // when
        var parsed = AdmissionsQueryParser.parse(q, UNIVS);

        // then
        assertThat(parsed.universities()).containsExactlyInAnyOrder("연세대학교", "고려대학교(서울)");
    }

    @Test
    @DisplayName("given 학년도 표기 when 분석하면 then 2027·2028만 인정하고 2029는 미지원으로 표시한다")
    void parses_supported_and_unsupported_years() {
        // given / when
        var both = AdmissionsQueryParser.parse("2028 수능은 통합과학? 저는 2027 수능인데", UNIVS);
        var y2029 = AdmissionsQueryParser.parse("2029학년도 기본사항 알려줘", UNIVS);
        var none = AdmissionsQueryParser.parse("서울대 지역균형", UNIVS);

        // then
        assertThat(both.years()).containsExactly(2028, 2027);
        assertThat(y2029.years()).isEmpty();
        assertThat(y2029.mentionsUnsupportedYear()).isTrue();
        assertThat(none.yearUnspecified()).isTrue();
    }

    @Test
    @DisplayName("given 합격 가능성·추천 질문 when 분석하면 then 판단성 질문으로 표시한다")
    void flags_admission_chance_questions() {
        // given / when / then
        assertThat(AdmissionsQueryParser.parse("합격 가능성 높은 조합으로 6장 추천해 주세요.", UNIVS).asksAdmissionChance()).isTrue();
        assertThat(AdmissionsQueryParser.parse("내신이 2.5등급이면 성균관대 어디쯤 지원 가능할까요?", UNIVS).asksAdmissionChance()).isTrue();
        assertThat(AdmissionsQueryParser.parse("서울대 지역균형은 수능최저가 얼마인가요?", UNIVS).asksAdmissionChance()).isFalse();
        assertThat(AdmissionsQueryParser.parse("가능성", UNIVS).asksAdmissionChance()).as("가능성만 물어도 합격 가능성 질문").isTrue();
        assertThat(AdmissionsQueryParser.parse("연세대 가능성 높을까요?", UNIVS).asksAdmissionChance()).isTrue();
        assertThat(AdmissionsQueryParser.parse("수시 일정 변경 가능성 있나요?", UNIVS).asksAdmissionChance()).as("합격 판단이 아닌 가능성").isFalse();
    }

    @Test
    @DisplayName("given 경쟁률·입결·권장이수 질문 when 분석하면 then 미수집 주제로 표시한다")
    void flags_not_collected_topics() {
        // given / when / then
        assertThat(AdmissionsQueryParser.parse("작년 경쟁률 보면 감이 오나요?", UNIVS).asksNotCollected()).isTrue();
        assertThat(AdmissionsQueryParser.parse("인문계열인데 미적분 권장이수 꼭 들어야 해요?", UNIVS).asksNotCollected()).isTrue();
        assertThat(AdmissionsQueryParser.parse("인하대 컴퓨터공학과 몇 명 뽑나요", UNIVS).asksNotCollected()).isFalse();
    }

    @Test
    @DisplayName("given 경쟁률·입결·충원·컷 질문 when 분석하면 then 지난해 입시결과 질문으로 표시하고 권장이수는 표시하지 않는다")
    void flags_prior_result_questions() {
        // given / when / then
        assertThat(AdmissionsQueryParser.parse("연세대 경영학과 경쟁률 알려줘", UNIVS).asksPriorResult()).isTrue();
        assertThat(AdmissionsQueryParser.parse("작년 입결 어땠어요?", UNIVS).asksPriorResult()).isTrue();
        assertThat(AdmissionsQueryParser.parse("교과 70% 컷이 몇이에요?", UNIVS).asksPriorResult()).isTrue();
        assertThat(AdmissionsQueryParser.parse("충원 몇 명 돌았어요?", UNIVS).asksPriorResult()).isTrue();
        assertThat(AdmissionsQueryParser.parse("미적분 권장이수 꼭 들어야 해요?", UNIVS).asksPriorResult()).isFalse();
        assertThat(AdmissionsQueryParser.parse("인하대 컴퓨터공학과 몇 명 뽑나요", UNIVS).asksPriorResult()).isFalse();
    }

    @Test
    @DisplayName("given 영문 cut·'지난해 ○○ 결과' 질문 when 분석하면 then 지난해 입시결과 질문으로 표시한다")
    void flags_prior_result_english_cut_and_spaced_result() {
        // given
        String englishCut = "이화여대 경영학부 고교추천전형 지난해 70% cut 등급은?";
        String cutGrade = "서강대 경영학부 등급 cut 알려줘";
        String spacedResult = "성공회대 경영학부 지난해 교과성적 전형 결과";
        String notPrior = "작년에 바뀐 전형이 뭐예요?";
        // when / then
        assertThat(AdmissionsQueryParser.parse(englishCut, UNIVS).asksPriorResult()).isTrue();
        assertThat(AdmissionsQueryParser.parse(cutGrade, UNIVS).asksPriorResult()).isTrue();
        assertThat(AdmissionsQueryParser.parse(spacedResult, UNIVS).asksPriorResult()).isTrue();
        assertThat(AdmissionsQueryParser.parse(notPrior, UNIVS).asksPriorResult()).isFalse();
    }

    @Test
    @DisplayName("given 지원자 수 질문 when 분석하면 then 지난해 입시결과 질문으로 보고, 지원자격 질문은 아니다")
    void flags_applicant_count_but_not_eligibility() {
        // given / when / then
        assertThat(AdmissionsQueryParser.parse("연세대 연기과 지난해 지원자 수 알려줘", UNIVS).asksPriorResult()).isTrue();
        assertThat(AdmissionsQueryParser.parse("작년 지원자 몇 명이었어요?", UNIVS).asksPriorResult()).isTrue();
        assertThat(AdmissionsQueryParser.parse("지원 인원이 얼마나 됐나요", UNIVS).asksPriorResult()).isTrue();
        assertThat(AdmissionsQueryParser.parse("연세대 추천형 지원자격이 뭐예요?", UNIVS).asksPriorResult()).isFalse();
    }

    @Test
    @DisplayName("given 2026학년도 입시결과 질문 when 분석하면 then 2026을 지원하지 않는 학년도로 보지 않는다")
    void prior_question_with_2026_is_not_unsupported_year() {
        // given / when
        var prior = AdmissionsQueryParser.parse("2026학년도 경쟁률 알려줘", UNIVS);
        var plain = AdmissionsQueryParser.parse("2026학년도 모집인원 알려줘", UNIVS);

        // then
        assertThat(prior.mentionsUnsupportedYear()).isFalse();
        assertThat(prior.yearUnspecified()).isTrue();
        assertThat(plain.mentionsUnsupportedYear()).as("입시결과가 아닌 질문의 2026은 여전히 미지원").isTrue();
    }

    @Test
    @DisplayName("given 성적이 적힌 질문 when 읽으면 then 등급(1~9)·백분위(0~100)만 종류와 함께 뽑는다")
    void reads_student_scores() {
        // given / when
        var school = AdmissionsQueryParser.studentScores("내신 2.5등급인데 가능할까요?");
        var csat = AdmissionsQueryParser.studentScores("수능 3등급, 백분위 85예요");
        var unknown = AdmissionsQueryParser.studentScores("2등급이면요?");
        var outOfRange = AdmissionsQueryParser.studentScores("12등급");

        // then
        assertThat(school).singleElement().satisfies(s -> {
            assertThat(s.kind()).isEqualTo("SCHOOL_GRADE");
            assertThat(s.value()).isEqualByComparingTo("2.5");
        });
        assertThat(csat).extracting(AdmissionsQueryParser.StudentScore::kind).containsExactly("CSAT_GRADE", "PERCENTILE");
        assertThat(unknown).singleElement().satisfies(s -> assertThat(s.kind()).isEqualTo("GRADE"));
        assertThat(outOfRange).isEmpty();
    }

    @Test
    @DisplayName("given 성적을 적고 가능성을 묻는 질문 when 분석하면 then 합격 가능성 질문으로 보되 수능최저 기준 질문은 제외한다")
    void flags_score_based_chance_questions() {
        // given / when / then
        assertThat(AdmissionsQueryParser.parse("수능 백분위 85인데 연세대 정시 경영학과 가능성", UNIVS).asksAdmissionChance()).isTrue();
        assertThat(AdmissionsQueryParser.parse("내신 2.5등급이면 연세대 교과 어때요", UNIVS).asksAdmissionChance()).isTrue();
        assertThat(AdmissionsQueryParser.parse("연세대 수능최저 2개 영역 등급 합 4 이내인데 3등급이면 충족인가요", UNIVS).asksAdmissionChance())
                .as("수능최저 기준 질문").isFalse();
        assertThat(AdmissionsQueryParser.parse("연세대 수시 일정 알려줘", UNIVS).asksAdmissionChance()).isFalse();
    }

    @Test
    @DisplayName("given 원문 지표명 when 성적 체계를 판단하면 then 교과·수능·백분위만 구분하고 모르면 null")
    void classifies_metric_kind() {
        // given / when / then
        assertThat(AdmissionsQueryParser.metricKind("최종등록자 교과등급 70%컷", "GRADE")).isEqualTo("SCHOOL_GRADE");
        assertThat(AdmissionsQueryParser.metricKind("수능등급 평균", "GRADE")).isEqualTo("CSAT_GRADE");
        assertThat(AdmissionsQueryParser.metricKind("국수탐 백분위 평균", "PERCENTILE")).isEqualTo("PERCENTILE");
        assertThat(AdmissionsQueryParser.metricKind("최종등록자 환산점수 70%", "SCORE")).isNull();
        assertThat(AdmissionsQueryParser.metricKind("최종등록자 상위 80% 평균 영어 등급", "GRADE")).isNull();
        assertThat(AdmissionsQueryParser.metricKind("교과종합등급 표준편차", "OTHER")).as("분포 지표").isNull();
        assertThat(AdmissionsQueryParser.metricKind("최초합격자 교과 등급 학생부총점(1,000점) 평균", null)).as("점수 지표").isNull();
        assertThat(AdmissionsQueryParser.metricKind("최종등록자 학생부등급 70%CUT", "GRADE")).isEqualTo("SCHOOL_GRADE");
    }

    @Test
    @DisplayName("given 일정·전형 질문 when 분석하면 then 의도 플래그가 켜진다")
    void detects_schedule_and_type_intent() {
        // given / when
        var schedule = AdmissionsQueryParser.parse("인하대 학교장추천 입력이 언제까지였죠?", UNIVS);
        var type = AdmissionsQueryParser.parse("고려대 계열적합전형은 수능 안 봐도 되나요?", UNIVS);

        // then
        assertThat(schedule.wantsSchedule()).isTrue();
        assertThat(type.wantsType()).isTrue();
    }

    @Test
    @DisplayName("given 전형 이름 when 조각을 뽑으면 then 괄호·전형 접미를 정리한다")
    void splits_type_pieces() {
        // given / when / then
        assertThat(AdmissionsQueryParser.typePieces("학생부교과(지역균형)")).containsExactly("학생부교과", "지역균형");
        assertThat(AdmissionsQueryParser.typePieces("논술(논술우수자)")).containsExactly("논술", "논술우수자");
        assertThat(AdmissionsQueryParser.typePieces("학생부종합(계열적합전형)")).containsExactly("학생부종합", "계열적합");
    }

    @Test
    @DisplayName("given 학과 이름 when 질문과 비교하면 then 접미를 뗀 표현도 같은 학과로 본다")
    void matches_department_by_stem() {
        // given / when / then
        assertThat(AdmissionsQueryParser.mentionsDepartment("컴퓨터공학 하려고 하는데", "컴퓨터공학과")).isTrue();
        assertThat(AdmissionsQueryParser.mentionsDepartment("의예과 논술로 지원", "의예과")).isTrue();
        assertThat(AdmissionsQueryParser.mentionsDepartment("경영학과은 고려대가", "경영학과")).isTrue();
        assertThat(AdmissionsQueryParser.mentionsDepartment("수학 공부법", "수학과")).isFalse();
    }

    @Test
    @DisplayName("given '학과 / 세부모집' 이름 when 학과 부분을 꺼내면 then 구분자 앞만, 없으면 그대로")
    void departmentHead_splitsOnlyOnSlashSeparator() {
        // given / when / then
        assertThat(AdmissionsQueryParser.departmentHead("연기과 / 연기(여)")).isEqualTo("연기과");
        assertThat(AdmissionsQueryParser.departmentHead("통합모집(신학과/상담심리학과)")).isEqualTo("통합모집(신학과/상담심리학과)");
        assertThat(AdmissionsQueryParser.departmentHead("컴퓨터공학과")).isEqualTo("컴퓨터공학과");
        assertThat(AdmissionsQueryParser.departmentHead(null)).isNull();
    }

    /** 시드(학과 480행)에서 "반도체"가 들어간 실제 모집단위 8개. */
    private static final List<String> SEMICONDUCTOR_DEPTS = List.of(
            "시스템반도체공학과", "지능형반도체전공", "반도체시스템공학과", "반도체융합공학과",
            "반도체공학과◇", "반도체공학과", "전자공학부 반도체공학과", "반도체시스템공학과");
    private static final List<String> OTHER_DEPTS = List.of(
            "컴퓨터공학과", "경영학과", "의예과", "치의예과", "한의예과(인문)", "신소재공학부", "자유전공학부", "전자공학부", "기계공학과");

    @Test
    @DisplayName("given 반도체만 말한 질문(S21) when 핵심어 매칭하면 then 반도체 관련 8개 모집단위만 걸리고 다른 학과는 걸리지 않는다")
    void keyword_matches_all_semiconductor_departments_only() {
        // given
        String q = "반도체 전공 가고 싶은데 대학별 길이가 어떻게 돼요?";

        // when / then
        assertThat(SEMICONDUCTOR_DEPTS).allSatisfy(n -> assertThat(AdmissionsQueryParser.mentionsDepartmentByKeyword(q, n)).as(n).isTrue());
        assertThat(OTHER_DEPTS).allSatisfy(n -> assertThat(AdmissionsQueryParser.mentionsDepartmentByKeyword(q, n)).as(n).isFalse());
    }

    @Test
    @DisplayName("given 일반 단어·짧은 단어뿐인 질문 when 핵심어를 뽑으면 then 학과 오탐을 일으킬 말은 제외된다")
    void keywords_exclude_stopwords_and_short_words() {
        // given / when
        var generic = AdmissionsQueryParser.departmentKeywords("전공 학과 대학별 모집 전형 공학 전자 수시 정시");
        var particle = AdmissionsQueryParser.departmentKeywords("반도체는 어떻게 되나요");

        // then
        assertThat(generic).isEmpty();                         // 3자 미만("공학","전자")·일반 단어는 쓰지 않는다
        assertThat(particle).contains("반도체").doesNotContain("어떻게");
    }

    @Test
    @DisplayName("given 일반 단어만 있는 질문 when 핵심어 매칭하면 then 자유전공학부 같은 학과에 걸리지 않는다")
    void generic_words_do_not_match_departments() {
        // given
        String q = "전공 학과 상관없이 대학별 전형이 궁금해요";

        // when / then
        assertThat(AdmissionsQueryParser.mentionsDepartmentByKeyword(q, "자유전공학부")).isFalse();
        assertThat(AdmissionsQueryParser.mentionsDepartmentByKeyword(q, "전자공학부")).isFalse();
    }

    @Test
    @DisplayName("given 정확한 학과명 질문 when 이름 매칭하면 then 이름 기준으로만 걸린다(치의예과·한의예과는 걸리지 않는다)")
    void exact_name_match_is_unchanged() {
        // given
        String q = "의예과 논술로 지원할 수 있는 대학이 있나요?";

        // when / then
        assertThat(AdmissionsQueryParser.mentionsDepartment(q, "의예과")).isTrue();
        assertThat(AdmissionsQueryParser.mentionsDepartment(q, "치의예과")).isFalse();
        assertThat(AdmissionsQueryParser.mentionsDepartment(q, "한의예과(인문)")).isFalse();
    }

    @Test
    @DisplayName("given 총량 통계 질문 when 분석하면 then 총량 통계로 표시하고 대학별 질문은 표시하지 않는다")
    void flags_aggregate_stats() {
        // given / when / then
        assertThat(AdmissionsQueryParser.parse("2028이면 수시 비중이 80% 좀 넘죠? 몇 퍼센트예요?", UNIVS).asksAggregateStats()).isTrue();
        assertThat(AdmissionsQueryParser.parse("전국 수시 모집인원이 몇 명이에요", UNIVS).asksAggregateStats()).isTrue();
        assertThat(AdmissionsQueryParser.parse("인하대 컴퓨터공학과 몇 명 뽑나요", UNIVS).asksAggregateStats()).isFalse();
    }

    private static final List<String> TEN = List.of(
            "서울대학교", "연세대학교", "성균관대학교", "고려대학교", "이화여자대학교", "중앙대학교",
            "한국외국어대학교", "한양대학교", "경희대학교", "서강대학교");

    @Test
    @DisplayName("given 학생들이 쓰는 줄임말 when 입시 질문을 분석하면 then 정식 대학으로 연결된다")
    void informal_aliases_map_to_full_names() {
        // given / when / then
        assertThat(AdmissionsQueryParser.parse("연대 교과전형 수능최저 있어?", TEN).universities()).containsExactly("연세대학교");
        assertThat(AdmissionsQueryParser.parse("고대 2027 수시 지역균형 전형은?", TEN).universities()).containsExactly("고려대학교");
        assertThat(AdmissionsQueryParser.parse("이대 정시 원서접수 일정", TEN).universities()).containsExactly("이화여자대학교");
        assertThat(AdmissionsQueryParser.parse("성대 논술 전형 알려줘", TEN).universities()).containsExactly("성균관대학교");
        assertThat(AdmissionsQueryParser.parse("중대 수시 일정", TEN).universities()).containsExactly("중앙대학교");
        assertThat(AdmissionsQueryParser.parse("외대 학종 지원자격", TEN).universities()).containsExactly("한국외국어대학교");
        assertThat(AdmissionsQueryParser.parse("한국외대 수시 일정", TEN).universities()).containsExactly("한국외국어대학교");
        assertThat(AdmissionsQueryParser.parse("이화여대 정시 모집", TEN).universities()).containsExactly("이화여자대학교");
        assertThat(AdmissionsQueryParser.parse("연대는 교과전형 수능최저가 있나요", TEN).universities()).containsExactly("연세대학교");
    }

    @Test
    @DisplayName("given 서울 확대 대학 줄임말(건대·홍대·시립대) when 입시 질문이면 then 정식 대학으로 연결되고 입시 문맥이 없으면 연결하지 않는다")
    void seoul_expansion_aliases_need_admission_context() {
        // given
        List<String> univs = List.of("건국대학교", "홍익대학교", "서울시립대학교", "서울대학교");

        // when / then
        assertThat(AdmissionsQueryParser.parse("건대 정시 모집인원", univs).universities()).containsExactly("건국대학교");
        assertThat(AdmissionsQueryParser.parse("홍대 수시 실기전형 일정", univs).universities()).containsExactly("홍익대학교");
        assertThat(AdmissionsQueryParser.parse("시립대 학생부교과 수능최저", univs).universities()).containsExactly("서울시립대학교");
        assertThat(AdmissionsQueryParser.parse("홍대 맛집 추천", univs).universities()).isEmpty();
        assertThat(AdmissionsQueryParser.parse("건대 입구역 근처 카페", univs).universities()).isEmpty();
    }

    @Test
    @DisplayName("given 서울 2차 대학 줄임말(숙명여대·숙대·성신여대·서울여대·서울과기대) when 분석하면 then 정식 대학으로 연결되고 서울대와 섞이지 않는다")
    void seoul_second_batch_aliases() {
        // given
        List<String> univs = List.of("숙명여자대학교", "성신여자대학교", "서울여자대학교", "서울과학기술대학교", "서울대학교");

        // when / then
        assertThat(AdmissionsQueryParser.parse("숙명여대 수시 일정", univs).universities()).containsExactly("숙명여자대학교");
        assertThat(AdmissionsQueryParser.parse("숙대 정시 모집인원", univs).universities()).containsExactly("숙명여자대학교");
        assertThat(AdmissionsQueryParser.parse("성신여대 논술 전형", univs).universities()).containsExactly("성신여자대학교");
        assertThat(AdmissionsQueryParser.parse("서울여대 수시 원서접수", univs).universities()).containsExactly("서울여자대학교");
        assertThat(AdmissionsQueryParser.parse("서울과기대 정시 모집인원", univs).universities()).containsExactly("서울과학기술대학교");
        assertThat(AdmissionsQueryParser.parse("숙대입구역 맛집", univs).universities()).isEmpty();
    }

    @Test
    @DisplayName("given 서울 3차 대학 줄임말(덕성여대·동덕여대·가톨릭대·경기대·명지대) when 분석하면 then 괄호 캠퍼스 표기가 있는 정식 대학으로 연결된다")
    void seoul_third_batch_aliases() {
        // given
        List<String> univs = List.of("덕성여자대학교", "동덕여자대학교", "가톨릭대학교(서울)", "경기대학교(서울캠퍼스)", "명지대학교(인문캠퍼스)");

        // when / then
        assertThat(AdmissionsQueryParser.parse("덕성여대 수시 원서접수 일정", univs).universities()).containsExactly("덕성여자대학교");
        assertThat(AdmissionsQueryParser.parse("동덕여대 정시 모집인원", univs).universities()).containsExactly("동덕여자대학교");
        assertThat(AdmissionsQueryParser.parse("가톨릭대 의예과 정시", univs).universities()).containsExactly("가톨릭대학교(서울)");
        assertThat(AdmissionsQueryParser.parse("경기대 수시 논술", univs).universities()).containsExactly("경기대학교(서울캠퍼스)");
        assertThat(AdmissionsQueryParser.parse("명지대 정시 모집인원", univs).universities()).containsExactly("명지대학교(인문캠퍼스)");
    }

    @Test
    @DisplayName("given 서울 4차 대학 줄임말(한체대·한국체대·한예종·추계예대·장신대·감신대) when 분석하면 then 정식 대학으로 연결된다")
    void seoul_fourth_batch_aliases() {
        // given
        List<String> univs = List.of("한국체육대학교", "한국예술종합학교", "추계예술대학교", "장로회신학대학교", "감리교신학대학교");

        // when / then
        assertThat(AdmissionsQueryParser.parse("한체대 수시 체육특기자 모집인원", univs).universities()).containsExactly("한국체육대학교");
        assertThat(AdmissionsQueryParser.parse("한국체대 정시 원서접수", univs).universities()).containsExactly("한국체육대학교");
        assertThat(AdmissionsQueryParser.parse("한예종 일반전형 일정", univs).universities()).containsExactly("한국예술종합학교");
        assertThat(AdmissionsQueryParser.parse("추계예대 실기 전형", univs).universities()).containsExactly("추계예술대학교");
        assertThat(AdmissionsQueryParser.parse("장신대 수시 원서접수", univs).universities()).containsExactly("장로회신학대학교");
        assertThat(AdmissionsQueryParser.parse("감신대 정시 모집인원", univs).universities()).containsExactly("감리교신학대학교");
    }

    @Test
    @DisplayName("given 다른 뜻으로 쓰인 표현 when 분석하면 then 대학으로 잘못 치환하지 않는다")
    void ambiguous_words_are_not_universities() {
        // given: 입시 문맥이 없거나, 다른 단어의 일부이거나, 조사 '로'가 붙은 부사
        List<String> questions = List.of(
                "연대 책임이 뭐예요", "고대 그리스 문명 알려줘", "성대 결절 수술 후기", "중대한 사안이에요",
                "이대로 괜찮을까요", "고대사 공부법", "연대감을 키우는 법", "외대 안쪽 통증",
                "이대로 수시 지원해도 되나요", "고대인의 생활", "성대한 파티");

        // when / then
        for (String q : questions) {
            assertThat(AdmissionsQueryParser.parse(q, TEN).universities()).as(q).isEmpty();
        }
    }

    @Test
    @DisplayName("given 2025학년도 같은 과거 학년도 when 분석하면 then 미지원 학년도로 인식한다 (2027·2028·개정교육과정·인원 숫자는 제외)")
    void past_academic_years_are_unsupported() {
        // given / when / then
        assertThat(AdmissionsQueryParser.parse("2025학년도 서울대 수시 모집요강", TEN).mentionsUnsupportedYear()).isTrue();
        assertThat(AdmissionsQueryParser.parse("2025학년도 서울대 수시 모집요강", TEN).years()).isEmpty();
        assertThat(AdmissionsQueryParser.parse("25학년도 연세대 수시", TEN).mentionsUnsupportedYear()).isTrue();
        assertThat(AdmissionsQueryParser.parse("2024년도 입시 결과", TEN).mentionsUnsupportedYear()).isTrue();
        assertThat(AdmissionsQueryParser.parse("2026학년도 정시", TEN).mentionsUnsupportedYear()).isTrue();
        assertThat(AdmissionsQueryParser.parse("27학년도 연세대 수시", TEN).years()).containsExactly(2027);
        assertThat(AdmissionsQueryParser.parse("27학년도 연세대 수시", TEN).mentionsUnsupportedYear()).isFalse();
        assertThat(AdmissionsQueryParser.parse("2022 개정 교육과정이 수능에 미치는 영향", TEN).mentionsUnsupportedYear()).isFalse();
        assertThat(AdmissionsQueryParser.parse("2015년 개정 교육과정 이후", TEN).mentionsUnsupportedYear()).isFalse();
        assertThat(AdmissionsQueryParser.parse("모집인원이 2000명이에요?", TEN).mentionsUnsupportedYear()).isFalse();
    }

    @Test
    @DisplayName("given 등록되지 않은 대학 when 분석하면 then 미등록 대학으로 구분하고, 등록 대학이면 제외한다")
    void unknown_universities_are_detected() {
        // given / when
        var kaist = AdmissionsQueryParser.parse("카이스트 수시 모집요강", TEN);
        var english = AdmissionsQueryParser.parse("KAIST 입학 전형", TEN);
        var full = AdmissionsQueryParser.parse("건국대학교 수시 일정", TEN);
        var mixed = AdmissionsQueryParser.parse("연세대학교와 카이스트 교과전형", TEN);
        var registered = AdmissionsQueryParser.parse("서울대학교 수시 모집요강", TEN);
        var cityUniv = AdmissionsQueryParser.parse("서울시립대 수시 일정", TEN);

        // then
        assertThat(kaist.unknownUniversities()).containsExactly("카이스트");
        assertThat(kaist.onlyUnregisteredUniversities()).isTrue();
        assertThat(english.unknownUniversities()).containsExactly("KAIST");
        assertThat(full.unknownUniversities()).containsExactly("건국대");
        assertThat(mixed.universities()).containsExactly("연세대학교");
        assertThat(mixed.unknownUniversities()).containsExactly("카이스트");
        assertThat(mixed.onlyUnregisteredUniversities()).isFalse();
        assertThat(registered.unknownUniversities()).isEmpty();
        assertThat(cityUniv.universities()).as("서울시립대를 서울대로 오인하지 않는다").isEmpty();
        assertThat(cityUniv.unknownUniversities()).isNotEmpty();
    }

    @Test
    @DisplayName("given 본교와 ERICA가 별도 대학으로 등록 when 질문이 ERICA·에리카를 말하면 then ERICA 항목만, 캠퍼스를 말하지 않으면 둘 다 고른다")
    void asked_campus_narrows_to_that_campus_entity() {
        // given
        var names = List.of("한양대학교(서울)", "한양대학교(ERICA)", "고려대학교(서울)", "중앙대학교", "중앙대학교(다빈치캠퍼스)");

        // when
        var erica = AdmissionsQueryParser.parse("한양대 ERICA 2027 수시 원서접수", names);
        var ericaKo = AdmissionsQueryParser.parse("한양대 에리카 정시 모집인원", names);
        var plain = AdmissionsQueryParser.parse("한양대 수시 원서접수", names);
        var davinci = AdmissionsQueryParser.parse("중앙대 다빈치 수시 모집인원", names);
        var sejong = AdmissionsQueryParser.parse("고려대 세종캠퍼스 수시 일정", names);
        var both = AdmissionsQueryParser.parse("한양대 ERICA와 고려대 수시 비교", names);

        // then
        assertThat(erica.universities()).containsExactly("한양대학교(ERICA)");
        assertThat(ericaKo.universities()).containsExactly("한양대학교(ERICA)");
        assertThat(plain.universities()).containsExactlyInAnyOrder("한양대학교(서울)", "한양대학교(ERICA)");
        assertThat(davinci.universities()).containsExactly("중앙대학교(다빈치캠퍼스)");
        assertThat(sejong.universities()).as("세종캠퍼스가 별도 항목이 아니면 그대로").containsExactly("고려대학교(서울)");
        assertThat(both.universities()).containsExactlyInAnyOrder("한양대학교(ERICA)", "고려대학교(서울)");
    }

    @Test
    @DisplayName("given 한국항공대학교 when 항공대라고 물으면 then 한국항공대학교로 본다")
    void hangkongdae_alias() {
        // given / when
        var p = AdmissionsQueryParser.parse("항공대 2027 수시 원서접수 일정", List.of("한국항공대학교", "서울대학교"));

        // then
        assertThat(p.universities()).containsExactly("한국항공대학교");
        assertThat(p.unknownUniversities()).isEmpty();
    }

    @Test
    @DisplayName("given '국립'이 붙은 정식명 when '국립' 없이 줄여 물으면 then 그 대학으로 보고, 비슷한 이름의 다른 대학과 섞지 않는다")
    void national_prefix_is_optional() {
        // given
        var names = List.of("국립공주대학교", "국립목포대학교", "국립목포해양대학교", "순천향대학교", "국립순천대학교", "서울대학교");

        // when
        var shortName = AdmissionsQueryParser.parse("공주대 2026학년도 교과 경쟁률 알려줘", names);
        var fullNoPrefix = AdmissionsQueryParser.parse("공주대학교 수시 모집요강", names);
        var withPrefix = AdmissionsQueryParser.parse("국립공주대 수시 일정", names);
        var haeyang = AdmissionsQueryParser.parse("목포해양대 수시 경쟁률", names);
        var mokpo = AdmissionsQueryParser.parse("목포대 수시 경쟁률", names);
        var soonchunhyang = AdmissionsQueryParser.parse("순천향대 수시 일정", names);
        var suncheon = AdmissionsQueryParser.parse("순천대 수시 일정", names);

        // then
        assertThat(shortName.universities()).containsExactly("국립공주대학교");
        assertThat(fullNoPrefix.universities()).containsExactly("국립공주대학교");
        assertThat(fullNoPrefix.unknownUniversities()).as("'국립' 없는 정식명을 미등록 대학으로 보지 않는다").isEmpty();
        assertThat(withPrefix.universities()).containsExactly("국립공주대학교");
        assertThat(haeyang.universities()).containsExactly("국립목포해양대학교");
        assertThat(mokpo.universities()).containsExactly("국립목포대학교");
        assertThat(soonchunhyang.universities()).containsExactly("순천향대학교");
        assertThat(suncheon.universities()).containsExactly("국립순천대학교");
    }

    @Test
    @DisplayName("given 세종·글로컬·WISE·천안·미래캠퍼스 분교 항목 when 캠퍼스 줄임말로 물으면 then 그 분교 항목만, 분교 항목이 없는 대학은 그대로 고른다")
    void campus_short_names_narrow_to_branch_entities() {
        // given
        var names = List.of("고려대학교(서울)", "고려대학교(세종캠퍼스)", "건국대학교", "건국대학교(글로컬캠퍼스)",
                "동국대학교", "동국대학교(WISE캠퍼스)", "상명대학교", "상명대학교(천안캠퍼스)", "연세대학교", "연세대학교(미래캠퍼스)",
                "세종대학교", "단국대학교");

        // when / then
        assertThat(AdmissionsQueryParser.parse("고려대 세종 수시 원서접수", names).universities()).containsExactly("고려대학교(세종캠퍼스)");
        assertThat(AdmissionsQueryParser.parse("건국대 글로컬 정시 모집인원", names).universities()).containsExactly("건국대학교(글로컬캠퍼스)");
        assertThat(AdmissionsQueryParser.parse("동국대 와이즈 수시", names).universities()).containsExactly("동국대학교(WISE캠퍼스)");
        assertThat(AdmissionsQueryParser.parse("동국대 wise 정시", names).universities()).containsExactly("동국대학교(WISE캠퍼스)");
        assertThat(AdmissionsQueryParser.parse("상명대 천안 수시 모집인원", names).universities()).containsExactly("상명대학교(천안캠퍼스)");
        assertThat(AdmissionsQueryParser.parse("연세대 원주 수시", names).universities()).containsExactly("연세대학교(미래캠퍼스)");
        assertThat(AdmissionsQueryParser.parse("연세대 미래캠퍼스 수시", names).universities()).containsExactly("연세대학교(미래캠퍼스)");
        assertThat(AdmissionsQueryParser.parse("세종대 수시 원서접수", names).universities()).as("세종대는 분교 줄임말과 무관").containsExactly("세종대학교");
        assertThat(AdmissionsQueryParser.parse("단국대 천안 의예과", names).universities()).as("통합 항목 대학은 그대로").containsExactly("단국대학교");
        assertThat(AdmissionsQueryParser.parse("고려대 수시 원서접수", names).universities())
                .containsExactlyInAnyOrder("고려대학교(서울)", "고려대학교(세종캠퍼스)");
    }
}
