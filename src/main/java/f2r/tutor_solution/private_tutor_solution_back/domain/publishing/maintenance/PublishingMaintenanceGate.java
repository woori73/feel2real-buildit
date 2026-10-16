package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.maintenance;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** One JVM's operation admission. It never claims to drain external workers/instances. */
@Component
public class PublishingMaintenanceGate {
  public enum State { OPEN, DRAINING, CLOSED, FAULT }
  public record Status(State state, int activeOperations, int liveCliProcesses,
      boolean singleProcessDrained, boolean fleetSafeToReplace) {}
  private static final ThreadLocal<Operation> CURRENT = new ThreadLocal<>();
  private final Path directory;
  private FileChannel instanceChannel;
  private FileLock instanceLock;
  private State state = State.CLOSED;
  private int active;
  private int processes;

  public PublishingMaintenanceGate(@Value("${publishing.maintenance.state-dir:/var/lib/feel2real/designcraft-maintenance}") String directory) {
    this.directory = Path.of(directory);
    try {
      Files.createDirectories(this.directory);
      instanceChannel = FileChannel.open(this.directory.resolve("instance.lock"),
          StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      instanceLock = instanceChannel.tryLock();
      if (instanceLock == null) throw new IOException("another instance owns the directory");
      // Always start closed, including after an unclean stop or a failed marker write.
      persist(State.CLOSED);
    } catch (Exception e) {
      state = State.FAULT; // Only DesignCraft is unavailable; do not fail shared Spring startup.
    }
  }

  private void persist(State next) throws IOException {
    Path temporary = directory.resolve("state.tmp");
    try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
      channel.write(java.nio.ByteBuffer.wrap(next.name().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
      channel.force(true);
    }
    Files.move(temporary, directory.resolve("state"), StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
  }

  public synchronized Scope enter() {
    Operation previous = CURRENT.get();
    if (previous != null && previous.gate == this && !previous.finished) {
      previous.references++;
      return new Scope(previous, previous);
    }
    if (state != State.OPEN) throw unavailable();
    Operation operation = new Operation(this);
    active++;
    CURRENT.set(operation);
    return new Scope(operation, previous);
  }

  public synchronized Status drain() {
    if (instanceLock == null || !instanceLock.isValid()) { state = State.FAULT; return status(); }
    state = State.DRAINING; // Same monitor as enter(): no admission/check/increment race.
    try { persist(state); } catch (IOException e) { state = State.FAULT; }
    settle();
    return status();
  }

  public synchronized Status resume() {
    if (state != State.CLOSED || active != 0 || processes != 0
        || instanceLock == null || !instanceLock.isValid()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "DesignCraft drain이 완료되지 않았습니다.");
    }
    try { persist(State.OPEN); state = State.OPEN; }
    catch (IOException e) { state = State.FAULT; throw unavailable(); }
    return status();
  }

  public synchronized Status status() {
    return new Status(state, active, processes,
        state == State.CLOSED && active == 0 && processes == 0, false);
  }

  private void settle() {
    if (state == State.DRAINING && active == 0 && processes == 0) {
      try { persist(State.CLOSED); state = State.CLOSED; }
      catch (IOException e) { state = State.FAULT; }
    }
    notifyAll();
  }

  private synchronized void release(Operation operation) {
    if (--operation.references == 0) {
      operation.finished = true;
      active--;
      settle();
    }
  }

  /** Retains a complete service operation until an asynchronous result really completes. */
  public synchronized AutoCloseable retainCurrent() {
    Operation operation = requireOperation();
    operation.references++;
    AtomicBoolean closed = new AtomicBoolean();
    return () -> { if (closed.compareAndSet(false, true)) release(operation); };
  }

  private Operation requireOperation() {
    Operation operation = CURRENT.get();
    if (operation == null || operation.gate != this || operation.finished) throw unavailable();
    return operation;
  }

  /** A servlet async lifetime plus bindable task context. */
  public synchronized Hold holdCurrent() {
    Operation operation = requireOperation(); operation.references++;
    return new Hold(operation);
  }
  public final class Hold implements AutoCloseable {
    private final Operation operation;
    private final AtomicBoolean closed = new AtomicBoolean();
    private Hold(Operation operation) { this.operation = operation; }
    public Scope bind() {
      synchronized (PublishingMaintenanceGate.this) {
        if (closed.get() || operation.finished) throw unavailable();
        operation.references++;
        Operation previous = CURRENT.get(); CURRENT.set(operation);
        return new Scope(operation, previous);
      }
    }
    @Override public void close() {
      if (closed.compareAndSet(false, true)) release(operation);
    }
  }

  /** Explicit propagation for any future background work. Cancellation before start releases it. */
  public <T> Future<T> submit(Executor executor, Callable<T> callable) {
    final Operation operation;
    synchronized (this) { operation = requireOperation(); operation.references++; }
    AtomicBoolean started = new AtomicBoolean(), released = new AtomicBoolean();
    Runnable releaseOnce = () -> { if (released.compareAndSet(false, true)) release(operation); };
    FutureTask<T> task = new FutureTask<>(() -> {
      synchronized (started) {
        if (released.get()) throw new CancellationException();
        started.set(true);
      }
      Operation previous = CURRENT.get(); CURRENT.set(operation);
      try { return callable.call(); }
      finally { restore(previous); releaseOnce.run(); }
    }) {
      @Override protected void done() {
        synchronized (started) { if (!started.get()) releaseOnce.run(); }
      }
    };
    try { executor.execute(task); }
    catch (RuntimeException e) { task.cancel(false); throw e; }
    return task;
  }

  /** Actual child lifetime is retained even if a timeout/exception unwinds the Java operation. */
  public static void trackCurrentProcess(Process process) {
    Operation operation = CURRENT.get();
    if (operation == null) return; // Unmanaged legacy unit-test instances only; Spring beans are proxied.
    PublishingMaintenanceGate gate = operation.gate;
    synchronized (gate) { operation.references++; gate.processes++; }
    process.onExit().whenComplete((ignored, error) -> {
      synchronized (gate) { gate.processes--; gate.release(operation); }
    });
  }

  private static void restore(Operation previous) {
    if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
  }
  public static ResponseStatusException unavailable() {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
        "DesignCraft 유지보수 중입니다. 잠시 후 다시 시도하세요.");
  }
  private static final class Operation {
    final PublishingMaintenanceGate gate;
    int references = 1;
    boolean finished;
    Operation(PublishingMaintenanceGate gate) { this.gate = gate; }
  }
  public final class Scope implements AutoCloseable {
    private final Operation operation, previous;
    private final AtomicBoolean closed = new AtomicBoolean();
    private Scope(Operation operation, Operation previous) { this.operation = operation; this.previous = previous; }
    @Override public void close() {
      if (closed.compareAndSet(false, true)) { restore(previous); release(operation); }
    }
  }
  @PreDestroy public synchronized void close() {
    // Shutdown does not resume admission. Every subsequent instance starts CLOSED.
    state = State.CLOSED;
    try { if (instanceLock != null) instanceLock.close(); } catch (IOException ignored) {}
    try { if (instanceChannel != null) instanceChannel.close(); } catch (IOException ignored) {}
  }
}
