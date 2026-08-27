package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ChartSiteToolService {

  public static final String CRITERIA_VERSION = "buildit-chart-v1";
  private static final Set<String> EDUCATOR_ROLES = Set.of("원장", "대표", "강사", "선생님");
  private static final Set<String> INFORMATIVE_KINDS = Set.of(
      "PHILOSOPHY", "CURRICULUM", "TEACHING_METHOD", "MATERIAL", "STUDENT_GUIDANCE",
      "SUBJECT_EXPERTISE", "EDUCATIONAL_CONTENT", "OFFLINE_ACTIVITY");
  private static final Set<String> PROMOTIONAL_KINDS = Set.of("ADVERTISEMENT", "PRESS_RELEASE", "RECRUITMENT");

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final Clock clock = Clock.systemDefaultZone();

  public ChartSiteToolService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> context() {
    Map<String, Integer> weights = new LinkedHashMap<>();
    weights.put("교육자 고유성", 35);
    weights.put("수업·교육 실천", 20);
    weights.put("교육 콘텐츠 지속성", 15);
    weights.put("전문성", 15);
    weights.put("검증 근거", 10);
    weights.put("지역 활동", 5);
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("criteriaVersion", CRITERIA_VERSION);
    context.put("mission", "누가 가장 유명한가가 아니라, 우리가 아직 발견하지 못한 좋은 지역 교육자는 누구인가를 찾는 Living Archive");
    context.put("subject", "학원이 아닌 원장·강사 개인");
    context.put("hardGate", List.of("현재 오프라인 교육 활동", "현재 연도 활동", "교육자 고유성", "제외대상 아님"));
    context.put("excluded", List.of("온라인 강의 전업", "과외 전업", "대형·체인 유명세만 확인", "광고 외 개인 교육활동 없음", "현재 활동 미확인"));
    context.put("scoreWeights", weights);
    context.put("nonSignals", List.of("검색 결과 수", "광고량", "학원 규모", "지점 수", "매출", "유명세", "BuildIt 가입 여부"));
    context.put("searchAngles", List.of("원장 수업", "강사 수업", "자체 교재", "커리큘럼", "수업 연구", "교육 철학", "특강", "학생 지도", "교육 콘텐츠"));
    context.put("searchPolicy", List.of("첫 검색 결과에서 멈추지 않기", "검색 노출이 적다는 이유로 제외하지 않기", "후보 수를 채우기 위해 Hard Gate를 낮추지 않기"));
    context.put("privacy", "시/도와 시·군·구까지만 저장하며 전화·이메일·개인주소는 수집하지 않습니다.");
    context.put("disclaimer", "BuildIt Score는 공개 교육 활동과 브랜드 성장 신호를 기준으로 한 자체 지표이며 인격, 전반적 능력 또는 학생 성과를 단정하지 않습니다.");
    return context;
  }

  @Transactional
  public Map<String, Object> claimRegion(String regionId) {
    LocalDateTime now = LocalDateTime.now(clock);
    int updated = jdbcTemplate.update("UPDATE chart_regions SET status='IN_PROGRESS', updated_at=? WHERE region_id=? AND status IN ('UNRESEARCHED','DUE','ERROR')", now, require(regionId, "regionId"));
    if (updated == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "조사 가능한 지역이 아니거나 다른 작업이 진행 중입니다.");
    return Map.of("regionId", regionId, "status", "IN_PROGRESS", "claimedAt", now);
  }

  @Transactional
  public Map<String, Object> completeRegion(String regionId, int eligibleCount, int rejectedCount, String note) {
    LocalDateTime now = LocalDateTime.now(clock);
    int updated = jdbcTemplate.update("UPDATE chart_regions SET status='RESEARCHED', last_researched_at=?, next_research_at=?, last_error=NULL, updated_at=? WHERE region_id=? AND status='IN_PROGRESS'", now, now.plusWeeks(1), now, require(regionId, "regionId"));
    if (updated == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "IN_PROGRESS 지역만 완료할 수 있습니다.");
    return Map.of("regionId", regionId, "status", "RESEARCHED", "eligibleCount", Math.max(0, eligibleCount),
        "rejectedCount", Math.max(0, rejectedCount), "note", blank(note) == null ? "" : note, "nextResearchAt", now.plusWeeks(1));
  }

  @Scheduled(cron = "${chart.weekly-refresh-cron:0 10 3 * * MON}", zone = "Asia/Seoul")
  @Transactional
  public void markWeeklyResearchDue() {
    LocalDateTime now = LocalDateTime.now(clock);
    jdbcTemplate.update("UPDATE chart_regions SET status='DUE', updated_at=? WHERE status='RESEARCHED' AND next_research_at <= ?", now, now);
  }

  public List<Map<String, Object>> nextRegions(int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 10));
    return jdbcTemplate.queryForList("""
        SELECT region_id AS regionId, province, city_district AS cityDistrict, status,
               last_researched_at AS lastResearchedAt, next_research_at AS nextResearchAt
          FROM chart_regions
         WHERE status IN ('UNRESEARCHED', 'DUE', 'ERROR')
           AND (next_research_at IS NULL OR next_research_at <= NOW(6))
         ORDER BY CASE status WHEN 'DUE' THEN 0 WHEN 'UNRESEARCHED' THEN 1 ELSE 2 END, region_id
         LIMIT ?
        """, safeLimit);
  }

  public List<Map<String, Object>> pendingReviewBatches() {
    return jdbcTemplate.queryForList("""
        SELECT review_batch_id AS reviewBatchId, region_id AS regionId, status,
               criteria_version AS criteriaVersion, summary, submitted_at AS submittedAt
          FROM chart_review_batches
         WHERE status = 'PENDING_ADMIN_REVIEW'
         ORDER BY review_batch_id DESC
        """);
  }

  public List<Map<String, Object>> findExisting(String name, String academyName, String province, String cityDistrict) {
    String normalizedName = require(name, "name").replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    String academy = academyName == null ? "" : academyName.trim();
    return jdbcTemplate.queryForList("""
        SELECT 'EDUCATOR' AS recordType, educator_id AS id, name, academy_name AS academyName,
               activity_region AS activityRegion, person_key AS personKey
          FROM chart_educators
         WHERE REPLACE(LOWER(name), ' ', '') = ?
            OR (? <> '' AND LOWER(academy_name) = LOWER(?))
        UNION ALL
        SELECT 'CANDIDATE', candidate_id, name, academy_name, activity_region, person_key
          FROM chart_candidates
         WHERE REPLACE(LOWER(name), ' ', '') = ?
            OR (? <> '' AND LOWER(academy_name) = LOWER(?))
        """, normalizedName, academy, academy, normalizedName, academy, academy);
  }

  @Transactional
  public Map<String, Object> saveDraft(DraftRequest request) {
    Eligibility eligibility = evaluate(request);
    if (!eligibility.eligible()) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "BuildIt Chart Hard Gate 탈락: " + String.join(", ", eligibility.reasons()));
    }
    EvidenceRequest primary = request.evidence().get(0);
    String personKey = normalize(request.name()) + "|" + normalize(request.academyName()) + "|"
        + normalize(request.province()) + "|" + normalize(request.cityDistrict());
    String region = require(request.province(), "province") + " " + require(request.cityDistrict(), "cityDistrict");
    String sourceUrl = validPublicUrl(primary.sourceUrl());
    LocalDateTime now = LocalDateTime.now(clock);
    try {
      jdbcTemplate.update("""
          INSERT INTO chart_candidates
          (zone_id, person_key, name, role, academy_name, activity_region, activity_province,
           activity_city_district, content_type, content_summary, identity_summary, education_philosophy,
           curriculum_features, teaching_features, growth_advice, official_homepage, official_blog,
           official_youtube, official_social, offline_activity_confirmed, current_year_activity_confirmed,
           educator_identity_confirmed, exclusion_free, eligibility_status, eligibility_reasons_json,
           potential_signals_json, research_source, criteria_version, source_url, source_url_hash,
           source_title, discovery_reason, deep_search_result, exclusion_check, verification_status,
           status, google_query, discovered_at, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, b'1', b'1', b'1', b'1',
                  'ELIGIBLE', ?, ?, 'CHATGPT_SITE_TOOL', ?, ?, ?, ?, ?, '', ?, 'VERIFIED', 'DISCOVERED',
                  'ChatGPT Site tool research draft', ?, ?, ?)
          """, request.regionId(), personKey, require(request.name(), "name"), validatedRole(request.role()),
          blank(request.academyName()), region, request.province(), request.cityDistrict(),
          require(primary.contentType(), "evidence.contentType"), require(primary.summary(), "evidence.summary"),
          require(request.identitySummary(), "identitySummary"), blank(request.educationPhilosophy()),
          blank(request.curriculumFeatures()), blank(request.teachingFeatures()), blank(request.growthAdvice()),
          nullableUrl(request.officialHomepage()), nullableUrl(request.officialBlog()),
          nullableUrl(request.officialYoutube()), nullableUrl(request.officialSocial()), json(eligibility.reasons()),
          json(request.potentialSignals() == null ? List.of() : request.potentialSignals()), CRITERIA_VERSION,
          sourceUrl, sha256(sourceUrl), blank(primary.title()), require(request.discoveryReason(), "discoveryReason"),
          "Hard Gate 통과: " + String.join(", ", eligibility.reasons()), now, now, now);
    } catch (DuplicateKeyException error) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "동일 교육자와 근거 URL 후보가 이미 존재합니다.");
    }
    Long candidateId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    for (EvidenceRequest evidence : request.evidence()) {
      String url = validPublicUrl(evidence.sourceUrl());
      jdbcTemplate.update("""
          INSERT IGNORE INTO chart_candidate_activities
          (candidate_id, content_type, content_summary, activity_date, evidence_kind,
           educationally_informative, researched_at, source_url, discovered_at, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, candidateId, require(evidence.contentType(), "evidence.contentType"),
          require(evidence.summary(), "evidence.summary"), evidence.activityDate(),
          normalizedKind(evidence.evidenceKind()), evidence.educationallyInformative(), now, url, now, now, now);
    }
    return Map.of("candidateId", candidateId, "status", "DISCOVERED", "eligibility", eligibility,
        "criteriaVersion", CRITERIA_VERSION, "published", false);
  }

  public Map<String, Object> previewScore(long candidateId) {
    Map<String, Object> candidate = candidate(candidateId);
    if (!"ELIGIBLE".equals(candidate.get("eligibility_status"))) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Hard Gate 통과 후보만 Score를 계산합니다.");
    }
    List<Map<String, Object>> evidence = jdbcTemplate.queryForList(
        "SELECT evidence_kind, activity_date, source_url, educationally_informative FROM chart_candidate_activities WHERE candidate_id=?", candidateId);
    Map<String, Integer> breakdown = score(candidate, evidence);
    int total = breakdown.values().stream().mapToInt(Integer::intValue).sum();
    List<String> potential = parseStrings((String) candidate.get("potential_signals_json"));
    return Map.of("candidateId", candidateId, "criteriaVersion", CRITERIA_VERSION, "totalScore", total,
        "breakdown", breakdown, "evidenceCount", evidence.size(), "calculatedAt", LocalDateTime.now(clock),
        "potentialSignals", potential, "promisingCandidate", !potential.isEmpty(), "published", false);
  }

  @Transactional
  public Map<String, Object> submitReview(String regionId, List<Long> candidateIds, String summary) {
    if (candidateIds == null || candidateIds.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "candidateIds가 필요합니다.");
    }
    LocalDateTime now = LocalDateTime.now(clock);
    jdbcTemplate.update("INSERT INTO chart_review_batches (region_id, status, criteria_version, summary, submitted_at, created_at, updated_at) VALUES (?, 'PENDING_ADMIN_REVIEW', ?, ?, ?, ?, ?)",
        require(regionId, "regionId"), CRITERIA_VERSION, require(summary, "summary"), now, now, now);
    Long batchId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    List<Map<String, Object>> previews = candidateIds.stream().map(this::previewScore)
        .sorted((a, b) -> Integer.compare((Integer) b.get("totalScore"), (Integer) a.get("totalScore"))).toList();
    int rank = 1;
    for (Map<String, Object> preview : previews) {
      jdbcTemplate.update("""
          INSERT INTO chart_score_snapshots
          (candidate_id, review_batch_id, criteria_version, total_score, score_breakdown_json, evidence_json,
           calculated_at, proposed_rank, review_status, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_ADMIN_REVIEW', ?, ?)
          """, preview.get("candidateId"), batchId, CRITERIA_VERSION, preview.get("totalScore"),
          json(preview.get("breakdown")), json(Map.of("evidenceCount", preview.get("evidenceCount"))),
          now, rank++, now, now);
    }
    return Map.of("reviewBatchId", batchId, "status", "PENDING_ADMIN_REVIEW", "proposals", previews,
        "publicationChanged", false);
  }

  public Map<String, Object> publicationDisabled() {
    throw new ResponseStatusException(HttpStatus.CONFLICT,
        "1차 검증 범위에서는 publication이 잠겨 있습니다. 관리자 승인 후 2차 범위에서 활성화하세요.");
  }

  @Transactional
  public Map<String, Object> reviewDecision(long reviewBatchId, String decision, String note) {
    String normalized = require(decision, "decision").toUpperCase(Locale.ROOT);
    if (!Set.of("APPROVED", "REJECTED").contains(normalized)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "APPROVED 또는 REJECTED만 허용됩니다.");
    }
    LocalDateTime now = LocalDateTime.now(clock);
    int updated = jdbcTemplate.update("UPDATE chart_review_batches SET status=?, reviewed_at=?, review_note=?, updated_at=? WHERE review_batch_id=? AND status='PENDING_ADMIN_REVIEW'", normalized, now, blank(note), now, reviewBatchId);
    if (updated == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "검토 대기 중인 배치가 아닙니다.");
    jdbcTemplate.update("UPDATE chart_score_snapshots SET review_status=?, updated_at=? WHERE review_status='PENDING_ADMIN_REVIEW' AND review_batch_id=?", normalized, now, reviewBatchId);
    return Map.of("reviewBatchId", reviewBatchId, "status", normalized, "publicationChanged", false);
  }

  @Transactional
  public Map<String, Object> requestWebDraft(long candidateId, LocalDateTime educatorConsentAt) {
    if (educatorConsentAt == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "교육자 동의 시각이 필요합니다.");
    Map<String, Object> row = candidate(candidateId);
    Map<String, Object> payload = new LinkedHashMap<>();
    for (String key : List.of("name", "activity_province", "activity_city_district", "identity_summary", "education_philosophy", "curriculum_features", "teaching_features", "growth_advice", "official_homepage", "official_blog", "official_youtube", "official_social")) payload.put(key, row.get(key));
    LocalDateTime now = LocalDateTime.now(clock);
    jdbcTemplate.update("INSERT INTO chart_web_draft_requests (candidate_id, educator_consent_at, status, payload_json, created_at, updated_at) VALUES (?, ?, 'REQUESTED', ?, ?, ?)", candidateId, educatorConsentAt, json(payload), now, now);
    return Map.of("candidateId", candidateId, "status", "REQUESTED", "consentRecorded", true, "scoreBoost", false);
  }

  Eligibility evaluate(DraftRequest request) {
    List<String> reasons = new ArrayList<>();
    if (!request.offlineActivityConfirmed()) reasons.add("현재 오프라인 교육 활동 미확인");
    if (!request.currentYearActivityConfirmed()) reasons.add("현재 연도 활동 미확인");
    if (!request.educatorIdentityConfirmed()) reasons.add("교육자 고유성 미확인");
    if (!request.exclusionFree()) reasons.add("제외대상 신호 존재");
    if (request.evidence() == null || request.evidence().isEmpty()) reasons.add("실제 근거 URL 없음");
    else {
      boolean current = request.evidence().stream().anyMatch(e -> e.activityDate() != null && e.activityDate().getYear() == LocalDate.now(clock).getYear());
      boolean offline = request.evidence().stream().anyMatch(e -> "OFFLINE_ACTIVITY".equals(normalizedKind(e.evidenceKind())));
      boolean informative = request.evidence().stream().anyMatch(e -> e.educationallyInformative()
          && INFORMATIVE_KINDS.contains(normalizedKind(e.evidenceKind())));
      boolean allPromotional = request.evidence().stream().allMatch(e -> PROMOTIONAL_KINDS.contains(normalizedKind(e.evidenceKind())));
      if (!current) reasons.add("현재 연도 날짜가 있는 근거 없음");
      if (!offline) reasons.add("오프라인 교육 활동 URL 근거 없음");
      if (!informative) reasons.add("철학·커리큘럼·수업 등 교육적 정보 근거 없음");
      if (allPromotional) reasons.add("광고·보도자료만 존재");
    }
    if (reasons.isEmpty()) return new Eligibility(true, List.of("오프라인 활동 확인", "현재 연도 활동 확인", "교육자 고유성 확인", "제외대상 아님"));
    return new Eligibility(false, reasons);
  }

  Map<String, Integer> score(Map<String, Object> candidate, List<Map<String, Object>> evidence) {
    long unique = evidence.stream().map(e -> e.get("source_url")).distinct().count();
    long months = evidence.stream().map(e -> e.get("activity_date")).filter(v -> v != null)
        .map(Object::toString).map(v -> v.substring(0, 7)).distinct().count();
    Set<String> kinds = evidence.stream().map(e -> String.valueOf(e.get("evidence_kind"))).collect(java.util.stream.Collectors.toSet());
    int identity = Math.min(35, nonBlank(candidate, "identity_summary", "education_philosophy", "curriculum_features", "teaching_features") * 10);
    int teaching = Math.min(20, countKinds(kinds, "TEACHING_METHOD", "STUDENT_GUIDANCE", "OFFLINE_ACTIVITY") * 7);
    int persistence = (int) Math.min(15, months * 5);
    int professionalism = Math.min(15, countKinds(kinds, "MATERIAL", "SUBJECT_EXPERTISE", "EDUCATIONAL_CONTENT") * 5);
    int verification = (int) Math.min(10, unique * 2);
    int regional = kinds.contains("OFFLINE_ACTIVITY") ? 5 : 0;
    Map<String, Integer> result = new LinkedHashMap<>();
    result.put("교육자 고유성", identity);
    result.put("수업·교육 실천", teaching);
    result.put("교육 콘텐츠 지속성", persistence);
    result.put("전문성", professionalism);
    result.put("검증 근거", verification);
    result.put("지역 활동", regional);
    return result;
  }

  private Map<String, Object> candidate(long id) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM chart_candidates WHERE candidate_id=?", id);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "후보를 찾을 수 없습니다.");
    return rows.get(0);
  }

  private int nonBlank(Map<String, Object> row, String... keys) {
    int count = 0;
    for (String key : keys) if (row.get(key) != null && !row.get(key).toString().isBlank()) count++;
    return count;
  }

  private int countKinds(Set<String> values, String... kinds) {
    int count = 0;
    for (String kind : kinds) if (values.contains(kind)) count++;
    return count;
  }

  private String validatedRole(String value) {
    String role = require(value, "role");
    if (!EDUCATOR_ROLES.contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "원장·대표·강사·선생님만 대상입니다.");
    return role;
  }

  private String validPublicUrl(String value) {
    try {
      URI uri = URI.create(require(value, "sourceUrl"));
      if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) throw new IllegalArgumentException();
      return uri.toString();
    } catch (IllegalArgumentException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "실제 http(s) 근거 URL이 필요합니다.");
    }
  }

  private String nullableUrl(String value) { return value == null || value.isBlank() ? null : validPublicUrl(value); }
  private String normalizedKind(String value) { return require(value, "evidenceKind").trim().toUpperCase(Locale.ROOT); }
  private String normalize(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }
  private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private String require(String value, String field) {
    if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 값이 필요합니다.");
    return value.trim();
  }
  private String json(Object value) {
    try { return objectMapper.writeValueAsString(value); }
    catch (JsonProcessingException e) { throw new IllegalStateException(e); }
  }
  private List<String> parseStrings(String value) {
    if (value == null || value.isBlank()) return List.of();
    try { return objectMapper.readValue(value, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)); }
    catch (JsonProcessingException e) { return List.of(); }
  }
  private String sha256(String value) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(bytes);
    } catch (Exception e) { throw new IllegalStateException(e); }
  }

  public record Eligibility(boolean eligible, List<String> reasons) {}
  public record EvidenceRequest(String contentType, String summary, String title, String sourceUrl,
                                LocalDate activityDate, String evidenceKind, boolean educationallyInformative) {}
  public record DraftRequest(String regionId, String name, String role, String academyName,
                             String province, String cityDistrict, String identitySummary,
                             String educationPhilosophy, String curriculumFeatures, String teachingFeatures,
                             String growthAdvice, String officialHomepage, String officialBlog,
                             String officialYoutube, String officialSocial, String discoveryReason,
                             boolean offlineActivityConfirmed, boolean currentYearActivityConfirmed,
                             boolean educatorIdentityConfirmed, boolean exclusionFree,
                             List<String> potentialSignals, List<EvidenceRequest> evidence) {}
}
