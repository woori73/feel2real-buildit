package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * BuildIt Chart 전국 Living Archive 조사 파이프라인.
 *
 * <p>운영 루프: get_next_regions → claim → save_research_drafts → (submit_chart_review_batch) →
 * complete → 서버 자동 publication → RESEARCHED → 다음 지역. publication 은 AI/WebMCP 의 직접 명령이
 * 아니라 정상 파이프라인 완료의 서버 측 결과다(별도 publish tool 없음).
 */
@Service
@Transactional(readOnly = true)
public class ChartSiteToolService {

  public static final String CRITERIA_VERSION = "buildit-chart-v1";
  private static final Set<String> EDUCATOR_ROLES = Set.of("원장", "대표", "강사", "선생님");
  private static final Set<String> INFORMATIVE_KINDS = Set.of(
      "PHILOSOPHY", "CURRICULUM", "TEACHING_METHOD", "MATERIAL", "STUDENT_GUIDANCE",
      "SUBJECT_EXPERTISE", "EDUCATIONAL_CONTENT", "OFFLINE_ACTIVITY");
  private static final Set<String> PROMOTIONAL_KINDS = Set.of("ADVERTISEMENT", "PRESS_RELEASE", "RECRUITMENT");

  /** 교육철학으로 인정하지 않는 일반 광고 문구. 요약이 사실상 이것만이면 Hard Gate 탈락. */
  private static final List<String> PHILOSOPHY_GENERIC = List.of(
      "학생중심", "학생을중심", "성적향상", "성적up", "최선을다합니다", "최선을다하겠습니다",
      "책임감있게", "1대1맞춤", "1:1맞춤", "개인별맞춤", "꼼꼼하게지도", "즐거운수업", "행복한수업");
  /** 커리큘럼으로 인정하지 않는 과목 나열 토큰. 요약이 이것들의 나열뿐이면 탈락. */
  private static final Set<String> CURRICULUM_SUBJECT_TOKENS = Set.of(
      "초등영어", "중등영어", "고등영어", "초등", "중등", "고등", "내신", "수능", "영어",
      "회화", "문법", "독해", "어휘", "초중고", "정규반", "특강반", "방학특강");
  private static final int SUMMARY_MIN_CHARS = 30;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Clock clock = Clock.systemDefaultZone();

  /** RESEARCHED → DUE 재조사 주기(일). 전국 초기 구축에서는 신규 조사를 우선하도록 기본 90일. */
  @Value("${chart.re-research-days:90}")
  private int reResearchDays = 90;

  public ChartSiteToolService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
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
    context.put("hardGate", List.of(
        "현재 실제 교육 활동", "최근 공개 활동", "교육자 개인 식별", "교육철학(공개 근거로 2~3문장 설명 가능)",
        "커리큘럼 또는 고유한 수업 구조(공개 근거로 2~3문장 설명 가능)", "공개 검증 근거 URL", "제외대상 아님 · Chart 중복 아님"));
    context.put("finalCheck", "교육철학과 커리큘럼 중 하나라도 공개 근거로 충분히 설명할 수 없으면 최종 선정하지 않는다. 빈 부분을 추측·일반 문구로 채우지 않는다.");
    context.put("excluded", List.of("온라인 강의 전업", "과외 전업", "대형·체인 유명세만 확인", "광고 외 개인 교육활동 없음", "현재 활동 미확인"));
    context.put("scoreWeights", weights);
    context.put("selectionTargets", "지역별 선정 인원 목표 없음. 7명·1명·0명 모두 정상 결과다. TOP100을 채우려고 기준을 낮추지 않는다.");
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

  /**
   * 지역 조사 완료 = 서버 자동 발행. 이 지역의 최종 선정자(현행 Hard Gate + 현행 Score)를 전국 누적
   * publication 에 반영한 뒤에만 region 을 RESEARCHED 로 만든다. publication 실패 시 전체 롤백되어
   * region 완료도 취소된다(단일 트랜잭션). 선정자 0명도 정상 완료다.
   */
  @Transactional
  public Map<String, Object> completeRegion(String regionId, int eligibleCountHint, int rejectedCountHint, String note) {
    String rid = require(regionId, "regionId");
    LocalDateTime now = LocalDateTime.now(clock);
    Map<String, Object> region = jdbcTemplate.queryForList(
        "SELECT region_id, status, city_district FROM chart_regions WHERE region_id=? FOR UPDATE", rid)
        .stream().findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지역을 찾을 수 없습니다."));
    if (!"IN_PROGRESS".equals(region.get("status"))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "IN_PROGRESS 지역만 완료할 수 있습니다.");
    }

