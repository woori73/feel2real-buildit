package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiCampaignScope;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaign;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaignState;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermWikiSourceExposure;
import f2r.tutor_solution.private_tutor_solution_back.config.MidtermExamScopeProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermCampaignRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermWikiSourceExposureRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class MidtermWikiSourceServiceTest {

  private AcademyRepository academyRepository;
  private MidtermCampaignRepository campaignRepository;
  private MidtermWikiSourceExposureRepository exposureRepository;
  private MidtermWikiSourceGateway gateway;
  private MidtermWikiSourceService service;
  private Academy academy;
  private MidtermCampaign campaign;
  private WikiSourceRow candidate;

  @BeforeEach
  void setUp() {
    academyRepository = mock(AcademyRepository.class);
    campaignRepository = mock(MidtermCampaignRepository.class);
    exposureRepository = mock(MidtermWikiSourceExposureRepository.class);
    gateway = mock(MidtermWikiSourceGateway.class);
    service = new MidtermWikiSourceService(
        academyRepository, campaignRepository, exposureRepository, gateway,
        new MidtermExamScopePolicy(new MidtermExamScopeProperties(), gateway));

    academy = mock(Academy.class);
    User tutor = mock(User.class);
    when(academy.getAcademyId()).thenReturn(1L);
    when(academy.getTutor()).thenReturn(tutor);
    when(tutor.getUserId()).thenReturn(7L);
    when(tutor.getLoginId()).thenReturn("tutor");
    when(tutor.getRole()).thenReturn(Role.TUTOR);
    when(academyRepository.findByHandle("eduholic")).thenReturn(Optional.of(academy));

    campaign = MidtermCampaign.builder()
        .academy(academy).examName("2026 2학기 중간고사").examYear(2026)
        .semester("2학기").examType("중간고사")
        .state(MidtermCampaignState.PREPARING).published(true).build();
    setId(campaign, "campaignId", 10L);
    when(campaignRepository.findByAcademy_AcademyId(1L)).thenReturn(Optional.of(campaign));

    candidate = new WikiSourceRow(
        "school-print", 123L, "", "신구중", "3학년",
        2026, "2학기", "중간고사", "2026-09-07T12:00:00");
    when(gateway.listCandidates(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.eq(new WikiCampaignScope(2026, "2학기", "중간고사"))))
        .thenReturn(List.of(candidate));
  }

  @Test
  void candidateWithoutExposure_isHidden() {
    when(exposureRepository
        .findByCampaignIdAndVisibleTrueOrderBySortOrderAscExposureIdAsc(10L))
        .thenReturn(List.of());

    assertThat(service.listSources("eduholic").views()).isEmpty();
  }

  @Test
  void hiddenExposure_isNotQueriedAsPublic() {
    when(exposureRepository
        .findByCampaignIdAndVisibleTrueOrderBySortOrderAscExposureIdAsc(10L))
        .thenReturn(List.of());

    assertThat(service.listSources("eduholic").views()).isEmpty();
  }

  @Test
  void zeroSchoolCollections_candidateAndVisibleExposure_returnsOne() {
    MidtermWikiSourceExposure exposure = exposure(true);
    when(exposureRepository
        .findByCampaignIdAndVisibleTrueOrderBySortOrderAscExposureIdAsc(10L))
        .thenReturn(List.of(exposure));

    assertThat(service.listSources("eduholic").views())
        .singleElement()
        .satisfies(view -> {
          assertThat(view.sourceId()).isEqualTo("school-print-123");
          assertThat(view.school()).isEqualTo("신구중");
          assertThat(view.previewUrl()).doesNotContain("storage");
        });
  }

  @Test
  void visiblePreview_revalidatesCandidate_andPreservesBytes() {
    when(exposureRepository.findByCampaignIdAndSourceKindAndSourceId(
        10L, "school-print", 123L)).thenReturn(Optional.of(exposure(true)));
    byte[] html = new byte[] {0, 1, -1, 60, 62};
    when(gateway.fetchHtml(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("school-print"),
        org.mockito.ArgumentMatchers.eq(123L))).thenReturn(Optional.of(html));

    assertThat(service.previewHtml("eduholic", "school-print-123")).isSameAs(html);
  }

  @Test
  void hiddenPreview_failsClosedBeforeHtmlFetch() {
    when(exposureRepository.findByCampaignIdAndSourceKindAndSourceId(
        10L, "school-print", 123L)).thenReturn(Optional.of(exposure(false)));

    assertThatThrownBy(() -> service.previewHtml("eduholic", "school-print-123"))
        .isInstanceOf(ResponseStatusException.class);
    verify(gateway, never()).fetchHtml(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void candidateRemovedAfterExposure_failsClosed() {
    when(exposureRepository.findByCampaignIdAndSourceKindAndSourceId(
        10L, "school-print", 123L)).thenReturn(Optional.of(exposure(true)));
    when(gateway.listCandidates(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.previewHtml("eduholic", "school-print-123"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void unpublishedCampaign_failsClosed() {
    campaign.updatePublished(false);
    assertThatThrownBy(() -> service.listSources("eduholic"))
        .isInstanceOf(ResponseStatusException.class);
  }

  private MidtermWikiSourceExposure exposure(boolean visible) {
    return MidtermWikiSourceExposure.builder()
        .campaignId(10L).sourceKind("school-print").sourceId(123L)
        .visible(visible).featured(false).sortOrder(0).build();
  }

  private static void setId(Object entity, String field, Long value) {
    try {
      var f = entity.getClass().getDeclaredField(field);
      f.setAccessible(true);
      f.set(entity, value);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
