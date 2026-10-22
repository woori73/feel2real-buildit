package f2r.tutor_solution.private_tutor_solution_back.infrastructure.cluster;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/** Two separate JVMs and physical MySQL sessions. Only disposable loopback databases accepted. */
@EnabledIfEnvironmentVariable(named = "F2R_CLUSTER_MYSQL_TEST", matches = "true")
public class ClusterConcurrencyMySqlTest {
  @TempDir Path dir;
  static String url() { return System.getenv("SPRING_DATASOURCE_URL"); }
  static Connection connect() throws Exception {
    return DriverManager.getConnection(url(), System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
  }
  static MysqlNamedLocks locks() {
    return new MysqlNamedLocks(() -> {
      try { return connect(); } catch (Exception e) { throw new java.sql.SQLException("test connection unavailable"); }
    });
  }

  @BeforeAll static void migrateIsolatedOnly() {
    assertTrue(url().matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/f2r_(?:test|cluster_test)(?:\\?.*)?"));
    Flyway.configure().dataSource(url(), System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD")).locations("classpath:db/migration", "classpath:db/dev-seed").load().migrate();
  }

  Process fork(String... args) throws Exception {
    var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", System.getProperty("f2r.test.runtime-classpath"), Contender.class.getName()));
    command.addAll(List.of(args));
    return new ProcessBuilder(command).redirectError(dir.resolve("child.err").toFile()).start();
  }
  String result(Process process) throws Exception {
    assertTrue(process.waitFor(30, TimeUnit.SECONDS));
    assertEquals(0, process.exitValue());
    return new String(process.getInputStream().readAllBytes()).trim();
  }

  @Test void twoJvmsCannotOwnSameLockAndGracefulReleaseTransfersIt() throws Exception {
    try (var leader = locks().tryAcquire("test-leader")) {
      assertNotNull(leader);
      assertTrue(leader.valid());
      assertEquals("FOLLOWER", result(fork("lock", "test-leader")));
      assertEquals("FOLLOWER", result(fork("lock", "test-leader")));
    }
    assertEquals("LEADER", result(fork("lock", "test-leader")));
  }

  @Test void crashedJvmReleasesPhysicalSessionAndNewJvmCanAcquire() throws Exception {
    Path ready = dir.resolve("ready");
    Process process = fork("hold", "test-crash", ready.toString());
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(20), () -> { while (!Files.exists(ready)) Thread.sleep(25); });
      assertEquals("FOLLOWER", result(fork("lock", "test-crash")));
      process.destroyForcibly();
      assertTrue(process.waitFor(10, TimeUnit.SECONDS));
      assertEquals("LEADER", result(fork("lock", "test-crash")));
    } finally { process.destroyForcibly(); }
  }

  @Test void explicitReleaseOnSessionMakesFormerLeaderInvalidAndReconnectionWins() throws Exception {
    var lock = locks().tryAcquire("test-loss");
    assertNotNull(lock);
    // RELEASE_ALL_LOCKS on another connection cannot release the owner's lock.
    try (var other = connect(); var sql = other.createStatement()) { sql.execute("SELECT RELEASE_ALL_LOCKS()"); }
    assertTrue(lock.valid());
    lock.close();
    assertFalse(lock.valid());
    assertEquals("LEADER", result(fork("lock", "test-loss")));
  }

  @Test void databaseDisconnectInvalidatesOwnerAndPhysicalReconnectCanWin() throws Exception {
    var lock = locks().tryAcquire("test-disconnect");
    assertNotNull(lock);
    Connection owned = (Connection) org.springframework.test.util.ReflectionTestUtils.getField(lock, "connection");
    long connectionId;
    try (var sql = owned.createStatement(); var result = sql.executeQuery("SELECT CONNECTION_ID()")) {
      assertTrue(result.next()); connectionId = result.getLong(1);
    }
    // MySQL allows killing another connection owned by the same disposable test user.
    try (var other = connect(); var sql = other.createStatement()) { sql.execute("KILL CONNECTION " + connectionId); }
    assertFalse(lock.valid());
    lock.close(); // RELEASE fails on the dead socket; physical close must still be safe.
    assertEquals("LEADER", result(fork("lock", "test-disconnect")));
  }

  @Test void twoJvmsCompeteForPublishingAndHwpxAndOnlyOneClaimsEach() throws Exception {
    for (String table : List.of("publishing_jobs", "hwpx_jobs")) {
      long id;
      try (Connection connection = connect(); var sql = connection.createStatement()) {
        String insert = table.equals("publishing_jobs")
            ? "INSERT INTO publishing_jobs(user_id,title,template,status,progress,attempt_count,created_at,updated_at) VALUES(990001,'isolated race','ko-textbook-a4','QUEUED',0,0,NOW(),NOW())"
            : "INSERT INTO hwpx_jobs(tutor_id,title,source_format,status,progress,edits_json,created_at,updated_at) VALUES(990001,'isolated race','hwpx','QUEUED',0,'{}',NOW(),NOW())";
        sql.executeUpdate(insert, java.sql.Statement.RETURN_GENERATED_KEYS);
        try (var keys = sql.getGeneratedKeys()) { assertTrue(keys.next()); id = keys.getLong(1); }
      }
      try {
        Path gate = dir.resolve(table);
        Process a = fork("claim", table, String.valueOf(id), gate.toString());
        Process b = fork("claim", table, String.valueOf(id), gate.toString());
        Files.writeString(gate, "start");
        assertEquals(1, Integer.parseInt(result(a)) + Integer.parseInt(result(b)));
        // A rollback must release the row claim so a later worker can recover; history row remains.
        try (Connection connection = connect(); var sql = connection.createStatement()) {
          sql.executeUpdate("UPDATE " + table + " SET status='QUEUED' WHERE id=" + id);
          connection.setAutoCommit(false);
          assertEquals(1, sql.executeUpdate("UPDATE " + table + " SET status='RUNNING' WHERE status='QUEUED' AND id=" + id));
          connection.rollback();
        }
        assertEquals("1", result(fork("claim", table, String.valueOf(id), gate.toString())));
      } finally {
        try (Connection connection = connect(); var sql = connection.createStatement()) { sql.executeUpdate("DELETE FROM " + table + " WHERE id=" + id); }
      }
    }
  }

  public static class Contender {
    public static void main(String[] args) throws Exception {
      if (args[0].equals("claim")) {
        if (!List.of("hwpx_jobs", "publishing_jobs").contains(args[1])) throw new IllegalArgumentException();
        while (!Files.exists(Path.of(args[3]))) Thread.sleep(10);
        try (Connection connection = connect(); var sql = connection.prepareStatement("UPDATE " + args[1] + " SET status='RUNNING' WHERE id=? AND status='QUEUED'")) {
          sql.setLong(1, Long.parseLong(args[2]));
          System.out.println(sql.executeUpdate());
        }
      } else {
        try (var session = locks().tryAcquire(args[1])) {
          if (args[0].equals("hold") && session != null) {
            Files.writeString(Path.of(args[2]), "ready");
            Thread.sleep(60000);
          }
          System.out.println(session == null ? "FOLLOWER" : "LEADER");
        }
      }
    }
  }
}