    List<Long> selectedIds = jdbcTemplate.queryForList("""
        SELECT candidate_id FROM chart_candidates c
         WHERE c.zone_id = ? AND c.eligibility_status = 'ELIGIBLE'
           AND (c.excluded_from_chart IS NULL OR c.excluded_from_chart = 0)
           AND (c.legacy_source IS NOT NULL OR (
                 COALESCE(NULLIF(c.education_philosophy_summary,''), NULLIF(c.education_philosophy,'')) IS NOT NULL
             AND COALESCE(NULLIF(c.curriculum_summary,''), NULLIF(c.curriculum_features,'')) IS NOT NULL))
        """, Long.class, rid);

    Long batchId = null;
    Long publicationId = null;
    int rankedCount = 0;
    int discoveredCount = 0;
    if (!selectedIds.isEmpty()) {
      String summary = blank(note) != null ? note
          : "전국 자동 조사 완료: " + region.get("city_district") + " · 선정 " + selectedIds.size() + "명";
      jdbcTemplate.update("""
          INSERT INTO chart_review_batches
          (region_id, status, criteria_version, summary, submitted_at, reviewed_at, created_at, updated_at)
          VALUES (?, 'AUTO_PUBLISHED', ?, ?, ?, ?, ?, ?)
          """, rid, CRITERIA_VERSION, summary, now, now, now, now);
      batchId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
      for (Long candidateId : selectedIds) {
        writeScoreSnapshot(candidateId, batchId, "AUTO_PUBLISHED", now);
      }
      Map<String, Object> published = autoPublishNationwide(batchId, now);
      publicationId = (Long) published.get("publicationId");
      rankedCount = (int) published.get("rankedCount");
      discoveredCount = (int) published.get("discoveredCount");
    }

