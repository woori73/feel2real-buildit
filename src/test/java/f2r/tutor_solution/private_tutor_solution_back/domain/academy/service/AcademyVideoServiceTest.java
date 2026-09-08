package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoPurpose;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoOptimizationJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoOptimizationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoOptimizationJobRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.TranscodeResult;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoProbe;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoTranscoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AcademyVideoService — 업로드 제한 재검증 · 저장 정합 · 물리삭제")
class AcademyVideoServiceTest {

  private static final long MAX_BYTES = 200L * 1024 * 1024;

  private AcademyService academyService;
  private AcademyVideoRepository academyVideoRepository;
  private AcademyVideoOptimizationJobRepository optimizationJobRepository;
  private FileStorage fileStorage;
  private VideoTranscoder videoTranscoder;
  private LessonMediaReferenceGuard lessonMediaReferenceGuard;
  private AcademyVideoCleanupTransactions cleanupTransactions;
  private f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service.MidtermLiveVideoUsageLookup liveVideoUsageLookup;
  private final ExternalVideoUrlValidator externalVideoUrlValidator = new ExternalVideoUrlValidator();
  private AcademyVideoService service;

  private Academy academy;
  private JwtTokenProvider.AuthenticatedPrincipal principal;

  @BeforeEach
  void setUp() {
    academyService = mock(AcademyService.class);
    academyVideoRepository = mock(AcademyVideoRepository.class);
    optimizationJobRepository = mock(AcademyVideoOptimizationJobRepository.class);
    fileStorage = mock(FileStorage.class);
    videoTranscoder = mock(VideoTranscoder.class);
    lessonMediaReferenceGuard = mock(LessonMediaReferenceGuard.class);
    cleanupTransactions = mock(AcademyVideoCleanupTransactions.class);
    liveVideoUsageLookup = mock(
        f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service.MidtermLiveVideoUsageLookup.class);
    when(liveVideoUsageLookup.labelsByVideoId(org.mockito.ArgumentMatchers.any()))
        .thenReturn(java.util.Map.of());
    service = new AcademyVideoService(
        academyService, academyVideoRepository, optimizationJobRepository, lessonMediaReferenceGuard,
        cleanupTransactions, liveVideoUsageLookup, externalVideoUrlValidator,
        fileStorage, videoTranscoder,
        120, MAX_BYTES, MAX_BYTES, 30);

    User tutor = User.builder().loginId("tutor").password("pw").name("강사").phone("010").build();
    ReflectionTestUtils.setField(tutor, "userId", 10L);
    academy = Academy.builder()
        .tutor(tutor).slug("my-academy").name("우리학원")
        .logoStorageKey("logo").intro("소개").build();
    ReflectionTestUtils.setField(academy, "academyId", 100L);

    principal = mock(JwtTokenProvider.AuthenticatedPrincipal.class);
    when(academyService.requireMyAcademy(principal)).thenReturn(academy);
  }

  private MultipartFile videoFile(long size) {
    byte[] content = new byte[(int) Math.min(size, 64)];
    MockMultipartFile file = new MockMultipartFile("video", "clip.mov", "video/quicktime", content);
    if (size <= 64) return file;
    // 실제로 100MB 배열을 만들지 않고 크기만 크게 보이도록 감싼다.
    return new MockMultipartFile("video", "clip.mov", "video/quicktime", content) {
      @Override
      public long getSize() {
        return size;
      }
    };
  }

  /** 변환이 성공해 mp4/썸네일이 만들어진 상황을 만든다. */
  private TranscodeResult stubSuccessfulTranscode(Path dir, long outputBytes) throws IOException {
    Path mp4 = dir.resolve("output.mp4");
    Path thumb = dir.resolve("thumbnail.jpg");
    Files.writeString(mp4, "mp4");
    Files.writeString(thumb, "jpg");
    TranscodeResult result = new TranscodeResult(mp4, thumb, 720, 1280, 12, outputBytes);
    when(videoTranscoder.probe(any())).thenReturn(new VideoProbe(1080, 1920, "hevc", 12.0));
    when(videoTranscoder.transcode(any(), any(), any())).thenReturn(result);
    return result;
  }

