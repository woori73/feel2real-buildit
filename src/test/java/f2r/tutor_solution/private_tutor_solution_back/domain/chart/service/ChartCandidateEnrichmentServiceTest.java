package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartCandidateEnrichmentService.EnrichmentRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartCandidateEnrichmentService.EvidenceInput;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 기존 교육자 보완(Enrichment)의 순수 병합·검증 로직.
 *
 * <ul>
 *   <li>요약: null/blank 로는 절대 덮어쓰지 않는다. 빈 필드는 채우고, 값이 있으면 overwrite 일 때만 교체.</li>
 *   <li>강점/개선제안: 기존을 보존한 합집합 누적(삭제 없음).</li>
 *   <li>Evidence URL: 실제 원 출처만. 네이버 AI/검색·grounding redirect·placeholder 거부.</li>
 *   <li>빈 요청은 기존 데이터 삭제 방지를 위해 거부 대상으로 식별된다.</li>
 * </ul>
 */
class ChartCandidateEnrichmentServiceTest {

  private static final String PHILOSOPHY =
      "학생이 스스로 질문을 만들며 원리를 이해하도록 이끌고, 틀린 문제는 사고 과정을 다시 설계하는 재료로 삼는다.";

  // ----- mergeSummary -----

  @Test
  void mergeSummary_fillsBlankField() {
    // given 기존이 비어 있고 새 값이 옴 → when merge → then 새 값
    assertThat(ChartCandidateEnrichmentService.mergeSummary(null, PHILOSOPHY, false)).isEqualTo(PHILOSOPHY);
    assertThat(ChartCandidateEnrichmentService.mergeSummary("   ", PHILOSOPHY, false)).isEqualTo(PHILOSOPHY);
  }

  @Test
  void mergeSummary_neverOverwritesWithNullOrBlank() {
    // given 기존 값이 있고 새 값이 없음 → when merge → then 변경 없음(null)
    assertThat(ChartCandidateEnrichmentService.mergeSummary(PHILOSOPHY, null, true)).isNull();
    assertThat(ChartCandidateEnrichmentService.mergeSummary(PHILOSOPHY, "  ", true)).isNull();
  }

  @Test
  void mergeSummary_keepsExistingWhenNotOverwrite() {
    // given 기존 값이 이미 충분하고 overwrite=false → when merge → then 보존(null = 변경 없음)
    assertThat(ChartCandidateEnrichmentService.mergeSummary("기존 철학 설명", PHILOSOPHY, false)).isNull();
  }

  @Test
  void mergeSummary_replacesOnlyWhenOverwriteAndDifferent() {
    assertThat(ChartCandidateEnrichmentService.mergeSummary("기존 철학 설명", PHILOSOPHY, true)).isEqualTo(PHILOSOPHY);
    // 동일하면 굳이 UPDATE 하지 않는다
    assertThat(ChartCandidateEnrichmentService.mergeSummary(PHILOSOPHY, PHILOSOPHY, true)).isNull();
  }

  // ----- mergeList -----

  @Test
  void mergeList_accumulatesUnionPreservingExisting() {
    List<String> merged = ChartCandidateEnrichmentService.mergeList(
        List.of("학교별 내신 분석"), List.of("원서 다독", "학교별 내신 분석"));
    assertThat(merged).containsExactly("학교별 내신 분석", "원서 다독");
  }

  @Test
  void mergeList_returnsNullWhenNothingNew() {
    assertThat(ChartCandidateEnrichmentService.mergeList(List.of("A", "B"), List.of("A"))).isNull();
    assertThat(ChartCandidateEnrichmentService.mergeList(List.of("A"), List.of())).isNull();
    assertThat(ChartCandidateEnrichmentService.mergeList(List.of("A"), null)).isNull();
  }

  // ----- isEmptyRequest -----

  @Test
  void isEmptyRequest_trueWhenAllBlank() {
    assertThat(ChartCandidateEnrichmentService.isEmptyRequest(EnrichmentRequest.empty())).isTrue();
    assertThat(ChartCandidateEnrichmentService.isEmptyRequest(new EnrichmentRequest(
        "  ", null, "", null, List.of(), List.of(), true, List.of()))).isTrue();
  }

  @Test
  void isEmptyRequest_falseWhenAnyFieldPresent() {
    assertThat(ChartCandidateEnrichmentService.isEmptyRequest(new EnrichmentRequest(
        PHILOSOPHY, null, null, null, null, null, false, List.of()))).isFalse();
    assertThat(ChartCandidateEnrichmentService.isEmptyRequest(new EnrichmentRequest(
        null, null, null, null, List.of("강점"), null, false, List.of()))).isFalse();
    assertThat(ChartCandidateEnrichmentService.isEmptyRequest(new EnrichmentRequest(
        null, null, null, null, null, null, false,
        List.of(new EvidenceInput("글", "요약", "https://blog.naver.com/x/1", null, null, null))))).isFalse();
  }

  // ----- isOriginalSourceUrl -----

  @Test
  void isOriginalSourceUrl_acceptsRealPublicSources() {
    assertThat(ChartCandidateEnrichmentService.isOriginalSourceUrl("https://blog.naver.com/teacher/223849189138")).isTrue();
    assertThat(ChartCandidateEnrichmentService.isOriginalSourceUrl("https://myacademy.co.kr/curriculum")).isTrue();
    assertThat(ChartCandidateEnrichmentService.isOriginalSourceUrl("http://cafe.naver.com/abc/123")).isTrue();
  }

  @Test
  void isOriginalSourceUrl_rejectsNonOriginalOrInvalid() {
    assertThat(ChartCandidateEnrichmentService.isOriginalSourceUrl(
        "https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIabc")).isFalse();
    assertThat(ChartCandidateEnrichmentService.isOriginalSourceUrl("https://buildit.example/legacy-v3/122")).isFalse();
    assertThat(ChartCandidateEnrichmentService.isOriginalSourceUrl(
        "https://search.naver.com/search.naver?query=영어학원")).isFalse();
    assertThat(ChartCandidateEnrichmentService.isOriginalSourceUrl("https://www.google.com/search?q=x")).isFalse();
    assertThat(ChartCandidateEnrichmentService.isOriginalSourceUrl("not a url")).isFalse();
    assertThat(ChartCandidateEnrichmentService.isOriginalSourceUrl("ftp://example.com/a")).isFalse();
    assertThat(ChartCandidateEnrichmentService.isOriginalSourceUrl(null)).isFalse();
  }
}
