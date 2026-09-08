package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermLiveVideoDtos.LiveVideoLinkCreate;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermLiveVideoDtos.LiveVideoLinkUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaign;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaignState;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermLiveVideoLink;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermLiveVideoRole;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermCampaignRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermLiveVideoLinkRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class MidtermLiveVideoAdminServiceTest {

  private AcademyService academyService;
  private MidtermCampaignRepository campaigns;
  private MidtermLiveVideoLinkRepository links;
  private AcademyVideoRepository videos;
  private MidtermLiveVideoAdminService service;
  private Academy academy;

  @BeforeEach
  void setUp() {
    academyService = mock(AcademyService.class);
    campaigns = mock(MidtermCampaignRepository.class);
    links = mock(MidtermLiveVideoLinkRepository.class);
    videos = mock(AcademyVideoRepository.class);
    service = new MidtermLiveVideoAdminService(academyService, campaigns, links, videos);

    academy = mock(Academy.class);
    when(academy.getAcademyId()).thenReturn(1L);
    when(academy.getSlug()).thenReturn("압구정에듀홀릭");
    when(academyService.requireOwnedAcademyByHandle("eduholic")).thenReturn(academy);

    MidtermCampaign campaign = MidtermCampaign.builder()
        .academy(academy).examName("2026 2학기 중간고사").examYear(2026)
        .semester("2학기").examType("중간고사")
        .state(MidtermCampaignState.PREPARING).published(true).build();
    setId(campaign, "campaignId", 10L);
    when(campaigns.findByAcademy_AcademyId(1L)).thenReturn(Optional.of(campaign));
    when(links.findByCampaignIdOrderBySortOrderAscLinkIdAsc(10L)).thenReturn(List.of());
    when(videos.findByAcademy_AcademyIdAndLifecycleStatusOrderByCreatedAtDesc(
        1L, VideoLifecycleStatus.READY)).thenReturn(List.of());
  }

  /** 소유 검증을 통과하는 READY 영상을 videoId 로 스텁한다(중첩 stubbing 방지 위해 먼저 조립). */
  private void stubOwnedVideo(long id) {
    AcademyVideo v = mock(AcademyVideo.class);
    when(v.getVideoId()).thenReturn(id);
    when(v.getLifecycleStatus()).thenReturn(VideoLifecycleStatus.READY);
    when(v.getThumbnailKey()).thenReturn("thumb-" + id);
    when(videos.findByVideoIdAndAcademy_AcademyId(id, 1L)).thenReturn(Optional.of(v));
  }

  @Test
  void create_linksVideoOwnedBySameAcademy() {
    stubOwnedVideo(8L);
    when(links.existsByCampaignIdAndSchoolNameAndGradeAndRoleAndAcademyVideoId(
        10L, "신구중", 2, MidtermLiveVideoRole.STUDENT_STUDY, 8L)).thenReturn(false);

    service.create("eduholic", new LiveVideoLinkCreate(8L, "신구중", 2, "STUDENT_STUDY", true, 0));

    verify(links).save(any(MidtermLiveVideoLink.class));
  }

  @Test
  void create_rejectsVideoOwnedByOtherAcademy_withoutWrite() {
    when(videos.findByVideoIdAndAcademy_AcademyId(99L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(
        "eduholic", new LiveVideoLinkCreate(99L, "신구중", 2, "STUDENT_STUDY", true, 0)))
        .isInstanceOf(ResponseStatusException.class);
    verify(links, never()).save(any());
  }

  @Test
  void create_rejectsDuplicateSlotVideo() {
    stubOwnedVideo(8L);
    when(links.existsByCampaignIdAndSchoolNameAndGradeAndRoleAndAcademyVideoId(
        10L, "신구중", 2, MidtermLiveVideoRole.STUDENT_STUDY, 8L)).thenReturn(true);

    assertThatThrownBy(() -> service.create(
        "eduholic", new LiveVideoLinkCreate(8L, "신구중", 2, "STUDENT_STUDY", true, 0)))
        .isInstanceOf(ResponseStatusException.class);
    verify(links, never()).save(any());
  }

  @Test
  void create_rejectsUnknownRole() {
    stubOwnedVideo(8L);

    assertThatThrownBy(() -> service.create(
        "eduholic", new LiveVideoLinkCreate(8L, "신구중", 2, "PROMO", true, 0)))
        .isInstanceOf(ResponseStatusException.class);
    verify(links, never()).save(any());
  }

  @Test
  void create_rejectsMissingSchoolOrGrade() {
    stubOwnedVideo(8L);

    assertThatThrownBy(() -> service.create(
        "eduholic", new LiveVideoLinkCreate(8L, "  ", 2, "STUDENT_STUDY", true, 0)))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> service.create(
        "eduholic", new LiveVideoLinkCreate(8L, "신구중", null, "STUDENT_STUDY", true, 0)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void update_scopesLinkToOwnedCampaign() {
    when(links.findByLinkIdAndCampaignId(5L, 10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(
        "eduholic", 5L, new LiveVideoLinkUpdate(null, null, null, false, 3)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void update_exposureOnly_keepsTarget() {
    MidtermLiveVideoLink link = MidtermLiveVideoLink.builder()
        .campaignId(10L).academyVideoId(8L).schoolName("신구중").grade(2)
        .role(MidtermLiveVideoRole.STUDENT_STUDY).visible(false).sortOrder(0).build();
    when(links.findByLinkIdAndCampaignId(5L, 10L)).thenReturn(Optional.of(link));

    service.update("eduholic", 5L, new LiveVideoLinkUpdate(null, null, null, true, 7));

    assertThat(link.isVisible()).isTrue();
    assertThat(link.getSortOrder()).isEqualTo(7);
    assertThat(link.getSchoolName()).isEqualTo("신구중");
    assertThat(link.getGrade()).isEqualTo(2);
    assertThat(link.getRole()).isEqualTo(MidtermLiveVideoRole.STUDENT_STUDY);
  }

  @Test
  void missingCampaign_isNotFound() {
    when(campaigns.findByAcademy_AcademyId(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.list("eduholic"))
        .isInstanceOf(ResponseStatusException.class);
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
