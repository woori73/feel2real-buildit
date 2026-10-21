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
}
