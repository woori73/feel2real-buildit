package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * HWPX Worker 설정.
 *
 * <p>publishing 패키지와 독립적이다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "hwpx")
public class HwpxProperties {

  /** Worker 활성화 여부. */
  private boolean workerEnabled = true;

  /** 작업 파일 저장 루트. */
  private String storageRoot = "/tmp/hwpx";

  /** Python CLI 경로 (hwpx_cli.py). */
  private String cliPath = "/opt/hwpx-worker/hwpx_cli.py";

  /** Python 인터프리터. */
  private String pythonBin = "python3";

  /** CLI 실행 타임아웃 (초). */
  private int cliTimeoutSeconds = 300;
}
