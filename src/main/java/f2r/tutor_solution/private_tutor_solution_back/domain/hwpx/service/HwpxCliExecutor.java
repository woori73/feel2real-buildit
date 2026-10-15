package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.config.HwpxProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.dto.HwpxCellEdit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * HWPX Python CLI 실행기.
 *
 * <p>DesignCraftCliExecutor 패턴을 따른다. Python을 JVM에 임베드하지 않고
 * 별도 프로세스로 호출해 의존성을 분리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HwpxCliExecutor {

  private final HwpxProperties props;
  private final ObjectMapper objectMapper;

  /** CLI 실행 결과. */
  public record CliResult(boolean ok, Map<String, Object> output, String error) {}

  /**
   * 표 셀 수정 실행.
   *
   * @param workDir 작업 디렉터리 (입력 파일이 이미 배치됨)
   * @param inputFileName 입력 파일명 (hwp | hwpx)
   * @param edits 셀 수정 목록
   * @param outputFileName 출력 파일명 (hwpx)
   */
  public CliResult edit(Path workDir, String inputFileName,
      List<HwpxCellEdit> edits, String outputFileName) {
    try {
      // edits.json 작성
      Path editsFile = workDir.resolve("edits.json");
      Map<String, Object> editsDoc = Map.of("edits", edits);
      Files.writeString(editsFile,
          objectMapper.writeValueAsString(editsDoc), StandardCharsets.UTF_8);

      List<String> cmd = List.of(
          props.getPythonBin(),
          props.getCliPath(),
          "edit",
          "--in", workDir.resolve(inputFileName).toString(),
          "--edits", editsFile.toString(),
          "--out", workDir.resolve(outputFileName).toString());

      return run(cmd);
    } catch (IOException e) {
      return new CliResult(false, Map.of(), "edits.json 작성 실패: " + e.getMessage());
    }
  }

  /** 문서 정보 조회. */
  public CliResult info(Path inputFile) {
    List<String> cmd = List.of(
        props.getPythonBin(), props.getCliPath(),
        "info", "--in", inputFile.toString());
    return run(cmd);
  }

  /** 무결성 검증. */
  public CliResult validate(Path inputFile) {
    List<String> cmd = List.of(
        props.getPythonBin(), props.getCliPath(),
        "validate", "--in", inputFile.toString());
    return run(cmd);
  }

  private CliResult run(List<String> cmd) {
    log.info("HWPX CLI 실행: {}", String.join(" ", cmd));
    try {
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      Process process = pb.start();
      boolean finished = process.waitFor(props.getCliTimeoutSeconds(), TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return new CliResult(false, Map.of(), "CLI 타임아웃");
      }
      String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exitCode = process.exitValue();
      log.info("HWPX CLI 종료: exit={}, output={}", exitCode, stdout);

      Map<String, Object> output;
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = objectMapper.readValue(stdout, Map.class);
        output = parsed;
      } catch (Exception e) {
        output = Map.of("raw", stdout);
      }

      if (exitCode != 0) {
        return new CliResult(false, output, "CLI exit=" + exitCode);
      }
      boolean ok = Boolean.TRUE.equals(output.get("ok"));
      return new CliResult(ok, output, ok ? "" : "CLI ok=false");
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      return new CliResult(false, Map.of(), "CLI 실행 실패: " + e.getMessage());
    }
  }
}
