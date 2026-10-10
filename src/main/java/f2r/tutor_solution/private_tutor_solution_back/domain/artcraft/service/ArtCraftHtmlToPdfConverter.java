package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.config.ArtCraftEditorProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception.ArtCraftCliException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 학습지 HTML → PDF 변환기 (headless Chromium {@code --print-to-pdf}).
 *
 * <p>PdfCraft에 HTML 입력 기능이 없어, ArtCraft 프로젝트의 편집 소스 주입 경로로
 * 사용한다. 정본 정책: HTML이 정본, PDF는 산출물. PDF→HTML 역변환은 조판이
 * 소실되므로(Qoder 실측) 원본 복원 수단으로 사용하지 않는다.
 *
 * <p>동작:
 * <ol>
 *   <li>Chromium 바이너리를 런타임에 탐지한다. {@code artcraft.editor.chromium-path}
 *       설정값이 있으면 그것을, 비어 있으면 PATH에서
 *       {@code chromium} → {@code chromium-browser} → {@code google-chrome} →
 *       {@code google-chrome-stable} → {@code chrome} 순으로 찾는다.
 *   <li>찾지 못하면 503 ({@link ArtCraftCliException}) — 서버에 Chromium이 없을 수
 *       있으므로 HTML 업로드 경로는 이 경우를 명확히 알린다.
 *   <li>{@code --headless --print-to-pdf} 로 변환. 타임아웃은
 *       {@code artcraft.editor.cli-timeout-seconds} (기본 60초)를 재사용한다.
 * </ol>
 *
 * <p>Chromium은 서버 내부에서만 실행되며 외부에서 직접 호출할 수 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArtCraftHtmlToPdfConverter {

  /** PATH 자동 탐색 후보 (우선순위 순). */
  private static final List<String> CANDIDATE_BINARIES = List.of(
      "chromium", "chromium-browser", "google-chrome", "google-chrome-stable", "chrome");

  /** 로그/응답에 포함할 Chromium 출력 최대 길이. */
  private static final int MAX_OUTPUT_CHARS = 64 * 1024;

  private final ArtCraftEditorProperties props;

  /**
   * 사용할 Chromium 바이너리를 찾는다.
   *
   * @return 실행 가능한 Chromium 바이너리의 절대 경로
   * @throws ArtCraftCliException 찾을 수 없으면 503
   */
  public Path resolveChromium() {
    String configured = props.getChromiumPath();
    if (configured != null && !configured.isBlank()) {
      Path path = Path.of(configured);
      if (Files.isExecutable(path)) {
        return path.toAbsolutePath();
      }
      throw new ArtCraftCliException(HttpStatus.SERVICE_UNAVAILABLE,
          "HTML→PDF 변환용 Chromium을 찾을 수 없습니다: " + configured
              + " — artcraft.editor.chromium-path 설정을 확인하세요.");
    }
    for (String name : CANDIDATE_BINARIES) {
      Path found = findOnPath(name);
      if (found != null) {
        return found;
      }
    }
    throw new ArtCraftCliException(HttpStatus.SERVICE_UNAVAILABLE,
        "HTML→PDF 변환용 Chromium을 찾을 수 없습니다. "
            + "관리자가 서버에 chromium 패키지를 설치하거나 "
            + "artcraft.editor.chromium-path에 바이너리 경로를 설정해야 합니다.");
  }

  /**
   * HTML 파일을 PDF로 변환한다.
   *
   * @param htmlFile 입력 HTML (절대 경로 권장)
   * @param pdfFile 출력 PDF 경로 (덮어쓰기)
   * @throws ArtCraftCliException Chromium 부재(503), 비정상 종료·출력 누락(502),
   *         타임아웃(504)
   */
  public void convert(Path htmlFile, Path pdfFile) {
    Path chromium = resolveChromium();
    List<String> command = new ArrayList<>();
    command.add(chromium.toString());
    command.add("--headless");
    command.add("--disable-gpu");
    command.add("--no-sandbox");
    command.add("--no-pdf-header-footer");
    command.add("--print-to-pdf=" + pdfFile.toAbsolutePath());
    command.add(htmlFile.toAbsolutePath().toString());

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(htmlFile.toAbsolutePath().getParent().toFile());
    pb.redirectErrorStream(true);
    log.info("ArtCraft HTML→PDF 변환: {} (html={}, pdf={})",
        String.join(" ", command), htmlFile, pdfFile);

    try {
      Process process = pb.start();
      // 파이프 데드락 방지: 출력은 별도 스레드에서 수집하면서 타임아웃 대기
      CompletableFuture<String> outputFuture =
          CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
      boolean finished = process.waitFor(props.getCliTimeoutSeconds(), TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new ArtCraftCliException(HttpStatus.GATEWAY_TIMEOUT,
            "HTML→PDF 변환 시간 초과 (" + props.getCliTimeoutSeconds() + "초)");
      }
      String output = truncate(outputFuture.get(5, TimeUnit.SECONDS));
      int exitCode = process.exitValue();
      if (exitCode != 0) {
        log.warn("ArtCraft Chromium 비정상 종료: exit={}, output={}", exitCode, output);
        throw new ArtCraftCliException(HttpStatus.BAD_GATEWAY,
            "HTML→PDF 변환 실패 (exit=" + exitCode + "): " + output);
      }
      if (!Files.isRegularFile(pdfFile)) {
        throw new ArtCraftCliException(HttpStatus.BAD_GATEWAY,
            "HTML→PDF 변환이 완료됐지만 출력 파일이 생성되지 않았습니다: " + pdfFile
                + (output.isEmpty() ? "" : " (chromium 출력: " + output + ")"));
      }
      log.info("ArtCraft HTML→PDF 변환 완료: {} ({} bytes)",
          pdfFile, Files.size(pdfFile));
    } catch (ArtCraftCliException e) {
      throw e;
    } catch (Exception e) {
      throw new ArtCraftCliException(HttpStatus.BAD_GATEWAY,
          "HTML→PDF 변환 중 오류: " + e.getMessage(), e);
    }
  }

  /** PATH에서 실행 가능한 바이너리를 찾는다. 없으면 null. */
  private static Path findOnPath(String name) {
    String pathEnv = System.getenv("PATH");
    if (pathEnv == null || pathEnv.isBlank()) {
      return null;
    }
    for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
      if (dir.isBlank()) {
        continue;
      }
      Path candidate = Path.of(dir, name);
      if (Files.isExecutable(candidate)) {
        return candidate.toAbsolutePath();
      }
    }
    return null;
  }

  private static String readAll(InputStream in) {
    try {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "";
    }
  }

  private static String truncate(String output) {
    if (output != null && output.length() > MAX_OUTPUT_CHARS) {
      return output.substring(0, MAX_OUTPUT_CHARS) + "...(truncated)";
    }
    return output == null ? "" : output;
  }
}
