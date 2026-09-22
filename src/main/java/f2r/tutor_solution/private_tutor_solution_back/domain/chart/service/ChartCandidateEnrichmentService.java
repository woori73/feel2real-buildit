package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * BuildIt Chart — 이미 저장된 교육자(chart_candidates)의 Educator Intelligence 보완(Enrichment).
 *
 * <p>신규 발굴 저장({@link ChartSiteToolService#saveDraft}, INSERT)과 완전히 분리된 <b>기존 candidate_id
 * 전용 UPDATE</b> 경로다. 재평가가 아니라 "이후 다시 조사해 채워 넣는" 작업이므로 다음을 절대 건드리지
 * 않는다: Score, Rank, {@code eligibility_status}, publication 상태, {@code excluded_from_chart},
 * {@code legacy_source}, 그리고 이름·학원·지역 등 identity 필드.
 *
 * <p>수정 가능한 범위는 Educator Intelligence 요약 필드와 Evidence(활동) 추가뿐이다.
 * <ul>
 *   <li>요약 필드: 비어 있으면 채우고, 이미 값이 있으면 {@code overwrite=true} 일 때만 교체한다.
 *       null/blank 로는 절대 기존 값을 지우지 않는다.</li>
 *   <li>강점/개선제안 리스트: 기존 항목을 보존한 채 새 항목을 합집합으로 누적한다(삭제 없음).</li>
 *   <li>Evidence: {@code chart_candidate_activities} 에 원 출처 URL 로만 추가한다(누적, 중복 무시).
 *       네이버 AI/검색 결과 URL, Gemini grounding redirect, placeholder URL 은 거부한다.</li>
 * </ul>
 * saveDraft 의 교육철학·커리큘럼 2~3문장 평가 게이트는 여기 적용하지 않는다 —
 * 확인된 Evidence 가 있는 정보만, 확인된 필드만 부분 보완한다.
 */
@Service
@Transactional(readOnly = true)
public class ChartCandidateEnrichmentService {

  /** 보완 endpoint 가 만질 수 있는 유일한 텍스트 컬럼(요약). 이 밖의 컬럼은 어떤 경로로도 수정하지 않는다. */
  static final List<String> SUMMARY_COLUMNS = List.of(
      "education_philosophy_summary", "curriculum_summary",
      "distinctive_teaching_summary", "recent_activity_summary");

  /** 원 출처로 인정하지 않는 host/패턴 — 네이버 AI·검색 결과, Gemini grounding redirect, placeholder. */
  private static final List<String> NON_ORIGINAL_URL_MARKERS = List.of(
      "vertexaisearch.cloud.google.com", "grounding-api-redirect",
      "buildit.example", "search.naver.com", "search.daum.net",
      "google.com/search", "www.google.com/search", "bing.com/search", "cse.google");

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Clock clock = Clock.systemDefaultZone();

  public ChartCandidateEnrichmentService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Map<String, Object> getResearch(long candidateId) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
        SELECT candidate_id AS candidateId, name, academy_name AS academyName,
               activity_region AS activityRegion,
               education_philosophy_summary AS educationPhilosophySummary,
               curriculum_summary AS curriculumSummary,
               distinctive_teaching_summary AS distinctiveTeachingSummary,
               recent_activity_summary AS recentActivitySummary,
               strengths_json AS strengthsJson, improvement_suggestions_json AS improvementSuggestionsJson,
               eligibility_status AS eligibilityStatus, updated_at AS updatedAt
          FROM chart_candidates WHERE candidate_id=?
        """, candidateId);
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "교육자를 찾을 수 없습니다.");
    }
    Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
    result.put("strengths", parseStrings(str(result.remove("strengthsJson"))));
    result.put("improvementSuggestions", parseStrings(str(result.remove("improvementSuggestionsJson"))));
    result.put("evidence", jdbcTemplate.queryForList("""
        SELECT content_type AS contentType, content_summary AS contentSummary, source_url AS sourceUrl,
               activity_date AS activityDate, evidence_kind AS evidenceKind, researched_at AS researchedAt
          FROM chart_candidate_activities WHERE candidate_id=? ORDER BY activity_id
        """, candidateId));
    return result;
  }

  @Transactional
  public Map<String, Object> enrich(long candidateId, EnrichmentRequest request) {
    EnrichmentRequest req = request == null ? EnrichmentRequest.empty() : request;
    if (isEmptyRequest(req)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "보완할 내용이 없습니다: 요약·강점·개선제안·근거 중 최소 하나가 필요합니다.");
    }

    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "SELECT * FROM chart_candidates WHERE candidate_id=? FOR UPDATE", candidateId);
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          "candidate_id " + candidateId + " 교육자를 찾을 수 없습니다.");
    }
    Map<String, Object> row = rows.get(0);
    String eligibilityBefore = str(row.get("eligibility_status"));

    // 쓰기 전에 Evidence 를 모두 검증·정규화한다(원 출처 아니면 여기서 400 — 부분 반영 없음).
    List<NormalizedEvidence> evidence = new ArrayList<>();
    if (req.evidence() != null) {
      for (EvidenceInput e : req.evidence()) {
        evidence.add(new NormalizedEvidence(
            originalSourceUrlOrThrow(e.sourceUrl()),
            require(e.contentType(), "evidence.contentType"),
            require(e.contentSummary(), "evidence.contentSummary"),
            e.activityDate(),
            blank(e.evidenceKind()) == null ? null : e.evidenceKind().trim().toUpperCase(Locale.ROOT),
            e.educationallyInformative() == null || e.educationallyInformative()));
      }
    }

    LocalDateTime now = LocalDateTime.now(clock);
    List<String> setClauses = new ArrayList<>();
    List<Object> args = new ArrayList<>();
    List<String> changedFields = new ArrayList<>();
    List<String> conflicts = new ArrayList<>();

    Map<String, String> incomingSummaries = new LinkedHashMap<>();
    incomingSummaries.put("education_philosophy_summary", blank(req.educationPhilosophySummary()));
    incomingSummaries.put("curriculum_summary", blank(req.curriculumSummary()));
    incomingSummaries.put("distinctive_teaching_summary", blank(req.distinctiveTeachingSummary()));
    incomingSummaries.put("recent_activity_summary", blank(req.recentActivitySummary()));
    for (String column : SUMMARY_COLUMNS) {
      String current = blank(str(row.get(column)));
      String incoming = incomingSummaries.get(column);
      if (!req.overwrite() && current != null && incoming != null && !current.equals(incoming)) {
        conflicts.add(column);
      }
      String merged = mergeSummary(current, incoming, req.overwrite());
      if (merged != null) {
        setClauses.add(column + "=?");
        args.add(merged);
        changedFields.add(column);
      }
    }

    List<String> mergedStrengths = mergeList(parseStrings(str(row.get("strengths_json"))), req.strengths());
    if (mergedStrengths != null) {
      setClauses.add("strengths_json=?");
      args.add(json(mergedStrengths));
      changedFields.add("strengths_json");
    }
    List<String> mergedImprovements =
        mergeList(parseStrings(str(row.get("improvement_suggestions_json"))), req.improvementSuggestions());
    if (mergedImprovements != null) {
      setClauses.add("improvement_suggestions_json=?");
      args.add(json(mergedImprovements));
      changedFields.add("improvement_suggestions_json");
    }

    int evidenceAdded = 0;
    int evidenceSkippedDuplicate = 0;

    if (!setClauses.isEmpty() || !evidence.isEmpty()) {
      setClauses.add("updated_at=?");
      args.add(now);
      args.add(candidateId);
      jdbcTemplate.update(
          "UPDATE chart_candidates SET " + String.join(", ", setClauses) + " WHERE candidate_id=?",
          args.toArray());
    }

    for (NormalizedEvidence e : evidence) {
      int inserted = jdbcTemplate.update("""
          INSERT IGNORE INTO chart_candidate_activities
            (candidate_id, content_type, content_summary, source_url, activity_date, evidence_kind,
             educationally_informative, researched_at, discovered_at, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, candidateId, e.contentType(), e.contentSummary(), e.sourceUrl(), e.activityDate(),
          e.evidenceKind(), e.educationallyInformative(), now, now, now, now);
      if (inserted > 0) {
        evidenceAdded++;
      } else {
        evidenceSkippedDuplicate++;
      }
    }

    String eligibilityAfter = str(jdbcTemplate.queryForObject(
        "SELECT eligibility_status FROM chart_candidates WHERE candidate_id=?", String.class, candidateId));
    if (eligibilityBefore != null && !eligibilityBefore.equals(eligibilityAfter)) {
      // 방어: enrichment 은 재평가가 아니다. 어떤 경로로도 eligibility 가 바뀌면 롤백한다.
      throw new IllegalStateException("enrichment must not change eligibility_status ("
          + eligibilityBefore + " -> " + eligibilityAfter + ")");
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("candidateId", candidateId);
    result.put("changedFields", changedFields);
    result.put("conflicts", conflicts);
    result.put("saved", true);
    result.put("research", getResearch(candidateId));
    result.put("evidenceAdded", evidenceAdded);
    result.put("evidenceSkippedDuplicate", evidenceSkippedDuplicate);
    result.put("eligibilityStatus", eligibilityAfter);
    result.put("updatedAt", now);
    return result;
  }

  // ----- 순수 로직 (JdbcTemplate 비의존, 단위 테스트 대상) --------------------------------------

  /** 요약 병합: 새 값이 없으면 변경 없음(null). 기존이 비어 있으면 채움. 이미 있으면 overwrite 일 때만. */
  static String mergeSummary(String existing, String incoming, boolean overwrite) {
    String next = trimToNull(incoming);
    if (next == null) return null;                       // null/blank 로는 절대 덮어쓰지 않는다
    String current = trimToNull(existing);
    if (current == null) return next;                    // 빈 필드 → 채움
    if (!overwrite) return null;                         // 기존 값 보존
    return next.equals(current) ? null : next;           // overwrite 이고 실제로 달라질 때만
  }

  /** 리스트 병합: 기존 항목을 보존한 채 새 항목을 합집합으로 누적. 실제로 늘어날 때만 결과 반환. */
  static List<String> mergeList(List<String> existing, List<String> incoming) {
    if (incoming == null || incoming.isEmpty()) return null;
    LinkedHashSet<String> merged = new LinkedHashSet<>();
    if (existing != null) {
      for (String s : existing) {
        String v = trimToNull(s);
        if (v != null) merged.add(v);
      }
    }
    int before = merged.size();
    for (String s : incoming) {
      String v = trimToNull(s);
      if (v != null) merged.add(v);
    }
    return merged.size() == before ? null : new ArrayList<>(merged);
  }

  /** 요약·리스트·근거가 모두 비어 있으면 빈 요청(기존 데이터 삭제 방지용 가드). */
  static boolean isEmptyRequest(EnrichmentRequest req) {
    boolean anySummary = trimToNull(req.educationPhilosophySummary()) != null
        || trimToNull(req.curriculumSummary()) != null
        || trimToNull(req.distinctiveTeachingSummary()) != null
        || trimToNull(req.recentActivitySummary()) != null;
    boolean anyList = (req.strengths() != null && !req.strengths().isEmpty())
        || (req.improvementSuggestions() != null && !req.improvementSuggestions().isEmpty());
    boolean anyEvidence = req.evidence() != null && !req.evidence().isEmpty();
    return !anySummary && !anyList && !anyEvidence;
  }

  /** 공개 http(s) URL 이고 원 출처(검색/AI 요약/redirect/placeholder 아님)인지. */
  static boolean isOriginalSourceUrl(String value) {
    if (value == null || value.isBlank()) return false;
    URI uri;
    try {
      uri = URI.create(value.trim());
    } catch (IllegalArgumentException error) {
      return false;
    }
    if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
        || uri.getHost() == null) {
      return false;
    }
    String lowered = value.toLowerCase(Locale.ROOT);
    return NON_ORIGINAL_URL_MARKERS.stream().noneMatch(lowered::contains);
  }

  private String originalSourceUrlOrThrow(String value) {
    if (value == null || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "evidence.sourceUrl 값이 필요합니다.");
    }
    if (!isOriginalSourceUrl(value)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "근거는 실제 원 출처 http(s) URL 이어야 합니다(네이버 AI/검색 결과·redirect·placeholder 불가): " + value);
    }
    return value.trim();
  }

  // ----- helper --------------------------------------------------------------------------------

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String blank(String value) {
    return trimToNull(value);
  }

  private String str(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private String require(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 값이 필요합니다.");
    }
    return value.trim();
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private List<String> parseStrings(String value) {
    if (value == null || value.isBlank()) return List.of();
    try {
      return objectMapper.readValue(value,
          objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    } catch (JsonProcessingException e) {
      return List.of();
    }
  }

  // ----- 요청/응답 DTO --------------------------------------------------------------------------

  /**
   * 기존 교육자 보완 요청. identity(이름·학원·지역)·Score·eligibility·publication 필드는 이 record 에
   * 존재하지 않으므로 어떤 값을 보내도 바뀌지 않는다.
   */
  public record EnrichmentRequest(
      String educationPhilosophySummary,
      String curriculumSummary,
      String distinctiveTeachingSummary,
      String recentActivitySummary,
      List<String> strengths,
      List<String> improvementSuggestions,
      boolean overwrite,
      List<EvidenceInput> evidence) {

    static EnrichmentRequest empty() {
      return new EnrichmentRequest(null, null, null, null, null, null, false, List.of());
    }
  }

  public record EvidenceInput(
      String contentType,
      String contentSummary,
      String sourceUrl,
      LocalDate activityDate,
      String evidenceKind,
      Boolean educationallyInformative) {}

  /** 쓰기 전에 검증·정규화를 마친 Evidence(원 출처 URL 확정, 필수 값 채움). */
  private record NormalizedEvidence(
      String sourceUrl,
      String contentType,
      String contentSummary,
      LocalDate activityDate,
      String evidenceKind,
      boolean educationallyInformative) {}
}
