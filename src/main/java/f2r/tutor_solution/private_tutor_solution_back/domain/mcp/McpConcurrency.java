package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** One process-wide admission limit includes catalog, search, fetch and upstream reads. */
@Component
public class McpConcurrency {
  private final Semaphore permits;

  public McpConcurrency(@Value("${app.mcp.max-concurrent-calls:8}") int maximum) {
    if (maximum < 1 || maximum > 64)
      throw new IllegalArgumentException("MCP concurrency must be 1..64");
    permits = new Semaphore(maximum, true);
  }

  public boolean tryAcquire() {
    return permits.tryAcquire();
  }

  public void release() {
    permits.release();
  }
}
