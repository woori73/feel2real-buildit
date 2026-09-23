package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

/**
 * Visible Local Browser Collector task lifecycle. IDLE exists only as the collector agent's own
 * pre-task concept (no DB row yet) — a persisted task always starts at STARTING.
 */
public enum EasyCollectorTaskStatus {
  IDLE,
  STARTING,
  BROWSER_OPEN,
  WAITING_FOR_USER_AUTH,
  AUTHENTICATED,
  COLLECTING,
  DOWNLOADING,
  COMPLETED,
  FAILED,
  CANCELLED;

  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED || this == CANCELLED;
  }
}
