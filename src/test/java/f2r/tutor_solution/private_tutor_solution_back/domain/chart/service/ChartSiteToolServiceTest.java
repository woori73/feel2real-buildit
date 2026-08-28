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

/**
 * 조사 파이프라인 판정({@code assess}) — PR-A 아카이브 우선 재편.
 *
 * <ul>
 *   <li>최소 게이트(개인 식별 / 현장 영어교육 범위 / 공개 근거 URL) 실패 → REJECTED(저장 거부).</li>
 *   <li>교육철학·커리큘럼·현재 오프라인 활동·현재 연도 근거 부족 → RESEARCH_PENDING(저장, 점수 제외).</li>
 *   <li>둘 다 통과 → EVALUATABLE.</li>
 *   <li>"현재 오프라인 활동 미확인"(단순 미확인)과 "명백히 현장 교육자 아님"(exclusionFree=false)을 구분.</li>
 * </ul>
 */
class ChartSiteToolServiceTest {

  private final ChartSiteToolService service = new ChartSiteToolService(mock(JdbcTemplate.class));

  private static final String PHILOSOPHY =
      "학생이 스스로 질문을 만들며 원리를 이해하도록 이끌고, 틀린 문제는 사고 과정을 다시 설계하는 재료로 삼는다.";
  private static final String CURRICULUM =
      "진단 평가로 취약 지점을 찾은 뒤 개념 강의를 하고, 학교별 기출 분석과 변형 문제로 적용한 다음 오답 노트를 반복한다.";

  // ---- 최소 게이트 통과 + 평가 게이트 통과 → EVALUATABLE ----

  @Test
  void assess_evaluatable_givenPhilosophyCurriculumAndCurrentOfflineEvidence() {
    var result = service.assess(fullRequest(List.of(
        new EvidenceRequest("수업", "자체 독해 커리큘럼과 수업 방법을 설명함", "수업 소개", "https://example.com/class",
            LocalDate.now(), "CURRICULUM", true),
        new EvidenceRequest("오프라인 수업", "강남구에서 현재 학생을 지도함", "수업 일정", "https://example.com/offline",
            LocalDate.now(), "OFFLINE_ACTIVITY", true))));

    assertThat(result.status()).isEqualTo("EVALUATABLE");
    assertThat(result.rejected()).isFalse();
    assertThat(result.holdReasons()).isEmpty();
  }

  // ---- 평가 게이트 실패 → RESEARCH_PENDING (저장은 허용) ----

  @Test
  void assess_researchPending_givenMissingPhilosophySummary() {
    var result = service.assess(request(null, CURRICULUM, true, true, true, true, List.of(currentClassEvidence())));
    assertThat(result.status()).isEqualTo("RESEARCH_PENDING");
    assertThat(result.rejected()).isFalse();
    assertThat(result.holdReasons()).contains("교육철학을 공개 근거로 2~3문장 설명할 수 없음");
  }

  @Test
  void assess_researchPending_givenGenericAdPhilosophy() {
    String generic = "학생 중심으로 성적 향상에 최선을 다하며 책임감 있게 꼼꼼하게 지도하고 즐거운 수업을 만듭니다.";
    var result = service.assess(request(generic, CURRICULUM, true, true, true, true, List.of(currentClassEvidence())));
    assertThat(result.status()).isEqualTo("RESEARCH_PENDING");
    assertThat(result.holdReasons()).contains("교육철학이 일반 광고 문구 수준임");
  }

  @Test
  void assess_researchPending_givenSubjectEnumerationCurriculum() {
    String subjects = "초등영어 중등영어 고등영어 내신 수능 회화 문법 독해 어휘 초중고 정규반 특강반 방학특강";
    var result = service.assess(request(PHILOSOPHY, subjects, true, true, true, true, List.of(currentClassEvidence())));
    assertThat(result.status()).isEqualTo("RESEARCH_PENDING");
    assertThat(result.holdReasons()).contains("커리큘럼이 과목 나열 수준임");
  }

  @Test
  void assess_researchPending_givenOnlyPromotionalEvidence() {
    var result = service.assess(fullRequest(List.of(new EvidenceRequest(
        "광고", "최고 강사진 수강생 모집", "설명회", "https://example.com/ad",
        LocalDate.now(), "ADVERTISEMENT", false))));
    // 광고만 있어도 신원·범위·URL 최소 조건은 통과 → 제외/거부 아님, 보류
    assertThat(result.status()).isEqualTo("RESEARCH_PENDING");
    assertThat(result.holdReasons()).contains("광고·보도자료가 아닌 실제 교육 정보 근거 없음");
  }

  @Test
  void assess_researchPending_givenPastEvidenceOnly() {
    var result = service.assess(fullRequest(List.of(new EvidenceRequest(
        "교육칼럼", "교육 철학 설명", "과거 글", "https://example.com/old",
        LocalDate.now().minusYears(1), "PHILOSOPHY", true))));
    assertThat(result.status()).isEqualTo("RESEARCH_PENDING");
    assertThat(result.holdReasons()).contains("현재 연도 날짜가 있는 근거 없음");
  }

  @Test
  void assess_researchPending_givenOfflineNotConfirmedButNotExcluded() {
    // 신원·범위·근거 확인, 단지 현재 오프라인 활동만 아직 미확인 → 거부 아님, 보류
    var result = service.assess(request(PHILOSOPHY, CURRICULUM, false, true, true, true, List.of(
        new EvidenceRequest("수업", "자체 커리큘럼 설명", "수업 소개", "https://example.com/class",
            LocalDate.now(), "CURRICULUM", true))));
    assertThat(result.status()).isEqualTo("RESEARCH_PENDING");
    assertThat(result.rejected()).isFalse();
    assertThat(result.holdReasons()).contains("현재 오프라인 현장 활동 미확인");
  }

