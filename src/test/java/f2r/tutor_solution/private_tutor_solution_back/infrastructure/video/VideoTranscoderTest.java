package f2r.tutor_solution.private_tutor_solution_back.infrastructure.video;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("VideoTranscoder — ffmpeg 호출 규칙과 해상도 계산")
class VideoTranscoderTest {

  /** 실제 바이너리 대신 미리 정한 결과를 돌려주는 ProcessRunner. 호출된 명령을 기록한다. */
  private static class StubProcessRunner implements ProcessRunner {
    protected final List<List<String>> commands = new ArrayList<>();
    private ProcessOutput nextOutput = new ProcessOutput(0, "", "");
    private RuntimeException nextThrow;
    private Runnable sideEffect = () -> {};

    @Override
    public ProcessOutput run(List<String> command, int timeoutSec) {
      commands.add(command);
      if (nextThrow != null) throw nextThrow;
      sideEffect.run();
      return nextOutput;
    }

    List<String> lastCommand() {
      return commands.get(commands.size() - 1);
    }
  }

  private VideoTranscoder transcoderWith(StubProcessRunner runner, int maxConcurrent) {
    return new VideoTranscoder(runner, "ffmpeg", "ffprobe", 120, maxConcurrent);
  }

  @Nested
  @DisplayName("targetDimensions — 짧은 변 720 / 긴 변 1280 캡")
  class TargetDimensions {

    @Test
    @DisplayName("given 1080x1920 세로 숏폼 when 계산 then 720x1280으로 줄인다")
    void portrait_scales_to_720x1280() {
      // given
      int width = 1080;
      int height = 1920;

      // when
      int[] target = VideoTranscoder.targetDimensions(width, height);

      // then — 1280x720 박스에 맞추면 405x720이 되어 필요 이상으로 작아진다
      assertThat(target).containsExactly(720, 1280);
    }

    @Test
    @DisplayName("given 1920x1080 가로 영상 when 계산 then 1280x720으로 줄인다")
    void landscape_scales_to_1280x720() {
      // given / when
      int[] target = VideoTranscoder.targetDimensions(1920, 1080);

      // then
      assertThat(target).containsExactly(1280, 720);
    }

    @Test
    @DisplayName("given 상한보다 작은 640x480 when 계산 then 확대하지 않고 그대로 둔다")
    void small_input_is_not_upscaled() {
      // given / when
      int[] target = VideoTranscoder.targetDimensions(640, 480);

      // then
      assertThat(target).containsExactly(640, 480);
    }

    @Test
    @DisplayName("given 1080x2400 초장축 영상 when 계산 then 긴 변 1280 캡이 적용된다")
    void extreme_aspect_ratio_is_capped_by_long_side() {
      // given / when
      int[] target = VideoTranscoder.targetDimensions(1080, 2400);

      // then — 짧은 변 기준(720)이면 1920이 되어 긴 변 상한을 넘는다
      assertThat(target[1]).isEqualTo(1280);
      assertThat(target[0]).isEqualTo(576);
    }

    @Test
    @DisplayName("given 홀수가 나오는 비율 when 계산 then 짝수로 보정된다")
    void dimensions_are_always_even() {
      // given / when — libx264 yuv420p는 홀수 해상도를 받지 못한다
      int[] target = VideoTranscoder.targetDimensions(1081, 1921);

      // then
      assertThat(target[0] % 2).isZero();
      assertThat(target[1] % 2).isZero();
    }
  }

  @Nested
  @DisplayName("probe — ffprobe 출력 파싱")
  class Probe {

    @Test
    @DisplayName("given ffprobe가 요청과 다른 순서로 출력할 때 when 파싱 then key 기준으로 올바르게 읽는다")
    void parses_by_key_not_by_position(@TempDir Path dir) throws IOException {
      // given — 실제 ffprobe는 codec_name을 width보다 먼저 출력한다
      StubProcessRunner runner = new StubProcessRunner();
      runner.nextOutput = new ProcessRunner.ProcessOutput(
          0, "codec_name=hevc\nwidth=1080\nheight=1920\nduration=3.000000\n", "");

      // when
      VideoProbe probe = transcoderWith(runner, 2).probe(dir.resolve("in.mov"));

      // then
      assertThat(probe.videoCodec()).isEqualTo("hevc");
      assertThat(probe.width()).isEqualTo(1080);
      assertThat(probe.height()).isEqualTo(1920);
      assertThat(probe.durationSec()).isEqualTo(3.0d);
    }

