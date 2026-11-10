package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasySiteCollectionCatalog.Category;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasySiteCollectionCatalog.Dataset;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasySiteCollectionCatalog.Site;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EasySiteCollectionCatalogTest {

  @Test
  @DisplayName("given 기관 목록 when 조회 then 코드는 겹치지 않고 주소는 모두 https 공식 주소다")
  void sitesHaveUniqueCodesAndHttpsUrls() {
    // given
    var sites = EasySiteCollectionCatalog.sites();

    // when
    var codes = sites.stream().map(Site::code).toList();

    // then
    assertThat(codes).doesNotHaveDuplicates();
    assertThat(sites).allSatisfy(site -> {
      assertThat(site.url()).startsWith("https://").endsWith("/");
      if (site.businessUrl() != null) {
        assertThat(site.businessUrl()).startsWith("https://");
      }
      assertThat(site.datasets()).isNotEmpty();
    });
  }

  @Test
  @DisplayName("given 은행·카드사·홈택스·여신협회 when 범주별로 보면 then 네 범주가 모두 있고 범주마다 내려받기 순서가 있다")
  void everyCategoryHasSitesAndSteps() {
    // given
    var sites = EasySiteCollectionCatalog.sites();

    // when / then
    for (Category category : Category.values()) {
      assertThat(sites).anyMatch(site -> site.category() == category);
      assertThat(EasySiteCollectionCatalog.steps(category)).isNotEmpty();
    }
    assertThat(sites).filteredOn(site -> site.category() == Category.BANK)
        .allMatch(site -> site.datasets().equals(java.util.List.of(Dataset.BANK_ACCOUNT)));
    assertThat(EasySiteCollectionCatalog.find("CREFIA_CARDSALES")).get()
        .extracting(Site::datasets).isEqualTo(java.util.List.of(Dataset.CARD_SALES));
  }

  @Test
  @DisplayName("given 안전 규칙 when 조회 then 사용자 본인 로그인과 비밀번호 비전송이 들어 있다")
  void safetyRulesForbidCredentialHandling() {
    // when
    var rules = String.join("\n", EasySiteCollectionCatalog.safetyRules());

    // then
    assertThat(rules).contains("사용자 본인이 직접").contains("비밀번호").contains("이체");
  }

  @Test
  @DisplayName("given 없는 코드나 null when find then 비어 있다")
  void unknownCodeIsEmpty() {
    // when / then
    assertThat(EasySiteCollectionCatalog.find("NO_SUCH")).isEmpty();
    assertThat(EasySiteCollectionCatalog.find(null)).isEmpty();
    assertThat(EasySiteCollectionCatalog.find(" KB_BANK ")).isPresent();
  }
}
