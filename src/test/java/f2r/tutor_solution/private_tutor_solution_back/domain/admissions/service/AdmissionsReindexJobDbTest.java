package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.config.AdmissionsEmbeddingProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeEmbeddingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 JPA 저장소 + 실제 백그라운드 스레드로 재색인 잡의 커밋·상태·중복 방지를 검증한다. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({JpaConfig.class, AdmissionsEmbeddingService.class, AdmissionsVectorIndex.class,
        AdmissionsReindexJob.class, AdmissionsReindexJobDbTest.Cfg.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:adm_reindex_job;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DisplayName("재색인 잡 — DB 연동(항목별 커밋·진행률·중복 방지)")
class AdmissionsReindexJobDbTest {

    static final AtomicInteger calls = new AtomicInteger();
    static volatile CountDownLatch gate = null;
    static volatile Long failId = null;

    @TestConfiguration
    static class Cfg {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }

        @Bean AdmissionsEmbeddingProperties admissionsEmbeddingProperties() {
            var p = new AdmissionsEmbeddingProperties();
            p.setEnabled(true);
            p.setIndexPauseMs(0);
            p.setIndexRetryBackoffMs(0);
            return p;
        }

        @Bean @Primary
        AdmissionsEmbeddingClient admissionsEmbeddingClient(AdmissionsEmbeddingProperties props, ObjectMapper om) {
            return new AdmissionsEmbeddingClient(props, om) {
                @Override
                public DocResult embedDocument(String text) {
                    calls.incrementAndGet();
                    CountDownLatch g = gate;
                    if (g != null) {
                        try { g.await(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                    }
                    if (failId != null && text.contains("제목" + failId)) {
                        return DocResult.fail("타임아웃 60000ms", false);
                    }
                    float[] v = new float[768];
                    v[0] = 1f;
                    return DocResult.ok(v);
                }
            };
        }
    }

    @Autowired AdmissionsReindexJob job;
    @Autowired AdmissionsKnowledgeItemRepository items;
    @Autowired AdmissionsKnowledgeEmbeddingRepository embeddings;
    @Autowired AdmissionsEmbeddingProperties props;

    @BeforeEach
    void setUp() {
        embeddings.deleteAll();
        items.deleteAll();
        calls.set(0);
        gate = null;
        failId = null;
        for (int i = 1; i <= 5; i++) {
            items.save(AdmissionsKnowledgeItem.builder()
                    .academicYear(2027).yearConfirmed(true).title("제목" + i).content("본문" + i)
                    .category("전형").tags("전형")
                    .sourceClass(AdmissionsKnowledgeItem.SourceClass.OFFICIAL)
                    .status(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE)
                    .role(AdmissionsKnowledgeItem.Role.FACT)
                    .dedupKey("type:t:2027:" + i).createdBy("test").build());
        }
    }

    @AfterEach
    void tearDown() {
        if (gate != null) gate.countDown();
        awaitIdle();
    }

    private void awaitIdle() {
        long end = System.currentTimeMillis() + 15_000;
        while (job.isRunning() && System.currentTimeMillis() < end) {
            try { Thread.sleep(20); } catch (InterruptedException ignored) { }
        }
        assertThat(job.isRunning()).isFalse();
    }

    @Test
    @DisplayName("given ACTIVE 5건 when 백그라운드로 재색인하면 then 5건이 개별 커밋되어 저장되고 진행률이 5/5가 된다")
    void runsInBackgroundAndCommitsEachItem() {
        // when
        var outcome = job.start(AdmissionsReindexJob.Mode.MISSING);
        awaitIdle();

        // then
        assertThat(outcome).isEqualTo(AdmissionsReindexJob.StartOutcome.STARTED);
        assertThat(embeddings.count()).isEqualTo(5);
        Map<String, Object> st = job.status();
        assertThat(st).containsEntry("total", 5).containsEntry("done", 5).containsEntry("missing", 0)
                .containsEntry("indexSize", 5).containsEntry("indexReady", true);
    }

    @Test
    @DisplayName("given 실행 중 when 다시 시작하면 then ALREADY_RUNNING이고 임베딩 호출이 중복되지 않는다")
    void duplicateStartWhileRunning_doesNotDuplicateWork() throws Exception {
        // given: 첫 항목에서 멈춰 있는 잡
        gate = new CountDownLatch(1);
        job.start(AdmissionsReindexJob.Mode.MISSING);

        // when
        var again = job.start(AdmissionsReindexJob.Mode.MISSING);

        // then
        assertThat(again).isEqualTo(AdmissionsReindexJob.StartOutcome.ALREADY_RUNNING);
        assertThat(job.status()).containsEntry("running", true);

        // when: 재개
        gate.countDown();
        awaitIdle();

        // then: 항목당 정확히 1회
        assertThat(calls.get()).isEqualTo(5);
        assertThat(embeddings.count()).isEqualTo(5);
    }

    @Test
    @DisplayName("given 한 항목이 실패 when 실행하면 then 나머지는 저장되고 실패 사유가 남으며, 재시도는 실패분만 호출한다")
    void failureIsIsolatedAndRetryOnlyTouchesMissing() {
        // given: 3번 항목 실패
        Long third = items.findAllIds().get(2);
        failId = third;
        // 제목은 "제목1..5" 순서와 id 순서가 같다는 가정 대신 실제 제목으로 매핑
        String title = items.findById(third).orElseThrow().getTitle();
        failId = Long.valueOf(title.replace("제목", ""));

        // when
        job.start(AdmissionsReindexJob.Mode.MISSING);
        awaitIdle();

        // then
        assertThat(embeddings.count()).isEqualTo(4);
        assertThat(job.status()).containsEntry("done", 4).containsEntry("missing", 1);
        @SuppressWarnings("unchecked")
        var last = (Map<String, Object>) job.status().get("lastRun");
        assertThat(last).containsEntry("failed", 1).containsEntry("embedded", 4);
        @SuppressWarnings("unchecked")
        var failures = (Map<Long, String>) last.get("failures");
        assertThat(failures).containsEntry(third, "타임아웃 60000ms");

        // when: 복구 후 재시도
        failId = null;
        calls.set(0);
        job.start(AdmissionsReindexJob.Mode.MISSING);
        awaitIdle();

        // then: 실패분 1건만 호출, 총 행 5개
        assertThat(calls.get()).isEqualTo(1);
        assertThat(embeddings.count()).isEqualTo(5);
        assertThat(job.status()).containsEntry("missing", 0);
    }
}
