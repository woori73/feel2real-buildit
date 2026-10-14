package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * PhotoCraft CLI를 이용한 이미지 편집 서비스.
 *
 * <p>지원 작업:
 * <ul>
 *   <li>밝기 조절 (brightness): -100 ~ 100</li>
 *   <li>대비 조절 (contrast): -100 ~ 100</li>
 *   <li>노출 조절 (exposure): -5.0 ~ 5.0</li>
 *   <li>채도 조절 (vibrance): -100 ~ 100</li>
 *   <li>리사이즈 (resize): 너비 x 높이</li>
 * </ul>
 *
 * <p>안전 제어:
 * <ul>
 *   <li>타임아웃: 30초 (설정 가능)</li>
 *   <li>작업 디렉토리 격리: 임시 디렉토리 사용</li>
 *   <li>원본 보존: 실패 시 원본 파일 untouched</li>
 *   <li>입력 검증: 경로 traversal 방지</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoCraftImageService {

  private final ObjectMapper objectMapper;

  /** PhotoCraft CLI 경로. */
  @Value("${artcraft.photocraft.cli-path:/opt/artcraft/photocraft/bin/photocraft-cli}")
  private String cliPath;

  /** 이미지 처리 타임아웃 (초). */
  @Value("${artcraft.photocraft.timeout-sec:30}")
  private int timeoutSec;

  /** CLI 사용 가능 여부. */
  public boolean isAvailable() {
    try {
      Path cli = Path.of(cliPath);
      return Files.exists(cli) && Files.isExecutable(cli);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * 이미지 편집 요청.
   *
   * @param inputPath 입력 이미지 경로 (서버 로컬 경로)
   * @param outputPath 출력 이미지 경로 (서버 로컬 경로)
   * @param operations 편집 작업 목록 (예: [{"op": "brightness", "value": 20}])
   * @return 성공 여부
   */
  public EditResult editImage(String inputPath, String outputPath, List<ImageOperation> operations) {
    // 입력 검증: 경로 traversal 방지
    if (!isSafePath(inputPath) || !isSafePath(outputPath)) {
      return EditResult.failure("허용되지 않은 파일 경로입니다.");
    }

    if (!isAvailable()) {
      return EditResult.failure("PhotoCraft CLI를 사용할 수 없습니다.");
    }

    Path workDir = null;
    try {
      // 작업 디렉토리 격리
      workDir = Files.createTempDirectory("photocraft-edit-");
      Path workInput = workDir.resolve("input" + extensionOf(inputPath));
      Path workOutput = workDir.resolve("output" + extensionOf(outputPath));

      // 입력 파일을 작업 디렉토리로 복사 (원본 보호)
      Files.copy(Path.of(inputPath), workInput);

      // CLI 명령어 구성
      List<String> command = buildCommand(workInput, workOutput, operations);

      log.info("PhotoCraft 실행: {} operations", operations.size());
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process = pb.start();

      boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return EditResult.failure("이미지 처리 시간 초과 (" + timeoutSec + "초)");
      }

      int exitCode = process.exitValue();
      if (exitCode != 0) {
        String output = new String(process.getInputStream().readAllBytes());
        log.warn("PhotoCraft 실패 (exit={}): {}", exitCode, output.substring(0, Math.min(500, output.length())));
        return EditResult.failure("이미지 처리 실패 (code=" + exitCode + ")");
      }

      // 결과물을 최종 경로로 이동
      Files.createDirectories(Path.of(outputPath).getParent());
      Files.move(workOutput, Path.of(outputPath));

      return EditResult.success(outputPath);

    } catch (IOException e) {
      log.error("PhotoCraft IO 오류", e);
      return EditResult.failure("파일 처리 오류: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return EditResult.failure("처리 중단됨");
    } catch (Exception e) {
      log.error("PhotoCraft 예상치 못한 오류", e);
      return EditResult.failure("처리 오류: " + e.getMessage());
    } finally {
      // 임시 디렉토리 정리
      if (workDir != null) {
        try {
          deleteRecursively(workDir);
        } catch (Exception e) {
          log.warn("임시 디렉토리 정리 실패: {}", workDir, e);
        }
      }
    }
  }

  private List<String> buildCommand(Path input, Path output, List<ImageOperation> operations) {
    List<String> cmd = new ArrayList<>();
    cmd.add(cliPath);
    cmd.add("run");
    cmd.add(input.toString());

    for (ImageOperation op : operations) {
      String commandId = mapToCommandId(op.op());
      if (commandId == null) {
        log.warn("지원하지 않는 작업 무시: {}", op.op());
        continue;
      }
      cmd.add("--cmd");
      cmd.add(commandId);
      cmd.add("--params");
      try {
        cmd.add(objectMapper.writeValueAsString(op.params()));
      } catch (Exception e) {
        cmd.add("{}");
      }
    }

    cmd.add("--out");
    cmd.add(output.toString());
    return cmd;
  }

  private String mapToCommandId(String op) {
    return switch (op.toLowerCase()) {
      case "brightness" -> "image.adjustments.brightnessContrast";
      case "contrast" -> "image.adjustments.brightnessContrast";
      case "exposure" -> "image.adjustments.exposure";
      case "vibrance", "saturation" -> "image.adjustments.vibrance";
      case "levels" -> "image.adjustments.levels";
      default -> null;
    };
  }

  private boolean isSafePath(String path) {
    if (path == null || path.isBlank()) return false;
    // 경로 traversal 방지
    if (path.contains("..")) return false;
    // 절대 경로만 허용
    return Path.of(path).isAbsolute();
  }

  private String extensionOf(String path) {
    int dot = path.lastIndexOf('.');
    return dot >= 0 ? path.substring(dot) : ".png";
  }

  private void deleteRecursively(Path dir) throws IOException {
    if (Files.isDirectory(dir)) {
      try (var stream = Files.list(dir)) {
        for (Path p : stream.toList()) {
          deleteRecursively(p);
        }
      }
    }
    Files.deleteIfExists(dir);
  }

  /** 이미지 편집 작업. */
  public record ImageOperation(String op, Map<String, Object> params) {
    public static ImageOperation brightness(int value) {
      // -100 ~ 100 범위로 제한
      int v = Math.max(-100, Math.min(100, value));
      return new ImageOperation("brightness", Map.of("brightness", v, "contrast", 0));
    }

    public static ImageOperation exposure(double value) {
      // -5.0 ~ 5.0 범위로 제한
      double v = Math.max(-5.0, Math.min(5.0, value));
      return new ImageOperation("exposure", Map.of("exposure", v));
    }

    public static ImageOperation vibrance(int value) {
      int v = Math.max(-100, Math.min(100, value));
      return new ImageOperation("vibrance", Map.of("vibrance", v, "saturation", 0));
    }
  }

  /** 편집 결과. */
  public record EditResult(boolean success, String outputPath, String errorMessage) {
    public static EditResult success(String outputPath) {
      return new EditResult(true, outputPath, null);
    }

    public static EditResult failure(String errorMessage) {
      return new EditResult(false, null, errorMessage);
    }
  }
}
