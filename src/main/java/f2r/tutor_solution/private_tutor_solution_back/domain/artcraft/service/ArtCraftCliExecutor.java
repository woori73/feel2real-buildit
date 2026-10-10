package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.config.ArtCraftEditorProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.EditCommandRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception.ArtCraftCliException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 한글 패치된 PdfCraft CLI({@code pdfcraft-cli run --script steps.json}) 실행 래퍼.
 *
 * <p>동작:
 * <ol>
 *   <li>편집 명령 목록을 {@code steps.json} (JSON 배열, {@code [{"tool":..,"args":{..}}]}) 으로 기록
 *   <li>{@code ProcessBuilder}로 CLI 실행. 작업 디렉토리는 프로젝트 디렉토리로 격리하므로
 *       {@code doc_save}의 상대 경로 출력물이 프로젝트 안에 생성된다.
 *   <li>표준 출력/에러를 병합 수집하고, 설정된 타임아웃(기본 60초)을 적용
 * </ol>
 *
 * <p>CLI는 서버 내부에서만 실행되며 외부에서 직접 호출할 수 없다.
 * 명령 화이트리스트 검증은 호출자({@link ArtCraftEditorService})가 수행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArtCraftCliExecutor {

  /** 로그/응답에 포함할 CLI 출력 최대 길이. */
  private static final int MAX_OUTPUT_CHARS = 64 * 1024;

  private final ArtCraftEditorProperties props;
  private final ObjectMapper objectMapper;

  /** CLI 실행 결과. */
  public record CliResult(int exitCode, String output) {
  }

  /**
   * 편집 명령을 steps.json으로 기록하고 CLI를 실행한다.
   *
   * @param commands 화이트리스트를 통과한 편집 명령 목록
   * @param workDir steps.json을 기록할 격리된 작업 디렉토리 (호출자가 생성/정리)
   * @param projectDir CLI 프로세스의 작업 디렉토리 (= 프로젝트 디렉토리)
   * @return CLI 실행 결과 (exit 0 보장)
   * @throws ArtCraftCliException 바이너리 부재(503), 비정상 종료(502), 타임아웃(504)
   */
  public CliResult execute(List<EditCommandRequest> commands, Path workDir, Path projectDir) {
    Path cli = Path.of(props.getCliPath());
    if (!Files.isExecutable(cli)) {
      throw new ArtCraftCliException(HttpStatus.SERVICE_UNAVAILABLE,
          "PDF 편집 CLI를 찾을 수 없습니다: " + props.getCliPath()
              + " — 관리자가 한글 패치된 pdfcraft-cli 바이너리를 배치해야 합니다.");
    }
    try {
      Files.createDirectories(workDir);
      Path stepsFile = workDir.resolve("steps.json");
      List<Map<String, Object>> steps = new ArrayList<>();
      for (EditCommandRequest cmd : commands) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("tool", cmd.tool());
        step.put("args", cmd.args() == null ? Map.of() : cmd.args());
        steps.add(step);
      }
      objectMapper.writeValue(stepsFile.toFile(), steps);

      ProcessBuilder pb = new ProcessBuilder(
          cli.toAbsolutePath().toString(), "run", "--script",
          stepsFile.toAbsolutePath().toString());
      pb.directory(projectDir.toFile());
      pb.redirectErrorStream(true);
      log.info("ArtCraft CLI 실행: {} (workDir={})", String.join(" ", pb.command()), workDir);

      Process process = pb.start();
      // 파이프 데드락 방지: 출력은 별도 스레드에서 수집하면서 타임아웃 대기
      CompletableFuture<String> outputFuture =
          CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
      boolean finished = process.waitFor(props.getCliTimeoutSeconds(), TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new ArtCraftCliException(HttpStatus.GATEWAY_TIMEOUT,
            "PDF 편집 CLI 실행 시간 초과 (" + props.getCliTimeoutSeconds() + "초)");
      }
      String output = truncate(outputFuture.get(5, TimeUnit.SECONDS));
      int exitCode = process.exitValue();
      if (exitCode != 0) {
        log.warn("ArtCraft CLI 비정상 종료: exit={}, output={}", exitCode, output);
        throw new ArtCraftCliException(HttpStatus.BAD_GATEWAY,
            "PDF 편집 CLI 실행 실패 (exit=" + exitCode + "): " + output);
      }
      return new CliResult(exitCode, output);
    } catch (ArtCraftCliException e) {
      throw e;
    } catch (Exception e) {
      throw new ArtCraftCliException(HttpStatus.BAD_GATEWAY,
          "PDF 편집 CLI 실행 중 오류: " + e.getMessage(), e);
    }
  }

  private static String readAll(InputStream in) {
    try {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "";
    }
  }

  private static String truncate(String s) {
    if (s != null && s.length() > MAX_OUTPUT_CHARS) {
      return s.substring(0, MAX_OUTPUT_CHARS) + "...(truncated)";
    }
    return s == null ? "" : s;
  }
}
