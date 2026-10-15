package f2r.tutor_solution.private_tutor_solution_back.infrastructure.video;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 외부 바이너리(ffmpeg/ffprobe) 실행 seam.
 *
 * <p>{@link VideoTranscoder}가 ProcessBuilder를 직접 붙들면 exit code/타임아웃 분기를 단위 테스트할 수
 * 없어 이 인터페이스로 분리한다. 운영 구현은 {@link DefaultProcessRunner}.
 */
public interface ProcessRunner {
  String PROGRESS_FILE_TOKEN = "{academy-video-progress-file}";

  /**
   * 명령을 실행하고 종료를 기다린다.
   *
   * @param timeoutSec 초과 시 프로세스를 강제 종료하고 {@link VideoTranscodeException}을 던진다
   * @throws VideoToolUnavailableException 바이너리를 찾을 수 없을 때
   */
  ProcessOutput run(List<String> command, int timeoutSec) throws IOException;

  /**
   * Runs a process under an already-started whole-attempt budget.
   *
   * <p>The command may contain {@link #PROGRESS_FILE_TOKEN}; the production runner replaces it
   * with a private progress file and treats only increasing ffmpeg media timestamps as progress.
   */
  default ProcessOutput runProgressAware(
      List<String> command,
      Duration remaining,
      Duration mediaProgressStall,
      BooleanSupplier cancelled) throws IOException {
    if (cancelled.getAsBoolean()) {
      throw new ProcessCancelledException("PROCESS_CANCELLED");
    }
    return run(command, Math.max(1, Math.toIntExact((remaining.toMillis() + 999) / 1_000)));
  }

  default ProcessOutput runProgressAware(
      List<String> command,
      Duration remaining,
      Duration mediaProgressStall,
      BooleanSupplier cancelled,
      Runnable mediaProgressObserved) throws IOException {
    return runProgressAware(command, remaining, mediaProgressStall, cancelled);
  }

  record ProcessOutput(int exitCode, String stdout, String stderr) {
    public boolean succeeded() {
      return exitCode == 0;
    }
  }
}
