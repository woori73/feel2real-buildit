package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartActivityResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartEducatorResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartCandidate;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartCandidateActivity;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartDiscoveryZone;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartEducator;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository.ChartCandidateActivityRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository.ChartCandidateRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository.ChartEducatorRepository;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChartPublicService {

  private final ChartEducatorRepository educatorRepository;
  private final ChartCandidateRepository candidateRepository;
  private final ChartCandidateActivityRepository activityRepository;
  private final JdbcTemplate jdbcTemplate;

  public Map<String, Object> currentPublication() {
    List<Map<String, Object>> publications = jdbcTemplate.queryForList("""
        SELECT publication_id, week_start, published_at
          FROM chart_publications
         WHERE status='PUBLISHED'
         ORDER BY published_at DESC
         LIMIT 1
        """);
    if (publications.isEmpty()) {
      return Map.of("publicationId", "legacy", "weeklyUpdateDate", "", "entries", defaultTopEducators(),
          "promisingEducators", List.of(), "disclaimer", disclaimer());
    }
    Map<String, Object> publication = publications.get(0);
    long publicationId = ((Number) publication.get("publication_id")).longValue();
    List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
        SELECT pe.entry_type AS entryType, pe.chart_rank AS `rank`, pe.previous_rank AS previousRank,
               pe.short_description AS identitySummary, pe.recent_activity_summary AS recentActivity,
               c.candidate_id AS educatorId, c.name, c.role, c.academy_name AS academyName,
               c.activity_region AS activityRegion, c.education_philosophy AS educationPhilosophy,
               c.curriculum_features AS curriculumFeatures, c.teaching_features AS teachingFeatures,
               c.growth_advice AS growthAdvice, c.official_homepage AS officialHomepage,
               c.official_blog AS officialBlog, c.official_youtube AS officialYoutube,
               c.official_social AS officialSocial, ss.total_score AS buildItScore,
               ss.score_breakdown_json AS scoreBreakdown
          FROM chart_publication_entries pe
          JOIN chart_candidates c ON c.candidate_id=pe.candidate_id
          JOIN chart_score_snapshots ss ON ss.score_snapshot_id=pe.score_snapshot_id
         WHERE pe.publication_id=?
         ORDER BY CASE pe.entry_type WHEN 'RANKED' THEN 0 ELSE 1 END, pe.chart_rank, pe.publication_entry_id
        """, publicationId);
    for (Map<String, Object> row : rows) {
      long candidateId = ((Number) row.get("educatorId")).longValue();
      row.put("activities", jdbcTemplate.queryForList("""
          SELECT content_type AS contentType, content_summary AS contentSummary, source_url AS sourceUrl,
                 activity_date AS activityDate, evidence_kind AS evidenceKind
            FROM chart_candidate_activities WHERE candidate_id=? ORDER BY activity_date DESC, activity_id DESC
          """, candidateId));
    }
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("publicationId", publicationId);
    response.put("weeklyUpdateDate", publication.get("week_start"));
    response.put("entries", rows.stream().filter(row -> "RANKED".equals(row.get("entryType"))).toList());
    response.put("promisingEducators", rows.stream().filter(row -> "PROMISING".equals(row.get("entryType"))).toList());
    response.put("disclaimer", disclaimer());
    return response;
  }

  private String disclaimer() {
    return "BuildIt Score는 공개적으로 확인 가능한 교육 활동과 성장 신호를 BuildIt 기준으로 기록한 지표이며, 교육자의 인격·절대적 능력·학생 성과를 의미하지 않습니다.";
  }

  public List<ChartEducatorResponse> topEducators(String zoneId) {
    ChartDiscoveryZone.fromId(zoneId);
    List<ChartEducator> educators = educatorRepository.findByZoneId(zoneId, Sort.by(Sort.Direction.DESC, "approvedAt"));
    int limit = Math.min(100, educators.size());
    return java.util.stream.IntStream.range(0, limit)
        .mapToObj(index -> toResponse(index + 1, educators.get(index)))
        .toList();
  }

  public List<ChartEducatorResponse> defaultTopEducators() {
    return topEducators(ChartDiscoveryZone.APGUJEONG_SINSA.getId());
  }

  private ChartEducatorResponse toResponse(int rank, ChartEducator educator) {
    List<ChartActivityResponse> activities = candidateRepository.findById(educator.getApprovedFromCandidateId())
        .map(this::activitiesFor)
        .orElse(List.of());
    return ChartEducatorResponse.of(rank, educator, activities);
  }

  private List<ChartActivityResponse> activitiesFor(ChartCandidate candidate) {
    List<ChartCandidateActivity> activities = activityRepository.findByCandidate_CandidateIdOrderByActivityIdAsc(candidate.getCandidateId());
    return activities.stream().map(ChartActivityResponse::from).toList();
  }
}
