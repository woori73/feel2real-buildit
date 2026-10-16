package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DesignCraft 출판 백엔드 설정.
 *
 * <p>{@code application.yaml}에서 {@code publishing} prefix로 바인딩된다.
 *
 * <pre>
 * publishing:
 *   cli-path: /opt/designcraft/designcraft-cli  # designcraft-cli 0.8.0 바이너리
 *   storage-root: /var/lib/feel2real/publishing # 작업·산출물 저장 루트
 *   template-dir: /opt/designcraft/templates    # Document Fonts 별도 배치 경로
 *   cli-timeout-seconds: 120
 *   worker-enabled: true
 *   worker-poll-seconds: 10
 * </pre>
 *
 * <p>주의: CLI 바이너리는 저장소에 커밋하지 않으며 배포 시 별도로 배치한다.
 * 바이너리가 없으면 Worker는 작업을 FAILED로 처리한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "publishing")
public class PublishingProperties {

  /** designcraft-cli 0.8.0 바이너리 절대 경로. */
  private String cliPath = "/opt/designcraft/designcraft-cli";

  /** 작업 디렉토리·산출물 저장 루트. 레이아웃: {root}/jobs/{jobId}/ */
  private String storageRoot =
      System.getProperty("java.io.tmpdir") + "/publishing";

  /** Document Fonts 별도 배치 경로. 레이아웃: {templateDir}/{templateCode}/Document Fonts/ */
  private String templateDir = "/opt/designcraft/templates";

  /** CLI 1회 실행 타임아웃(초). */
  private long cliTimeoutSeconds = 120;

  /** Worker 활성화 여부. */
  private boolean workerEnabled = true;

  /** Worker 폴링 간격(초). */
  private long workerPollSeconds = 10;

  /** 작업 최대 재시도 횟수 (무한 반복 방지). */
  private int maxAttempts = 3;
}
