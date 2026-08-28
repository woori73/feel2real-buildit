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
 * BuildIt Ranking E2E — <b>publication 과 독립</b>. CI 미실행(MySQL 필요).
 *   · pool = EVALUATABLE(또는 Legacy) + 미제외 + PUBLISHABLE + 최신 Score snapshot. publication 참조 안 함.
 *   · publication 에 없는 scored 교육자도 순위에 포함되고 publication 은 그대로다.
 *   · RESEARCH_PENDING / Score 없음 / excluded=true → 순위 제외.
 *   · 정렬 total_score DESC, candidate_id ASC. 지역 grouping.
 */
@SpringBootTest
@Transactional
class ChartRankingMySqlTest {

  @Autowired ChartSiteToolService siteToolService;
  @Autowired ChartRankingService rankingService;
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

  private long fullDraft(String regionId, String name, String province, String city, int extraEvidence) {
    java.util.List<EvidenceRequest> ev = new java.util.ArrayList<>();
    ev.add(new EvidenceRequest("커리큘럼", "자체 커리큘럼과 수업 단계 설명", "커리큘럼",
        "https://example.com/" + name + "/c", LocalDate.now(), "CURRICULUM", true));
    ev.add(new EvidenceRequest("오프라인 수업", "현재 학생 지도 정규 수업", "수업",
        "https://example.com/" + name + "/class", LocalDate.now(), "OFFLINE_ACTIVITY", true));
    String[] kinds = {"TEACHING_METHOD", "MATERIAL", "STUDENT_GUIDANCE", "EDUCATIONAL_CONTENT", "SUBJECT_EXPERTISE"};
    for (int i = 0; i < extraEvidence && i < kinds.length; i++) {
      ev.add(new EvidenceRequest("근거" + i, "교육 근거 " + i, "근거",
          "https://example.com/" + name + "/e" + i, LocalDate.now().minusMonths(i + 1), kinds[i], true));
    }
    return ((Number) siteToolService.saveDraft(new DraftRequest(regionId, name, "원장", name + "영어",
        province, city, name + " 교육 정체성", PHIL, CURR, "질문식 토론 수업", "2026년 봄학기 공지",
        List.of("자체 커리큘럼 운영"), List.of("온라인 정리 보강"), null, null,
        "https://example.com/" + name, null, null, null, "공개 콘텐츠에서 개인 교육활동 확인",
        true, true, true, true, List.of(), ev)).get("candidateId")).longValue();
  }

  private long researchPendingDraft(String regionId, String name, String province, String city) {
    return ((Number) siteToolService.saveDraft(new DraftRequest(regionId, name, "원장", name + "영어",
        province, city, name + " 교육 정체성", null, null, null, null,
        List.of(), List.of(), null, null,
        "https://example.com/" + name, null, null, null, "공개 콘텐츠에서 개인 교육활동 확인",
        false, true, true, true, List.of(),
        List.of(new EvidenceRequest("글", "블로그 글", "글", "https://example.com/" + name + "/p",
            LocalDate.now(), "EDUCATIONAL_CONTENT", true)))).get("candidateId")).longValue();
  }

  private void publish(String regionId, List<Long> ids) {
    siteToolService.claimRegion(regionId);
    siteToolService.completeRegion(regionId, ids, ids.size(), null);
  }

