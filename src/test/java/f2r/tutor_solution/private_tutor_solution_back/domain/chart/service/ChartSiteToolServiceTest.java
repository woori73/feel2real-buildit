package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService.DraftRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService.EvidenceRequest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ChartSiteToolServiceTest {

  private final ChartSiteToolService service = new ChartSiteToolService(mock(JdbcTemplate.class));

  private static final String PHILOSOPHY =
      "학생이 스스로 질문을 만들며 원리를 이해하도록 이끌고, 틀린 문제는 사고 과정을 다시 설계하는 재료로 삼는다.";
  private static final String CURRICULUM =
      "진단 평가로 취약 지점을 찾은 뒤 개념 강의를 하고, 학교별 기출 분석과 변형 문제로 적용한 다음 오답 노트를 반복한다.";

  @Test
  void evaluate_passes_givenPhilosophyCurriculumAndCurrentOfflineEvidence() {
    // given / when
    var result = service.evaluate(eligibleRequest(List.of(
        new EvidenceRequest("수업", "자체 독해 커리큘럼과 수업 방법을 설명함", "수업 소개", "https://example.com/class",
            LocalDate.now(), "CURRICULUM", true),
        new EvidenceRequest("오프라인 수업", "강남구에서 현재 학생을 지도함", "수업 일정", "https://example.com/offline",
            LocalDate.now(), "OFFLINE_ACTIVITY", true))));

    // then
    assertThat(result.eligible()).isTrue();
  }

  @Test
  void evaluate_fails_givenMissingPhilosophySummary() {
    // given: 교육철학 요약이 비어 있음
    DraftRequest request = request(null, CURRICULUM, List.of(currentClassEvidence()));

    // when / then
    assertThat(service.evaluate(request).eligible()).isFalse();
    assertThat(service.evaluate(request).reasons()).contains("교육철학을 공개 근거로 2~3문장 설명할 수 없음");
  }

  @Test
  void evaluate_fails_givenGenericAdPhilosophy() {
    // given: 길이는 충분하지만 일반 광고 문구뿐
    String generic = "학생 중심으로 성적 향상에 최선을 다하며 책임감 있게 꼼꼼하게 지도하고 즐거운 수업을 만듭니다.";
    DraftRequest request = request(generic, CURRICULUM, List.of(currentClassEvidence()));

    // when / then
    assertThat(service.evaluate(request).eligible()).isFalse();
    assertThat(service.evaluate(request).reasons()).contains("교육철학이 일반 광고 문구 수준임");
  }

  @Test
  void evaluate_fails_givenSubjectEnumerationCurriculum() {
    // given: 과목 나열만 있는 커리큘럼
    String subjects = "초등영어 중등영어 고등영어 내신 수능 회화 문법 독해 어휘 초중고 정규반 특강반 방학특강";
    DraftRequest request = request(PHILOSOPHY, subjects, List.of(currentClassEvidence()));

    // when / then
    assertThat(service.evaluate(request).eligible()).isFalse();
    assertThat(service.evaluate(request).reasons()).contains("커리큘럼이 과목 나열 수준임");
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
    candidate.put("education_philosophy_summary", "철학");
    candidate.put("curriculum_summary", "커리큘럼");
    candidate.put("distinctive_teaching_summary", "수업 방식");
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
    assertThat(first.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
  }

  private EvidenceRequest currentClassEvidence() {
    return new EvidenceRequest("오프라인 수업", "현재 학생을 지도하는 정규 수업", "수업 일정", "https://example.com/offline",
        LocalDate.now(), "OFFLINE_ACTIVITY", true);
  }

  private DraftRequest eligibleRequest(List<EvidenceRequest> evidence) {
    return request(PHILOSOPHY, CURRICULUM, evidence);
  }

  private DraftRequest request(String philosophy, String curriculum, List<EvidenceRequest> evidence) {
    return new DraftRequest("seoul-gangnam", "홍길동", "원장", "길동영어", "서울특별시", "강남구",
        "독해 중심 교육자", philosophy, curriculum, "질문식 토론 수업", "2026년 봄학기 신규반 개강 공지",
        List.of("자체 독해 커리큘럼 운영", "수업 콘텐츠 지속 공개"), List.of("대표 커리큘럼을 온라인에 더 명확히 정리"),
        null, null, null, null, null, null, "공개 콘텐츠에서 개인 교육활동과 커리큘럼을 확인",
        true, true, true, true, List.of("명확한 교육 철학"), evidence);
  }

  private Map<String, Object> evidence(String kind, String date, String url) {
    return Map.of("evidence_kind", kind, "activity_date", date, "source_url", url, "educationally_informative", true);
  }
}
