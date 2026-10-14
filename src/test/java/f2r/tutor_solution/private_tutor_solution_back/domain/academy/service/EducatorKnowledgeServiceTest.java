package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.EducatorReference;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EducatorKnowledgeService — 배포 시점 고정 데이터(59건)의 검색 품질과 데이터 격리/안전 강하 검증.
 *
 * <p>핵심 계약: (1) 학습-safe 59건만 검색 대상(본문 없음·researchPending·중복 id 81 배제),
 * (2) 항상 결정적(같은 쿼리 → 같은 순서), (3) 데이터 없음·쿼리 없음에서도 예외 없이 빈 목록
 * (기존 홈페이지 제작 기능은 사례 없이도 동작해야 한다), (4) 결과는 출처(id·sourceCardUrl·rankAsOf)를 유지한다.
 */
@DisplayName("EducatorKnowledgeService — 59건 고정 데이터 검색 + 격리 + 안전 강하")
class EducatorKnowledgeServiceTest {

  private static final AcademyAiContext EMPTY_CONTEXT = new AcademyAiContext(
      null, null, null, null, null, null, null, List.of(), List.of(), null);

  private static EducatorKnowledgeService.Card card(
      long id, String province, String region, List<String> tags, String philosophy) {
    EducatorReference reference = new EducatorReference(
        id, "원장" + id, "원장", "학원" + id, region, "2026-09-14",
        philosophy, "커리큘럼 " + id, "수업 방식 " + id, tags,
        "https://feel2real.co.kr/chart/educators/" + id);
    return new EducatorKnowledgeService.Card(id, region, province, tags, reference);
  }

  @Test
  @DisplayName("given 배포 리소스 when 서비스 생성 then 학습-safe 59건이 로드되고 중복 id 81은 없다")
  void realResource_loads59_excludesDuplicate81() {
    EducatorKnowledgeService service = new EducatorKnowledgeService();
    assertThat(service.poolSize()).isEqualTo(59);

    List<EducatorReference> hits = service.search("영어 암기 말하기 내신 학부모", 100);
    assertThat(hits).isNotEmpty();
    assertThat(hits).extracting(EducatorReference::id).doesNotContain(81L);
  }

  @Test
  @DisplayName("given 배포 리소스 when 검색 then 모든 사례가 출처 URL과 순위 기준일을 유지한다")
  void realResource_referencesKeepProvenance() {
    EducatorKnowledgeService service = new EducatorKnowledgeService();
    List<EducatorReference> hits = service.search("말하기 원서 파닉스", 3);
    assertThat(hits).allSatisfy(r -> {
      assertThat(r.sourceCardUrl()).startsWith("https://feel2real.co.kr/chart/educators/");
      assertThat(r.rankAsOf()).isNotBlank();
      assertThat(r.region()).isNotBlank();
    });
  }

  @Test
  @DisplayName("given 같은 쿼리 when 두 번 검색 then 순서까지 동일한 결정적 결과, 최대 3건")
  void search_isDeterministic_andCappedAt3() {
    EducatorKnowledgeService service = new EducatorKnowledgeService();
    List<EducatorReference> first = service.search(EMPTY_CONTEXT, "암기 반대, 말하기 중심 수업");
    List<EducatorReference> second = service.search(EMPTY_CONTEXT, "암기 반대, 말하기 중심 수업");

    assertThat(first).isNotEmpty();
    assertThat(first).extracting(EducatorReference::id).isEqualTo(second.stream()
        .map(EducatorReference::id).toList());
    assertThat(first).hasSizeLessThanOrEqualTo(3);
  }

  @Test
  @DisplayName("given 빈 풀 when 검색 then 예외 없이 빈 목록(기존 기능은 사례 없이 동작)")
  void emptyPool_returnsEmptyWithoutThrowing() {
    EducatorKnowledgeService service = new EducatorKnowledgeService(List.of());
    assertThat(service.search(EMPTY_CONTEXT, "말하기 중심")).isEmpty();
    assertThat(service.search("어떤 쿼리", 3)).isEmpty();
  }

  @Test
  @DisplayName("given 빈/널 쿼리 when 검색 then 빈 목록 — 컨텍스트 일부만 있어도 동작")
  void blankQuery_returnsEmpty() {
    EducatorKnowledgeService service = new EducatorKnowledgeService(
        List.of(card(1L, "경기도", "경기도 성남시 분당구", List.of("communication_first"), "말하기 중심")));
    assertThat(service.search((String) null, 3)).isEmpty();
    assertThat(service.search("   ", 3)).isEmpty();
    assertThat(service.search((AcademyAiContext) null, null)).isEmpty();

    AcademyAiContext withPhilosophy = new AcademyAiContext(
        "우리학원", null, null, null, null, null, null,
        List.of("말하기 실력을 기른다", "", "", ""), List.of(), null);
    assertThat(service.search(withPhilosophy, null)).hasSize(1);
  }

  @Test
  @DisplayName("given 태그+지역 일치 카드 when 검색 then 키워드만 일치하는 카드보다 먼저, limit 준수")
  void scoring_tagsAndRegion_outrankKeywordOnly() {
    EducatorKnowledgeService service = new EducatorKnowledgeService(List.of(
        card(2L, "부산광역시", "부산광역시 사하구", List.of(), "말하기 관련 원문 없음"),
        card(1L, "경기도", "경기도 성남시 분당구", List.of("communication_first"), "수업 소개")));

    List<EducatorReference> hits = service.search("경기도 성남시 분당구 말하기", 3);
    assertThat(hits.get(0).id()).isEqualTo(1L);
    assertThat(service.search("말하기", 1)).hasSize(1);
  }

  @Test
  @DisplayName("given 동점 카드 when 검색 then id 오름차순으로 결정적.tie-break")
  void tieBreak_byIdAscending() {
    EducatorKnowledgeService service = new EducatorKnowledgeService(List.of(
        card(9L, "서울특별시", "서울특별시 금천구", List.of("anti_roterism"), "암기"),
        card(3L, "서울특별시", "서울특별시 금천구", List.of("anti_roterism"), "암기")));

    assertThat(service.search("서울특별시 금천구 암기", 2))
        .extracting(EducatorReference::id).containsExactly(3L, 9L);
  }
}
