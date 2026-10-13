package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PreflightResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PreflightResponse.Finding;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PreflightResponse.LowPpiFinding;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PreflightResponse.MissingGlyphFinding;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PublishingCommand;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob.Status;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingVersion;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.repository.PublishingVersionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 출판 편집 서비스 (P1).
 *
 * <p>명령 실행·페이지 PNG 렌더링·preflight 검수를 담당한다.
 * 모든 변경 전 스냅샷을 자동 생성한다 (복원 가능).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishingEditService {

  private final PublishingJobService jobService;
  private final DesignCraftCliExecutor cliExecutor;
  private final CommandAllowlist allowlist;
  private final StyleParamNormalizer normalizer;
  private final PublishingVersionRepository versionRepository;
  private final ObjectMapper objectMapper;

  /**
   * 편집 명령을 순차 실행한다.
   *
   * <p>실행 전 스냅샷을 생성한다. 빈 명령 목록은 스냅샷만 생성한다 (수동 저장용).
   *
   * @return 생성된 스냅샷 ID
   */
  @Transactional
  public Long executeCommands(AuthenticatedPrincipal principal, Long jobId,
      List<PublishingCommand> commands, String label) {
    PublishingJob job = jobService.requireOwned(principal, jobId);
    requireReady(job);

    Long snapshotId = createSnapshot(job, label != null ? label : "편집 전");
    if (commands == null || commands.isEmpty()) {
      log.info("빈 명령: 스냅샷만 생성 jobId={}, snapshotId={}", jobId, snapshotId);
      return snapshotId;
    }
    // 직접 편집 API는 AI allowlist와 동일한 제약을 적용 (내부 명령 차단)
    allowlist.validateAiPlan(commands);

    Path workDir = Path.of(job.getWorkDir());

    int i = 0;
    for (PublishingCommand cmd : commands) {
      i++;
      PublishingCommand normalized = normalizeCommand(cmd);
      try {
        cliExecutor.execute(normalized, workDir);
      } catch (ResponseStatusException e) {
        throw new ResponseStatusException(e.getStatusCode(),
            "명령 " + i + "/" + commands.size() + " 실패 (" + cmd.id() + "): "
                + e.getReason(), e);
      }
    }
    log.info("명령 실행 완료: jobId={}, {}건, snapshotId={}", jobId, commands.size(), snapshotId);
    return snapshotId;
  }

  /**
   * 편집 명령을 순차 실행한다 (라벨 없음).
   */
  @Transactional
  public Long executeCommands(AuthenticatedPrincipal principal, Long jobId,
      List<PublishingCommand> commands) {
    return executeCommands(principal, jobId, commands, null);
  }

  /**
   * 페이지를 PNG로 렌더링한다.
   *
   * @param page 1-based 페이지 번호
   * @param scale 배율 (기본 2.0, 썸네일은 0.5)
   * @return PNG 바이트
   */
  public byte[] renderPagePng(AuthenticatedPrincipal principal, Long jobId,
      int page, double scale) {
    PublishingJob job = jobService.requireOwned(principal, jobId);
    requireReady(job);
    if (page < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 번호는 1 이상이어야 합니다.");
    }
    Path workDir = Path.of(job.getWorkDir());
    Path designcraftFile = Path.of(job.getDesigncraftFile());
    Path outPng;
    try {
      outPng = Files.createTempFile(workDir, "page-" + page + "-", ".png");
      cliExecutor.renderPage(designcraftFile, page, scale, outPng, workDir);
      byte[] bytes = Files.readAllBytes(outPng);
      Files.deleteIfExists(outPng);
      return bytes;
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "페이지 렌더링 실패: " + e.getMessage(), e);
    }
  }

  /**
   * preflight 검수를 실행한다.
   */
  public PreflightResponse runPreflight(AuthenticatedPrincipal principal, Long jobId) {
    PublishingJob job = jobService.requireOwned(principal, jobId);
    requireReady(job);
    Path workDir = Path.of(job.getWorkDir());
    Path designcraftFile = Path.of(job.getDesigncraftFile());

    // preflight.run은 내부 전용 명령으로 직접 실행
    PublishingCommand cmd = new PublishingCommand("preflight.run",
        Map.of("minPpi", 150));
    DesignCraftCliExecutor.CliResult result = cliExecutor.execute(cmd, workDir);
    return parsePreflight(result.output());
  }

  /** 스냅샷 생성 (정본 파일 복사). */
  @Transactional
  public Long createSnapshot(PublishingJob job, String label) {
    try {
      Path src = Path.of(job.getDesigncraftFile());
      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
      Path snapshotsDir = Path.of(job.getWorkDir(), "snapshots");
      Files.createDirectories(snapshotsDir);
      Path dest = snapshotsDir.resolve("snapshot-" + timestamp + ".designcraft");
      Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
      PublishingVersion version = new PublishingVersion(
          job.getId(), dest.toString(), label);
      version = versionRepository.save(version);
      log.info("스냅샷 생성: jobId={}, versionId={}", job.getId(), version.getId());
      return version.getId();
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          "스냅샷 생성 실패: " + e.getMessage(), e);
    }
  }

  private void requireReady(PublishingJob job) {
    if (job.getStatus() != Status.RUNNING && job.getStatus() != Status.DONE) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "작업이 준비되지 않았습니다: " + job.getStatus());
    }
    if (job.getWorkDir() == null || job.getDesigncraftFile() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "작업 디렉토리가 준비되지 않았습니다.");
    }
  }

  /** style.paragraph.create 파라미터 정규화. */
  private PublishingCommand normalizeCommand(PublishingCommand cmd) {
    if ("style.paragraph.create".equals(cmd.id())) {
      Map<String, Object> normalized = normalizer.normalizeParagraph(cmd.params());
      return new PublishingCommand(cmd.id(), normalized);
    }
    return cmd;
  }

  /** preflight.run JSON 출력을 Qoder §2 스키마로 파싱한다. */
  private PreflightResponse parsePreflight(String output) {
    try {
      String json = extractJson(output);
      Map<String, Object> map = objectMapper.readValue(json,
          new TypeReference<Map<String, Object>>() {});
      List<Finding> overset = new ArrayList<>();
      List<MissingGlyphFinding> missingGlyphs = new ArrayList<>();
      List<LowPpiFinding> lowPpi = new ArrayList<>();
      Object issuesObj = map.get("issues");
      if (issuesObj instanceof List<?> list) {
        for (Object o : list) {
          if (o instanceof Map<?, ?> m) {
            String kind = str(m.get("kind"));
            String severity = str(m.get("severity"));
            String message = str(m.get("message"));
            Integer page = m.get("page") instanceof Number n ? n.intValue() : null;
            boolean isError = "error".equalsIgnoreCase(severity);
            if ("missingGlyphs".equalsIgnoreCase(kind)) {
              String glyph = str(m.get("glyph"));
              if (glyph.isEmpty()) {
                glyph = str(m.get("item"));
              }
              missingGlyphs.add(new MissingGlyphFinding(page, message, glyph));
            } else if ("lowPpi".equalsIgnoreCase(kind) || "ppi".equalsIgnoreCase(kind)) {
              Integer ppi = m.get("ppi") instanceof Number n ? n.intValue()
                  : m.get("actualPpi") instanceof Number n2 ? n2.intValue() : null;
              lowPpi.add(new LowPpiFinding(page, message, ppi));
            } else if ("overset".equalsIgnoreCase(kind)) {
              overset.add(new Finding(page, message));
            } else if (isError) {
              // 알 수 없는 error는 overset에 포함 (프론트 표시용)
              overset.add(new Finding(page, "[" + kind + "] " + message));
            }
            // warning은 프론트에서 표시하지 않으므로 무시
          }
        }
      }
      boolean passed = overset.isEmpty() && missingGlyphs.isEmpty();
      return new PreflightResponse(passed, overset, missingGlyphs, lowPpi);
    } catch (Exception e) {
      log.warn("preflight 결과 파싱 실패: {}", e.getMessage());
      return new PreflightResponse(false,
          List.of(new Finding(null, "preflight 결과 파싱 실패")),
          List.of(), List.of());
    }
  }

  private String extractJson(String output) {
    int start = output.indexOf('{');
    int end = output.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return output.substring(start, end + 1);
    }
    return output;
  }

  private String str(Object o) {
    return o == null ? "" : String.valueOf(o);
  }
}
