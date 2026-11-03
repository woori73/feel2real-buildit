package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AdmissionsResearchService 순수 로직 단위 테스트.
 * given/when/then 구조.
 */
class AdmissionsResearchServiceLogicTest {

    private final AdmissionsResearchService service = new AdmissionsResearchService(
            null, null, null, null, null, null, null, null);

    @Test
    @DisplayName("키워드 추출 — 조사 제거 및 불용어 필터")
    void keywordsOf_stripsParticlesAndStopwords() {
        // given
        String question = "2027학년도 수시 원서접수 일정을 알려주세요";

        // when
        List<String> keywords = service.keywordsOf(question);

        // then
        assertTrue(keywords.contains("2027학년도") || keywords.contains("2027"),
                "년도 키워드 포함: " + keywords);
        assertTrue(keywords.contains("수시"), "수시 키워드 포함: " + keywords);
        assertFalse(keywords.contains("알려주세요"), "불용어 제거: " + keywords);
        assertFalse(keywords.contains("를"), "조사 제거: " + keywords);
    }

    @Test
    @DisplayName("키워드 추출 — 입시 동의어 확장")
    void keywordsOf_expandsSynonyms() {
        // given
        String question = "학종 전형 방법을 알려줘";

        // when
        List<String> keywords = service.keywordsOf(question);

        // then
        assertTrue(keywords.contains("학생부종합") || keywords.contains("학생부종합전형"),
                "학종 동의어 확장: " + keywords);
    }

    @Test
    @DisplayName("키워드 추출 — 빈 입력")
    void keywordsOf_emptyInput() {
        // given / when / then
        assertTrue(service.keywordsOf(null).isEmpty());
        assertTrue(service.keywordsOf("").isEmpty());
        assertTrue(service.keywordsOf("알려주세요").isEmpty(), "불용어만 있으면 빈 목록");
    }

    @Test
    @DisplayName("조사 제거 — stripParticle")
    void stripParticle_removesOneLayer() {
        // given / when / then
        assertEquals("서울대", AdmissionsResearchService.stripParticle("서울대를"));
        assertEquals("수시", AdmissionsResearchService.stripParticle("수시는"));
        assertEquals("가", AdmissionsResearchService.stripParticle("가"), "2글자 미만은 원형 유지");
    }

    @Test
    @DisplayName("키워드 히트 카운트")
    void keywordHits_countsMatches() {
        // given
        List<String> keywords = List.of("서울대", "수시", "일정");

        // when
        int hits = AdmissionsResearchService.keywordHits(keywords,
                "서울대 2027학년도 수시모집", "일정 안내");

        // then
        assertEquals(3, hits);
    }

    @Test
    @DisplayName("문자열 자르기 — firstChars")
    void firstChars_truncates() {
        // given / when / then
        assertNull(AdmissionsResearchService.firstChars(null, 10));
        assertEquals("abc", AdmissionsResearchService.firstChars("abc", 10));
        assertEquals("abcde…", AdmissionsResearchService.firstChars("abcdefghij", 5));
    }

    private static f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsOfficialMaterial material(
            long id, String title) {
        return f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsOfficialMaterial.builder()
                .materialId(id).academicYear(2027).title(title).issuer("중앙대학교 입학처")
                .documentUrl("https://mirror.test/" + id).build();
    }

    @Test
    @DisplayName("given 사설 미러본 자료 when 근거로 만들면 then 공식 자료가 아닌 미러본으로 표시하고 발행처를 공식처럼 내세우지 않는다")
    void materialEvidence_marksUnverifiedMirror() {
        // given
        var mirror = material(13, "중앙대학교 2027학년도 수시모집요강 (사설 미러본 · 공식 원본 미확보)");

        // when
        var e = AdmissionsResearchService.materialEvidence(mirror);

        // then
        assertEquals(AdmissionsResearchService.SourceClass.UNVERIFIED_MIRROR, e.getSourceClass());
        assertEquals("사설 미러본 (공식 원본 아님)", e.getNote());
        assertFalse(e.getExcerpt().contains("입학처"), "미러본에 공식 발행처를 표기하지 않는다");
        assertEquals("material:13", e.getRefId());
    }

    @Test
    @DisplayName("given 공식 원본 자료 when 근거로 만들면 then 기존과 같이 공식자료 원문으로 표시한다")
    void materialEvidence_keepsOfficialLabelForOfficialMaterial() {
        // given
        var official = material(14, "중앙대학교 2027학년도 정시모집요강");

        // when
        var e = AdmissionsResearchService.materialEvidence(official);

        // then
        assertEquals(AdmissionsResearchService.SourceClass.OFFICIAL_CONFIRMED, e.getSourceClass());
        assertEquals("공식자료 원문", e.getNote());
        assertTrue(e.getExcerpt().contains("중앙대학교 입학처"));
    }

    @Test
    @DisplayName("given 제목 표기 변형 when 미러본 판별하면 then 사설 미러본·공식 원본 미확보 표기를 모두 잡고 null은 아니다로 본다")
    void isUnverifiedMirror_detectsMarkers() {
        assertTrue(AdmissionsResearchService.isUnverifiedMirror(material(1, "X (사설 미러본)")));
        assertTrue(AdmissionsResearchService.isUnverifiedMirror(material(2, "X 공식 원본 미확보")));
        assertFalse(AdmissionsResearchService.isUnverifiedMirror(material(3, "X 수시모집요강")));
        assertFalse(AdmissionsResearchService.isUnverifiedMirror(null));
    }

    @Test
    @DisplayName("given 대학·학년도·일정 말만 있는 질문 when 유튜브 검색어를 고르면 then 남는 주제어가 없다")
    void youtubeTopicKeywords_scheduleOnlyQuestion_isEmpty() {
        // given
        List<String> keywords = service.keywordsOf("강원대 2027학년도 수시 원서접수 일정 알려줘");

        // when
        List<String> topic = AdmissionsResearchService.youtubeTopicKeywords(keywords);

        // then
        assertTrue(topic.isEmpty(), "주제어 없음: " + topic);
    }

    @Test
    @DisplayName("given 캠퍼스·기간을 묻는 질문 when 유튜브 검색어를 고르면 then 캠퍼스명·기간도 주제어가 아니다")
    void youtubeTopicKeywords_campusAndPeriod_isEmpty() {
        // given
        List<String> keywords = service.keywordsOf("동국대 와이즈캠퍼스 2027 수시 원서접수 기간은?");

        // when
        List<String> topic = AdmissionsResearchService.youtubeTopicKeywords(keywords);

        // then
        assertTrue(topic.isEmpty(), "주제어 없음: " + topic);
    }

    @Test
    @DisplayName("given 전형·수능최저를 묻는 질문 when 유튜브 검색어를 고르면 then 대학명은 빠지고 주제어는 남는다")
    void youtubeTopicKeywords_keepsTopicTerms() {
        // given
        List<String> keywords = service.keywordsOf("경북대 학생부종합 수능최저 전략");

        // when
        List<String> topic = AdmissionsResearchService.youtubeTopicKeywords(keywords);

        // then
        assertTrue(topic.contains("학생부종합"), "주제어: " + topic);
        assertTrue(topic.contains("수능최저"), "주제어: " + topic);
        assertFalse(topic.contains("경북대"), "대학명 제외: " + topic);
    }
}
