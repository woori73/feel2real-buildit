package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiExposureUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaign;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaignState;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermWikiSourceExposure;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermCampaignRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermWikiSourceExposureRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class MidtermWikiSourceAdminServiceTest {

  private MidtermWikiSourceExposureRepository exposures;
  private MidtermWikiSourceGateway gateway;
  private MidtermWikiSourceAdminService service;
  private MidtermCampaign campaign;

  @BeforeEach
  void setUp() {
    AcademyService academyService = mock(AcademyService.class);
    MidtermCampaignRepository campaigns = mock(MidtermCampaignRepository.class);
    exposures = mock(MidtermWikiSourceExposureRepository.class);
    gateway = mock(MidtermWikiSourceGateway.class);
    EntityManager entityManager = mock(EntityManager.class);
    service = new MidtermWikiSourceAdminService(
        academyService, campaigns, exposures, gateway, entityManager);

    Academy academy = mock(Academy.class);
    User tutor = mock(User.class);
    when(academy.getAcademyId()).thenReturn(1L);
    when(academy.getTutor()).thenReturn(tutor);
    when(tutor.getUserId()).thenReturn(7L);
    when(tutor.getLoginId()).thenReturn("tutor");
    when(tutor.getRole()).thenReturn(Role.TUTOR);
    when(academyService.requireOwnedAcademyByHandle("eduholic")).thenReturn(academy);

    campaign = MidtermCampaign.builder()
        .academy(academy).examName("2026 2학기 중간고사").examYear(2026)
        .semester("2학기").examType("중간고사")
        .state(MidtermCampaignState.PREPARING).published(true).build();
    setId(campaign, "campaignId", 10L);
    when(campaigns.findByAcademy_AcademyId(1L)).thenReturn(Optional.of(campaign));
    when(exposures.findByCampaignId(10L)).thenReturn(List.of());
  }

  @Test
  void unknownOrOutOfScopeCandidate_isRejectedWithoutWrite() {
    when(gateway.listCandidates(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.updateExposure(
        "eduholic", "school-print-123", new WikiExposureUpdate(true, false, 0)))
        .isInstanceOf(ResponseStatusException.class);
    verify(exposures, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void makingCandidateFeatured_clearsPreviousFeaturedInsideTransaction() {
    WikiSourceRow candidate = new WikiSourceRow(
        "school-print", 123L, "", "신구중", "3학년",
        2026, "2학기", "중간고사", "2026-09-07T12:00:00");
    when(gateway.listCandidates(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(candidate));
    when(exposures.findByCampaignIdAndSourceKindAndSourceId(
        10L, "school-print", 123L)).thenReturn(Optional.empty());

    service.updateExposure(
        "eduholic", "school-print-123", new WikiExposureUpdate(true, true, 2));

    verify(exposures).clearFeatured(10L, null);
    verify(exposures).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
        e -> e.isVisible() && e.isFeatured() && e.getSortOrder() == 2));
  }

  @Test
  void hiddenCandidate_cannotRemainFeatured() {
    WikiSourceRow candidate = new WikiSourceRow(
        "final", 9L, "", "신구중", "3학년",
        2026, "2학기", "중간고사", "2026-09-07T12:00:00");
    when(gateway.listCandidates(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(candidate));
    when(exposures.findByCampaignIdAndSourceKindAndSourceId(
        10L, "final", 9L)).thenReturn(Optional.empty());

    service.updateExposure(
        "eduholic", "final-9", new WikiExposureUpdate(false, true, 0));

    verify(exposures, never()).clearFeatured(
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    verify(exposures).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
        e -> !e.isVisible() && !e.isFeatured()));
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
