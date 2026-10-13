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
}
