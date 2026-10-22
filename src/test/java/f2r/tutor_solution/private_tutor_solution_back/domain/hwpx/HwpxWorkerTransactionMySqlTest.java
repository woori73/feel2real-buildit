package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.config.HwpxProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.repository.HwpxJobRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.service.HwpxCliExecutor;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.service.HwpxCliExecutor.CliResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.service.HwpxWorker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * HwpxWorker 상태 전이 MySQL 통합 테스트.
 *
 * <p>일부러 {@code @Transactional}을 붙이지 않고, 운영과 같은 진입점인 {@code poll()}을 호출한다.
 * {@code process()}를 프록시 빈에서 직접 호출하면 {@code @Transactional}이 적용돼 버그(poll → 같은 객체의
 * {@code process()} self-invocation으로 트랜잭션 무시 → 작업이 QUEUED에 머무름)가 가려진다.
 * 상태는 별도 커넥션({@link JdbcTemplate})으로 읽어 커밋 여부를 확인한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "hwpx.worker-enabled=false"
})
@DisplayName("HwpxWorker 트랜잭션·상태 전이 MySQL 통합 테스트")
class HwpxWorkerTransactionMySqlTest {

  @Autowired private HwpxWorker worker;
  @Autowired private HwpxJobRepository jobRepository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private HwpxProperties props;
  @MockitoBean private HwpxCliExecutor cliExecutor;

  private final List<Long> createdJobIds = new ArrayList<>();

  @BeforeEach
  void enableWorker() {
    props.setWorkerEnabled(true); // poll()이 실제로 작업을 집도록 켠다(스케줄러는 클래스 설정으로 꺼져 있음)
  }

  @AfterEach
  void cleanup() {
    props.setWorkerEnabled(false);
    for (Long id : createdJobIds) {
      jdbc.update("delete from hwpx_artifacts where job_id = ?", id);
      jdbc.update("delete from hwpx_jobs where id = ?", id);
    }
  }

  private Long newJob(boolean withInputFile) throws Exception {
    HwpxJob job = jobRepository.saveAndFlush(
        new HwpxJob(990001L, "상태 전이 테스트", "hwpx", "{\"edits\":[]}"));
    createdJobIds.add(job.getId());
    if (withInputFile) {
      Path dir = Files.createTempDirectory("hwpx-worker-it-");
      Path input = Files.writeString(dir.resolve("input.hwpx"), "dummy");
      jdbc.update("update hwpx_jobs set input_file = ? where id = ?", input.toString(), job.getId());
    }
    return job.getId();
  }

  private String statusOf(Long id) {
    return jdbc.queryForObject("select status from hwpx_jobs where id = ?", String.class, id);
  }

  private int artifactCount(Long id) {
    return jdbc.queryForObject("select count(*) from hwpx_artifacts where job_id = ?", Integer.class, id);
  }

  @Test
  void twoWorkersRaceAndOnlyWinnerExecutesCli() throws Exception {
    Long id = newJob(true);
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    when(cliExecutor.edit(any(), anyString(), any(), anyString())).thenAnswer(inv -> {
      entered.countDown();
      if (!release.await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
      Files.writeString(((Path) inv.getArgument(0)).resolve((String) inv.getArgument(3)), "isolated result");
      return new CliResult(true, Map.of("ok", true), "");
    });
    when(cliExecutor.validate(any())).thenReturn(new CliResult(true, Map.of("ok", true), ""));
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> worker.process(id));
      try {
        assertThat(entered.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        pool.submit(() -> worker.process(id)).get(10, java.util.concurrent.TimeUnit.SECONDS);
      } finally { release.countDown(); }
      first.get(15, java.util.concurrent.TimeUnit.SECONDS);
    }
    org.mockito.Mockito.verify(cliExecutor, org.mockito.Mockito.times(1)).edit(any(), anyString(), any(), anyString());
    assertThat(statusOf(id)).isEqualTo("DONE");
    assertThat(artifactCount(id)).isEqualTo(1);
  }

