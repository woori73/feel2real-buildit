package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * PR-C 공개 화면 계약. 지역 발견을 1차 진입으로, TOP100 은 하위 큐레이션으로,
 * 교육자는 modal 이 아니라 공개 상세 URL 로 연결한다.
 */
class ChartPublicPageContractTest {

  @Test
  void chartPage_leadsWithRegionDiscovery_keepsTop100_asSecondarySection() throws IOException {
    String html = read("templates/public/chart.html");
    String js = read("static/js/chart_public.js");

    // 지역 선택이 1차 진입
    assertThat(html).contains("id=\"provinceSelect\"", "id=\"districtSelect\"", "id=\"regionPicker\"");
    assertThat(html).contains("동네 실력있는 영어쌤");
    // 평가 기준 + Ranking disclaimer
    assertThat(html).contains("id=\"criteria\"", "AI는 무엇을 보고 평가하나요?", "id=\"rankingDisclaimer\"");
    // TOP100 은 유지하되 하위 섹션
    assertThat(html).contains("id=\"top100\"", "BuildIt TOP100");
    assertThat(html).contains("실제 수업 능력 순위가 아닙니다");
    // 서비스명 유지
    assertThat(html).contains("BuildIt Chart");

    // JS: 새 공개 API 사용
    assertThat(js).contains("/api/chart/regions", "/api/chart/educators/search", "/api/chart/rankings",
        "/api/chart/criteria", "/api/chart/publication/current");
    // 교육자는 상세 URL 로 (modal 없음)
    assertThat(js).contains("/chart/educators/");
    assertThat(js).doesNotContain("openModal", "modal-backdrop", "profile-sheet");
    // 검색 결과에 Score 를 노출하는 건 ranking 데이터가 있을 때만(카드에 하드코딩 금지)
    assertThat(js).contains("rankMap");
  }

  @Test
  void educatorDetailPage_hasStableUrlAndFactSections_noModal() throws IOException {
    String html = read("templates/public/chart_educator.html");
    String js = read("static/js/chart_educator.js");

    assertThat(html).contains("data-educator-id", "detail-hero", "id=\"detailRanking\"",
        "id=\"blockPhilosophy\"", "id=\"blockCurriculum\"", "id=\"blockEvidence\"", "id=\"detailPending\"");
    assertThat(html).contains("평가 기준 보기");
    assertThat(js).contains("/api/chart/educators/", "/api/chart/rankings/");
    // 순위 없으면 순위 영역 미표시
    assertThat(js).contains("rk.ranked");
  }

  @Test
  void landingChartScript_keepsRegionApiContract_andHomeHasNoNameSearch() throws IOException {
    // Feel2Real | EDUNOVA 홈에는 CHART 영역이 없다. 스크립트 계약은 /chart 화면용으로 유지한다.
    String html = read("templates/public/landing.html");
    String js = read("static/js/landing_chart.js");

    assertThat(html).doesNotContain("id=\"chartNameSearch\"", "교육자 이름 검색");
    assertThat(js).contains("/api/chart/regions", "/chart?");
  }

  private String read(String path) throws IOException {
    try (var input = new ClassPathResource(path).getInputStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
