package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.config.PublishingProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob.Template;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * 한글 템플릿 프로비저닝.
 *
 * <p>정본: {@code buildit-p0/repo/ops/designcraft/templates/} (실측 확정).
 * {@code template.designcraft}(3KB)는 JAR 리소스에 패키징하고,
 * {@code Document Fonts/}(Noto Sans KR, 12MB)는 용량상 서버에 별도 배치한다.
 *
 * <p>작업 디렉토리 레이아웃:
 * <pre>
 * {workDir}/
 *   job.designcraft        # 정본 복사
 *   Document Fonts/        # NotoSansKR-Regular.ttf, NotoSansKR-Bold.ttf, OFL-1.1.txt
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

  private final PublishingProperties props;

  /**
   * 작업 디렉토리에 템플릿 정본과 폰트를 배치한다.
   *
   * @param template 템플릿 종류
   * @param workDir 작업 디렉토리
   * @return 배치된 정본 파일 경로
   */
  public Path provision(Template template, Path workDir) throws IOException {
    Files.createDirectories(workDir);

    // 1. JAR 리소스에서 template.designcraft 복사
    String resourcePath = "publishing/templates/" + template.getCode() + "/template.designcraft";
    ClassPathResource resource = new ClassPathResource(resourcePath);
    if (!resource.exists()) {
      throw new IOException("템플릿 리소스를 찾을 수 없습니다: " + resourcePath);
    }
    Path designcraftFile = workDir.resolve("job.designcraft");
    try (InputStream in = resource.getInputStream()) {
      Files.copy(in, designcraftFile, StandardCopyOption.REPLACE_EXISTING);
    }
    log.info("템플릿 배치: {} -> {}", template.getCode(), designcraftFile);

    // 2. Document Fonts 복사 (서버 별도 배치 경로에서)
    Path fontsSource = templateFontsDir(template);
    Path fontsTarget = workDir.resolve("Document Fonts");
    if (fontsSource != null && Files.isDirectory(fontsSource)) {
      Files.createDirectories(fontsTarget);
      try (Stream<Path> files = Files.list(fontsSource)) {
        for (Path f : files.toList()) {
          Files.copy(f, fontsTarget.resolve(f.getFileName()),
              StandardCopyOption.REPLACE_EXISTING);
        }
      }
      log.info("Document Fonts 배치: {} -> {}", fontsSource, fontsTarget);
    } else {
      log.warn("Document Fonts를 찾을 수 없습니다 (templateDir={}). "
          + "한글 missingGlyphs가 발생할 수 있습니다.", props.getTemplateDir());
    }
    return designcraftFile;
  }

  /**
   * 서버에 별도 배치된 템플릿 디렉토리에서 Document Fonts 위치를 찾는다.
   *
   * <p>레이아웃: {@code {templateDir}/{templateCode}/Document Fonts/}
   */
  private Path templateFontsDir(Template template) {
    String templateDir = props.getTemplateDir();
    if (templateDir == null || templateDir.isBlank()) {
      return null;
    }
    return Path.of(templateDir, template.getCode(), "Document Fonts");
  }
}
