package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService.DraftRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService.EvidenceRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch 안전 복구(inspect_chart_review_batch / revert_chart_review_batch) 회귀.
 *
 * <p>확인 흐름: inspect → revert → (서버 내부 rebuild) → inspect.
 * <ul>
 *   <li>잘못된 batch 의 score snapshot / publication entry 만 제거된다.</li>
 *   <li>이 batch 로만 유입된 educator 는 publication 에서 빠진다.</li>
 *   <li>다른 정상 batch snapshot 이 남아 있는 educator 는 publication 에 유지된다.</li>
 *   <li>candidate 행과 activity 는 삭제하지 않는다.</li>
 *   <li>지역은 RESEARCHED 로 유지되고 batch 는 REVERTED 가 된다.</li>
 *   <li>이미 REVERTED 인 batch 재실행은 아무 것도 바꾸지 않는다(idempotent).</li>
 * </ul>
 */
@SpringBootTest
@Transactional
class ChartRevertReviewBatchMySqlTest {

  @Autowired ChartSiteToolService siteToolService;
  @Autowired JdbcTemplate jdbcTemplate;

  private static final String PHILOSOPHY =
      "학생이 스스로 질문을 만들며 원리를 이해하도록 이끌고, 틀린 문제는 사고 과정을 다시 설계하는 재료로 삼는다.";
  private static final String CURRICULUM =
      "진단 평가로 취약 지점을 찾은 뒤 개념 강의를 하고, 학교별 기출 분석과 변형 문제로 적용한 다음 오답 노트를 반복한다.";

  private String freshRegion(String suffix) {
    String id = "test-revert-" + suffix + "-" + System.nanoTime();
    jdbcTemplate.update("""
        INSERT INTO chart_regions (region_id, province, city_district, status, created_at, updated_at)
        VALUES (?, '테스트도', ?, 'UNRESEARCHED', NOW(6), NOW(6))
        """, id, "테스트시 " + suffix);
    return id;
  }

  private long researchOne(String regionId, String name, String academy) {
    Map<String, Object> saved = siteToolService.saveDraft(new DraftRequest(regionId, name, "원장", academy,
        "테스트도", "테스트시", name + " 교육 정체성", PHILOSOPHY, CURRICULUM, "질문식 토론 수업",
        "2026년 봄학기 신규반 개강 공지", List.of("자체 커리큘럼 운영", "수업 콘텐츠 지속 공개"),
        List.of("대표 커리큘럼을 온라인에 더 명확하게 정리"), null, null,
        "https://example.com/" + name, null, null, null, "공개 콘텐츠에서 개인 교육활동과 커리큘럼 확인",
        true, true, true, true, List.of(),
        List.of(
            new EvidenceRequest("커리큘럼 소개", "자체 독해 커리큘럼과 수업 단계 설명", "커리큘럼", "https://example.com/" + name + "/curriculum",
                LocalDate.now(), "CURRICULUM", true),
            new EvidenceRequest("오프라인 수업", "현재 학생을 지도하는 정규 수업 일정", "수업", "https://example.com/" + name + "/class",
                LocalDate.now(), "OFFLINE_ACTIVITY", true))));
    return ((Number) saved.get("candidateId")).longValue();
  }

  private long completeWithSelection(String regionId, long candidateId) {
    siteToolService.claimRegion(regionId);
    Map<String, Object> res = siteToolService.completeRegion(regionId, List.of(candidateId), 1, null);
    return ((Number) res.get("reviewBatchId")).longValue();
  }

