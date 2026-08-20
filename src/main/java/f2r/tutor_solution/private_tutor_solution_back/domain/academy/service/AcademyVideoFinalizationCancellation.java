package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AcademyVideoFinalizationCancellation {
  private final ConcurrentHashMap<Key, AtomicBoolean> active = new ConcurrentHashMap<>();
  private final Set<Key> signalled = ConcurrentHashMap.newKeySet();

  public Handle register(String sessionId, String token) {
    Key key = new Key(sessionId, token);
    AtomicBoolean cancelled = new AtomicBoolean(signalled.remove(key));
    active.put(key, cancelled);
    return new Handle(key, cancelled);
  }

  public void signal(String sessionId, String token) {
    if (token == null) return;
    Key key = new Key(sessionId, token);
    AtomicBoolean flag = active.get(key);
    if (flag != null) {
      flag.set(true);
    } else {
      signalled.add(key);
    }
  }

  public boolean isActive(String sessionId, String token) {
    return token != null && active.containsKey(new Key(sessionId, token));
  }

  public final class Handle implements AutoCloseable {
    private final Key key;
    private final AtomicBoolean cancelled;

    private Handle(Key key, AtomicBoolean cancelled) {
      this.key = key;
      this.cancelled = cancelled;
    }

    public boolean cancelled() {
      return cancelled.get();
    }

    @Override
    public void close() {
      active.remove(key, cancelled);
      signalled.remove(key);
    }
  }

  private record Key(String sessionId, String token) {}
}