  private boolean inCurrentPublication(long candidateId) {
    Integer n = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM chart_publication_entries pe
         JOIN chart_publications p ON p.publication_id = pe.publication_id
        WHERE p.status='PUBLISHED' AND pe.candidate_id=?
        """, Integer.class, candidateId);
    return n != null && n > 0;
  }

  private long currentPublicationId() {
    Long id = jdbcTemplate.queryForObject(
        "SELECT publication_id FROM chart_publications WHERE status='PUBLISHED' "
        + "ORDER BY published_at DESC, publication_id DESC LIMIT 1", Long.class);
    return id == null ? 0L : id;
  }

  private long publishedEducatorCount() {
    Long n = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_publication_entries WHERE publication_id=?", Long.class, currentPublicationId());
    return n == null ? 0L : n;
  }

  @Test
  void scoredEducatorNotInPublication_stillGetsRanked_publicationUnchanged() {
    tag = "np" + System.nanoTime();
    String reg = freshRegion("독립랭크도", "독립시" + tag);
    long publishedId = fullDraft(reg, "발행자" + tag, "독립랭크도", "독립시" + tag, 4);
    long auditedOnlyId = fullDraft(reg, "감사자" + tag, "독립랭크도", "독립시" + tag, 2);

    publish(reg, List.of(publishedId));
    long pubIdAfterPublish = currentPublicationId();
    long pubCountAfterPublish = publishedEducatorCount();

    siteToolService.submitReview(reg, List.of(auditedOnlyId), "감사 기록"); // Score 만, publication 무변경

    assertThat(inCurrentPublication(auditedOnlyId)).isFalse();
    ChartEducatorRanking r = rankingService.forEducator(auditedOnlyId);
    assertThat(r.ranked()).isTrue();
    assertThat(r.overallRank()).isNotNull().isPositive();
    assertThat(r.regionRank()).isNotNull().isPositive();
    assertThat(rankingService.nationwide(null).educators())
        .extracting(ChartRankedEducator::id).contains(auditedOnlyId);
    assertThat(rankingService.region("독립랭크도", "독립시" + tag).educators())
        .extracting(ChartRankedEducator::id).contains(auditedOnlyId, publishedId);

    // submitReview 는 publication 을 바꾸지 않았다
    assertThat(currentPublicationId()).isEqualTo(pubIdAfterPublish);
    assertThat(publishedEducatorCount()).isEqualTo(pubCountAfterPublish);
  }

  @Test
  void rankingPool_excludesResearchPending_noScore_andExcluded() {
    tag = "ex" + System.nanoTime();
    String reg = freshRegion("제외랭크도", "제외시" + tag);
    long scored = fullDraft(reg, "채점자" + tag, "제외랭크도", "제외시" + tag, 3);
    long pending = researchPendingDraft(reg, "보류자" + tag, "제외랭크도", "제외시" + tag);
    long noScore = fullDraft(reg, "무점수자" + tag, "제외랭크도", "제외시" + tag, 1); // EVALUATABLE, Score snapshot 없음
    publish(reg, List.of(scored));
    long excluded = fullDraft(reg, "제외자" + tag, "제외랭크도", "제외시" + tag, 3);
    siteToolService.submitReview(reg, List.of(excluded), "감사");
    jdbcTemplate.update("UPDATE chart_candidates SET excluded_from_chart=1 WHERE candidate_id=?", excluded);

    List<Long> ranked = rankingService.region("제외랭크도", "제외시" + tag).educators()
        .stream().map(ChartRankedEducator::id).toList();
    assertThat(ranked).contains(scored).doesNotContain(pending, noScore, excluded);

    assertThat(rankingService.forEducator(pending).ranked()).isFalse();
    assertThat(rankingService.forEducator(noScore).ranked()).isFalse();
    assertThat(rankingService.forEducator(excluded).ranked()).isFalse();
  }

  @Test
  void ranking_ordersByScoreThenCandidateId_regionGrouping() {
    tag = "ord" + System.nanoTime();
    String regA = freshRegion("정렬도", "정렬시A" + tag);
    String regB = freshRegion("정렬도", "정렬시B" + tag);
    long a1 = fullDraft(regA, "가1" + tag, "정렬도", "정렬시A" + tag, 5);
    long a2 = fullDraft(regA, "가2" + tag, "정렬도", "정렬시A" + tag, 5); // 동점 예상 → candidate_id ASC
    long b1 = fullDraft(regB, "나1" + tag, "정렬도", "정렬시B" + tag, 1);
    publish(regA, List.of(a1, a2));
    publish(regB, List.of(b1));

    ChartRankingList regionA = rankingService.region("정렬도", "정렬시A" + tag);
    assertThat(regionA.educators()).extracting(ChartRankedEducator::id).containsExactly(a1, a2);
    assertThat(regionA.educators().get(0).regionRank()).isEqualTo(1);
    assertThat(regionA.educators().get(1).regionRank()).isEqualTo(2);

    ChartRankingList regionB = rankingService.region("정렬도", "정렬시B" + tag);
    assertThat(regionB.educators()).extracting(ChartRankedEducator::id).containsExactly(b1);

    assertThat(rankingService.region("정렬도", null).educators())
        .extracting(ChartRankedEducator::id).contains(a1, a2, b1);

    if (rankingService.forEducator(a1).buildItScore().equals(rankingService.forEducator(a2).buildItScore())) {
      assertThat(rankingService.forEducator(a1).overallRank())
          .isLessThan(rankingService.forEducator(a2).overallRank());
    }
  }

  @Test
  void ranking_usesLatestScoreSnapshot() {
    tag = "lt" + System.nanoTime();
    String reg = freshRegion("최신도", "최신시" + tag);
    long id = fullDraft(reg, "최신자" + tag, "최신도", "최신시" + tag, 3);
    publish(reg, List.of(id));
    int first = rankingService.forEducator(id).buildItScore();

    // 과거 시각의 낮은 점수 snapshot → 최신이 아니므로 무시돼야 한다
    jdbcTemplate.update("""
        INSERT INTO chart_score_snapshots
        (candidate_id, review_batch_id, criteria_version, total_score, score_breakdown_json, evidence_json,
         calculated_at, review_status, created_at, updated_at)
        VALUES (?, NULL, 'buildit-chart-v1', 1, '{}', '{}', DATE_SUB(NOW(6), INTERVAL 10 DAY), 'AUDIT_RECORDED', NOW(6), NOW(6))
        """, id);
    assertThat(rankingService.forEducator(id).buildItScore()).isEqualTo(first);
  }

  @Test
  void regions_directoryListsSeededRegionWithCounts() {
    tag = "d" + System.nanoTime();
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