    @Test
    @DisplayName("given rotation -90이 붙은 세로 촬영본 when probe then 화면에 보이는 방향(세로)으로 돌려준다")
    void swaps_dimensions_for_quarter_turn_rotation(@TempDir Path dir) throws IOException {
      // given — 아이폰 세로 촬영본은 프레임을 가로로 저장하고 회전 정보를 따로 붙이기도 한다.
      // 저장값을 그대로 쓰면 목표 해상도가 가로로 잡혀 세로 영상이 눌린다.
      StubProcessRunner runner = new StubProcessRunner();
      runner.nextOutput = new ProcessRunner.ProcessOutput(
          0, "codec_name=h264\nwidth=1920\nheight=1080\nrotation=-90\nduration=4.500000\n", "");

      // when
      VideoProbe probe = transcoderWith(runner, 2).probe(dir.resolve("portrait.mov"));

      // then
      assertThat(probe.width()).isEqualTo(1080);
      assertThat(probe.height()).isEqualTo(1920);
      assertThat(VideoTranscoder.targetDimensions(probe.width(), probe.height()))
          .containsExactly(720, 1280);
    }

    @Test
    @DisplayName("given 회전 정보가 없는 영상 when probe then 저장값을 그대로 쓴다")
    void keeps_dimensions_without_rotation(@TempDir Path dir) throws IOException {
      // given
      StubProcessRunner runner = new StubProcessRunner();
      runner.nextOutput = new ProcessRunner.ProcessOutput(
          0, "codec_name=h264\nwidth=1920\nheight=1080\nduration=5.700000\n", "");

      // when
      VideoProbe probe = transcoderWith(runner, 2).probe(dir.resolve("landscape.mov"));

      // then
      assertThat(probe.width()).isEqualTo(1920);
      assertThat(probe.height()).isEqualTo(1080);
    }

    @Test
    @DisplayName("given 180도 회전 when 판정 then 가로·세로를 바꾸지 않는다")
    void half_turn_does_not_swap() {
      // given / when / then — 180도는 상하만 뒤집힐 뿐 크기는 그대로다
      assertThat(VideoTranscoder.isQuarterTurn("-90")).isTrue();
      assertThat(VideoTranscoder.isQuarterTurn("90")).isTrue();
      assertThat(VideoTranscoder.isQuarterTurn("270")).isTrue();
      assertThat(VideoTranscoder.isQuarterTurn("180")).isFalse();
      assertThat(VideoTranscoder.isQuarterTurn("0")).isFalse();
      assertThat(VideoTranscoder.isQuarterTurn(null)).isFalse();
    }

    @Test
    @DisplayName("given ffprobe가 비정상 종료할 때 when probe then VideoTranscodeException")
    void nonzero_exit_throws(@TempDir Path dir) {
      // given
      StubProcessRunner runner = new StubProcessRunner();
      runner.nextOutput = new ProcessRunner.ProcessOutput(1, "", "Invalid data found");

      // when / then
      assertThatThrownBy(() -> transcoderWith(runner, 2).probe(dir.resolve("in.mov")))
          .isInstanceOf(VideoTranscodeException.class)
          .hasMessageContaining("영상 정보를 읽을 수 없습니다");
    }

    @Test
    @DisplayName("given 영상 트랙이 없는 파일 when probe then VideoTranscodeException")
    void missing_video_stream_throws(@TempDir Path dir) {
      // given — 오디오 전용 파일이면 width/height가 비어서 나온다
      StubProcessRunner runner = new StubProcessRunner();
      runner.nextOutput = new ProcessRunner.ProcessOutput(0, "duration=10.0\n", "");

      // when / then
      assertThatThrownBy(() -> transcoderWith(runner, 2).probe(dir.resolve("audio.m4a")))
          .isInstanceOf(VideoTranscodeException.class)
          .hasMessageContaining("영상 트랙");
    }
  }

  @Nested
  @DisplayName("transcode — 출력 코덱 고정과 실패 처리")
  class Transcode {

