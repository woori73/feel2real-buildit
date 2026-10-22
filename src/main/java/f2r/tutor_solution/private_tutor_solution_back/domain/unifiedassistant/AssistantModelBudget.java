package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Per-user Muse call ceiling (this instance only). Exhausted budget falls back to the rule parser; it never blocks business work. */
@Slf4j @Component
public class AssistantModelBudget {
  private final int perMinute, perDay, perUpload;
  private final LongSupplier clock;
  private final Map<Long,Deque<Long>> calls=new ConcurrentHashMap<>();
  @Autowired
  public AssistantModelBudget(@Value("${buildit.unified-assistant.model-calls-per-minute:6}") int perMinute,
      @Value("${buildit.unified-assistant.model-calls-per-day:60}") int perDay,
      @Value("${buildit.unified-assistant.model-calls-per-upload:3}") int perUpload) {
    this(perMinute,perDay,perUpload,System::currentTimeMillis);
  }
  AssistantModelBudget(int perMinute,int perDay,int perUpload,LongSupplier clock) {
    this.perMinute=perMinute; this.perDay=perDay; this.perUpload=perUpload; this.clock=clock;
  }
  public int perUpload() { return perUpload; }
  /** Records one call when allowed. Logs counts only, never the user or the text. */
  public boolean tryAcquire(Long userId) {
    Deque<Long> history=calls.computeIfAbsent(userId,k->new ArrayDeque<>());
    synchronized(history) {
      long now=clock.getAsLong();
      while(!history.isEmpty()&&history.peekFirst()<=now-86_400_000L) history.pollFirst();
      long lastMinute=history.stream().filter(t->t>now-60_000L).count();
      if(history.size()>=perDay||lastMinute>=perMinute) { log.info("unified-assistant model call denied minute={} day={}",lastMinute,history.size()); return false; }
      history.addLast(now);
      log.info("unified-assistant model call minute={} day={}",lastMinute+1,history.size());
      return true;
    }
  }
}
