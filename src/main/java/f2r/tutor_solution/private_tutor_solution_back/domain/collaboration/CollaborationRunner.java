package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

@Component
public class CollaborationRunner {
  private final CollaborationService service;
  private final CollaborationProvider provider;
  private final ThreadPoolExecutor executor =
      new ThreadPoolExecutor(
          2,
          2,
          0,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(8),
          r -> {
            Thread t = new Thread(r, "collaboration-dispatch");
            t.setDaemon(true);
            return t;
          });

  public CollaborationRunner(CollaborationService service, CollaborationProvider provider) {
    this.service = service;
    this.provider = provider;
  }

  public void launch(String id) {
    try {
      executor.execute(() -> run(id));
    } catch (RejectedExecutionException full) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "호출 대기열이 가득 찼습니다. 중단 후 다시 시작하세요.");
    }
  }

  void run(String id) {
    for (int i = 0; i <= 20; i++) {
      CollaborationService.Call call;
      try {
        call = service.claim(id);
      } catch (RuntimeException failed) {
        return;
      }
      if (call == null) return;
      try {
        service.finish(call, provider.complete(call.agent(), call.prompt()));
      } catch (Exception failed) {
        service.unknown(call);
        return;
      }
    }
  }

  @PreDestroy
  void stop() {
    executor.shutdown();
  }
}
