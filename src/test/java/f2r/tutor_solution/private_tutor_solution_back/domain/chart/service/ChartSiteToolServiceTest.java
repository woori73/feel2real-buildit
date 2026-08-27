package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService.DraftRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService.EvidenceRequest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ChartSiteToolServiceTest {

  private final ChartSiteToolService service = new ChartSiteToolService(mock(JdbcTemplate.class), new ObjectMapper());

  @Test
  void evaluate_passes_givenCurrentOfflineDistinctiveEducatorEvidence() {
    DraftRequest request = eligibleRequest(List.of(
        new EvidenceRequest("수업", "자체 독해 커리큘럼과 수업 방법을 설명함", "수업 소개", "https://example.com/class",
            LocalDate.now(), "CURRICULUM", true),
        new EvidenceRequest("오프라인 수업", "강남구에서 현재 학생을 지도함", "수업 일정", "https://example.com/offline",
            LocalDate.now(), "OFFLINE_ACTIVITY", true)));

    assertThat(service.evaluate(request).eligible()).isTrue();
  }

  @Test
  void evaluate_failsBeforeScore_givenOnlyPromotionalEvidence() {
    DraftRequest request = eligibleRequest(List.of(new EvidenceRequest(
        "광고", "최고 강사진 수강생 모집", "설명회", "https://example.com/ad",
        LocalDate.now(), "ADVERTISEMENT", false)));

    assertThat(service.evaluate(request).eligible()).isFalse();
    assertThat(service.evaluate(request).reasons()).contains("철학·커리큘럼·수업 등 교육적 정보 근거 없음", "광고·보도자료만 존재");
  }

  @Test
  void evaluate_usesCurrentYearDynamically_givenPastEvidence() {
    DraftRequest request = eligibleRequest(List.of(new EvidenceRequest(
        "교육칼럼", "교육 철학 설명", "과거 글", "https://example.com/old",
        LocalDate.now().minusYears(1), "PHILOSOPHY", true)));

    assertThat(service.evaluate(request).eligible()).isFalse();
    assertThat(service.evaluate(request).reasons()).contains("현재 연도 날짜가 있는 근거 없음");
  }

  @Test
  void score_isDeterministicAndNeverExceedsOneHundred() {
    Map<String, Object> candidate = new HashMap<>();
    candidate.put("identity_summary", "정체성");
    candidate.put("education_philosophy", "철학");
    candidate.put("curriculum_features", "커리큘럼");
    candidate.put("teaching_features", "수업 방식");
    List<Map<String, Object>> evidence = List.of(
        evidence("TEACHING_METHOD", "2026-01-01", "https://a.example/1"),
        evidence("STUDENT_GUIDANCE", "2026-02-01", "https://a.example/2"),
        evidence("OFFLINE_ACTIVITY", "2026-03-01", "https://a.example/3"),
        evidence("MATERIAL", "2026-04-01", "https://a.example/4"),
        evidence("SUBJECT_EXPERTISE", "2026-05-01", "https://a.example/5"),
        evidence("EDUCATIONAL_CONTENT", "2026-06-01", "https://a.example/6"));

    Map<String, Integer> first = service.score(candidate, evidence);
    Map<String, Integer> second = service.score(candidate, evidence);

    assertThat(first).isEqualTo(second);
    assertThat(first.values()).hasSum(100);
  }

  private DraftRequest eligibleRequest(List<EvidenceRequest> evidence) {
    return new DraftRequest("seoul-gangnam", "홍길동", "원장", "길동영어", "서울특별시", "강남구",
        "독해 중심 교육자", "스스로 읽는 힘", "자체 커리큘럼", "질문식 수업", "활동 기록을 정기 공개하세요.",
        null, null, null, null, "콘텐츠에서 개인 교육활동을 확인", true, true, true, true,
        List.of("명확한 교육 철학"), evidence);
  }

  private Map<String, Object> evidence(String kind, String date, String url) {
    return Map.of("evidence_kind", kind, "activity_date", date, "source_url", url, "educationally_informative", true);
  }
}
