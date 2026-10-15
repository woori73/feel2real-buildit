package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxArtifact;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.repository.HwpxArtifactRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.repository.HwpxJobRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * V1075(hwpx_jobs·hwpx_artifacts)와 JPA 매핑 일치 검증 (실제 MySQL + Flyway).
 *
 * <p>컨텍스트 기동 시 Hibernate schema validation(ddl-auto=validate)이 통과해야 하고,
 * 저장·조회·FK cascade가 동작해야 한다.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "hwpx.worker-enabled=false"
})
@DisplayName("HWPX 영속 계층 MySQL 통합 테스트 — V1075 스키마·JPA 일치")
class HwpxPersistenceMySqlTest {

  @Autowired private HwpxJobRepository jobRepository;
  @Autowired private HwpxArtifactRepository artifactRepository;
  @Autowired private JdbcTemplate jdbc;
  @PersistenceContext private EntityManager em;

  @Test
  @DisplayName("given 작업 저장 when 진행률·상태 갱신 then 컬럼에 그대로 저장·조회된다")
  void job_roundTrip_withProgressAndStatus() {
    // given
    HwpxJob job = jobRepository.save(new HwpxJob(7L, "사업계획서", "hwp", "{\"edits\":[]}"));
    job.markRunning("/tmp/w", "/tmp/w/input.hwp");
    job.updateProgress(90, "검증 중");
    em.flush();
    em.clear();
    // when
    HwpxJob loaded = jobRepository.findById(job.getId()).orElseThrow();
    // then
    assertThat(loaded.getProgress()).isEqualTo(90);
    assertThat(loaded.getStatus()).isEqualTo(HwpxJob.Status.RUNNING);
    assertThat(loaded.getEditsJson()).isEqualTo("{\"edits\":[]}");
    assertThat(jdbc.queryForObject(
        "select data_type from information_schema.columns "
            + "where table_schema = database() and table_name = 'hwpx_jobs' and column_name = 'progress'",
        String.class)).isEqualToIgnoringCase("int");
  }

  @Test
  @DisplayName("given 여러 작업 when QUEUED·소유자 조회 then 상태·tutorId로 걸러지고 오래된 순으로 정렬된다")
  void repositoryQueries_filterByStatusAndTutor() {
    // given
    HwpxJob a = jobRepository.save(new HwpxJob(7L, "a", "hwpx", "{}"));
    HwpxJob b = jobRepository.save(new HwpxJob(7L, "b", "hwpx", "{}"));
    jobRepository.save(new HwpxJob(8L, "other", "hwpx", "{}"));
    b.markCancelled();
    em.flush();
    em.clear();
    // when
    var queued = jobRepository.findQueued(HwpxJob.Status.QUEUED);
    var mine = jobRepository.findByTutorIdOrderByIdDesc(7L);
    // then
    assertThat(queued).extracting(HwpxJob::getTitle).containsSubsequence("a", "other").doesNotContain("b");
    assertThat(mine).extracting(HwpxJob::getId).containsExactly(b.getId(), a.getId());
  }

  @Test
  @DisplayName("given 산출물 when 작업 삭제 then FK cascade로 산출물도 함께 삭제된다")
  void artifact_isDeletedWithJob() {
    // given
    HwpxJob job = jobRepository.save(new HwpxJob(7L, "t", "hwpx", "{}"));
    artifactRepository.save(new HwpxArtifact(job.getId(), "/tmp/w/output.hwpx", 123L));
    em.flush();
    em.clear();
    assertThat(artifactRepository.findFirstByJobIdOrderByIdDesc(job.getId())).isPresent();
    // when
    jdbc.update("delete from hwpx_jobs where id = ?", job.getId());
    em.clear();
    // then
    assertThat(artifactRepository.findByJobId(job.getId())).isEmpty();
  }
}