  // ---- 최소 게이트 실패 → REJECTED ----

  @Test
  void assess_rejected_givenExclusionSignal() {
    // 명백히 현장 영어교육 범위를 벗어남(온라인 전업 등) → 거부
    var result = service.assess(request(PHILOSOPHY, CURRICULUM, true, true, true, false, List.of(currentClassEvidence())));
    assertThat(result.status()).isEqualTo("REJECTED");
    assertThat(result.rejectReasons()).contains("현재 초·중·고 현장 영어교육 범위를 명백히 벗어남(온라인 전업 등)");
  }

  @Test
  void assess_rejected_givenIdentityNotConfirmed() {
    var result = service.assess(request(PHILOSOPHY, CURRICULUM, true, true, false, true, List.of(currentClassEvidence())));
    assertThat(result.status()).isEqualTo("REJECTED");
    assertThat(result.rejectReasons()).contains("교육자 개인 식별 불가");
  }

  @Test
  void assess_rejected_givenNoEvidenceUrl() {
    var result = service.assess(request(PHILOSOPHY, CURRICULUM, true, true, true, true, List.of()));
    assertThat(result.status()).isEqualTo("REJECTED");
    assertThat(result.rejectReasons()).contains("실제 공개 근거 URL 없음");
  }

  // ---- 평가 게이트: 현재 연도 활동 미확인 → 저장 허용 + RESEARCH_PENDING ----

  @Test
  void assess_researchPending_givenCurrentYearActivityNotConfirmed() {
    var result = service.assess(request(PHILOSOPHY, CURRICULUM, true, false, true, true, List.of(currentClassEvidence())));
    assertThat(result.status()).isEqualTo("RESEARCH_PENDING");
    assertThat(result.rejected()).isFalse();
    assertThat(result.holdReasons()).contains("최근 공개 활동 미확인");
  }

  // ---- REJECT 조건과 HOLD 조건 동시 존재 → REJECTED 우선 ----

  @Test
  void assess_rejected_takesPrecedenceWhenRejectAndHoldCoexist() {
    // 신원 미확인(최소 게이트 실패) + 철학·커리큘럼 미서술(평가 게이트 실패) 동시
    var result = service.assess(request(null, null, false, false, false, true, List.of(currentClassEvidence())));
    assertThat(result.status()).isEqualTo("REJECTED");
    assertThat(result.rejectReasons()).contains("교육자 개인 식별 불가");
    assertThat(result.holdReasons()).isNotEmpty(); // 참고용 hold 사유는 함께 실리되 status 는 REJECTED
  }

  // ---- 최소 게이트 URL 판정: evidence[0] 이 잘못돼도 뒤의 정상 evidence 를 대표로 사용 ----
  //      (saveDraft 도 동일한 primaryEvidence 기준을 쓴다)

  @Test
  void assess_minimumGate_usesFirstValidEvidenceUrl_notOnlyIndexZero() {
    var result = service.assess(request(PHILOSOPHY, CURRICULUM, true, true, true, true, List.of(
        new EvidenceRequest("커리큘럼", "자체 독해 커리큘럼과 수업 단계 설명", "커리큘럼", "not-a-url",
            LocalDate.now(), "CURRICULUM", true),
        new EvidenceRequest("오프라인 수업", "현재 학생을 지도하는 정규 수업", "수업 일정", "https://example.com/valid",
            LocalDate.now(), "OFFLINE_ACTIVITY", true))));
    assertThat(result.rejectReasons()).doesNotContain("실제 공개 근거 URL 없음");
    assertThat(result.status()).isEqualTo("EVALUATABLE");
  }

  @Test
  void assess_rejected_whenNoEvidenceHasValidPublicHttpUrl() {
    var result = service.assess(request(PHILOSOPHY, CURRICULUM, true, true, true, true, List.of(
        new EvidenceRequest("커리큘럼", "설명", "커리큘럼", "not-a-url", LocalDate.now(), "CURRICULUM", true),
        new EvidenceRequest("오프라인 수업", "설명", "수업", "   ", LocalDate.now(), "OFFLINE_ACTIVITY", true))));
    assertThat(result.status()).isEqualTo("REJECTED");
    assertThat(result.rejectReasons()).contains("실제 공개 근거 URL 없음");
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

  private DraftRequest fullRequest(List<EvidenceRequest> evidence) {
    return request(PHILOSOPHY, CURRICULUM, true, true, true, true, evidence);
  }

  private DraftRequest request(String philosophy, String curriculum, boolean offline, boolean currentYear,
      boolean identity, boolean exclusionFree, List<EvidenceRequest> evidence) {
    return new DraftRequest("seoul-gangnam", "홍길동", "원장", "길동영어", "서울특별시", "강남구",
        "독해 중심 교육자", philosophy, curriculum, "질문식 토론 수업", "2026년 봄학기 신규반 개강 공지",
        List.of("자체 독해 커리큘럼 운영", "수업 콘텐츠 지속 공개"), List.of("대표 커리큘럼을 온라인에 더 명확히 정리"),
        null, null, null, null, null, null, "공개 콘텐츠에서 개인 교육활동과 커리큘럼을 확인",
        offline, currentYear, identity, exclusionFree, List.of("명확한 교육 철학"), evidence);
  }

  private Map<String, Object> evidence(String kind, String date, String url) {
    return Map.of("evidence_kind", kind, "activity_date", date, "source_url", url, "educationally_informative", true);
  }
}