  /* ── 서버 재검증 (AC #4, #10) ─────────────────────────────── */

  @Test
  @DisplayName("given 이미 30개를 등록한 학원 when 31번째 업로드 then 400 + 변환하지 않는다")
  void upload_rejects_video_over_max_count() throws IOException {
    // given
    when(academyVideoRepository.countByAcademy_AcademyId(100L)).thenReturn(30L);

    // when / then
    assertThatThrownBy(() -> service.upload(principal, videoFile(1024), "LESSON"))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("최대 30개");
    verify(videoTranscoder, never()).probe(any());
  }

  @Test
  @DisplayName("given 200MiB를 넘는 파일 when 업로드 then 400 + 변환하지 않는다")
  void upload_rejects_oversized_file() throws IOException {
    // given
    when(academyVideoRepository.countByAcademy_AcademyId(100L)).thenReturn(0L);

    // when / then
    assertThatThrownBy(() -> service.upload(principal, videoFile(MAX_BYTES + 1), "LESSON"))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("200MB");
    verify(videoTranscoder, never()).probe(any());
  }

  @Test
  @DisplayName("given 클라이언트를 우회한 120초 초과 영상 when 업로드 then ffprobe 결과로 400")
  void upload_rejects_long_video_by_server_probe() throws IOException {
    // given — 클라이언트 사전검증은 우회 가능하므로 서버가 다시 판정해야 한다
    when(academyVideoRepository.countByAcademy_AcademyId(100L)).thenReturn(0L);
    when(videoTranscoder.probe(any())).thenReturn(new VideoProbe(1080, 1920, "hevc", 121.0));

    // when / then
    assertThatThrownBy(() -> service.upload(principal, videoFile(1024), "LESSON"))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("120초");
    verify(videoTranscoder, never()).transcode(any(), any(), any());
  }

  @Test
  @DisplayName("given 지원하지 않는 코덱 when 업로드 then 400 + 변환하지 않는다")
  void upload_rejects_unsupported_codec() throws IOException {
    // given
    when(academyVideoRepository.countByAcademy_AcademyId(100L)).thenReturn(0L);
    when(videoTranscoder.probe(any())).thenReturn(new VideoProbe(640, 480, "wmv3", 10.0));

    // when / then
    assertThatThrownBy(() -> service.upload(principal, videoFile(1024), "LESSON"))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("지원하지 않는");
    verify(videoTranscoder, never()).transcode(any(), any(), any());
  }

  /* ── 저장 정합 (AC #11, #12) ──────────────────────────────── */

  @Test
  @DisplayName("given 변환 산출물이 상한을 넘을 때 when 업로드 then 저장 전에 거부한다")
  void upload_rejects_oversized_output_before_persisting(@TempDir Path dir) throws IOException {
    // given — 크기는 인코딩 후에야 확정되므로 이 검사는 변환 뒤·저장 전에 있어야 한다
    when(academyVideoRepository.countByAcademy_AcademyId(100L)).thenReturn(0L);
    stubSuccessfulTranscode(dir, MAX_BYTES + 1);

    // when / then
    assertThatThrownBy(() -> service.upload(principal, videoFile(1024), "LESSON"))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("너무 큽니다");
    verify(fileStorage, never()).saveRaw(anyLong(), anyString(), any(), anyLong());
    verify(academyVideoRepository, never()).save(any());
  }

  @Test
  @DisplayName("given DB insert가 실패할 때 when 업로드 then 이미 올린 mp4와 썸네일을 보상 삭제한다")
  void upload_compensates_storage_when_insert_fails(@TempDir Path dir) throws IOException {
    // given — 파일만 남고 row가 없으면 저장소에 고아가 쌓인다
    when(academyVideoRepository.countByAcademy_AcademyId(100L)).thenReturn(0L);
    stubSuccessfulTranscode(dir, 1024L);
    when(fileStorage.saveRaw(eq(10L), anyString(), any(), anyLong())).thenReturn("mp4-key");
    when(fileStorage.saveImage(eq(10L), anyString(), any())).thenReturn("thumb-key");
    when(academyVideoRepository.save(any())).thenThrow(new IllegalStateException("commit 실패"));

    // when / then
    assertThatThrownBy(() -> service.upload(principal, videoFile(1024), "LESSON"))
        .isInstanceOf(IllegalStateException.class);
    verify(fileStorage).delete("mp4-key", "VIDEO");
    verify(fileStorage).delete("thumb-key", "IMAGE");
  }

