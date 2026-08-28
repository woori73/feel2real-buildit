package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartEducatorRanking;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartRankedEducator;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartRankingList;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartRegionDirectory;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService.DraftRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService.EvidenceRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * BuildIt Ranking E2E — 현재 publication 에서 파생. CI 미실행(MySQL 필요).
 *   · 전국 순위 = publication chart_rank 와 일치.
 *   · 지역 순위 = 같은 (province, cityDistrict) 안에서의 위치.
 *   · RESEARCH_PENDING 은 순위 어디에도 없음.
 *   · Score 재계산·publication 재생성 없음(이미 만들어진 snapshot/entry 를 읽기만).
 */
@SpringBootTest
@Transactional
class ChartRankingMySqlTest {

  @Autowired ChartSiteToolService siteToolService;
  @Autowired ChartRankingService rankingService;
  @Autowired ChartEducatorSearchService searchService;
  @Autowired JdbcTemplate jdbcTemplate;

  private static final String PHIL =
      "학생이 스스로 질문을 만들며 원리를 이해하도록 이끌고, 틀린 문제는 사고 과정을 다시 설계하는 재료로 삼는다.";
  private static final String CURR =
      "진단 평가로 취약 지점을 찾은 뒤 개념 강의를 하고, 학교별 기출 분석과 변형 문제로 적용한 다음 오답 노트를 반복한다.";

  private String tag;

  private String freshRegion(String province, String city) {
    String id = "rank-" + city + "-" + System.nanoTime();
    jdbcTemplate.update("""
        INSERT INTO chart_regions (region_id, province, city_district, status, created_at, updated_at)
        VALUES (?, ?, ?, 'UNRESEARCHED', NOW(6), NOW(6))
        """, id, province, city);
    return id;
  }

  private long fullDraft(String regionId, String name, String province, String city, int evidenceCount) {
    java.util.List<EvidenceRequest> ev = new java.util.ArrayList<>();
    ev.add(new EvidenceRequest("커리큘럼", "자체 커리큘럼과 수업 단계 설명", "커리큘럼",
        "https://example.com/" + name + "/c", LocalDate.now(), "CURRICULUM", true));
    ev.add(new EvidenceRequest("오프라인 수업", "현재 학생 지도 정규 수업", "수업",
        "https://example.com/" + name + "/class", LocalDate.now(), "OFFLINE_ACTIVITY", true));
    String[] kinds = {"TEACHING_METHOD", "MATERIAL", "STUDENT_GUIDANCE", "EDUCATIONAL_CONTENT", "SUBJECT_EXPERTISE"};
    for (int i = 0; i < evidenceCount && i < kinds.length; i++) {
      ev.add(new EvidenceRequest("근거" + i, "교육 근거 " + i, "근거",
          "https://example.com/" + name + "/e" + i, LocalDate.now().minusMonths(i + 1), kinds[i], true));
    }
    var result = siteToolService.saveDraft(new DraftRequest(regionId, name, "원장", name + "영어",
        province, city, name + " 교육 정체성", PHIL, CURR, "질문식 토론 수업", "2026년 봄학기 공지",
        List.of("자체 커리큘럼 운영"), List.of("온라인 정리 보강"), null, null,
        "https://example.com/" + name, null, null, null, "공개 콘텐츠에서 개인 교육활동 확인",
        true, true, true, true, List.of(), ev));
    return ((Number) result.get("candidateId")).longValue();
  }

  private long researchPendingDraft(String regionId, String name, String province, String city) {
    var result = siteToolService.saveDraft(new DraftRequest(regionId, name, "원장", name + "영어",
        province, city, name + " 교육 정체성", null, null, null, null,
        List.of(), List.of(), null, null,
        "https://example.com/" + name, null, null, null, "공개 콘텐츠에서 개인 교육활동 확인",
        false, true, true, true, List.of(),
        List.of(new EvidenceRequest("글", "블로그 글", "글", "https://example.com/" + name + "/p",
            LocalDate.now(), "EDUCATIONAL_CONTENT", true))));
    return ((Number) result.get("candidateId")).longValue();
  }

  private void publish(String regionId, List<Long> ids) {
    siteToolService.claimRegion(regionId);
    siteToolService.completeRegion(regionId, ids, ids.size(), null);
  }

