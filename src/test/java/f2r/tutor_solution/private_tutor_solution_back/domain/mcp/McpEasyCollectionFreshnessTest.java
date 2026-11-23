package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionFreshnessService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class McpEasyCollectionFreshnessTest {

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("given 사업체 목록이 있는 수집 기관 목록 when withFreshness then 사업체별 매일 수집 점검 결과와 안내가 붙는다")
  void addsCollectionStatusPerBusiness() {
    // given
    var gateway = new McpGateway(null, null, null, null, null, null, null);
    var freshness = mock(EasyCollectionFreshnessService.class);
    var rows = List.<EasyCollectionFreshnessService.Row>of();
    when(freshness.checkToday(1L)).thenReturn(rows);
    ObjectProvider<EasyCollectionFreshnessService> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(freshness);
    ReflectionTestUtils.setField(gateway, "easyFreshness", provider);
    Map<String, Object> catalog = Map.of("businesses", List.of(Map.of("businessId", 1L, "name", "학원")));
    // when
    Map<String, Object> out = gateway.withFreshness(catalog);
    // then
    assertThat(out).containsKeys("businesses", "collectionStatus", "collectionStatusGuide");
    assertThat((List<Map<String, Object>>) out.get("collectionStatus"))
        .containsExactly(Map.of("businessId", 1L, "rows", rows));
  }

  @Test
  @DisplayName("given 점검 서비스가 없음 when withFreshness then 기관 목록을 그대로 돌려준다")
  void returnsCatalogUnchangedWithoutService() {
    // given
    var gateway = new McpGateway(null, null, null, null, null, null, null);
    Map<String, Object> catalog = Map.of("businesses", List.of());
    // when / then
    assertThat(gateway.withFreshness(catalog)).isSameAs(catalog);
  }
}
