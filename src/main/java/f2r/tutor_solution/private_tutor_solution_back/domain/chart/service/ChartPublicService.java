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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.format.DateTimeFormatter;
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
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * 홈페이지 TOP5 와 /chart 가 공유하는 단일 소스. 가장 최근 정상 PUBLISHED 전국 누적 snapshot 하나를
   * RANKED(최대 100) / PROMISING / DISCOVERED 로 나눠 상세 Living Archive 필드까지 반환한다.
   */
  public Map<String, Object> currentPublication() {
    List<Map<String, Object>> publications = jdbcTemplate.queryForList("""
        SELECT publication_id, week_start, published_at
          FROM chart_publications
         WHERE status='PUBLISHED'
         ORDER BY published_at DESC, publication_id DESC
         LIMIT 1
        """);
    if (publications.isEmpty()) {
      Map<String, Object> legacy = new LinkedHashMap<>();
      legacy.put("publicationId", "legacy");
      legacy.put("weeklyUpdateDate", "");
      legacy.put("updatedAt", null);
      legacy.put("updatedLabel", "");
      legacy.put("entries", defaultTopEducators());
      legacy.put("rankedTop100", legacy.get("entries"));
      legacy.put("promisingEducators", List.of());
      legacy.put("discovered", List.of());
      legacy.put("disclaimer", disclaimer());
      return legacy;
    }
    Map<String, Object> publication = publications.get(0);
    long publicationId = ((Number) publication.get("publication_id")).longValue();
    List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
        SELECT pe.entry_type AS entryType, pe.chart_rank AS `rank`, pe.previous_rank AS previousRank,
               pe.short_description AS identitySummary, pe.recent_activity_summary AS recentActivity,
               c.candidate_id AS educatorId, c.name, c.role, c.academy_name AS academyName,
               c.activity_region AS activityRegion, c.activity_province AS activityProvince,
               c.activity_city_district AS activityCityDistrict,
               COALESCE(NULLIF(c.education_philosophy_summary,''), c.education_philosophy) AS educationPhilosophy,
               COALESCE(NULLIF(c.curriculum_summary,''), c.curriculum_features) AS curriculum,
               COALESCE(NULLIF(c.distinctive_teaching_summary,''), c.teaching_features) AS distinctiveTeaching,
               c.strengths_json AS strengthsJson, c.improvement_suggestions_json AS improvementSuggestionsJson,
               c.growth_advice AS growthAdvice,
               c.discovery_reason AS discoveryReason, c.profile_image_url AS profileImageUrl,
               c.official_homepage AS officialHomepage, c.official_blog AS officialBlog,
               c.official_youtube AS officialYoutube, c.official_social AS officialSocial,
               c.potential_signals_json AS potentialSignalsJson, c.legacy_source AS legacySource,
               ss.total_score AS buildItScore, ss.score_breakdown_json AS scoreBreakdown,
               ss.evidence_json AS evidenceJson
          FROM chart_publication_entries pe
          JOIN chart_candidates c ON c.candidate_id=pe.candidate_id
          JOIN chart_score_snapshots ss ON ss.score_snapshot_id=pe.score_snapshot_id
         WHERE pe.publication_id=?
         ORDER BY CASE pe.entry_type WHEN 'RANKED' THEN 0 WHEN 'PROMISING' THEN 1 ELSE 2 END,
                  COALESCE(pe.chart_rank, 2147483647), ss.total_score DESC, pe.publication_entry_id
        """, publicationId);
    for (Map<String, Object> row : rows) {
      long candidateId = ((Number) row.get("educatorId")).longValue();
      row.put("strengths", parseStringArray(row.remove("strengthsJson")));
      List<String> improvements = parseStringArray(row.remove("improvementSuggestionsJson"));
      Object growthAdvice = row.remove("growthAdvice");
      if (improvements.isEmpty() && growthAdvice != null && !String.valueOf(growthAdvice).isBlank()) {
        improvements = List.of(String.valueOf(growthAdvice));
      }
      row.put("improvementSuggestions", improvements);
      row.put("potentialSignals", parseStringArray(row.remove("potentialSignalsJson")));
      row.put("activities", jdbcTemplate.queryForList("""
          SELECT content_type AS contentType, content_summary AS contentSummary, source_url AS sourceUrl,
                 activity_date AS activityDate, evidence_kind AS evidenceKind
            FROM chart_candidate_activities WHERE candidate_id=? ORDER BY activity_date DESC, activity_id DESC
          """, candidateId));
    }
    List<Map<String, Object>> ranked = rows.stream().filter(r -> "RANKED".equals(r.get("entryType"))).toList();
    List<Map<String, Object>> promising = rows.stream().filter(r -> "PROMISING".equals(r.get("entryType"))).toList();
    List<Map<String, Object>> discovered = rows.stream()
        .filter(r -> "DISCOVERED".equals(r.get("entryType")) || "PROMISING".equals(r.get("entryType"))).toList();

    Object publishedAt = publication.get("published_at");
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("publicationId", publicationId);
    response.put("weeklyUpdateDate", publication.get("week_start"));
    response.put("updatedAt", publishedAt);
    response.put("updatedLabel", updatedLabel(publishedAt));
    response.put("entries", ranked);
    response.put("rankedTop100", ranked);
    response.put("promisingEducators", promising);
    response.put("discovered", discovered);
    response.put("rankedCount", ranked.size());
    response.put("discoveredCount", discovered.size());
    response.put("disclaimer", disclaimer());
    return response;
  }

  private List<String> parseStringArray(Object json) {
    if (json == null) return List.of();
    String text = String.valueOf(json);
    if (text.isBlank()) return List.of();
    try {
      return objectMapper.readValue(text, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    } catch (Exception e) {
      return List.of();
    }
  }

  private String updatedLabel(Object publishedAt) {
    if (publishedAt == null) return "";
    try {
      java.time.LocalDateTime dt;
      if (publishedAt instanceof java.time.LocalDateTime ldt) dt = ldt;
      else if (publishedAt instanceof java.sql.Timestamp ts) dt = ts.toLocalDateTime();
      else dt = java.time.LocalDateTime.parse(String.valueOf(publishedAt).replace(' ', 'T'));
      return "최근 업데이트 " + dt.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
    } catch (Exception e) {
      return "";
    }
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
