package f2r.tutor_solution.private_tutor_solution_back.infrastructure.video;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class VideoTranscoderAttemptTest {
  @TempDir Path temp;

  @Test
  void givenWorkerAttempt_whenTranscoding_thenCommandPinsCodecPixelsAndMachineProgress()
      throws Exception {
    RecordingRunner runner = new RecordingRunner();
    VideoTranscoder transcoder =
        new VideoTranscoder(runner, "ffmpeg", "ffprobe", 120, 2);
    Path input = Files.writeString(temp.resolve("raw.mov"), "raw");

    TranscodeResult result = transcoder.transcodeAttempt(
        input, new VideoProbe(1080, 1920, "hevc", 2.0), temp,
        () -> Duration.ofMinutes(10), () -> false, () -> {});

    assertThat(result.width()).isEqualTo(720);
    assertThat(result.height()).isEqualTo(1280);
    List<String> command = runner.commands.getFirst();
    assertThat(command).containsSubsequence("-progress", ProcessRunner.PROGRESS_FILE_TOKEN);
    assertThat(command).containsSubsequence("-c:v", "libx264");
    assertThat(command).containsSubsequence("-pix_fmt", "yuv420p");
    assertThat(command).containsSubsequence("-c:a", "aac");
    assertThat(command).containsSubsequence("-movflags", "+faststart");
    assertThat(runner.commands.get(1)).contains("-q:v", "3");
  }

  private static final class RecordingRunner implements ProcessRunner {
    private final List<List<String>> commands = new ArrayList<>();

    @Override
    public ProcessOutput run(List<String> command, int timeoutSec) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ProcessOutput runProgressAware(
        List<String> command,
        Duration remaining,
        Duration mediaProgressStall,
        BooleanSupplier cancelled) throws IOException {
      commands.add(List.copyOf(command));
      Files.writeString(Path.of(command.getLast()), "output");
      return new ProcessOutput(0, "", "");
    }
  }
}