  @Test
  void nationwideRank_matchesPublicationChartRank_regionRankPartitions() {
    tag = "A" + System.nanoTime();
    String regA = freshRegion("테스트랭크도", "테스트시" + tag);
    long a1 = fullDraft(regA, "가일" + tag, "테스트랭크도", "테스트시" + tag, 5);   // 근거 많음 → 높은 점수
    long a2 = fullDraft(regA, "가이" + tag, "테스트랭크도", "테스트시" + tag, 1);   // 근거 적음 → 낮은 점수
    publish(regA, List.of(a1, a2));

    ChartRankingList all = rankingService.nationwide(null);
    // 내 두 명이 전국 목록에 있고, chart_rank 와 overallRank 가 일치한다
    for (long id : List.of(a1, a2)) {
      Integer chartRank = jdbcTemplate.queryForObject("""
          SELECT pe.chart_rank FROM chart_publication_entries pe
           JOIN chart_publications p ON p.publication_id = pe.publication_id
          WHERE p.status='PUBLISHED' AND pe.candidate_id=?
          ORDER BY p.published_at DESC, p.publication_id DESC LIMIT 1
          """, Integer.class, id);
      ChartEducatorRanking r = rankingService.forEducator(id);
      assertThat(r.ranked()).isTrue();
      assertThat(r.overallRank()).isEqualTo(chartRank);
      assertThat(all.educators()).anySatisfy(e -> {
        if (e.id() == id) assertThat(e.overallRank()).isEqualTo(chartRank);
      });
    }

    ChartRankingList region = rankingService.region("테스트랭크도", "테스트시" + tag);
    assertThat(region.scope()).isEqualTo("REGION");
    assertThat(region.educators()).extracting(ChartRankedEducator::id).containsExactly(a1, a2);
    assertThat(region.educators().get(0).regionRank()).isEqualTo(1);
    assertThat(region.educators().get(1).regionRank()).isEqualTo(2);
    assertThat(region.educators().get(0).regionTotal()).isEqualTo(2);
    // 지역 순위 1위가 전국 순위도 더 앞
    assertThat(region.educators().get(0).overallRank()).isLessThan(region.educators().get(1).overallRank());
  }

  @Test
  void researchPending_isExcludedFromAllRankings() {
    tag = "P" + System.nanoTime();
    String reg = freshRegion("보류랭크도", "보류시" + tag);
    long ev = fullDraft(reg, "발행자" + tag, "보류랭크도", "보류시" + tag, 3);
    long pend = researchPendingDraft(reg, "보류자" + tag, "보류랭크도", "보류시" + tag);
    publish(reg, List.of(ev));

    assertThat(rankingService.forEducator(ev).ranked()).isTrue();
    assertThat(rankingService.forEducator(pend).ranked()).isFalse();
    assertThat(rankingService.forEducator(pend).buildItScore()).isNull();
    assertThat(rankingService.region("보류랭크도", "보류시" + tag).educators())
        .extracting(ChartRankedEducator::id).containsExactly(ev).doesNotContain(pend);
    assertThat(rankingService.nationwide(null).educators())
        .extracting(ChartRankedEducator::id).doesNotContain(pend);
  }

  @Test
  void regions_directoryListsSeededRegionWithCounts() {
    tag = "D" + System.nanoTime();
    String reg = freshRegion("디렉토리도", "디렉시" + tag);
    long ev = fullDraft(reg, "디렉발행" + tag, "디렉토리도", "디렉시" + tag, 3);
    researchPendingDraft(reg, "디렉보류" + tag, "디렉토리도", "디렉시" + tag);
    publish(reg, List.of(ev));

    ChartRegionDirectory dir = rankingService.regions();
    var prov = dir.provinces().stream().filter(p -> p.province().equals("디렉토리도")).findFirst().orElseThrow();
    var dist = prov.cityDistricts().stream().filter(d -> ("디렉시" + tag).equals(d.cityDistrict())).findFirst().orElseThrow();
    assertThat(dist.educatorCount()).isEqualTo(2);
    assertThat(dist.evaluatableCount()).isEqualTo(1);
    assertThat(dist.researchPendingCount()).isEqualTo(1);
  }

  @Test
  void criteriaEndpoint_weightsMatchScoreCode() {
    var c = rankingService.criteria();
    assertThat(c.criteria().stream().mapToInt(x -> x.weight()).sum()).isEqualTo(100);
    assertThat(c.criteria()).extracting(x -> x.weight()).containsExactly(35, 20, 15, 15, 10, 5);
    assertThat(c.rankingDisclaimer()).contains("실제 수업 능력 순위가 아닙니다");
  }
}
