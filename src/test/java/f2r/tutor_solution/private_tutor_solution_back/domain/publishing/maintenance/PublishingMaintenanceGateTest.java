package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.maintenance;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

class PublishingMaintenanceGateTest {
  @TempDir Path dir;
  PublishingMaintenanceGate gate;
  @BeforeEach void setup() { gate = new PublishingMaintenanceGate(dir.resolve("state").toString()); }
  @AfterEach void cleanup() { gate.close(); }
  @Test void startupAndRestartNeverAutoResume() {
    assertEquals(PublishingMaintenanceGate.State.CLOSED, gate.status().state());
    gate.resume(); gate.drain(); gate.close();
    try(var restarted = new AutoGate(dir.resolve("state"))) {
      assertEquals(PublishingMaintenanceGate.State.CLOSED,restarted.gate.status().state());
      assertThrows(ResponseStatusException.class, restarted.gate::enter);
    }
    gate = new PublishingMaintenanceGate(dir.resolve("second").toString());
    gate.resume();gate.close(); // even a previous OPEN marker cannot open a restarted server
    try(var restarted = new AutoGate(dir.resolve("second"))) {
      assertEquals(PublishingMaintenanceGate.State.CLOSED,restarted.gate.status().state());
    }
  }
  @Test void multipleCliCallsRemainOneOperationAfterDrain() {
    gate.resume();
    try(var root = gate.enter()) {
      assertEquals(1,gate.status().activeOperations());
      gate.drain();
      for(int i=0;i<3;i++)try(var nested=gate.enter()) { assertEquals(1,gate.status().activeOperations()); }
      assertEquals(PublishingMaintenanceGate.State.DRAINING,gate.status().state());
    }
    assertTrue(gate.status().singleProcessDrained());
    assertFalse(gate.status().fleetSafeToReplace());
    assertThrows(ResponseStatusException.class,gate::enter);
  }
  @Test void exceptionAndRetainedAsyncCompletionCleanUp() throws Exception {
    gate.resume();AutoCloseable retained;
    try(var root=gate.enter()){retained=gate.retainCurrent();gate.drain();}
    assertEquals(1,gate.status().activeOperations());retained.close();retained.close();
    assertEquals(0,gate.status().activeOperations());
    gate.resume();assertThrows(IllegalStateException.class,()->{try(var root=gate.enter()){throw new IllegalStateException();}});
    assertEquals(0,gate.status().activeOperations());
  }
  @Test void admissionDrainRaceHasNoPostDrainEntry() throws Exception {
    gate.resume();ExecutorService pool=Executors.newFixedThreadPool(8);
    CountDownLatch go=new CountDownLatch(1);List<Future<?>> tasks=new ArrayList<>();
    for(int i=0;i<80;i++)tasks.add(pool.submit(()->{go.await();try(var scope=gate.enter()){}catch(ResponseStatusException expected){}return null;}));
    go.countDown();gate.drain();
    for(var task:tasks)task.get(3,TimeUnit.SECONDS);
    assertTrue(gate.status().singleProcessDrained());
    assertThrows(ResponseStatusException.class,gate::enter);pool.shutdownNow();
  }
  @Test void cancellationBeforeStartAndRejectedExecutorDoNotLeak() throws Exception {
    gate.resume();List<Runnable> queued=new ArrayList<>();
    try(var root=gate.enter()){
      var future=gate.submit(queued::add,()->"unused");future.cancel(false);
      assertThrows(RejectedExecutionException.class,()->gate.submit(r->{throw new RejectedExecutionException();},()->null));
      gate.drain();
    }
    queued.forEach(Runnable::run);assertTrue(gate.status().singleProcessDrained());
  }
  @Test void cancelledRunningTaskStaysActiveUntilActualExit() throws Exception {
    gate.resume();ExecutorService executor=Executors.newSingleThreadExecutor();
    CountDownLatch started=new CountDownLatch(1),finish=new CountDownLatch(1);Future<?> future;
    try(var root=gate.enter()) {
      future=gate.submit(executor,()->{started.countDown();finish.await();try(var child=gate.enter()){}return null;});
      assertTrue(started.await(2,TimeUnit.SECONDS));gate.drain();future.cancel(false);
    }
    assertEquals(1,gate.status().activeOperations());finish.countDown();executor.shutdown();assertTrue(executor.awaitTermination(3,TimeUnit.SECONDS));
    assertTrue(gate.status().singleProcessDrained());
  }
  @Test void timeoutSignalCannotReleaseStillRunningCli() throws Exception {
    gate.resume();Process process;
    try(var root=gate.enter()) {
      process=new ProcessBuilder("sh","-c","sleep 0.2").start();
      PublishingMaintenanceGate.trackCurrentProcess(process);gate.drain();
    }
    assertEquals(1,gate.status().activeOperations());
    process.waitFor();
    assertTimeoutPreemptively(Duration.ofSeconds(3),()->{while(!gate.status().singleProcessDrained())Thread.sleep(10);});
  }
  @Test void storageFailureAndSameDirectorySecondInstanceFailClosed() throws Exception {
    try(var other=new AutoGate(dir.resolve("state"))){
      assertEquals(PublishingMaintenanceGate.State.FAULT,other.gate.status().state());
      assertThrows(ResponseStatusException.class,other.gate::resume);
    }
    gate.resume();Files.createDirectory(dir.resolve("state/state.tmp"));
    assertEquals(PublishingMaintenanceGate.State.FAULT,gate.drain().state());
    assertThrows(ResponseStatusException.class,gate::enter);
    assertFalse(gate.status().singleProcessDrained());
  }
  static class AutoGate implements AutoCloseable {
    final PublishingMaintenanceGate gate;
    AutoGate(Path dir){gate=new PublishingMaintenanceGate(dir.toString());}
    public void close(){gate.close();}
  }
}