    jdbcTemplate.update("""
        UPDATE chart_regions
           SET status='RESEARCHED', last_researched_at=?, next_research_at=?, last_error=NULL, updated_at=?
         WHERE region_id=?
        """, now, now.plusDays(reResearchDays), now, rid);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("regionId", rid);
    result.put("status", "RESEARCHED");
    result.put("selectedCount", selectedIds.size());
    result.put("reviewBatchId", batchId);
    result.put("publicationId", publicationId);
    result.put("publicationChanged", publicationId != null);
    result.put("rankedCount", rankedCount);
    result.put("discoveredCount", discoveredCount);
    result.put("nextResearchAt", now.plusDays(reResearchDays));
    return result;
  }

  @Scheduled(cron = "${chart.re-research-cron:0 15 3 * * *}", zone = "Asia/Seoul")
  @Transactional
  public void markResearchDue() {
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
               criteria_version AS criteriaVersion, summary, submitted_at AS submittedAt, reviewed_at AS reviewedAt
          FROM chart_review_batches
         ORDER BY review_batch_id DESC
         LIMIT 100
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
           activity_city_district, content_type, content_summary, identity_summary,
           education_philosophy_summary, curriculum_summary, distinctive_teaching_summary,
           recent_activity_summary, strengths_json, improvement_suggestions_json,
           profile_image_url, profile_image_source_url,
           official_homepage, official_blog, official_youtube, official_social,
           offline_activity_confirmed, current_year_activity_confirmed,
           educator_identity_confirmed, exclusion_free, eligibility_status, eligibility_reasons_json,
           potential_signals_json, research_source, criteria_version, source_url, source_url_hash,
           source_title, discovery_reason, deep_search_result, exclusion_check, verification_status,
           status, google_query, discovered_at, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                  b'1', b'1', b'1', b'1', 'ELIGIBLE', ?, ?, 'CHATGPT_SITE_TOOL', ?, ?, ?, ?, ?,
                  ?, '', 'VERIFIED', 'DISCOVERED', 'BuildIt Chart Site tool research draft', ?, ?, ?)
          """,
          request.regionId(), personKey, require(request.name(), "name"), validatedRole(request.role()),
          blank(request.academyName()), region, request.province(), request.cityDistrict(),
          require(primary.contentType(), "evidence.contentType"), require(primary.summary(), "evidence.summary"),
          require(request.identitySummary(), "identitySummary"),
          require(request.educationPhilosophySummary(), "educationPhilosophySummary"),
          require(request.curriculumSummary(), "curriculumSummary"),
          blank(request.distinctiveTeachingSummary()), blank(request.recentActivitySummary()),
          jsonList(request.strengths()), jsonList(request.improvementSuggestions()),
          nullableUrl(request.profileImageUrl()), nullableUrl(request.profileImageSourceUrl()),
          nullableUrl(request.officialHomepage()), nullableUrl(request.officialBlog()),
          nullableUrl(request.officialYoutube()), nullableUrl(request.officialSocial()),
          json(eligibility.reasons()),
          jsonList(request.potentialSignals()), CRITERIA_VERSION,
          sourceUrl, sha256(sourceUrl), blank(primary.title()),
          require(request.discoveryReason(), "discoveryReason"),
          "Hard Gate(교육철학·커리큘럼 포함) 통과: " + String.join(", ", eligibility.reasons()),
          now, now, now);
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
    requireCurrentGate(candidate);
    List<Map<String, Object>> evidence = jdbcTemplate.queryForList(
        "SELECT evidence_kind, activity_date, source_url, content_summary, educationally_informative FROM chart_candidate_activities WHERE candidate_id=?", candidateId);
    Map<String, Integer> breakdown = score(candidate, evidence);
    int total = breakdown.values().stream().mapToInt(Integer::intValue).sum();
    List<String> potential = parseStrings((String) candidate.get("potential_signals_json"));
    return Map.of("candidateId", candidateId, "criteriaVersion", CRITERIA_VERSION, "totalScore", total,
        "breakdown", breakdown, "evidenceCount", evidence.size(), "calculatedAt", LocalDateTime.now(clock),
        "potentialSignals", potential, "promisingCandidate", !potential.isEmpty(), "published", false);
  }

  /**
   * 조사 Batch/audit 기록. 초기 자동발행 단계에서 이 Batch 는 관리자 사전 승인 gate 가 아니라
   * 누가/어떤 근거로/어떤 Score 로 선정했는지 보존하는 audit record 다.
   */
  @Transactional
  public Map<String, Object> submitReview(String regionId, List<Long> candidateIds, String summary) {
    if (candidateIds == null || candidateIds.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "candidateIds가 필요합니다.");
    }
    LocalDateTime now = LocalDateTime.now(clock);
    jdbcTemplate.update("INSERT INTO chart_review_batches (region_id, status, criteria_version, summary, submitted_at, created_at, updated_at) VALUES (?, 'AUDIT_RECORDED', ?, ?, ?, ?, ?)",
        require(regionId, "regionId"), CRITERIA_VERSION, require(summary, "summary"), now, now, now);
    Long batchId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    List<Map<String, Object>> previews = new ArrayList<>();
    for (Long candidateId : candidateIds) {
      previews.add(previewScore(candidateId));
    }
    previews.sort((a, b) -> Integer.compare((Integer) b.get("totalScore"), (Integer) a.get("totalScore")));
    int rank = 1;
    for (Map<String, Object> preview : previews) {
      long candidateId = ((Number) preview.get("candidateId")).longValue();
      writeScoreSnapshot(candidateId, batchId, "AUDIT_RECORDED", now, rank++);
    }
    return Map.of("reviewBatchId", batchId, "status", "AUDIT_RECORDED", "proposals", previews,
        "publicationChanged", false);
  }

  /**
   * 이미 Draft/Score/Batch 가 있는 조사(예: 분당 Review Batch 1)를 새 자동발행 코드에서 안전하게
   * publication 단계부터 resume 한다. 기존 Batch/Draft/Score 는 삭제하지 않는다. 현행 Hard Gate
   * (교육철학·커리큘럼 요약 보유)를 통과한 후보만 publication 에 포함된다.
   */
  @Transactional
  public Map<String, Object> finalizeReviewBatch(long reviewBatchId) {
    Map<String, Object> batch = jdbcTemplate.queryForList(
        "SELECT review_batch_id, region_id, status FROM chart_review_batches WHERE review_batch_id=?", reviewBatchId)
        .stream().findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "review batch를 찾을 수 없습니다."));
    Integer already = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_publications WHERE review_batch_id=?", Integer.class, reviewBatchId);
    if (already != null && already > 0) {
      return Map.of("reviewBatchId", reviewBatchId, "status", "ALREADY_FINALIZED", "publicationChanged", false);
    }
    LocalDateTime now = LocalDateTime.now(clock);
    String regionId = String.valueOf(batch.get("region_id"));
    List<Long> batchCandidateIds = jdbcTemplate.queryForList(
        "SELECT DISTINCT candidate_id FROM chart_score_snapshots WHERE review_batch_id=?", Long.class, reviewBatchId);
    if (batchCandidateIds.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "이 batch에 Score snapshot이 없습니다.");
    }
    long passing = batchCandidateIds.stream().filter(this::passesCurrentGate).count();

    Map<String, Object> published = autoPublishNationwide(reviewBatchId, now);
    jdbcTemplate.update("UPDATE chart_review_batches SET status='AUTO_PUBLISHED', reviewed_at=?, updated_at=? WHERE review_batch_id=?", now, now, reviewBatchId);
    jdbcTemplate.update("UPDATE chart_score_snapshots SET review_status='AUTO_PUBLISHED', updated_at=? WHERE review_batch_id=? AND review_status IN ('PENDING_ADMIN_REVIEW','AUDIT_RECORDED')", now, reviewBatchId);
    jdbcTemplate.update("""
        UPDATE chart_regions
           SET status='RESEARCHED',
               last_researched_at=COALESCE(last_researched_at, ?),
               next_research_at=COALESCE(next_research_at, ?),
               updated_at=?
         WHERE region_id=? AND status IN ('IN_PROGRESS','DUE','ERROR','UNRESEARCHED')
        """, now, now.plusDays(reResearchDays), now, regionId);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("reviewBatchId", reviewBatchId);
    result.put("regionId", regionId);
    result.put("batchCandidateCount", batchCandidateIds.size());
    result.put("currentGatePassingCount", passing);
    result.put("publicationId", published.get("publicationId"));
    result.put("rankedCount", published.get("rankedCount"));
    result.put("discoveredCount", published.get("discoveredCount"));
    result.put("publicationChanged", true);
    return result;
  }

  public Map<String, Object> publicationDisabled() {
    throw new ResponseStatusException(HttpStatus.CONFLICT,
        "publication은 AI/WebMCP 직접 명령으로 만들 수 없습니다. 정상 조사 파이프라인(complete) 완료 시 서버가 자동 발행합니다.");
  }

  /**
   * 현재까지 조사·선정된 교육자를 전국 누적 snapshot 으로 (재)생성한다. 지역 조사와 무관한 서버/관리자
   * 트리거(부팅 시 최초 1회, 또는 Legacy import 후)이며 AI/WebMCP 명령이 아니다. publishable 후보가
   * 없으면 아무것도 만들지 않는다.
   */
  @Transactional
  public Map<String, Object> rebuildNationwidePublication() {
    LocalDateTime now = LocalDateTime.now(clock);
    Integer publishable = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM chart_candidates c
         WHERE c.eligibility_status = 'ELIGIBLE'
           AND (c.excluded_from_chart IS NULL OR c.excluded_from_chart = 0)
           AND (c.legacy_source IS NOT NULL OR (
                 COALESCE(NULLIF(c.education_philosophy_summary,''), NULLIF(c.education_philosophy,'')) IS NOT NULL
             AND COALESCE(NULLIF(c.curriculum_summary,''), NULLIF(c.curriculum_features,'')) IS NOT NULL))
        """, Integer.class);
    if (publishable == null || publishable == 0) {
      return Map.of("publicationChanged", false, "reason", "공개 가능한 선정 교육자가 없습니다.");
    }
    // 아직 Score snapshot 이 없는 publishable 후보(예: Legacy import)에 현행 Score 를 계산해 둔다.
    List<Long> needScore = jdbcTemplate.queryForList("""
        SELECT c.candidate_id FROM chart_candidates c
         WHERE c.eligibility_status = 'ELIGIBLE'
           AND (c.excluded_from_chart IS NULL OR c.excluded_from_chart = 0)
           AND (c.legacy_source IS NOT NULL OR (
                 COALESCE(NULLIF(c.education_philosophy_summary,''), NULLIF(c.education_philosophy,'')) IS NOT NULL
             AND COALESCE(NULLIF(c.curriculum_summary,''), NULLIF(c.curriculum_features,'')) IS NOT NULL))
           AND NOT EXISTS (SELECT 1 FROM chart_score_snapshots s WHERE s.candidate_id = c.candidate_id)
        """, Long.class);
    for (Long candidateId : needScore) {
      writeScoreSnapshot(candidateId, null, "AUTO_PUBLISHED", now);
    }
    Map<String, Object> published = autoPublishNationwide(null, now);
    Map<String, Object> result = new LinkedHashMap<>(published);
    result.put("publicationChanged", true);
    result.put("scoredCandidates", needScore.size());
    return result;
  }

  @Transactional
  public Map<String, Object> reviewDecision(long reviewBatchId, String decision, String note) {
    String normalized = require(decision, "decision").toUpperCase(Locale.ROOT);
    if (!Set.of("APPROVED", "REJECTED").contains(normalized)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "APPROVED 또는 REJECTED만 허용됩니다.");
    }
    LocalDateTime now = LocalDateTime.now(clock);
    int updated = jdbcTemplate.update("UPDATE chart_review_batches SET status=?, reviewed_at=?, review_note=?, updated_at=? WHERE review_batch_id=? AND status IN ('PENDING_ADMIN_REVIEW','AUDIT_RECORDED')", normalized, now, blank(note), now, reviewBatchId);
    if (updated == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "판정 가능한 배치가 아닙니다.");
    jdbcTemplate.update("UPDATE chart_score_snapshots SET review_status=?, updated_at=? WHERE review_status IN ('PENDING_ADMIN_REVIEW','AUDIT_RECORDED') AND review_batch_id=?", normalized, now, reviewBatchId);
    return Map.of("reviewBatchId", reviewBatchId, "status", normalized, "publicationChanged", false);
  }

  @Transactional
  public Map<String, Object> requestWebDraft(long candidateId, LocalDateTime educatorConsentAt) {
    if (educatorConsentAt == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "교육자 동의 시각이 필요합니다.");
    Map<String, Object> row = candidate(candidateId);
    Map<String, Object> payload = new LinkedHashMap<>();
    for (String key : List.of("name", "activity_province", "activity_city_district", "identity_summary",
        "education_philosophy_summary", "curriculum_summary", "distinctive_teaching_summary",
        "recent_activity_summary", "strengths_json", "improvement_suggestions_json",
        "profile_image_url", "official_homepage", "official_blog", "official_youtube", "official_social")) {
      payload.put(key, row.get(key));
    }
    LocalDateTime now = LocalDateTime.now(clock);
    jdbcTemplate.update("INSERT INTO chart_web_draft_requests (candidate_id, educator_consent_at, status, payload_json, created_at, updated_at) VALUES (?, ?, 'REQUESTED', ?, ?, ?)", candidateId, educatorConsentAt, json(payload), now, now);
    return Map.of("candidateId", candidateId, "status", "REQUESTED", "consentRecorded", true, "scoreBoost", false);
  }

  // ----- Hard Gate -------------------------------------------------------------------------------

  Eligibility evaluate(DraftRequest request) {
    List<String> reasons = new ArrayList<>();
    if (!request.offlineActivityConfirmed()) reasons.add("현재 실제 교육 활동 미확인");
    if (!request.currentYearActivityConfirmed()) reasons.add("최근 공개 활동 미확인");
    if (!request.educatorIdentityConfirmed()) reasons.add("교육자 개인 식별 불가");
    if (!request.exclusionFree()) reasons.add("제외대상 신호 존재");

    if (!describable(request.educationPhilosophySummary())) {
      reasons.add("교육철학을 공개 근거로 2~3문장 설명할 수 없음");
    } else if (isGenericPhilosophy(request.educationPhilosophySummary())) {
      reasons.add("교육철학이 일반 광고 문구 수준임");
    }
    if (!describable(request.curriculumSummary())) {
      reasons.add("커리큘럼·수업 구조를 공개 근거로 2~3문장 설명할 수 없음");
    } else if (isSubjectEnumerationOnly(request.curriculumSummary())) {
      reasons.add("커리큘럼이 과목 나열 수준임");
    }

    if (request.evidence() == null || request.evidence().isEmpty()) {
      reasons.add("실제 공개 근거 URL 없음");
    } else {
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
    if (reasons.isEmpty()) {
      return new Eligibility(true, List.of("현재 교육 활동 확인", "최근 활동 확인", "교육자 개인 식별", "교육철학 서술 가능",
          "커리큘럼 서술 가능", "공개 근거 URL 확보", "제외대상 아님"));
    }
    return new Eligibility(false, reasons);
  }

  private boolean describable(String summary) {
    if (summary == null) return false;
    String trimmed = summary.trim();
    return trimmed.replaceAll("\\s+", "").length() >= SUMMARY_MIN_CHARS;
  }

  private boolean isGenericPhilosophy(String summary) {
    String compact = normalize(summary);
    for (String generic : PHILOSOPHY_GENERIC) {
      compact = compact.replace(normalize(generic), "");
    }
    // 흔한 조사·연결어를 걷어낸 뒤에도 남는 실질 내용이 거의 없으면 광고 문구로 본다.
    compact = compact.replaceAll("[을를이가은는와과로으며고및에의도만해하지요및그및또는또한때문에서부터까지]", "");
    return compact.length() < 15;
  }

  private boolean isSubjectEnumerationOnly(String summary) {
    String[] tokens = summary.trim().toLowerCase(Locale.ROOT).split("[\\s/,·|~\\-]+");
    int meaningful = 0;
    for (String token : tokens) {
      String t = token.replaceAll("[^0-9a-z가-힣]", "");
      if (t.isEmpty()) continue;
      if (!CURRICULUM_SUBJECT_TOKENS.contains(t)) meaningful++;
    }
    return meaningful == 0;
  }

  // ----- Score & auto publication -------------------------------------------------------------

  Map<String, Integer> score(Map<String, Object> candidate, List<Map<String, Object>> evidence) {
    long unique = evidence.stream().map(e -> e.get("source_url")).distinct().count();
    long months = evidence.stream().map(e -> e.get("activity_date")).filter(v -> v != null)
        .map(Object::toString).map(v -> v.substring(0, Math.min(7, v.length()))).distinct().count();
    Set<String> kinds = evidence.stream().map(e -> String.valueOf(e.get("evidence_kind"))).collect(java.util.stream.Collectors.toSet());
    int identityFields =
        (firstNonBlank(candidate.get("identity_summary"), null) != null ? 1 : 0)
        + (firstNonBlank(candidate.get("education_philosophy_summary"), candidate.get("education_philosophy")) != null ? 1 : 0)
        + (firstNonBlank(candidate.get("curriculum_summary"), candidate.get("curriculum_features")) != null ? 1 : 0)
        + (firstNonBlank(candidate.get("distinctive_teaching_summary"), candidate.get("teaching_features")) != null ? 1 : 0);
    int identity = Math.min(35, identityFields * 10);
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

  private long writeScoreSnapshot(long candidateId, Long reviewBatchId, String reviewStatus, LocalDateTime now) {
    return writeScoreSnapshot(candidateId, reviewBatchId, reviewStatus, now, null);
  }

  private long writeScoreSnapshot(long candidateId, Long reviewBatchId, String reviewStatus, LocalDateTime now, Integer proposedRank) {
    Map<String, Object> candidate = candidate(candidateId);
    requireCurrentGate(candidate);
    List<Map<String, Object>> evidence = jdbcTemplate.queryForList(
        "SELECT evidence_kind, activity_date, source_url, content_summary, educationally_informative FROM chart_candidate_activities WHERE candidate_id=?", candidateId);
    Map<String, Integer> breakdown = score(candidate, evidence);
    int total = breakdown.values().stream().mapToInt(Integer::intValue).sum();
    jdbcTemplate.update("""
        INSERT INTO chart_score_snapshots
        (candidate_id, review_batch_id, criteria_version, total_score, score_breakdown_json, evidence_json,
         calculated_at, proposed_rank, review_status, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, candidateId, reviewBatchId, CRITERIA_VERSION, total, json(breakdown),
        json(buildEvidenceJson(candidate, evidence)), now, proposedRank, reviewStatus, now, now);
    return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  private Map<String, Object> buildEvidenceJson(Map<String, Object> candidate, List<Map<String, Object>> evidence) {
    List<Map<String, Object>> activities = new ArrayList<>();
    for (Map<String, Object> row : evidence) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("evidenceKind", row.get("evidence_kind"));
      item.put("activityDate", row.get("activity_date") == null ? null : String.valueOf(row.get("activity_date")));
      item.put("sourceUrl", row.get("source_url"));
      item.put("summary", row.get("content_summary"));
      activities.add(item);
    }
    Map<String, Object> officialChannels = new LinkedHashMap<>();
    for (String key : List.of("official_homepage", "official_blog", "official_youtube", "official_social")) {
      if (candidate.get(key) != null) officialChannels.put(key, candidate.get(key));
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("criteriaVersion", CRITERIA_VERSION);
    out.put("activityCount", evidence.size());
    out.put("distinctSourceCount", evidence.stream().map(e -> e.get("source_url")).distinct().count());
    out.put("activities", activities);
    out.put("officialChannels", officialChannels);
    out.put("philosophyEvidence", firstNonBlank(candidate.get("education_philosophy_summary"), candidate.get("education_philosophy")));
    out.put("curriculumEvidence", firstNonBlank(candidate.get("curriculum_summary"), candidate.get("curriculum_features")));
    return out;
  }

  /**
   * 전국 누적 publication 재생성. 현행 Hard Gate(교육철학·커리큘럼 요약 보유) + 현행 Score 를 가진
   * 유효 교육자 전체를 total_score 기준으로 다시 정렬해 상위 최대 100명은 RANKED, 나머지는 DISCOVERED
   * (potential signals 보유 시 PROMISING)로 새 snapshot 을 만들고 이전 snapshot 은 SUPERSEDED 로 보존한다.
   */
  private Map<String, Object> autoPublishNationwide(Long reviewBatchId, LocalDateTime now) {
    List<Map<String, Object>> pool = jdbcTemplate.queryForList("""
        SELECT c.candidate_id AS candidateId, c.identity_summary AS identitySummary,
               c.recent_activity_summary AS recentActivitySummary, c.potential_signals_json AS potentialSignalsJson,
               s.score_snapshot_id AS scoreSnapshotId, s.total_score AS totalScore
          FROM chart_candidates c
          JOIN chart_score_snapshots s
            ON s.score_snapshot_id = (SELECT s2.score_snapshot_id FROM chart_score_snapshots s2
                                       WHERE s2.candidate_id = c.candidate_id
                                       ORDER BY s2.calculated_at DESC, s2.score_snapshot_id DESC LIMIT 1)
         WHERE c.eligibility_status = 'ELIGIBLE'
           AND (c.excluded_from_chart IS NULL OR c.excluded_from_chart = 0)
           AND (c.legacy_source IS NOT NULL OR (
                 COALESCE(NULLIF(c.education_philosophy_summary,''), NULLIF(c.education_philosophy,'')) IS NOT NULL
             AND COALESCE(NULLIF(c.curriculum_summary,''), NULLIF(c.curriculum_features,'')) IS NOT NULL))
         ORDER BY s.total_score DESC, c.candidate_id ASC
        """);

    List<Map<String, Object>> previousPub = jdbcTemplate.queryForList(
        "SELECT publication_id FROM chart_publications WHERE status='PUBLISHED' ORDER BY published_at DESC, publication_id DESC LIMIT 1");
    Long previousPubId = previousPub.isEmpty() ? null : ((Number) previousPub.get(0).get("publication_id")).longValue();
    Map<Long, Integer> previousRankByCandidate = new HashMap<>();
    if (previousPubId != null) {
      jdbcTemplate.queryForList("SELECT candidate_id, chart_rank FROM chart_publication_entries WHERE publication_id=? AND chart_rank IS NOT NULL", previousPubId)
          .forEach(row -> previousRankByCandidate.put(((Number) row.get("candidate_id")).longValue(), ((Number) row.get("chart_rank")).intValue()));
    }

    LocalDate weekStart = now.toLocalDate().with(DayOfWeek.MONDAY);
    jdbcTemplate.update("""
        INSERT INTO chart_publications (review_batch_id, status, scope, week_start, published_at, created_at, updated_at)
        VALUES (?, 'PUBLISHED', 'NATIONWIDE', ?, ?, ?, ?)
        """, reviewBatchId, weekStart, now, now, now);
    Long publicationId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

    int position = 1;
    int rankedCount = 0;
    int discoveredCount = 0;
    for (Map<String, Object> row : pool) {
      long candidateId = ((Number) row.get("candidateId")).longValue();
      long scoreSnapshotId = ((Number) row.get("scoreSnapshotId")).longValue();
      boolean promising = !parseStrings((String) row.get("potentialSignalsJson")).isEmpty();
      Integer previousRank = previousRankByCandidate.get(candidateId);
      String entryType;
      Integer chartRank;
      if (position <= 100) {
        entryType = "RANKED";
        chartRank = position;
        rankedCount++;
      } else {
        entryType = promising ? "PROMISING" : "DISCOVERED";
        chartRank = null;
        discoveredCount++;
      }
      jdbcTemplate.update("""
          INSERT INTO chart_publication_entries
          (publication_id, candidate_id, score_snapshot_id, entry_type, chart_rank, previous_rank,
           short_description, recent_activity_summary, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, publicationId, candidateId, scoreSnapshotId, entryType, chartRank, previousRank,
          blankOr(row.get("identitySummary"), "교육 정체성 기록 준비 중"), blankOrNull(row.get("recentActivitySummary")), now, now);
      position++;
    }

    if (previousPubId != null) {
      jdbcTemplate.update("UPDATE chart_publications SET status='SUPERSEDED', updated_at=? WHERE publication_id=?", now, previousPubId);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("publicationId", publicationId);
    result.put("rankedCount", rankedCount);
    result.put("discoveredCount", discoveredCount);
    result.put("totalEntries", pool.size());
    return result;
  }

  /** Legacy 선정자는 확보된 정보까지만 공개하므로 통과. 신규/분당 후보는 교육철학·커리큘럼을 설명할 수 있어야 한다. */
  private boolean describableProfile(Map<String, Object> candidate) {
    if (blank(str(candidate.get("legacy_source"))) != null) return true;
    String philosophy = firstNonBlank(candidate.get("education_philosophy_summary"), candidate.get("education_philosophy"));
    String curriculum = firstNonBlank(candidate.get("curriculum_summary"), candidate.get("curriculum_features"));
    return philosophy != null && curriculum != null;
  }

  private String firstNonBlank(Object a, Object b) {
    String first = blank(str(a));
    return first != null ? first : blank(str(b));
  }

  private void requireCurrentGate(Map<String, Object> candidate) {
    if (!"ELIGIBLE".equals(candidate.get("eligibility_status")) || !describableProfile(candidate)) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "교육철학·커리큘럼을 공개 근거로 설명할 수 있는 후보만 Score/발행 대상입니다: " + candidate.get("candidate_id"));
    }
  }

  private boolean passesCurrentGate(long candidateId) {
    Map<String, Object> candidate = candidate(candidateId);
    return "ELIGIBLE".equals(candidate.get("eligibility_status"))
        && !Boolean.TRUE.equals(bit(candidate.get("excluded_from_chart")))
        && describableProfile(candidate);
  }

  // ----- helpers -------------------------------------------------------------------------------

  private Map<String, Object> candidate(long id) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM chart_candidates WHERE candidate_id=?", id);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "후보를 찾을 수 없습니다.");
    return rows.get(0);
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
  private String str(Object value) { return value == null ? null : String.valueOf(value); }
  private Boolean bit(Object value) {
    if (value == null) return null;
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return n.intValue() != 0;
    if (value instanceof byte[] bytes) return bytes.length > 0 && bytes[0] != 0;
    return Boolean.parseBoolean(String.valueOf(value));
  }
  private String blankOr(Object value, String fallback) {
    String s = str(value);
    return s == null || s.isBlank() ? fallback : s;
  }
  private String blankOrNull(Object value) {
    String s = str(value);
    return s == null || s.isBlank() ? null : s;
  }
  private String require(String value, String field) {
    if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 값이 필요합니다.");
    return value.trim();
  }
  private String json(Object value) {
    try { return objectMapper.writeValueAsString(value); }
    catch (JsonProcessingException e) { throw new IllegalStateException(e); }
  }
  private String jsonList(List<String> value) { return json(value == null ? List.of() : value); }
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
                             String educationPhilosophySummary, String curriculumSummary,
                             String distinctiveTeachingSummary, String recentActivitySummary,
                             List<String> strengths, List<String> improvementSuggestions,
                             String profileImageUrl, String profileImageSourceUrl,
                             String officialHomepage, String officialBlog, String officialYoutube,
                             String officialSocial, String discoveryReason,
                             boolean offlineActivityConfirmed, boolean currentYearActivityConfirmed,
                             boolean educatorIdentityConfirmed, boolean exclusionFree,
                             List<String> potentialSignals, List<EvidenceRequest> evidence) {}
}