  @Test
  @DisplayName("given 정상 영상 when 업로드 then READY row와 serve URL을 돌려준다")
  void upload_success_returns_ready_response(@TempDir Path dir) throws IOException {
    // given
    when(academyVideoRepository.countByAcademy_AcademyId(100L)).thenReturn(0L);
    stubSuccessfulTranscode(dir, 1024L);
    when(fileStorage.saveRaw(eq(10L), anyString(), any(), anyLong())).thenReturn("mp4-key");
    when(fileStorage.saveImage(eq(10L), anyString(), any())).thenReturn("thumb-key");
    when(academyVideoRepository.save(any())).thenAnswer(invocation -> {
      AcademyVideo saved = invocation.getArgument(0);
      ReflectionTestUtils.setField(saved, "videoId", 7L);
      return saved;
    });

    // when
    var response = service.upload(principal, videoFile(1024), "LESSON");

    // then
    assertThat(response.videoId()).isEqualTo(7L);
    assertThat(response.status()).isEqualTo(VideoStatus.READY);
    assertThat(response.videoUrl()).isEqualTo("/api/academies/my-academy/videos/7/stream");
    assertThat(response.originalFilename()).isEqualTo("clip.mov");
    assertThat(response.thumbnailUrl()).isEqualTo("/api/academies/my-academy/videos/7/thumbnail");
    assertThat(response.width()).isEqualTo(720);
    assertThat(response.height()).isEqualTo(1280);
    assertThat(response.purpose()).isEqualTo(AcademyVideoPurpose.LESSON);
  }

  /* ── Phase 3: 영상 용도(purpose) 필수화 + 재지정 ──────────────── */

  @Test
  @DisplayName("given purpose를 보내지 않음 when 업로드 then 400 + 변환을 시도하지 않는다(NULL 신규 생성 차단)")
  void upload_rejects_missing_purpose_before_any_processing() throws IOException {
    // given — 개수/용량 등 다른 검증보다 먼저 걸려야 한다(순서와 무관하게 신규 NULL을 만들지 않음)
    when(academyVideoRepository.countByAcademy_AcademyId(100L)).thenReturn(0L);

    // when / then
    assertThatThrownBy(() -> service.upload(principal, videoFile(1024), null))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("영상 용도");
    verify(videoTranscoder, never()).probe(any());
    verify(academyVideoRepository, never()).save(any());
  }

  @Test
  @DisplayName("given purpose가 공백 문자열 when 업로드 then 400(빈 값을 용도 미상으로 조용히 허용하지 않는다)")
  void upload_rejects_blank_purpose() throws IOException {
    when(academyVideoRepository.countByAcademy_AcademyId(100L)).thenReturn(0L);

    assertThatThrownBy(() -> service.upload(principal, videoFile(1024), "   "))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("영상 용도");
    verify(videoTranscoder, never()).probe(any());
  }

  @Test
  @DisplayName("given DIRECTOR로 업로드 when 저장 then purpose=DIRECTOR로 저장된다")
  void upload_persists_director_purpose(@TempDir Path dir) throws IOException {
    when(academyVideoRepository.countByAcademy_AcademyId(100L)).thenReturn(0L);
    stubSuccessfulTranscode(dir, 1024L);
    when(fileStorage.saveRaw(eq(10L), anyString(), any(), anyLong())).thenReturn("mp4-key");
    when(fileStorage.saveImage(eq(10L), anyString(), any())).thenReturn("thumb-key");
    when(academyVideoRepository.save(any())).thenAnswer(invocation -> {
      AcademyVideo saved = invocation.getArgument(0);
      ReflectionTestUtils.setField(saved, "videoId", 8L);
      return saved;
    });

    var response = service.upload(principal, videoFile(1024), "director");

    assertThat(response.purpose()).isEqualTo(AcademyVideoPurpose.DIRECTOR);
  }

