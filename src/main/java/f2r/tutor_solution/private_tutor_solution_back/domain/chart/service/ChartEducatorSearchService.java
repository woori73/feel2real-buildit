package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicEducatorDetail;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicEducatorPage;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicEducatorSummary;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicEvidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicOfficialChannels;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * BuildIt Chart 공개 교육자 검색/상세. TOP100 publication({@code ChartPublicService})과 분리된,
 * "전국에 흩어진 초·중·고 영어교육자 공개정보"를 일반인이 검색·열람하는 경로다.
 *
 * <p>공개 대상: {@code eligibility_status IN ('EVALUATABLE','RESEARCH_PENDING')} 이고
 * {@code excluded_from_chart} 가 아닌 후보. {@code RESEARCH_PENDING} 도 검색·상세에 노출하되
 * Score·순위는 어느 응답에도 담지 않는다. 개인 연락처·내부 판정 JSON·운영 메모는 노출하지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class ChartEducatorSearchService {

  /** 공개 대상 eligibility 값. VARCHAR 그대로 매칭한다(V954 이후 Legacy 50명도 EVALUATABLE). */
  static final List<String> PUBLIC_STATUSES = List.of("EVALUATABLE", "RESEARCH_PENDING");
  static final int DEFAULT_SIZE = 20;
  static final int MAX_SIZE = 100;
  private static final char LIKE_ESCAPE = '!';

  private static final String PUBLIC_WHERE =
      " c.eligibility_status IN ('EVALUATABLE','RESEARCH_PENDING')"
      + " AND (c.excluded_from_chart IS NULL OR c.excluded_from_chart = 0) ";

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ChartEducatorSearchService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  // ----- 검색 --------------------------------------------------------------------------------

  public ChartPublicEducatorPage search(String province, String cityDistrict, String q,
      String status, Integer page, Integer size) {
    List<String> statuses = resolveStatuses(status);
    int safePage = clampPage(page);
    int safeSize = clampSize(size);

    StringBuilder where = new StringBuilder(" WHERE").append(PUBLIC_WHERE);
    List<Object> args = new ArrayList<>();

    if (statuses.size() == 1) {
      where.append(" AND c.eligibility_status = ?");
      args.add(statuses.get(0));
    }
    String prov = blankToNull(province);
    if (prov != null) {
      where.append(" AND c.activity_province = ?");
      args.add(prov);
    }
    String city = blankToNull(cityDistrict);
    if (city != null) {
      where.append(" AND c.activity_city_district = ?");
      args.add(city);
    }
    String term = blankToNull(q);
    if (term != null) {
      where.append(" AND (LOWER(c.name) LIKE ? ESCAPE '!'")
          .append(" OR LOWER(c.academy_name) LIKE ? ESCAPE '!'")
          .append(" OR LOWER(c.activity_region) LIKE ? ESCAPE '!'")
          .append(" OR LOWER(c.identity_summary) LIKE ? ESCAPE '!')");
      String like = likeArg(term);
      args.add(like);
      args.add(like);
      args.add(like);
      args.add(like);
    }

    Long total = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_candidates c" + where, Long.class, args.toArray());
    long totalElements = total == null ? 0L : total;

    List<ChartPublicEducatorSummary> content = List.of();
    if (totalElements > 0 && (long) safePage * safeSize < totalElements) {
      List<Object> pageArgs = new ArrayList<>(args);
      pageArgs.add(safeSize);
      pageArgs.add(safePage * safeSize);
      content = jdbcTemplate.queryForList(
          """
          SELECT c.candidate_id, c.name, c.role, c.academy_name, c.activity_region,
                 c.activity_province, c.activity_city_district, c.identity_summary,
                 COALESCE(NULLIF(c.education_philosophy_summary,''), c.education_philosophy) AS education_philosophy,
                 c.eligibility_status, c.profile_image_url, c.discovered_at, c.updated_at
            FROM chart_candidates c
          """ + where + """
           ORDER BY c.updated_at DESC, c.candidate_id DESC
           LIMIT ? OFFSET ?
          """,
          pageArgs.toArray())
          .stream().map(this::toSummary).toList();
    }
    return ChartPublicEducatorPage.of(content, totalElements, safePage, safeSize);
  }

  // ----- 상세 --------------------------------------------------------------------------------

  public ChartPublicEducatorDetail getById(long id) {
    Map<String, Object> row = jdbcTemplate.queryForList(
        """
        SELECT c.candidate_id, c.name, c.role, c.academy_name, c.activity_region,
               c.activity_province, c.activity_city_district, c.identity_summary,
               COALESCE(NULLIF(c.education_philosophy_summary,''), c.education_philosophy) AS education_philosophy,
               COALESCE(NULLIF(c.curriculum_summary,''), c.curriculum_features) AS curriculum,
               COALESCE(NULLIF(c.distinctive_teaching_summary,''), c.teaching_features) AS distinctive_teaching,
               c.recent_activity_summary, c.strengths_json, c.improvement_suggestions_json, c.growth_advice,
               c.discovery_reason, c.profile_image_url,
               c.official_homepage, c.official_blog, c.official_youtube, c.official_social,
               c.eligibility_status, c.discovered_at, c.updated_at
          FROM chart_candidates c
         WHERE c.candidate_id = ? AND
        """ + PUBLIC_WHERE, id)
        .stream().findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공개된 교육자를 찾을 수 없습니다."));

    List<Map<String, Object>> activityRows = jdbcTemplate.queryForList(
        """
        SELECT content_type, content_summary, source_url, activity_date, evidence_kind, researched_at
          FROM chart_candidate_activities
         WHERE candidate_id = ?
         ORDER BY activity_date DESC, activity_id DESC
        """, id);

    return toDetail(row, activityRows);
  }

  // ----- 매핑 (순수 함수, 테스트 대상) -------------------------------------------------------

  ChartPublicEducatorSummary toSummary(Map<String, Object> row) {
    String status = str(row.get("eligibility_status"));
    return new ChartPublicEducatorSummary(
        num(row.get("candidate_id")),
        str(row.get("name")),
        str(row.get("role")),
        blankToNull(str(row.get("academy_name"))),
        blankToNull(str(row.get("activity_province"))),
        blankToNull(str(row.get("activity_city_district"))),
        blankToNull(str(row.get("activity_region"))),
        blankToNull(str(row.get("identity_summary"))),
        blankToNull(str(row.get("education_philosophy"))),
        status,
        "RESEARCH_PENDING".equals(status),
        blankToNull(str(row.get("profile_image_url"))),
        lastResearchedAt(dateTime(row.get("updated_at")), dateTime(row.get("discovered_at")), List.of()));
  }

  ChartPublicEducatorDetail toDetail(Map<String, Object> row, List<Map<String, Object>> activityRows) {
    String status = str(row.get("eligibility_status"));
    boolean pending = "RESEARCH_PENDING".equals(status);

    List<ChartPublicEvidence> evidence = new ArrayList<>();
    List<LocalDateTime> researchedAts = new ArrayList<>();
    for (Map<String, Object> a : activityRows) {
      evidence.add(new ChartPublicEvidence(
          blankToNull(str(a.get("content_type"))),
          blankToNull(str(a.get("content_summary"))),
          blankToNull(str(a.get("source_url"))),
          date(a.get("activity_date")),
          blankToNull(str(a.get("evidence_kind")))));
      LocalDateTime r = dateTime(a.get("researched_at"));
      if (r != null) researchedAts.add(r);
    }

    List<String> improvements = parseStringArray(row.get("improvement_suggestions_json"));
    String growthAdvice = blankToNull(str(row.get("growth_advice")));
    if (improvements.isEmpty() && growthAdvice != null) {
      improvements = List.of(growthAdvice);
    }

    ChartPublicOfficialChannels channels = new ChartPublicOfficialChannels(
        blankToNull(str(row.get("official_homepage"))),
        blankToNull(str(row.get("official_blog"))),
        blankToNull(str(row.get("official_youtube"))),
        blankToNull(str(row.get("official_social"))));

    return new ChartPublicEducatorDetail(
        num(row.get("candidate_id")),
        str(row.get("name")),
        str(row.get("role")),
        blankToNull(str(row.get("academy_name"))),
        blankToNull(str(row.get("activity_province"))),
        blankToNull(str(row.get("activity_city_district"))),
        blankToNull(str(row.get("activity_region"))),
        blankToNull(str(row.get("identity_summary"))),
        blankToNull(str(row.get("education_philosophy"))),
        blankToNull(str(row.get("curriculum"))),
        blankToNull(str(row.get("distinctive_teaching"))),
        blankToNull(str(row.get("recent_activity_summary"))),
        parseStringArray(row.get("strengths_json")),
        improvements,
        channels.isEmpty() ? null : channels,
        blankToNull(str(row.get("profile_image_url"))),
        evidence,
        blankToNull(str(row.get("discovery_reason"))),
        lastResearchedAt(dateTime(row.get("updated_at")), dateTime(row.get("discovered_at")), researchedAts),
        status,
        pending,
        disclaimer(pending));
  }

  static String disclaimer(boolean pending) {
    if (pending) {
      return "이 교육자는 아직 평가에 필요한 공개 근거가 충분히 확인되지 않았습니다(RESEARCH_PENDING). "
          + "공개적으로 확인된 사실만 표시하며, BuildIt Score나 TOP100 순위 대상이 아닙니다.";
    }
    return "이 화면은 지역 교육자 검색·발견용입니다. 공개적으로 확인된 교육 활동을 구조화해 보여주며, "
        + "BuildIt Score·TOP100 순위는 표시하지 않습니다.";
  }

  // ----- 입력 정규화 (순수 함수, 테스트 대상) ---------------------------------------------

  List<String> resolveStatuses(String status) {
    String s = blankToNull(status);
    if (s == null) return PUBLIC_STATUSES;
    String upper = s.toUpperCase(Locale.ROOT);
    if (PUBLIC_STATUSES.contains(upper)) return List.of(upper);
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "status 는 EVALUATABLE 또는 RESEARCH_PENDING 만 허용됩니다.");
  }

  int clampPage(Integer page) {
    return page == null || page < 0 ? 0 : page;
  }

  int clampSize(Integer size) {
    if (size == null || size <= 0) return DEFAULT_SIZE;
    return Math.min(size, MAX_SIZE);
  }

  /** LIKE 메타문자(!, %, _)를 ESCAPE '!' 기준으로 이스케이프하고 부분일치 패턴으로 감싼다. */
  static String likeArg(String raw) {
    String lowered = raw.toLowerCase(Locale.ROOT);
    StringBuilder out = new StringBuilder(lowered.length() + 8).append('%');
    for (int i = 0; i < lowered.length(); i++) {
      char ch = lowered.charAt(i);
      if (ch == LIKE_ESCAPE || ch == '%' || ch == '_') out.append(LIKE_ESCAPE);
      out.append(ch);
    }
    return out.append('%').toString();
  }

  // ----- 값 변환 helper ------------------------------------------------------------------

  private List<String> parseStringArray(Object json) {
    if (json == null) return List.of();
    String text = String.valueOf(json);
    if (text.isBlank()) return List.of();
    try {
      return objectMapper.readValue(text,
          objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    } catch (Exception e) {
      return List.of();
    }
  }

  private static LocalDateTime lastResearchedAt(LocalDateTime updatedAt, LocalDateTime discoveredAt,
      List<LocalDateTime> activityResearchedAts) {
    LocalDateTime best = null;
    for (LocalDateTime candidate : activityResearchedAts) {
      if (candidate != null && (best == null || candidate.isAfter(best))) best = candidate;
    }
    if (best != null) return best;
    if (updatedAt != null) return updatedAt;
    return discoveredAt;
  }

  private static String str(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static long num(Object value) {
    return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
  }

  private static LocalDateTime dateTime(Object value) {
    if (value == null) return null;
    if (value instanceof LocalDateTime ldt) return ldt;
    if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
    if (value instanceof java.time.OffsetDateTime odt) return odt.toLocalDateTime();
    try {
      return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    } catch (Exception e) {
      return null;
    }
  }

  private static LocalDate date(Object value) {
    if (value == null) return null;
    if (value instanceof LocalDate ld) return ld;
    if (value instanceof java.sql.Date d) return d.toLocalDate();
    if (value instanceof java.time.LocalDateTime ldt) return ldt.toLocalDate();
    try {
      return LocalDate.parse(String.valueOf(value).substring(0, 10));
    } catch (Exception e) {
      return null;
    }
  }

}
