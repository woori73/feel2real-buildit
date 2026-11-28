package f2r.tutor_solution.private_tutor_solution_back.domain.parentnotice;

import static f2r.tutor_solution.private_tutor_solution_back.domain.parentnotice.ParentNoticeService.Source.DASHBOARD;
import static f2r.tutor_solution.private_tutor_solution_back.domain.parentnotice.ParentNoticeService.Source.MCP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant.AcademyAssistantAdapter;
import f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant.UnifiedAssistantService;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Run against an empty disposable MySQL 8 schema named codi_notice_test_*.
 * Provide CODI_NOTICE_TEST_JDBC_URL, CODI_NOTICE_TEST_USER and CODI_NOTICE_TEST_PASSWORD.
 * Only the external identity and academy lookup and the current time are controlled.
 */
@SpringJUnitConfig(ParentNoticeMeteringMySqlTest.Config.class)
@EnabledIfEnvironmentVariable(named = "CODI_NOTICE_TEST_JDBC_URL", matches = "jdbc:mysql:.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class ParentNoticeMeteringMySqlTest {
  static final AuthenticatedPrincipal OWNER = new AuthenticatedPrincipal(41L, "test-owner", "TUTOR");
  static final Instant NOVEMBER = Instant.parse("2026-11-27T00:00:00Z");

  @TestConfiguration(proxyBeanMethods = false)
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackageClasses = ParentNoticeDraftRepository.class)
  static class Config {
    @Bean DataSource dataSource() {
      return new DriverManagerDataSource(System.getenv("CODI_NOTICE_TEST_JDBC_URL"),
          System.getenv("CODI_NOTICE_TEST_USER"), System.getenv("CODI_NOTICE_TEST_PASSWORD"));
    }

    @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(dataSource);
      factory.setPackagesToScan(ParentNoticeDraft.class.getPackageName());
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(Map.of("hibernate.physical_naming_strategy",
          "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
      return factory;
    }

    @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
      return new JpaTransactionManager(factory);
    }

    @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
    @Bean AtomicReference<Instant> testTime() { return new AtomicReference<>(NOVEMBER); }
    @Bean Clock clock(AtomicReference<Instant> testTime) {
      InstantSource source = testTime::get;
      return source.withZone(ZoneOffset.UTC);
    }
    @Bean AcademyAssistantAdapter academy() { return mock(AcademyAssistantAdapter.class); }
    @Bean ParentNoticeService notices(ParentNoticeDraftRepository drafts, AcademyAssistantAdapter academy,
        JdbcTemplate jdbc, Clock clock) {
      return new ParentNoticeService(drafts, mock(UnifiedAssistantService.class), academy,
          new ParentNoticeStorage(jdbc), clock);
    }
  }

  @Autowired ParentNoticeService notices;
  @Autowired JdbcTemplate jdbc;
  @Autowired AtomicReference<Instant> testTime;
  @Autowired AcademyAssistantAdapter academy;
  @Autowired PlatformTransactionManager transactions;

  @BeforeAll void migrateEmptySchema(@Autowired DataSource dataSource) {
    assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).startsWith("codi_notice_test_");
    assertThat(number("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")).isZero();
    new ResourceDatabasePopulator(
        new ClassPathResource("db/migration/V1119__add_parent_notice_drafts.sql"),
        new ClassPathResource("db/migration/V1145__codi_parent_notice_metering.sql")).execute(dataSource);
  }

  @BeforeEach void setUp() {
    jdbc.update("DELETE FROM codi_usage_counter");
    jdbc.update("DELETE FROM codi_task_ledger");
    jdbc.update("DELETE FROM parent_notice_drafts");
    testTime.set(NOVEMBER);
    context("ACADEMY_OWNER", 7, 7);
  }

  private void context(String role, long defaultAcademy, long... ids) {
    var value = JsonNodeFactory.instance.objectNode().put("role", role).put("academyId", defaultAcademy);
    var options = value.putArray("academyOptions");
    for (long id : ids) options.addObject().put("id", id);
    when(academy.context(OWNER)).thenReturn(value);
  }

  private ParentNoticeService.Request notice() {
    return new ParentNoticeService.Request(UUID.randomUUID().toString(), ParentNoticeService.Kind.NOTICE,
        "학사 일정", "수업 일정을 확인해 주세요.");
  }

  private long number(String sql) { return jdbc.queryForObject(sql, Long.class); }

  private void assertStored(long drafts, long ledger, long confirmed) {
    assertThat(number("SELECT COUNT(*) FROM parent_notice_drafts")).isEqualTo(drafts);
    assertThat(number("SELECT COUNT(*) FROM codi_task_ledger")).isEqualTo(ledger);
    assertThat(number("SELECT COALESCE(SUM(confirmed_qty),0) FROM codi_usage_counter")).isEqualTo(confirmed);
    assertThat(number("SELECT COALESCE(SUM(reserved_qty),0) FROM codi_usage_counter")).isZero();
  }

  @Test void repeatedMcpSaveAndListingCountExactlyOneResult() {
    // given
    var request = notice();
    // when
    var saved = notices.save(OWNER, request, MCP);
    var retried = notices.save(OWNER, request, MCP);
    assertThat(notices.list(OWNER)).containsExactly(saved);
    // then
    assertThat(retried).isEqualTo(saved);
    assertThat(notices.monthlyConfirmed(OWNER)).isOne();
    assertStored(1, 1, 1);
    var row = jdbc.queryForMap("SELECT * FROM codi_task_ledger");
    assertThat(row).containsEntry("request_key", saved.id()).containsEntry("result_id", saved.id())
        .containsEntry("result_kind", "PARENT_NOTICE_DRAFT").containsEntry("task_type", "PARENT_NOTICE")
        .containsEntry("qty", 1).containsEntry("status", "CONFIRMED");
    assertThat(UUID.fromString(row.get("task_id").toString())).isNotNull();
  }

  private String existingDraftId(ParentNoticeService.Request request) {
    String legacyKey = OWNER.userId() + ":" + OWNER.role() + ":" + request.requestId();
    return UUID.nameUUIDFromBytes(legacyKey.getBytes(StandardCharsets.UTF_8)).toString();
  }

  @Test void replayOfPreMigrationDraftDoesNotBackfillUsage() {
    // given: an existing V1119 draft from before metering began.
    var request = notice();
    var id = existingDraftId(request);
    jdbc.update("""
        INSERT INTO parent_notice_drafts (id,owner_id,owner_role,kind,title,body,source,created_at)
        VALUES (?,?,?,'NOTICE',?,?,'MCP','2026-10-01 00:00:00')""",
        id, OWNER.userId(), OWNER.role(), request.title(), request.body());
    var beforeReplay = notices.list(OWNER).getFirst();
    // when
    var replay = notices.save(OWNER, request, MCP);
    // then
    assertThat(replay).isEqualTo(beforeReplay);
    assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s') FROM parent_notice_drafts",
        String.class)).isEqualTo("2026-10-01 00:00:00");
    assertThat(notices.monthlyConfirmed(OWNER)).isZero();
    assertStored(1, 0, 0);
  }

  @Test void duplicateLedgerKeyLeavesTheExistingTaskAndCounterUnchanged() {
    // given: the metering record exists when the corresponding draft needs restoring.
    var request = notice();
    var id = existingDraftId(request);
    var task = UUID.randomUUID().toString();
    jdbc.update("""
        INSERT INTO codi_task_ledger
          (task_id,academy_id,task_type,channel,request_key,result_kind,result_id,qty,status,created_at)
        VALUES (?,7,'PARENT_NOTICE','MCP',?,'PARENT_NOTICE_DRAFT',?,1,'CONFIRMED','2026-11-26 00:00:00')""",
        task, id, id);
    jdbc.update("""
        INSERT INTO codi_usage_counter (academy_id,period_start,period_end,task_type,confirmed_qty)
        VALUES (7,'2026-11-01','2026-11-30','PARENT_NOTICE',1)""");
    // when
    var restored = notices.save(OWNER, request, MCP);
    // then
    assertThat(restored.id()).isEqualTo(id);
    assertThat(jdbc.queryForObject("SELECT task_id FROM codi_task_ledger", String.class)).isEqualTo(task);
    assertThat(notices.monthlyConfirmed(OWNER)).isOne();
    assertStored(1, 1, 1);
  }

  @Test void changedContentReturnsConflictAndLeavesOriginalCounts() {
    // given
    var request = notice();
    var original = notices.save(OWNER, request, MCP);
    var changed = new ParentNoticeService.Request(request.requestId(), request.kind(), request.title(), "바뀐 문구");
    // when / then
    assertThatThrownBy(() -> notices.save(OWNER, changed, MCP))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    assertThat(notices.list(OWNER)).containsExactly(original);
    assertStored(1, 1, 1);
  }

  @Test void sourceIsPreservedForDashboardAndMcpOnTheSameCounter() {
    // given / when
    var dashboard = notices.save(OWNER, notice(), DASHBOARD);
    var mcp = notices.save(OWNER, notice(), MCP);
    // then
    assertThat(jdbc.queryForObject("SELECT channel FROM codi_task_ledger WHERE result_id=?",
        String.class, dashboard.id())).isEqualTo("DASHBOARD");
    assertThat(jdbc.queryForObject("SELECT channel FROM codi_task_ledger WHERE result_id=?",
        String.class, mcp.id())).isEqualTo("MCP");
    assertThat(number("SELECT COUNT(*) FROM codi_usage_counter")).isOne();
    assertStored(2, 2, 2);
  }

  @Test void validationAndDeniedPermissionDoNotWrite() {
    // given
    var invalid = new ParentNoticeService.Request(UUID.randomUUID().toString(), ParentNoticeService.Kind.NOTICE, "", "본문");
    // when / then
    assertThatThrownBy(() -> notices.save(OWNER, invalid, MCP))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
    assertStored(0, 0, 0);
    context("TEACHER", 7, 7);
    assertThatThrownBy(() -> notices.save(OWNER, notice(), DASHBOARD))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
    assertStored(0, 0, 0);
  }

  @Test void failedCounterWriteRollsBackBothDraftAndLedgerAndAllowsRetry() {
    // given: fail the last statement in a real InnoDB transaction.
    var request = notice();
    jdbc.execute("""
        ALTER TABLE codi_usage_counter
        ADD CONSTRAINT reject_notice_counter CHECK (confirmed_qty = 0)""");
    try {
      // when / then
      assertThatThrownBy(() -> notices.save(OWNER, request, MCP)).isInstanceOf(DataAccessException.class)
          .hasStackTraceContaining("reject_notice_counter");
      assertStored(0, 0, 0);
    } finally {
      jdbc.execute("ALTER TABLE codi_usage_counter DROP CHECK reject_notice_counter");
    }
    notices.save(OWNER, request, MCP);
    assertStored(1, 1, 1);
  }

  @Test void multiAcademyUsesTheReturnedDefaultAndLogsAmbiguity(CapturedOutput output) {
    // given: neither the smallest ID nor the first option is the default.
    context("ACADEMY_OWNER", 21, 4, 21);
    // when
    notices.save(OWNER, notice(), MCP);
    // then
    assertThat(output.getAll()).contains("ambiguous=true");
    assertThat(number("SELECT academy_id FROM codi_task_ledger")).isEqualTo(21);
    assertThat(number("SELECT academy_id FROM codi_usage_counter")).isEqualTo(21);
    assertThat(notices.monthlyConfirmed(OWNER)).isOne();
    context("ACADEMY_OWNER", 4, 4);
    assertThat(notices.monthlyConfirmed(OWNER)).isZero();
    assertStored(1, 1, 1);
  }

  @Test void simultaneousTransactionsWaitForTheSameRequestAndCountOnce() throws Exception {
    // given: hold the winning transaction open while an independent transaction attempts the same save.
    var request = notice();
    var written = new CountDownLatch(1);
    var commit = new CountDownLatch(1);
    var retryEntered = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
        var saved = notices.save(OWNER, request, MCP);
        written.countDown();
        await(commit);
        return saved;
      }));
      try {
        await(written);
        var retry = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
          // Open the repeatable-read snapshot before the winning INSERT commits.
          assertThat(number("SELECT COUNT(*) FROM parent_notice_drafts")).isZero();
          retryEntered.countDown();
          return notices.save(OWNER, request, MCP);
        }));
        await(retryEntered);
        assertThatThrownBy(() -> retry.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        commit.countDown();
        // then
        assertThat(retry.get(10, TimeUnit.SECONDS)).isEqualTo(first.get(10, TimeUnit.SECONDS));
      } finally {
        commit.countDown();
      }
    }
    assertStored(1, 1, 1);
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  @Test void kstMidnightClosesNovemberAndRetriesDoNotMoveUsageToDecember() {
    // given: two consecutive microseconds in UTC straddle midnight in Korea.
    testTime.set(Instant.parse("2026-11-30T14:59:59.999999Z"));
    var novemberRequest = notice();
    notices.save(OWNER, novemberRequest, MCP);
    // when
    testTime.set(Instant.parse("2026-11-30T15:00:00Z"));
    assertThat(notices.monthlyConfirmed(OWNER)).isZero();
    notices.save(OWNER, novemberRequest, MCP);
    assertThat(notices.monthlyConfirmed(OWNER)).isZero();
    notices.save(OWNER, notice(), MCP);
    // then
    assertThat(jdbc.queryForList("""
        SELECT CONCAT(period_start,'/',period_end,'/',confirmed_qty) FROM codi_usage_counter
        ORDER BY period_start""", String.class)).containsExactly("2026-11-01/2026-11-30/1", "2026-12-01/2026-12-31/1");
    assertThat(jdbc.queryForList("""
        SELECT DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s') FROM codi_task_ledger ORDER BY created_at""",
        String.class)).containsExactly("2026-11-30 14:59:59", "2026-11-30 15:00:00");
    assertThat(notices.monthlyConfirmed(OWNER)).isOne();
    assertStored(2, 2, 2);
  }
}