  @Test
  @DisplayName("given 미지정(purpose=NULL) 레거시 영상 when DIRECTOR로 재지정 then purpose가 갱신된다")
  void updatePurpose_classifies_legacy_null_video() {
    // given — purpose 컬럼 도입 이전 업로드를 흉내낸다(purpose 미설정)
    AcademyVideo legacy = AcademyVideo.builder()
        .academy(academy).storageKey("k").thumbnailKey("t").status(VideoStatus.READY).build();
    ReflectionTestUtils.setField(legacy, "videoId", 9L);
    when(academyVideoRepository.findByVideoIdAndAcademy_AcademyId(9L, 100L))
        .thenReturn(Optional.of(legacy));

    // when
    var response = service.updatePurpose(principal, 9L, "DIRECTOR");

    // then
    assertThat(response.purpose()).isEqualTo(AcademyVideoPurpose.DIRECTOR);
    assertThat(legacy.getPurpose()).isEqualTo(AcademyVideoPurpose.DIRECTOR);
  }

  @Test
  @DisplayName("given 기존 LESSON 영상 when DIRECTOR로 재지정 then 반대 방향도 허용된다")
  void updatePurpose_allows_lesson_to_director() {
    AcademyVideo video = AcademyVideo.builder()
        .academy(academy).storageKey("k").thumbnailKey("t").status(VideoStatus.READY)
        .purpose(AcademyVideoPurpose.LESSON).build();
    ReflectionTestUtils.setField(video, "videoId", 10L);
    when(academyVideoRepository.findByVideoIdAndAcademy_AcademyId(10L, 100L))
        .thenReturn(Optional.of(video));

    service.updatePurpose(principal, 10L, "DIRECTOR");

    assertThat(video.getPurpose()).isEqualTo(AcademyVideoPurpose.DIRECTOR);
  }

  @Test
  @DisplayName("given 용도 재지정 요청에 값이 없을 때 then 400 + 기존 purpose를 바꾸지 않는다")
  void updatePurpose_rejects_blank_value() {
    AcademyVideo video = AcademyVideo.builder()
        .academy(academy).storageKey("k").thumbnailKey("t").status(VideoStatus.READY)
        .purpose(AcademyVideoPurpose.LESSON).build();
    ReflectionTestUtils.setField(video, "videoId", 11L);
    when(academyVideoRepository.findByVideoIdAndAcademy_AcademyId(11L, 100L))
        .thenReturn(Optional.of(video));

    assertThatThrownBy(() -> service.updatePurpose(principal, 11L, ""))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("영상 용도");
    assertThat(video.getPurpose()).isEqualTo(AcademyVideoPurpose.LESSON);
  }