  @Test
  @DisplayName("given QUEUED 작업 when 처리하면 then RUNNING을 거쳐 DONE으로 커밋되고 결과가 1건만 저장된다")
  void queued_to_running_to_done_is_committed() throws Exception {
    // given
    Long id = newJob(true);
    List<String> statusDuringCli = new ArrayList<>();
    when(cliExecutor.edit(any(), anyString(), any(), anyString())).thenAnswer(inv -> {
      statusDuringCli.add(statusOf(id)); // CLI 실행 중 다른 커넥션이 보는 상태
      Path out = ((Path) inv.getArgument(0)).resolve((String) inv.getArgument(3));
      Files.writeString(out, "hwpx-bytes");
      return new CliResult(true, Map.of("ok", true), "");
    });
    when(cliExecutor.validate(any())).thenReturn(new CliResult(true, Map.of("ok", true), ""));

    // when
    worker.poll();

    // then
    assertThat(statusDuringCli).containsExactly("RUNNING");
    assertThat(statusOf(id)).isEqualTo("DONE");
    assertThat(jdbc.queryForObject("select progress from hwpx_jobs where id = ?", Integer.class, id)).isEqualTo(100);
    assertThat(jdbc.queryForObject("select output_file from hwpx_jobs where id = ?", String.class, id)).endsWith("output.hwpx");
    assertThat(artifactCount(id)).isEqualTo(1);
  }

  @Test
  @DisplayName("given 이미 DONE인 작업 when 다시 처리하면 then 결과를 중복 저장하지 않는다")
  void done_job_is_not_reprocessed() throws Exception {
    // given
    Long id = newJob(true);
    when(cliExecutor.edit(any(), anyString(), any(), anyString())).thenAnswer(inv -> {
      Files.writeString(((Path) inv.getArgument(0)).resolve((String) inv.getArgument(3)), "x");
      return new CliResult(true, Map.of("ok", true), "");
    });
    when(cliExecutor.validate(any())).thenReturn(new CliResult(true, Map.of("ok", true), ""));
    worker.poll();

    // when
    worker.poll();

    // then
    assertThat(statusOf(id)).isEqualTo("DONE");
    assertThat(artifactCount(id)).isEqualTo(1);
  }

  @Test
  @DisplayName("given CLI 편집 실패 when 처리하면 then FAILED와 오류 메시지가 커밋된다")
  void cli_failure_is_committed_as_failed() throws Exception {
    // given
    Long id = newJob(true);
    when(cliExecutor.edit(any(), anyString(), any(), anyString()))
        .thenReturn(new CliResult(false, Map.of(), "CLI exit=2"));

    // when
    worker.poll();

    // then
    assertThat(statusOf(id)).isEqualTo("FAILED");
    assertThat(jdbc.queryForObject("select error from hwpx_jobs where id = ?", String.class, id)).contains("CLI exit=2");
    assertThat(artifactCount(id)).isZero();
  }

  @Test
  @DisplayName("given 입력 파일 경로가 없는 작업 when 처리하면 then FAILED로 커밋된다")
  void missing_input_file_is_committed_as_failed() throws Exception {
    // given
    Long id = newJob(false);

    // when
    worker.poll();

    // then
    assertThat(statusOf(id)).isEqualTo("FAILED");
    assertThat(jdbc.queryForObject("select error from hwpx_jobs where id = ?", String.class, id)).contains("입력 파일");
  }

  @Test
  @DisplayName("given 실행 중 사용자가 취소 when 처리가 끝나면 then CANCELLED를 DONE으로 덮어쓰지 않는다")
  void cancel_during_run_is_not_overwritten() throws Exception {
    // given
    Long id = newJob(true);
    when(cliExecutor.edit(any(), anyString(), any(), anyString())).thenAnswer(inv -> {
      jdbc.update("update hwpx_jobs set status = 'CANCELLED' where id = ?", id);
      Files.writeString(((Path) inv.getArgument(0)).resolve((String) inv.getArgument(3)), "x");
      return new CliResult(true, Map.of("ok", true), "");
    });
    when(cliExecutor.validate(any())).thenReturn(new CliResult(true, Map.of("ok", true), ""));

    // when
    worker.poll();

    // then
    assertThat(statusOf(id)).isEqualTo("CANCELLED");
    assertThat(artifactCount(id)).isZero();
  }
}
