package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runs provider fetches outside DB transactions with an orchestration deadline and periodic
 * DB-backed lease heartbeat. Cancellation is interrupt-only; unsafe thread termination is never
 * used.
 */
@Component
public class EasyCollectionExecutionRuntime {

  private final ExecutorService fetchExecutor = Executors.newVirtualThreadPerTaskExecutor();
  private final ScheduledExecutorService heartbeatExecutor =
      Executors.newSingleThreadScheduledExecutor();
  private final Duration deadline;
  private final Duration heartbeatInterval;

  public EasyCollectionExecutionRuntime(
      @Value("${easy.collection.execution-deadline-seconds:120}") long deadlineSeconds,
      @Value("${easy.collection.heartbeat-seconds:30}") long heartbeatSeconds) {
    this.deadline = Duration.ofSeconds(Math.max(1L, deadlineSeconds));
    this.heartbeatInterval = Duration.ofSeconds(Math.max(1L, heartbeatSeconds));
  }

  public <T> T execute(Callable<T> fetch, Runnable heartbeat) {
    Future<T> future = fetchExecutor.submit(fetch);
    ScheduledFuture<?> heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(
        heartbeat, heartbeatInterval.toSeconds(), heartbeatInterval.toSeconds(), TimeUnit.SECONDS);
    try {
      return future.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new EasyCollectionException(EasyCollectionErrorCode.EXECUTION_TIMEOUT,
          "Collection execution deadline exceeded.", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      throw new EasyCollectionException(EasyCollectionErrorCode.PROVIDER_TEMPORARY,
          "Collection execution was interrupted.", e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof EasyCollectionException collectionFailure) {
        throw collectionFailure;
      }
      if (cause instanceof RuntimeException runtimeFailure) {
        throw runtimeFailure;
      }
      throw new EasyCollectionException(EasyCollectionErrorCode.PROVIDER_TEMPORARY,
          "Provider collection failed temporarily.", cause);
    } finally {
      heartbeatTask.cancel(false);
    }
  }

  @PreDestroy
  void shutdown() {
    fetchExecutor.shutdownNow();
    heartbeatExecutor.shutdownNow();
  }
}
