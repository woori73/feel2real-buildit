package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermLiveVideoDtos.LiveVideoView;
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

class MidtermLiveVideoServiceTest {

  private AcademyRepository academyRepository;
  private MidtermCampaignRepository campaigns;
  private AcademyVideoRepository videos;
  private MidtermLiveVideoLinkRepository links;
  private MidtermLiveVideoService service;
  private Academy academy;
  private MidtermCampaign campaign;

  @BeforeEach
  void setUp() {
    academyRepository = mock(AcademyRepository.class);
    campaigns = mock(MidtermCampaignRepository.class);
    videos = mock(AcademyVideoRepository.class);
    links = mock(MidtermLiveVideoLinkRepository.class);
    service = new MidtermLiveVideoService(academyRepository, campaigns, videos, links);

    academy = mock(Academy.class);
    when(academy.getAcademyId()).thenReturn(1L);
    when(academy.getSlug()).thenReturn("압구정에듀홀릭");
    when(academyRepository.findByHandle("eduholic")).thenReturn(Optional.of(academy));

    campaign = MidtermCampaign.builder()
        .academy(academy).examName("2026 2학기 중간고사").examYear(2026)
        .semester("2학기").examType("중간고사")
        .state(MidtermCampaignState.PREPARING).published(true).build();
    setId(campaign, "campaignId", 10L);
    when(campaigns.findByAcademy_AcademyId(1L)).thenReturn(Optional.of(campaign));
  }

  private MidtermLiveVideoLink link(long id, String school, int grade, MidtermLiveVideoRole role,
      long videoId, int sortOrder) {
    MidtermLiveVideoLink l = MidtermLiveVideoLink.builder()
        .campaignId(10L).academyVideoId(videoId).schoolName(school).grade(grade)
        .role(role).visible(true).sortOrder(sortOrder).build();
    setId(l, "linkId", id);
    return l;
  }

  private void visibleLinks(MidtermLiveVideoLink... rows) {
    when(links.findByCampaignIdAndVisibleTrueOrderBySortOrderAscLinkIdAsc(10L))
        .thenReturn(List.of(rows));
  }

  private void readyVideo(long id) {
    AcademyVideo v = mock(AcademyVideo.class);
    when(v.getVideoId()).thenReturn(id);
    when(v.getLifecycleStatus()).thenReturn(VideoLifecycleStatus.READY);
    when(v.getThumbnailKey()).thenReturn("thumb-" + id);
    when(videos.findByVideoIdAndAcademy_AcademyId(id, 1L)).thenReturn(Optional.of(v));
  }

  @Test
  void unpublishedCampaign_returnsEmpty() {
    setPublished(false);
    when(campaigns.findByAcademy_AcademyId(1L)).thenReturn(Optional.of(campaign));
    assertThat(service.listPublic("eduholic")).isEmpty();
  }

  @Test
  void unknownAcademy_returnsEmpty() {
    when(academyRepository.findByHandle("nope")).thenReturn(Optional.empty());
    assertThat(service.listPublic("nope")).isEmpty();
  }

  @Test
  void exposesSchoolGradeRole_withServeUrls_noRawMetadata() {
    visibleLinks(link(1L, "신구중", 2, MidtermLiveVideoRole.STUDENT_STUDY, 8L, 0));
    readyVideo(8L);

    List<LiveVideoView> out = service.listPublic("eduholic");

    assertThat(out).hasSize(1);
    LiveVideoView v = out.get(0);
    assertThat(v.school()).isEqualTo("신구중");
    assertThat(v.grade()).isEqualTo(2);
    assertThat(v.role()).isEqualTo("STUDENT_STUDY");
    assertThat(v.videoUrl()).isEqualTo(
        "/api/academies/%EC%95%95%EA%B5%AC%EC%A0%95%EC%97%90%EB%93%80%ED%99%80%EB%A6%AD/videos/8/stream");
    assertThat(v.thumbnailUrl()).endsWith("/videos/8/thumbnail");
  }

  @Test
  void excludesNonReadyVideo() {
    visibleLinks(link(1L, "신구중", 2, MidtermLiveVideoRole.STUDENT_STUDY, 8L, 0));
    AcademyVideo deleting = mock(AcademyVideo.class);
    when(deleting.getLifecycleStatus()).thenReturn(VideoLifecycleStatus.DELETING);
    when(videos.findByVideoIdAndAcademy_AcademyId(8L, 1L)).thenReturn(Optional.of(deleting));

    assertThat(service.listPublic("eduholic")).isEmpty();
  }

  @Test
  void excludesVideoNotOwnedByCampaignAcademy() {
    visibleLinks(link(1L, "신구중", 2, MidtermLiveVideoRole.STUDENT_STUDY, 8L, 0));
    when(videos.findByVideoIdAndAcademy_AcademyId(8L, 1L)).thenReturn(Optional.empty());

    assertThat(service.listPublic("eduholic")).isEmpty();
  }

  @Test
  void hiddenLinksAreNeverQueriedIntoOutput() {
    // repository method itself filters visible=true; an all-hidden campaign yields no rows.
    when(links.findByCampaignIdAndVisibleTrueOrderBySortOrderAscLinkIdAsc(10L))
        .thenReturn(List.of());
    assertThat(service.listPublic("eduholic")).isEmpty();
  }

  @Test
  void keepsServerOrder_deterministicTieBreak() {
    // repository already sorts by sort_order then link_id; service must not reorder.
    visibleLinks(
        link(3L, "신구중", 2, MidtermLiveVideoRole.STUDENT_STUDY, 8L, 0),
        link(7L, "신구중", 2, MidtermLiveVideoRole.STUDENT_STUDY, 6L, 0));
    readyVideo(8L);
    readyVideo(6L);

    List<LiveVideoView> out = service.listPublic("eduholic");

    assertThat(out).extracting(LiveVideoView::videoUrl)
        .containsExactly(
            "/api/academies/%EC%95%95%EA%B5%AC%EC%A0%95%EC%97%90%EB%93%80%ED%99%80%EB%A6%AD/videos/8/stream",
            "/api/academies/%EC%95%95%EA%B5%AC%EC%A0%95%EC%97%90%EB%93%80%ED%99%80%EB%A6%AD/videos/6/stream");
  }

  private void setPublished(boolean published) {
    try {
      var f = MidtermCampaign.class.getDeclaredField("published");
      f.setAccessible(true);
      f.setBoolean(campaign, published);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
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