    private StubProcessRunner runnerProducing(Path workDir) {
      StubProcessRunner runner = new StubProcessRunner();
      // ffmpeg가 산출물을 만든 것처럼 흉내낸다(존재 + 크기 > 0 검사를 통과시키기 위해).
      runner.sideEffect = () -> {
        try {
          Files.writeString(workDir.resolve("output.mp4"), "mp4-bytes");
          Files.writeString(workDir.resolve("thumbnail.jpg"), "jpg-bytes");
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      };
      return runner;
    }

    @Test
    @DisplayName("given HEVC 세로 원본 when 변환 then H.264/AAC/yuv420p로 정규화하는 인자를 넘긴다")
    void normalizes_codec_and_pixel_format(@TempDir Path dir) throws IOException {
      // given — 컨테이너만 mp4로 바꾸면 크롬/파이어폭스가 HEVC를 재생하지 못한다
      StubProcessRunner runner = runnerProducing(dir);
      VideoProbe probe = new VideoProbe(1080, 1920, "hevc", 12.0);

      // when
      TranscodeResult result = transcoderWith(runner, 2).transcode(dir.resolve("in.mov"), probe, dir);

      // then
      List<String> transcodeCommand = runner.commands.get(0);
      assertThat(transcodeCommand).containsSequence("-c:v", "libx264");
      assertThat(transcodeCommand).containsSequence("-c:a", "aac");
      assertThat(transcodeCommand).containsSequence("-pix_fmt", "yuv420p");
      assertThat(transcodeCommand).containsSequence("-vf", "scale=720:1280");
      assertThat(transcodeCommand).containsSequence("-movflags", "+faststart");
      assertThat(result.width()).isEqualTo(720);
      assertThat(result.height()).isEqualTo(1280);
      assertThat(result.durationSec()).isEqualTo(12);
    }

    @Test
    @DisplayName("given 변환은 성공했지만 썸네일 명령이 실패 when 변환 then VideoTranscodeException")
    void thumbnail_failure_throws(@TempDir Path dir) throws IOException {
      // given — 첫 호출(변환)은 성공, 두 번째 호출(썸네일)에서 비정상 종료
      StubProcessRunner runner = new StubProcessRunner() {
        @Override
        public ProcessOutput run(List<String> command, int timeoutSec) {
          super.run(command, timeoutSec);
          if (commands.size() == 1) {
            try {
              Files.writeString(dir.resolve("output.mp4"), "mp4-bytes");
            } catch (IOException e) {
              throw new IllegalStateException(e);
            }
            return new ProcessOutput(0, "", "");
          }
          return new ProcessOutput(1, "", "thumbnail failed");
        }
      };

      // when / then
      assertThatThrownBy(() -> transcoderWith(runner, 2)
          .transcode(dir.resolve("in.mp4"), new VideoProbe(720, 1280, "h264", 5.0), dir))
          .isInstanceOf(VideoTranscodeException.class)
          .hasMessageContaining("썸네일");
    }

    @Test
    @DisplayName("given 동시 변환 슬롯이 없을 때 when 변환 then 대기하지 않고 즉시 Busy 예외")
    void exhausted_semaphore_fails_fast(@TempDir Path dir) {
      // given — permit 0개 = 이미 상한까지 점유된 상태
      VideoTranscoder saturated = transcoderWith(runnerProducing(dir), 0);

      // when / then — 큐잉되면 요청 스레드가 묶이므로 즉시 거절되어야 한다
      assertThatThrownBy(() -> saturated.transcode(
          dir.resolve("in.mp4"), new VideoProbe(720, 1280, "h264", 5.0), dir))
          .isInstanceOf(VideoTranscoderBusyException.class)
          .hasMessageContaining("잠시 후");
    }

    @Test
    @DisplayName("given 슬롯 1개로 연속 변환 when 두 번째 호출 then 반납된 슬롯으로 통과한다")
    void semaphore_is_released_after_use(@TempDir Path dir) throws IOException {
      // given
      VideoTranscoder transcoder = transcoderWith(runnerProducing(dir), 1);
      VideoProbe probe = new VideoProbe(720, 1280, "h264", 5.0);

      // when
      transcoder.transcode(dir.resolve("in.mp4"), probe, dir);
      TranscodeResult second = transcoder.transcode(dir.resolve("in.mp4"), probe, dir);

      // then — finally에서 release되지 않으면 두 번째가 Busy로 터진다
      assertThat(second.mp4()).exists();
    }
  }

  @Nested
  @DisplayName("thumbnailSeekSeconds — 첫 유효 프레임")
  class ThumbnailSeek {

    @Test
    @DisplayName("given 일반 영상 when 계산 then 시작부 0.1초(첫 유효 프레임)를 쓴다")
    void normal_video_seeks_to_first_valid_frame() {
      // 시작 1초에 제작 썸네일을 넣은 영상(원장 소개 등)의 화면을 poster 로 잡아야 한다.
      // 과거의 1초 고정 지점은 그 화면을 지나쳐 인터뷰 중간 프레임을 잡았다.
      assertThat(VideoTranscoder.thumbnailSeekSeconds(30.0)).isEqualTo("0.100");
      assertThat(VideoTranscoder.thumbnailSeekSeconds(2.0)).isEqualTo("0.100");
    }

    @Test
    @DisplayName("given 0.2초 미만 영상 when 계산 then 중간 지점을 써서 빈 프레임을 피한다")
    void very_short_video_seeks_to_midpoint() {
      assertThat(VideoTranscoder.thumbnailSeekSeconds(0.1)).isEqualTo("0.050");
    }
  }
}