  @Test
  @DisplayName("given 다른 학원의 영상 id when 용도 재지정 then 404 + 값을 바꾸지 않는다")
  void updatePurpose_rejects_other_academy_video() {
    when(academyVideoRepository.findByVideoIdAndAcademy_AcademyId(999L, 100L))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.updatePurpose(principal, 999L, "LESSON"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("찾을 수 없습니다");
  }

  @Test
  @DisplayName("given 수업/원장 영상 when NONE(용도 지정 안 함)으로 재지정 then purpose=NONE, 행/파일은 그대로")
  void updatePurpose_allows_setting_none() {
    AcademyVideo lesson = AcademyVideo.builder()
        .academy(academy).storageKey("k").thumbnailKey("t").status(VideoStatus.READY)
        .purpose(AcademyVideoPurpose.LESSON).build();
    ReflectionTestUtils.setField(lesson, "videoId", 20L);
    when(academyVideoRepository.findByVideoIdAndAcademy_AcademyId(20L, 100L))
        .thenReturn(Optional.of(lesson));
    AcademyVideo director = AcademyVideo.builder()
        .academy(academy).storageKey("k").thumbnailKey("t").status(VideoStatus.READY)
        .purpose(AcademyVideoPurpose.DIRECTOR).build();
    ReflectionTestUtils.setField(director, "videoId", 21L);
    when(academyVideoRepository.findByVideoIdAndAcademy_AcademyId(21L, 100L))
        .thenReturn(Optional.of(director));

    service.updatePurpose(principal, 20L, "NONE");
    service.updatePurpose(principal, 21L, "none");   // 대소문자 무관

    assertThat(lesson.getPurpose()).isEqualTo(AcademyVideoPurpose.NONE);
    assertThat(director.getPurpose()).isEqualTo(AcademyVideoPurpose.NONE);
    verify(academyVideoRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    verify(fileStorage, never())
        .delete(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }

  /* ── 삭제 (AC #7, #14) ────────────────────────────────────── */

  @Test
  @DisplayName("given 본인 영상 when 삭제 then tombstone cleanup을 위임하고 즉시 물리삭제하지 않는다")
  void delete_tombstones_without_immediate_physical_delete() {
    // given
    AcademyVideo video = AcademyVideo.builder()
        .academy(academy).storageKey("mp4-key").thumbnailKey("thumb-key")
        .status(VideoStatus.READY).build();
    ReflectionTestUtils.setField(video, "videoId", 7L);
    when(academyVideoRepository.findForUpdateByVideoIdAndAcademyId(7L, 100L))
        .thenReturn(Optional.of(video));

    // when
    service.delete(principal, 7L);

    // then
    verify(cleanupTransactions).tombstoneLocked(video);
    verify(academyVideoRepository, never()).delete(any());
    verify(fileStorage, never()).delete(anyString(), anyString());
  }

  @Test
  @DisplayName("given 다른 학원의 영상 id when 삭제 then 404 + 저장소를 건드리지 않는다")
  void delete_rejects_other_academy_video() {
    // given — academyId를 쿼리 조건에 넣어 소유 밖 영상은 아예 조회되지 않는다
    when(academyVideoRepository.findForUpdateByVideoIdAndAcademyId(999L, 100L))
        .thenReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> service.delete(principal, 999L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("찾을 수 없습니다");
    verify(fileStorage, never()).delete(anyString(), anyString());
  }

  @Test
  @DisplayName("given 수업상품이 대표 또는 OT로 쓰는 영상 when 삭제 then 400 + row·파일 모두 보존")
  void delete_rejects_video_used_as_lesson_hero() {
    // given — DB FK가 ON DELETE SET NULL이라 여기서 막지 않으면 수업 히어로가 조용히 비고
    // 파일까지 사라진다. 복구 경로가 없다.
    AcademyVideo video = AcademyVideo.builder()
        .academy(academy).storageKey("mp4-key").thumbnailKey("thumb-key")
        .status(VideoStatus.READY).build();
    ReflectionTestUtils.setField(video, "videoId", 7L);
    when(academyVideoRepository.findForUpdateByVideoIdAndAcademyId(7L, 100L))
        .thenReturn(Optional.of(video));
    when(lessonMediaReferenceGuard.lessonNamesUsingForUpdate(7L))
        .thenReturn(List.of("고2 영어", "중3 수학"));

    // when / then
    assertThatThrownBy(() -> service.delete(principal, 7L))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("고2 영어")
        .hasMessageContaining("중3 수학");
    verify(academyVideoRepository, never()).delete(any());
    verify(fileStorage, never()).delete(anyString(), anyString());
  }

  @Test
  @DisplayName("given 홈페이지 히어로에서 쓰는 영상 when 삭제 then 400 + row·파일 모두 보존")
  void delete_rejects_video_used_on_homepage() {
    // given
    ReflectionTestUtils.setField(academy, "pageSettings", new AcademyPageSettings(
        null, null, null, null, Map.of("heroVideoId", "7")));
    AcademyVideo video = AcademyVideo.builder()
        .academy(academy).storageKey("mp4-key").thumbnailKey("thumb-key")
        .status(VideoStatus.READY).build();
    ReflectionTestUtils.setField(video, "videoId", 7L);
    when(academyVideoRepository.findForUpdateByVideoIdAndAcademyId(7L, 100L))
        .thenReturn(Optional.of(video));

    // when / then
    assertThatThrownBy(() -> service.delete(principal, 7L))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("히어로 영상")
        .hasMessageContaining("빌더에서 영상을 먼저 해제");
    verify(cleanupTransactions, never()).tombstoneLocked(any());
  }

  @Test
  @DisplayName("given 참조가 해제된 영상 when 삭제 then 정상 삭제된다")
  void delete_succeeds_after_reference_released() {
    // given
    AcademyVideo video = AcademyVideo.builder()
        .academy(academy).storageKey("mp4-key").thumbnailKey("thumb-key")
        .status(VideoStatus.READY).build();
    ReflectionTestUtils.setField(video, "videoId", 7L);
    when(academyVideoRepository.findForUpdateByVideoIdAndAcademyId(7L, 100L))
        .thenReturn(Optional.of(video));
    when(lessonMediaReferenceGuard.lessonNamesUsingForUpdate(7L)).thenReturn(List.of());

    // when
    service.delete(principal, 7L);

    // then
    verify(cleanupTransactions).tombstoneLocked(video);
    verify(academyVideoRepository, never()).delete(any());
    verify(fileStorage, never()).delete(anyString(), anyString());
  }

  /* ── 공개 노출 ────────────────────────────────────────────── */

  @Test
  @DisplayName("given READY raw 영상 when 강사 목록 조회 then availability와 최적화 상태를 함께 돌려준다")
  void listMine_returns_availability_and_optimization_state() {
    AcademyVideo ready = AcademyVideo.readyRaw(
        academy, "clip.webm", "raw-key", "video/webm", 1024L, 12_000L, 720, 1280);
    ReflectionTestUtils.setField(ready, "videoId", 1L);
    AcademyVideoOptimizationJob job = AcademyVideoOptimizationJob.pending(
        ready, java.time.LocalDateTime.now());
    when(academyVideoRepository
        .findByAcademy_AcademyIdAndLifecycleStatusOrderByCreatedAtDesc(
            100L, VideoLifecycleStatus.READY)).thenReturn(List.of(ready));
    when(optimizationJobRepository.findByVideo_VideoIdIn(List.of(1L)))
        .thenReturn(List.of(job));

    var videos = service.listMine(principal);

    assertThat(videos).hasSize(1);
    assertThat(videos.get(0).availabilityStatus()).isEqualTo(VideoLifecycleStatus.READY);
    assertThat(videos.get(0).optimizationStatus()).isEqualTo(VideoOptimizationStatus.PENDING);
    assertThat(videos.get(0).thumbnailUrl()).isNull();
  }

  @Test
  @DisplayName("given READY 영상 when 공개 목록 조회 then 최신순 최대 10개만 돌려준다")
  void listPublicReady_returns_ready_only() {
    // given
    AcademyVideo ready = AcademyVideo.builder()
        .academy(academy).storageKey("k").thumbnailKey("t").status(VideoStatus.READY).build();
    ReflectionTestUtils.setField(ready, "videoId", 1L);
    when(academyVideoRepository.findTop10ByAcademy_AcademyIdAndStatusOrderByCreatedAtDesc(
        100L, VideoStatus.READY)).thenReturn(List.of(ready));

    // when
    var videos = service.listPublicReady(academy);

    // then
    assertThat(videos).hasSize(1);
    assertThat(videos.get(0).status()).isEqualTo(VideoStatus.READY);
  }

  @Test
  @DisplayName("given 외부 링크 영상이 섞인 목록 when 공개 스토어프론트 조회 then 직접 업로드만 노출한다")
  void listPublicReady_excludes_external_links() {
    AcademyVideo upload = AcademyVideo.builder()
        .academy(academy).storageKey("k").thumbnailKey("t").status(VideoStatus.READY).build();
    ReflectionTestUtils.setField(upload, "videoId", 1L);
    AcademyVideo external = AcademyVideo.externalLink(
        academy, "네이버 수업 영상", "https://naver.me/F4WnDovq",
        f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyExternalVideoProvider.NAVER,
        null, AcademyVideoPurpose.LESSON);
    ReflectionTestUtils.setField(external, "videoId", 2L);
    when(academyVideoRepository.findTop10ByAcademy_AcademyIdAndStatusOrderByCreatedAtDesc(
        100L, VideoStatus.READY)).thenReturn(List.of(upload, external));

    var videos = service.listPublicReady(academy);

    assertThat(videos).extracting(r -> r.videoId()).containsExactly(1L);
  }

  /* ── 외부 링크 등록 (AC #2, #3) ───────────────────────────── */

  @Test
  @DisplayName("given naver.me URL when 외부 링크 등록 then EXTERNAL_LINK/NAVER 로 저장하고 embed 는 없다")
  void registerExternalLink_saves_naver_short_url() {
    when(academyVideoRepository.countByAcademy_AcademyId(100L)).thenReturn(6L);
    when(academyVideoRepository.save(any(AcademyVideo.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    var response = service.registerExternalLink(
        principal, "https://naver.me/F4WnDovq", "6월 공개수업 하이라이트", "LESSON");

    assertThat(response.sourceKind())
        .isEqualTo(f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoSourceKind.EXTERNAL_LINK);
    assertThat(response.provider())
        .isEqualTo(f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyExternalVideoProvider.NAVER);
    assertThat(response.externalUrl()).isEqualTo("https://naver.me/F4WnDovq");
    assertThat(response.embedUrl()).isNull();
    assertThat(response.videoUrl()).isNull();
    assertThat(response.purpose()).isEqualTo(AcademyVideoPurpose.LESSON);
  }

  @Test
  @DisplayName("given YouTube watch URL when 외부 링크 등록 then embed URL 이 함께 저장된다")
  void registerExternalLink_builds_youtube_embed() {
    when(academyVideoRepository.countByAcademy_AcademyId(100L)).thenReturn(0L);
    when(academyVideoRepository.save(any(AcademyVideo.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    var response = service.registerExternalLink(
        principal, "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "설명회", "DIRECTOR");

    assertThat(response.provider())
        .isEqualTo(f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyExternalVideoProvider.YOUTUBE);
    assertThat(response.embedUrl()).isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");
  }

  @Test
  @DisplayName("given http:// URL when 외부 링크 등록 then 400 (https 만 허용)")
  void registerExternalLink_rejects_non_https() {
    assertThatThrownBy(() -> service.registerExternalLink(
        principal, "http://example.com/v", "제목", "LESSON"))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("https");
    verify(academyVideoRepository, never()).save(any(AcademyVideo.class));
  }

  @Test
  @DisplayName("given javascript: scheme when 외부 링크 등록 then 400")
  void registerExternalLink_rejects_dangerous_scheme() {
    assertThatThrownBy(() -> service.registerExternalLink(
        principal, "javascript:alert(1)", "제목", "LESSON"))
        .isInstanceOf(VideoUploadRejectedException.class);
    verify(academyVideoRepository, never()).save(any(AcademyVideo.class));
  }

  @Test
  @DisplayName("given 제목 없음 when 외부 링크 등록 then 400")
  void registerExternalLink_requires_title() {
    assertThatThrownBy(() -> service.registerExternalLink(
        principal, "https://naver.me/F4WnDovq", "   ", "LESSON"))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("제목");
  }

  @Test
  @DisplayName("given 이미 30개(업로드+링크 합산) when 외부 링크 등록 then 400")
  void registerExternalLink_counts_against_shared_limit() {
    when(academyVideoRepository.countByAcademy_AcademyId(100L)).thenReturn(30L);

    assertThatThrownBy(() -> service.registerExternalLink(
        principal, "https://naver.me/F4WnDovq", "제목", "LESSON"))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("최대 30개");
    verify(academyVideoRepository, never()).save(any(AcademyVideo.class));
  }

  @Test
  @DisplayName("given 외부 링크 영상 when 삭제 then tombstone 없이 행을 직접 삭제한다")
  void delete_external_link_hard_deletes_row() {
    AcademyVideo external = AcademyVideo.externalLink(
        academy, "네이버 영상", "https://naver.me/F4WnDovq",
        f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyExternalVideoProvider.NAVER,
        null, AcademyVideoPurpose.LESSON);
    ReflectionTestUtils.setField(external, "videoId", 7L);
    when(academyVideoRepository.findForUpdateByVideoIdAndAcademyId(7L, 100L))
        .thenReturn(Optional.of(external));
    when(lessonMediaReferenceGuard.lessonNamesUsingForUpdate(7L)).thenReturn(List.of());

    service.delete(principal, 7L);

    verify(academyVideoRepository).delete(external);
    verify(cleanupTransactions, never()).tombstoneLocked(any());
  }
}
