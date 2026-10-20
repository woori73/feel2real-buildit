package f2r.tutor_solution.private_tutor_solution_back.domain.academycrafthelp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HelpCatalogTest {

  private static final String VALID = "---\nid: X-1\nslug: x\ntitle: T\naudience: ALL\nvisibility: STAFF\nstatus: REVIEWED\n"
      + "featureState: OPERATING\nappliesTo: v1\nlastVerified: 2026-10-20\nsource: s\nrelated: a, b\n---\n본문";

  @Test
  void parse_validFrontMatter_returnsDoc() {
    // given / when
    HelpDoc d = HelpDocParser.parse(VALID);
    // then
    assertThat(d.id()).isEqualTo("X-1");
    assertThat(d.related()).containsExactly("a", "b");
    assertThat(d.body()).isEqualTo("본문");
  }

  @Test
  void parse_missingRequiredMeta_throws() {
    // given
    String broken = VALID.replace("appliesTo: v1\n", "");
    // when / then
    assertThatThrownBy(() -> HelpDocParser.parse(broken)).hasMessageContaining("appliesTo");
  }

  @Test
  void parse_noFrontMatter_throws() {
    assertThatThrownBy(() -> HelpDocParser.parse("본문만")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void realCatalog_loadsTwelveDocsWithUniqueIdsAndResolvableRelatedLinks() throws Exception {
    // given / when
    HelpCatalog c = new HelpCatalog(List.of("REVIEW", "REVIEWED"));
    List<HelpDoc> all = c.viewable("ADMIN");
    // then
    assertThat(all).hasSize(12);
    assertThat(all.stream().map(HelpDoc::id).collect(Collectors.toSet())).hasSize(12);
    Set<String> slugs = all.stream().map(HelpDoc::slug).collect(Collectors.toSet());
    all.forEach(d -> assertThat(slugs).containsAll(d.related()));
  }

  @Test
  void realCatalog_tutorCannotSeeAdminOnlyDoc_adminSeesAll() throws Exception {
    // given
    HelpCatalog c = new HelpCatalog(List.of("REVIEW"));
    // when / then
    assertThat(c.find("admin-guide", "TUTOR")).isEmpty();
    assertThat(c.find("tutor-guide", "TUTOR")).isPresent();
    assertThat(c.find("admin-guide", "ADMIN")).isPresent();
    assertThat(c.find("admin-guide", "STUDENT")).isEmpty();
  }

  @Test
  void realCatalog_unreleasedFeatureNeverMarkedOperating() throws Exception {
    // given / when
    HelpDoc ai = new HelpCatalog(List.of("REVIEW")).find("ai-assistant", "ADMIN").orElseThrow();
    // then
    assertThat(ai.featureState()).isEqualTo("DEV_DONE_UNDEPLOYED");
    assertThat(ai.featureStateLabel()).isEqualTo("개발 완료·미배포");
  }

  @Test
  void defaultCatalog_reviewDocsAreNotAiSources() throws Exception {
    // given / when
    HelpCatalog c = new HelpCatalog(List.of("REVIEWED"));
    // then
    assertThat(c.aiSources("ADMIN")).isEmpty();
    assertThat(c.viewable("ADMIN")).isNotEmpty();
  }

  @Test
  void policy_draftIsNeverViewable() {
    // given
    HelpDoc draft = HelpDocParser.parse(VALID.replace("REVIEWED", "DRAFT"));
    HelpAccessPolicy policy = new HelpAccessPolicy(Set.of("DRAFT", "REVIEWED"));
    // when / then
    assertThat(policy.canView(draft, "ADMIN")).isFalse();
  }
}
