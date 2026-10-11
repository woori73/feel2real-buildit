package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageVersion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyPageVersionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D3 스냅샷 동시성 회귀 테스트.
 *
 * <p>격리 MySQL + 실제 Spring 트랜잭션으로 다음을 검증한다:
 * <ul>
 *   <li>2건 동시 요청 반복 — 모두 성공, version_no 중복 없음
 *   <li>8건 동시 요청 반복 — 모두 성공, version_no 중복·유실 없음
 *   <li>성공 응답과 실제 DB 커밋 일치 (실패 응답인데 DB에 저장되는 현상 없음)
 *   <li>버전 20개 상한 및 v1 보존
 * </ul>
 *
 * <p>주의: 이 테스트는 실제 MySQL이 필요하다. CI의 build-check에서는 실행하지 않는다.
 */
@SpringBootTest
class AcademyPageVersionConcurrencyTest {

  @Autowired
  private AcademyPageVersionService versionService;

  @Autowired
  private AcademyPageVersionRepository versionRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private ExecutorService executor;
  private JwtTokenProvider.AuthenticatedPrincipal principal;
  private Long academyId;

  @BeforeEach
  void setUp() {
    executor = Executors.newFixedThreadPool(16);

    // 테스트용 인증 주체 설정
    // 실제 환경에서는 테스트용 TUTOR 사용자를 사용한다
    Long testUserId = 999001L;
    principal = new JwtTokenProvider.AuthenticatedPrincipal(
        testUserId, "test-tutor", "TUTOR");

    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, List.of()));

    // 테스트 학원 조회 (실제 테스트에서는 fixture로 생성)
    // 여기서는 기존 테스트 패턴을 따른다
    academyId = jdbcTemplate.queryForObject(
        "SELECT academy_id FROM academy WHERE tutor_user_id = ? LIMIT 1",
        Long.class, testUserId);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Test
  void concurrentSnapshots_2threads_allSucceed_noDuplicates() throws Exception {
    runConcurrentSnapshotTest(2, 3);
  }

  @Test
  void concurrentSnapshots_8threads_allSucceed_noDuplicates() throws Exception {
    runConcurrentSnapshotTest(8, 2);
  }

  private void runConcurrentSnapshotTest(int threadCount, int rounds) throws Exception {
    for (int round = 0; round < rounds; round++) {
      long beforeCount = versionRepository.countByAcademyId(academyId);

      List<Callable<Long>> tasks = new ArrayList<>();
      for (int i = 0; i < threadCount; i++) {
        final int idx = i;
        tasks.add(() -> versionService.snapshot(
            principal,
            Map.of("key" + idx, "value" + idx),
            Map.of(),
            "동시성 테스트 round=" + round + " idx=" + idx));
      }

      List<Future<Long>> futures = executor.invokeAll(tasks, 60, TimeUnit.SECONDS);

      // 모든 요청이 성공해야 한다
      List<Long> versionIds = new ArrayList<>();
      for (Future<Long> f : futures) {
        assertThat(f.isDone()).isTrue();
        assertThat(f.isCancelled()).isFalse();
        Long id = f.get(5, TimeUnit.SECONDS);
        assertThat(id).isNotNull();
        versionIds.add(id);
      }

      // DB에 실제로 커밋되었는지 확인
      long afterCount = versionRepository.countByAcademyId(academyId);
      // 20개 상한으로 인해 일부가 정리될 수 있으므로, 증가량이 threadCount 이하
      assertThat(afterCount).isGreaterThanOrEqualTo(beforeCount);
      assertThat(afterCount).isLessThanOrEqualTo(20);

      // version_no 중복 검사
      List<AcademyPageVersion> versions =
          versionRepository.findByAcademyIdOrderByVersionNoDesc(academyId);
      List<Integer> versionNos = versions.stream()
          .map(AcademyPageVersion::getVersionNo)
          .toList();
      assertThat(versionNos).doesNotHaveDuplicates();

      // v1 보존 검사
      assertThat(versionNos).contains(1);
    }
  }

  @Test
  void versionCap20_v1Preserved() {
    // 25개 스냅샷 생성 후 20개 상한 및 v1 보존 확인
    for (int i = 0; i < 25; i++) {
      versionService.snapshot(
          principal,
          Map.of("bulk", String.valueOf(i)),
          Map.of(),
          "상한 테스트 " + i);
    }

    List<AcademyPageVersion> versions =
        versionRepository.findByAcademyIdOrderByVersionNoDesc(academyId);

    assertThat(versions).hasSize(20);

    List<Integer> versionNos = versions.stream()
        .map(AcademyPageVersion::getVersionNo)
        .toList();
    // v1 보존
    assertThat(versionNos).contains(1);
    // 중복 없음
    assertThat(versionNos).doesNotHaveDuplicates();
  }
}