  private boolean isPublished(long candidateId) {
    Integer n = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM chart_publication_entries pe
          JOIN chart_publications p ON p.publication_id = pe.publication_id
         WHERE p.status = 'PUBLISHED' AND pe.candidate_id = ?
        """, Integer.class, candidateId);
    return n != null && n > 0;
  }

  @Test
  void revert_removesOnlyThatBatchAndDropsCandidateIntroducedOnlyByIt() {
    // given: 정상 지역 A(유지되어야 함) + 잘못 자동발행된 지역 X
    String regionA = freshRegion("keep");
    long a1 = researchOne(regionA, "가유지", "가유지학원");
    completeWithSelection(regionA, a1);

    String regionX = freshRegion("bad");
    long x1 = researchOne(regionX, "엑스유입", "엑스학원");
    long badBatch = completeWithSelection(regionX, x1);

    assertThat(isPublished(a1)).isTrue();
    assertThat(isPublished(x1)).isTrue();

    // inspect
    Map<String, Object> before = siteToolService.inspectReviewBatch(badBatch);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> snaps = (List<Map<String, Object>>) before.get("scoreSnapshots");
    assertThat(snaps).extracting(s -> ((Number) s.get("candidateId")).longValue()).containsExactly(x1);

    // when: revert
    Map<String, Object> result = siteToolService.revertReviewBatch(badBatch);

    // then
    assertThat(result).containsEntry("batchStatus", "REVERTED").containsEntry("alreadyReverted", false);
    assertThat(((Number) result.get("removedScoreSnapshots")).intValue()).isEqualTo(1);
    assertThat(((Number) result.get("candidatesKeptElsewhere")).intValue()).isZero();

    assertThat(isPublished(a1)).as("정상 지역 A 선정자는 유지").isTrue();
    assertThat(isPublished(x1)).as("이 batch 로만 유입된 educator 는 빠짐").isFalse();

    // candidate / activity 보존
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_candidates WHERE candidate_id=?", Integer.class, x1)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_candidate_activities WHERE candidate_id=?", Integer.class, x1)).isGreaterThan(0);
    // batch snapshot 은 사라짐
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_score_snapshots WHERE review_batch_id=?", Integer.class, badBatch)).isZero();
    // batch REVERTED, 지역 RESEARCHED 유지
    assertThat(jdbcTemplate.queryForObject("SELECT status FROM chart_review_batches WHERE review_batch_id=?", String.class, badBatch)).isEqualTo("REVERTED");
    assertThat(jdbcTemplate.queryForObject("SELECT status FROM chart_regions WHERE region_id=?", String.class, regionX)).isEqualTo("RESEARCHED");

    // inspect 재확인: snapshot/entry 없음
    Map<String, Object> after = siteToolService.inspectReviewBatch(badBatch);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> afterSnaps = (List<Map<String, Object>>) after.get("scoreSnapshots");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> afterEntries = (List<Map<String, Object>>) after.get("publicationEntries");
    assertThat(afterSnaps).isEmpty();
    assertThat(afterEntries).isEmpty();
  }

  @Test
  void revert_keepsCandidateThatStillHasAnotherValidBatchSnapshot() {
    // given: educator 가 정상 batch 로 이미 게시된 상태
    String regionA = freshRegion("dual");
    long e1 = researchOne(regionA, "중복스냅", "중복스냅학원");
    long goodBatch = completeWithSelection(regionA, e1);

    long currentPub = jdbcTemplate.queryForObject(
        "SELECT publication_id FROM chart_publications WHERE status='PUBLISHED' ORDER BY published_at DESC, publication_id DESC LIMIT 1", Long.class);

    // 사고 재현: 다른 batch(badBatch)가 같은 educator 에 대해 별도 snapshot 을 만들고
    //           현재 publication entry 가 그 snapshot 을 가리키도록 오염됨.
    jdbcTemplate.update("""
        INSERT INTO chart_review_batches (region_id, status, criteria_version, summary, submitted_at, reviewed_at, created_at, updated_at)
        VALUES (?, 'AUTO_PUBLISHED', 'buildit-chart-v1', '사고 재현용', NOW(6), NOW(6), NOW(6), NOW(6))
        """, regionA);
    long badBatch = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbcTemplate.update("""
        INSERT INTO chart_score_snapshots
          (candidate_id, review_batch_id, criteria_version, total_score, score_breakdown_json, evidence_json,
           calculated_at, review_status, created_at, updated_at)
        VALUES (?, ?, 'buildit-chart-v1', 40, '{}', '{}', NOW(6), 'AUTO_PUBLISHED', NOW(6), NOW(6))
        """, e1, badBatch);
    long badSnap = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbcTemplate.update("UPDATE chart_publication_entries SET score_snapshot_id=? WHERE publication_id=? AND candidate_id=?",
        badSnap, currentPub, e1);

    assertThat(isPublished(e1)).isTrue();

    // when: 잘못된 badBatch 를 revert
    Map<String, Object> result = siteToolService.revertReviewBatch(badBatch);

    // then: e1 은 정상 goodBatch snapshot 으로 여전히 게시됨
    assertThat(((Number) result.get("candidatesKeptElsewhere")).intValue()).isEqualTo(1);
    assertThat(isPublished(e1)).as("다른 정상 snapshot 이 있는 educator 는 유지").isTrue();
    // 정상 snapshot 은 그대로, 오염 snapshot 만 삭제
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_score_snapshots WHERE review_batch_id=?", Integer.class, goodBatch)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_score_snapshots WHERE score_snapshot_id=?", Integer.class, badSnap)).isZero();
    // 현재 publication entry 는 정상 snapshot 을 가리킴
    Long liveSnap = jdbcTemplate.queryForObject("""
        SELECT pe.score_snapshot_id FROM chart_publication_entries pe
          JOIN chart_publications p ON p.publication_id = pe.publication_id
         WHERE p.status='PUBLISHED' AND pe.candidate_id=?
        """, Long.class, e1);
    Long goodSnap = jdbcTemplate.queryForObject("SELECT score_snapshot_id FROM chart_score_snapshots WHERE review_batch_id=?", Long.class, goodBatch);
    assertThat(liveSnap).isEqualTo(goodSnap);
  }

  @Test
  void revert_isIdempotent_whenBatchAlreadyReverted() {
    String region = freshRegion("idem");
    long c1 = researchOne(region, "멱등", "멱등학원");
    long batch = completeWithSelection(region, c1);

    Map<String, Object> first = siteToolService.revertReviewBatch(batch);
    assertThat(first).containsEntry("alreadyReverted", false);
    long pubAfterFirst = ((Number) first.get("finalPublicationId")).longValue();

    Map<String, Object> second = siteToolService.revertReviewBatch(batch);
    assertThat(second).containsEntry("alreadyReverted", true);
    assertThat(((Number) second.get("removedScoreSnapshots")).intValue()).isZero();
    assertThat(((Number) second.get("finalPublicationId")).longValue())
        .as("두 번째 호출은 새 publication 을 만들지 않음").isEqualTo(pubAfterFirst);
    assertThat(jdbcTemplate.queryForObject("SELECT status FROM chart_regions WHERE region_id=?", String.class, region))
        .isEqualTo("RESEARCHED");
  }
}
