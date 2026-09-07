package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermMaterialSourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermMaterialVisibility;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.repository.WikiAnalysisMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.service.WikiAnalysisMaterialService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** 자료 공개 범위 판정이 프런트가 아니라 서버에서 이뤄지는지 못박는다(§14·§37). */
class MidtermMaterialAccessServiceTest {

  private AuthenticatedUserResolver authResolver;
  private WikiAnalysisMaterialRepository wikiRepo;
  private WikiAnalysisMaterialService wikiService;
  private MidtermMaterialAccessService service;

  private final Optional<JwtTokenProvider.AuthenticatedPrincipal> anon = Optional.empty();
  private final Optional<JwtTokenProvider.AuthenticatedPrincipal> member =
      Optional.of(new JwtTokenProvider.AuthenticatedPrincipal(7L, "parent", "STUDENT"));

  @BeforeEach
  void setUp() {
    authResolver = mock(AuthenticatedUserResolver.class);
    wikiRepo = mock(WikiAnalysisMaterialRepository.class);
    wikiService = mock(WikiAnalysisMaterialService.class);
    service = new MidtermMaterialAccessService(authResolver, wikiRepo, wikiService);
  }

  private MidtermMaterial material(MidtermMaterialVisibility visibility, boolean published) {
    return MidtermMaterial.builder()
        .campaignId(1L).category("WORKSHEET").displayTitle("5과 학습지")
        .sourceType(MidtermMaterialSourceType.WIKI_ANALYSIS).sourceMaterialId(99L)
        .visibility(visibility).published(published).build();
  }

  @Test
  void privateMaterial_isNotListable_andHasNoFileAccess() {
    MidtermMaterial m = material(MidtermMaterialVisibility.PRIVATE, true);
    assertThat(service.isListable(m)).isFalse();
    assertThat(service.resolveAccess(m, anon)).isEqualTo(MidtermMaterialAccessService.Access.NONE);
    assertThatThrownBy(() -> service.readFile(m, anon)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void unpublishedMaterial_isNeverAccessible_evenIfPublicVisibility() {
    MidtermMaterial m = material(MidtermMaterialVisibility.PUBLIC, false);
    assertThat(service.isListable(m)).isFalse();
    assertThat(service.resolveAccess(m, member)).isEqualTo(MidtermMaterialAccessService.Access.NONE);
  }

  @Test
  void previewMaterial_isListable_butFileIsBlocked() {
    MidtermMaterial m = material(MidtermMaterialVisibility.PREVIEW, true);
    assertThat(service.isListable(m)).isTrue();
    assertThat(service.resolveAccess(m, anon)).isEqualTo(MidtermMaterialAccessService.Access.PREVIEW);
    assertThat(service.canExposeFileUrl(service.resolveAccess(m, anon))).isFalse();
    assertThatThrownBy(() -> service.readFile(m, anon)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void memberMaterial_requiresPrincipalForFile() {
    MidtermMaterial m = material(MidtermMaterialVisibility.MEMBER, true);
    assertThat(service.resolveAccess(m, anon)).isEqualTo(MidtermMaterialAccessService.Access.MEMBER_LOCKED);
    assertThat(service.resolveAccess(m, member)).isEqualTo(MidtermMaterialAccessService.Access.MEMBER);
    assertThatThrownBy(() -> service.readFile(m, anon)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void publicMaterial_servesUnderlyingWikiFile() {
    MidtermMaterial m = material(MidtermMaterialVisibility.PUBLIC, true);
    when(wikiService.getPublicDownload(99L)).thenReturn(
        new WikiAnalysisMaterialService.DownloadFile(new byte[] {1, 2}, "f.pdf", "application/pdf"));

    WikiAnalysisMaterialService.DownloadFile file = service.readFile(m, anon);

    assertThat(file.bytes()).hasSize(2);
    assertThat(service.canExposeFileUrl(service.resolveAccess(m, anon))).isTrue();
  }
}
