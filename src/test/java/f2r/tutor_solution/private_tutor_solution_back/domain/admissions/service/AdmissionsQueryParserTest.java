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
}
