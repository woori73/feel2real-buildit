package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("제출서류 원문 — 로더 합성·검색 본문 순서")
class AdmissionsSubmissionDocumentsTest {

    private static final ObjectMapper M = new ObjectMapper();

    @Test
    @DisplayName("given 문자열 배열 when 합치면 then 원문 그대로 ' | '로 잇는다")
    void arrayIsJoinedVerbatim() throws Exception {
        var node = M.readTree("[\"학교생활기록부 1부\", \"졸업증명서(해당자)\"]");
        assertThat(AdmissionsDataLoader.submissionDocuments(node)).isEqualTo("학교생활기록부 1부 | 졸업증명서(해당자)");
    }

    @Test
    @DisplayName("given 값이 없거나 비었을 때 when 합치면 then 빈 문자열(검색 단계에서 확인되지 않음으로 표기)")
    void missingOrEmptyIsBlank() throws Exception {
        assertThat(AdmissionsDataLoader.submissionDocuments(M.readTree("null"))).isEmpty();
        assertThat(AdmissionsDataLoader.submissionDocuments(M.readTree("[]"))).isEmpty();
        assertThat(AdmissionsDataLoader.submissionDocuments(M.readTree("[\" \", \"\"]"))).isEmpty();
        assertThat(AdmissionsDataLoader.submissionDocuments(M.missingNode())).isEmpty();
    }

    @Test
    @DisplayName("given 문자열 하나 when 합치면 then 앞뒤 공백만 제거한다")
    void singleStringIsTrimmedOnly() throws Exception {
        assertThat(AdmissionsDataLoader.submissionDocuments(M.readTree("\"  입학원서·학생부  \""))).isEqualTo("입학원서·학생부");
    }

    @Test
    @DisplayName("given 제출서류를 묻는 질문 when 본문을 정렬하면 then 제출서류 줄이 맨 앞이고 내용은 그대로, 다른 질문은 순서 유지")
    void detailsForPutsDocumentsFirstOnlyWhenAsked() {
        String details = "모집인원: 10\n전형방법: 일괄\n수능최저: 없음\n제출서류: 학생부 | 증명서";

        assertThat(AdmissionsFactSearchService.detailsFor("제출서류 알려줘", details))
                .isEqualTo("제출서류: 학생부 | 증명서\n모집인원: 10\n전형방법: 일괄\n수능최저: 없음");
        assertThat(AdmissionsFactSearchService.detailsFor("전형방법 알려줘", details)).isEqualTo(details);
        assertThat(AdmissionsFactSearchService.detailsFor(null, details)).isEqualTo(details);
    }
}
