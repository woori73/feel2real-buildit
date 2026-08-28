package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartCriteria;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartEducatorRanking;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartRankedEducator;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartRankingList;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartRegionDirectory;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.RankingComputation.RegionKeyed;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BuildIt Ranking / 지역 디렉터리 / 평가 기준 — 전부 read-only.
 *
 * <p><b>Ranking 은 현재 publication 에서 파생</b>한다. 즉 이 서비스는 Score 를 재계산하지도,
 * publication 을 재생성하지도 않는다. 현재 {@code PUBLISHED} publication 의 entry 를 삽입 순서
 * ({@code publication_entry_id ASC} = {@code total_score DESC, candidate_id ASC})대로 읽어
 * 위치를 전국 순위로, 같은 지역 안에서의 위치를 지역 순위로 매긴다. RESEARCH_PENDING 은 애초에
 * publication 에 없으므로 자동 제외된다.
 */
@Service
@Transactional(readOnly = true)
public class ChartRankingService {

  static final int NATIONWIDE_LIMIT = 100;
  static final int MAX_LIMIT = 100;

  static final String RANKING_DISCLAIMER =
      "BuildIt Ranking은 선생님의 실제 수업 능력 순위가 아닙니다. "
      + "BuildIt AI가 온라인에 공개된 교육자의 정보와 활동을 바탕으로 교육철학·커리큘럼·수업방식·전문성 및 "
      + "교육활동이 얼마나 구체적이고 지속적으로 확인되는지를 BuildIt의 동일한 기준으로 분석한 결과입니다. "
      + "실제 수업의 질, 학생 성과 또는 교사의 전체적인 능력을 평가하거나 보증하는 순위가 아닙니다. "
      + "지역 순위와 전국 순위는 동일한 BuildIt Score를 사용하며, 지역별로 평가 기준이나 가중치를 바꾸지 않습니다.";

  private static final String SCORE_DISCLAIMER =
      "BuildIt Score는 공개적으로 확인 가능한 교육 활동과 성장 신호를 BuildIt 기준으로 기록한 지표이며, "
      + "교육자의 인격·절대적 능력·학생 성과를 의미하지 않습니다.";

  private final JdbcTemplate jdbcTemplate;

  public ChartRankingService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  // ----- Ranking ---------------------------------------------------------------------------

  /** 전국 순위 상위 {@code limit} 명 (기본·최대 100 = BuildIt TOP100 큐레이션). */
  public ChartRankingList nationwide(Integer limit) {
    int safeLimit = limit == null || limit <= 0 ? NATIONWIDE_LIMIT : Math.min(limit, MAX_LIMIT);
    List<PublicationRow> rows = currentPublicationRows();
    Map<Long, RankingComputation.Ranked> ranks = RankingComputation.assign(toRegionKeyed(rows));

    List<ChartRankedEducator> educators = new ArrayList<>();
    for (PublicationRow r : rows) {
      RankingComputation.Ranked rk = ranks.get(r.candidateId());
      if (rk.overallRank() > safeLimit) break; // rows 는 이미 순위 순서
      educators.add(toRankedEducator(r, rk));
    }
    return new ChartRankingList("NATIONWIDE", null, null, criteriaVersion(rows),
        currentPublicationId(), educators.size(), educators, RANKING_DISCLAIMER);
  }

