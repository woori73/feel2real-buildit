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
 * <p><b>Ranking 은 publication 과 독립</b>이다. TOP100 publication 이 아니라
 * {@code chart_candidates}(공개 대상) × candidate 별 <b>최신 유효 Score snapshot</b> 에서 직접 계산한다.
 *
 * <pre>Archive → Eligibility → Score → Overall/Regional Ranking → (downstream) TOP100 Publication</pre>
 *
 * <ul>
 *   <li>pool = {@code (eligibility_status='EVALUATABLE' OR legacy_source IS NOT NULL)} AND 미제외
 *       AND {@link ChartSiteToolService#PUBLISHABLE_PROFILE_SQL}(PR-A 의 grandfather 규칙 그대로)
 *       AND 최신 Score snapshot 존재. RESEARCH_PENDING / Score 없는 후보는 자동 제외.</li>
 *   <li>정렬 = {@code total_score DESC, candidate_id ASC} — {@code autoPublishNationwide} 와 동일.
 *       동점은 candidate_id 오름차순으로 갈리며, 이는 순위 산정 규칙일 뿐 우열의 의미가 아니다.</li>
 *   <li>전국 순위 = pool 안의 위치(1..N). 지역 순위 = 같은 (province, cityDistrict) 안의 위치.
 *       TOP100 = 전국 순위 &le; 100.</li>
 * </ul>
 *
 * <p>{@code /api/chart/publication/current}(기존 TOP100)은 이 서비스가 읽지도 쓰지도 않는다.
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

  /**
   * Ranking pool. candidate 별 최신 Score snapshot 을 붙이고 PR-A 의 eligibility/grandfather 규칙으로 거른 뒤
   * {@code total_score DESC, candidate_id ASC} 로 정렬한다. publication 을 참조하지 않는다.
   */
  private static final String POOL_SQL =
      """
      SELECT c.candidate_id AS candidateId, s.total_score AS totalScore, s.criteria_version AS criteriaVersion,
             c.name AS name, c.role AS role, c.academy_name AS academyName, c.activity_region AS activityRegion,
             c.activity_province AS province, c.activity_city_district AS cityDistrict,
             c.identity_summary AS identitySummary, c.profile_image_url AS profileImageUrl,
             COALESCE(NULLIF(c.education_philosophy_summary,''), c.education_philosophy) AS educationPhilosophy
        FROM chart_candidates c
        JOIN chart_score_snapshots s
          ON s.score_snapshot_id = (SELECT s2.score_snapshot_id FROM chart_score_snapshots s2
                                     WHERE s2.candidate_id = c.candidate_id
                                     ORDER BY s2.calculated_at DESC, s2.score_snapshot_id DESC LIMIT 1)
       WHERE (c.eligibility_status = 'EVALUATABLE' OR c.legacy_source IS NOT NULL)
         AND (c.excluded_from_chart IS NULL OR c.excluded_from_chart = 0)
         AND""" + ChartSiteToolService.PUBLISHABLE_PROFILE_SQL + """
       ORDER BY s.total_score DESC, c.candidate_id ASC
      """;

  private final JdbcTemplate jdbcTemplate;

  public ChartRankingService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  // ----- Ranking ---------------------------------------------------------------------------

  /** 전국 순위 상위 {@code limit} 명 (기본·최대 100 = BuildIt TOP100 큐레이션 대상). */
  public ChartRankingList nationwide(Integer limit) {
    int safeLimit = limit == null || limit <= 0 ? NATIONWIDE_LIMIT : Math.min(limit, MAX_LIMIT);
    List<PoolRow> pool = rankingPool();
    Map<Long, RankingComputation.Ranked> ranks = RankingComputation.assign(toRegionKeyed(pool));

    List<ChartRankedEducator> educators = new ArrayList<>();
    for (PoolRow r : pool) {
      RankingComputation.Ranked rk = ranks.get(r.candidateId());
      if (rk.overallRank() > safeLimit) break; // pool 은 이미 순위 순서
      educators.add(toRankedEducator(r, rk));
    }
    return new ChartRankingList("NATIONWIDE", null, null, criteriaVersion(pool), pool.size(),
        educators, RANKING_DISCLAIMER);
  }

  /** 특정 지역(시·도 필수, 시·군·구 선택)의 순위 목록. 지역 순위 오름차순. */
  public ChartRankingList region(String province, String cityDistrict) {
    String prov = blankToNull(province);
    if (prov == null) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "province 가 필요합니다.");
    }
    String city = blankToNull(cityDistrict);
    List<PoolRow> pool = rankingPool();
    Map<Long, RankingComputation.Ranked> ranks = RankingComputation.assign(toRegionKeyed(pool));

    List<ChartRankedEducator> educators = new ArrayList<>();
    for (PoolRow r : pool) {
      if (!prov.equals(nz(r.province()))) continue;
      if (city != null && !city.equals(nz(r.cityDistrict()))) continue;
      educators.add(toRankedEducator(r, ranks.get(r.candidateId())));
    }
    educators.sort(java.util.Comparator.comparingInt(ChartRankedEducator::regionRank));
    return new ChartRankingList("REGION", prov, city, criteriaVersion(pool), educators.size(),
        educators, RANKING_DISCLAIMER);
  }

  /** 교육자 1명의 순위 (상세 페이지). pool 에 없으면(RESEARCH_PENDING·Score 없음·미존재) {@code ranked=false}. */
  public ChartEducatorRanking forEducator(long candidateId) {
    List<PoolRow> pool = rankingPool();
    PoolRow target = pool.stream().filter(r -> r.candidateId() == candidateId).findFirst().orElse(null);
    if (target == null) {
      return ChartEducatorRanking.unranked(candidateId, RANKING_DISCLAIMER);
    }
    RankingComputation.Ranked rk = RankingComputation.assign(toRegionKeyed(pool)).get(candidateId);
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

  /** publication 을 참조하지 않는 Ranking pool (이미 순위 순서로 정렬됨). */
  private List<PoolRow> rankingPool() {
    return jdbcTemplate.query(POOL_SQL, (rs, i) -> new PoolRow(
        rs.getLong("candidateId"), rs.getInt("totalScore"), rs.getString("criteriaVersion"),
        rs.getString("name"), rs.getString("role"), rs.getString("academyName"),
        rs.getString("activityRegion"), rs.getString("province"), rs.getString("cityDistrict"),
        rs.getString("identitySummary"), rs.getString("profileImageUrl"),
        rs.getString("educationPhilosophy")));
  }

  private static List<RegionKeyed> toRegionKeyed(List<PoolRow> rows) {
    List<RegionKeyed> out = new ArrayList<>(rows.size());
    for (PoolRow r : rows) out.add(new RegionKeyed(r.candidateId(), r.province(), r.cityDistrict()));
    return out;
  }

  private static ChartRankedEducator toRankedEducator(PoolRow r, RankingComputation.Ranked rk) {
    return new ChartRankedEducator(
        r.candidateId(), r.name(), r.role(), blankToNull(r.academyName()),
        blankToNull(r.province()), blankToNull(r.cityDistrict()), blankToNull(r.activityRegion()),
        blankToNull(r.identitySummary()), blankToNull(r.educationPhilosophy()),
        blankToNull(r.profileImageUrl()), r.totalScore(),
        rk.overallRank(), rk.overallTotal(), rk.regionRank(), rk.regionTotal(), rk.top100());
  }

  private static String criteriaVersion(List<PoolRow> pool) {
    if (!pool.isEmpty() && blankToNull(pool.get(0).criteriaVersion()) != null) {
      return pool.get(0).criteriaVersion();
    }
    return ChartSiteToolService.CRITERIA_VERSION;
  }

  private static String str(Object v) { return v == null ? null : String.valueOf(v); }
  private static String nz(String s) { return s == null ? "" : s.trim(); }
  private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
  private static int intval(Object v) { return v instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(v)); }

  record PoolRow(long candidateId, int totalScore, String criteriaVersion, String name, String role,
                 String academyName, String activityRegion, String province, String cityDistrict,
                 String identitySummary, String profileImageUrl, String educationPhilosophy) {}
}
