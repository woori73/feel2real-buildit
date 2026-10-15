package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ArtCraft 웹 편집기 제어 서버 설정.
 *
 * <p>{@code application.yaml}에서 {@code artcraft.editor} prefix로 바인딩된다.
 * 모든 값에 안전한 기본값이 있어 별도 설정 없이도 애플리케이션이 기동된다.
 *
 * <pre>
 * artcraft:
 *   editor:
 *     cli-path: /opt/artcraft/pdfcraft-cli   # 한글 패치된 PdfCraft CLI 바이너리 경로
 *     storage-root: /var/lib/feel2real/artcraft  # 프로젝트/스냅샷/PDF 저장 루트
 *     cli-timeout-seconds: 60                 # CLI 1회 실행 타임아웃
 *     sse-timeout-millis: 1800000             # SSE 구독 유지 시간 (30분)
 * </pre>
 *
 * <p>주의: CLI 바이너리(48MB)는 저장소에 커밋하지 않으며, 배포 시 별도로
 * {@code cli-path} 위치에 배치한다. 바이너리가 없으면 편집 API는 503을 반환한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "artcraft.editor")
public class ArtCraftEditorProperties {

  /** 한글 패치된 pdfcraft-cli 바이너리 절대 경로. */
  private String cliPath = "/opt/artcraft/pdfcraft-cli";

  /**
   * 프로젝트 저장 루트. 실제 레이아웃은
   * {@code {storage-root}/artcraft-projects/{academyId}/{projectId}/} 이다.
   */
  private String storageRoot =
      System.getProperty("java.io.tmpdir") + "/artcraft-editor";

  /** {@code pdfcraft-cli run --script} 1회 실행 타임아웃(초). */
  private long cliTimeoutSeconds = 60;

  /** SSE({@code /events}) 구독 유지 시간(밀리초). 기본 30분. */
  private long sseTimeoutMillis = 30 * 60 * 1000L;
}