  /** 특정 지역(시·도 필수, 시·군·구 선택)의 순위 목록. 지역 순위 오름차순. */
  public ChartRankingList region(String province, String cityDistrict) {
    String prov = blankToNull(province);
    if (prov == null) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "province 가 필요합니다.");
    }
    String city = blankToNull(cityDistrict);
    List<PublicationRow> rows = currentPublicationRows();
    Map<Long, RankingComputation.Ranked> ranks = RankingComputation.assign(toRegionKeyed(rows));

    List<ChartRankedEducator> educators = new ArrayList<>();
    for (PublicationRow r : rows) {
      if (!prov.equals(nz(r.province()))) continue;
      if (city != null && !city.equals(nz(r.cityDistrict()))) continue;
      educators.add(toRankedEducator(r, ranks.get(r.candidateId())));
    }
    educators.sort(java.util.Comparator.comparingInt(ChartRankedEducator::regionRank));
    return new ChartRankingList("REGION", prov, city, criteriaVersion(rows),
        currentPublicationId(), educators.size(), educators, RANKING_DISCLAIMER);
  }

  /** 교육자 1명의 순위 (상세 페이지). publication 에 없으면 {@code ranked=false}. */
  public ChartEducatorRanking forEducator(long candidateId) {
    List<PublicationRow> rows = currentPublicationRows();
    PublicationRow target = rows.stream().filter(r -> r.candidateId() == candidateId).findFirst().orElse(null);
    if (target == null) {
      return ChartEducatorRanking.unranked(candidateId, RANKING_DISCLAIMER);
    }
    RankingComputation.Ranked rk = RankingComputation.assign(toRegionKeyed(rows)).get(candidateId);
    return new ChartEducatorRanking(candidateId, true, target.totalScore(),
        rk.overallRank(), rk.overallTotal(), rk.regionRank(), rk.regionTotal(),
        blankToNull(target.province()), blankToNull(target.cityDistrict()), rk.top100(),
        blankToNull(target.criteriaVersion()), RANKING_DISCLAIMER);
  }

  // ----- 지역 디렉터리 --------------------------------------------------------------------

  /** 공개 대상 교육자가 존재하는 (시·도, 시·군·구) 목록 + 인원. 지역 선택 UI 를 채운다. */
  public ChartRegionDirectory regions() {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
        SELECT c.activity_province AS province, c.activity_city_district AS cityDistrict,
               SUM(c.eligibility_status = 'EVALUATABLE') AS evaluatable,
               SUM(c.eligibility_status = 'RESEARCH_PENDING') AS pending,
               COUNT(*) AS total
          FROM chart_candidates c
         WHERE c.eligibility_status IN ('EVALUATABLE','RESEARCH_PENDING')
           AND (c.excluded_from_chart IS NULL OR c.excluded_from_chart = 0)
           AND c.activity_province IS NOT NULL AND c.activity_province <> ''
         GROUP BY c.activity_province, c.activity_city_district
         ORDER BY c.activity_province, c.activity_city_district
        """);
    Map<String, List<ChartRegionDirectory.District>> byProvince = new LinkedHashMap<>();
    Map<String, Integer> provinceCount = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      String province = str(row.get("province"));
      String city = blankToNull(str(row.get("cityDistrict")));
      int total = intval(row.get("total"));
      int evaluatable = intval(row.get("evaluatable"));
      int pending = intval(row.get("pending"));
      byProvince.computeIfAbsent(province, k -> new ArrayList<>())
          .add(new ChartRegionDirectory.District(city, total, evaluatable, pending));
      provinceCount.merge(province, total, Integer::sum);
    }
    List<ChartRegionDirectory.Province> provinces = new ArrayList<>();
    byProvince.forEach((province, districts) ->
        provinces.add(new ChartRegionDirectory.Province(province, provinceCount.get(province), districts)));
    return new ChartRegionDirectory(provinces);
  }

  // ----- 평가 기준 ----------------------------------------------------------------------

  /**
   * 공개 평가 기준. 항목·가중치는 {@link ChartSiteToolService#score(Map, List)} 산식과 1:1 이어야 한다
   * (테스트로 검증). 사용자-facing 라벨/설명은 코드 의미를 왜곡하지 않는 범위의 한국어.
   */
  public ChartCriteria criteria() {
    return new ChartCriteria(
        ChartSiteToolService.CRITERIA_VERSION, 100,
        List.of(
            new ChartCriteria.Criterion("identity", "교육철학과 차별성", 35,
                "교육자가 무엇을 왜 그렇게 가르치는지 — 자신만의 교육 정체성·교육철학·커리큘럼·차별적 수업 방식이 "
                + "공개정보에 얼마나 구체적으로 기록되어 있는지를 봅니다."),
            new ChartCriteria.Criterion("teaching", "실제 수업 활동", 20,
                "실제 수업 방식과 학생 지도, 오프라인 현장 수업이 공개 근거로 확인되는지를 봅니다."),
            new ChartCriteria.Criterion("persistence", "교육 콘텐츠의 지속성", 15,
                "수업 자료·교재·글·영상 등 교육 활동이 여러 시점에 걸쳐 꾸준히 확인되는지를 봅니다."),
            new ChartCriteria.Criterion("expertise", "전문성", 15,
                "자체 교재, 특정 학년·영역, 교육 콘텐츠에서 축적된 경험과 전문성이 확인되는지를 봅니다."),
            new ChartCriteria.Criterion("verification", "여러 공개 출처의 확인", 10,
                "한 곳이 아니라 서로 다른 여러 공개 출처에서 교육 활동 근거가 교차 확인되는지를 봅니다."),
            new ChartCriteria.Criterion("regional", "지역 교육 활동", 5,
                "해당 지역에서 실제 오프라인 현장 교육을 이어가고 있다는 근거가 확인되는지를 봅니다.")),
        List.of(
            "광고 규모·학원 규모·유명세·프랜차이즈 여부 자체는 점수의 근거나 감점 요소로 쓰지 않습니다.",
            "\"지역 1위\", \"명문대 합격\", \"최고의 강사진\" 같은 광고성 표현은 전문성을 입증하는 근거로 인정하지 않습니다.",
            "학생의 성적·성과, 실제 수업의 질, 교사의 전체적인 능력은 평가하지 않습니다."),
        RANKING_DISCLAIMER, SCORE_DISCLAIMER);
  }

  // ----- 내부 --------------------------------------------------------------------------

  private Long currentPublicationId() {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "SELECT publication_id FROM chart_publications WHERE status='PUBLISHED' "
        + "ORDER BY published_at DESC, publication_id DESC LIMIT 1");
    return rows.isEmpty() ? null : ((Number) rows.get(0).get("publication_id")).longValue();
  }

  /** 현재 publication 의 entry 를 삽입 순서(= 순위 순서)대로. 공개 제외자는 뺀다. */
  private List<PublicationRow> currentPublicationRows() {
    Long pubId = currentPublicationId();
    if (pubId == null) return List.of();
    return jdbcTemplate.query("""
        SELECT pe.candidate_id AS candidateId, ss.total_score AS totalScore,
               ss.criteria_version AS criteriaVersion,
               c.name, c.role, c.academy_name AS academyName, c.activity_region AS activityRegion,
               c.activity_province AS province, c.activity_city_district AS cityDistrict,
               c.identity_summary AS identitySummary, c.profile_image_url AS profileImageUrl,
               COALESCE(NULLIF(c.education_philosophy_summary,''), c.education_philosophy) AS educationPhilosophy
          FROM chart_publication_entries pe
          JOIN chart_score_snapshots ss ON ss.score_snapshot_id = pe.score_snapshot_id
          JOIN chart_candidates c ON c.candidate_id = pe.candidate_id
         WHERE pe.publication_id = ?
           AND (c.excluded_from_chart IS NULL OR c.excluded_from_chart = 0)
         ORDER BY pe.publication_entry_id ASC
        """, (rs, i) -> new PublicationRow(
            rs.getLong("candidateId"), rs.getInt("totalScore"), rs.getString("criteriaVersion"),
            rs.getString("name"), rs.getString("role"), rs.getString("academyName"),
            rs.getString("activityRegion"), rs.getString("province"), rs.getString("cityDistrict"),
            rs.getString("identitySummary"), rs.getString("profileImageUrl"),
            rs.getString("educationPhilosophy")),
        pubId);
  }

  private static List<RegionKeyed> toRegionKeyed(List<PublicationRow> rows) {
    List<RegionKeyed> out = new ArrayList<>(rows.size());
    for (PublicationRow r : rows) out.add(new RegionKeyed(r.candidateId(), r.province(), r.cityDistrict()));
    return out;
  }

  private static ChartRankedEducator toRankedEducator(PublicationRow r, RankingComputation.Ranked rk) {
    return new ChartRankedEducator(
        r.candidateId(), r.name(), r.role(), blankToNull(r.academyName()),
        blankToNull(r.province()), blankToNull(r.cityDistrict()), blankToNull(r.activityRegion()),
        blankToNull(r.identitySummary()), blankToNull(r.educationPhilosophy()),
        blankToNull(r.profileImageUrl()), r.totalScore(),
        rk.overallRank(), rk.overallTotal(), rk.regionRank(), rk.regionTotal(), rk.top100());
  }

  private static String criteriaVersion(List<PublicationRow> rows) {
    return rows.isEmpty() ? ChartSiteToolService.CRITERIA_VERSION
        : (blankToNull(rows.get(0).criteriaVersion()) != null
            ? rows.get(0).criteriaVersion() : ChartSiteToolService.CRITERIA_VERSION);
  }

  private static String str(Object v) { return v == null ? null : String.valueOf(v); }
  private static String nz(String s) { return s == null ? "" : s.trim(); }
  private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
  private static int intval(Object v) { return v instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(v)); }

  record PublicationRow(long candidateId, int totalScore, String criteriaVersion, String name, String role,
                        String academyName, String activityRegion, String province, String cityDistrict,
                        String identitySummary, String profileImageUrl, String educationPhilosophy) {}
}
