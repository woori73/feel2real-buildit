package f2r.tutor_solution.private_tutor_solution_back.domain.landing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class HomeAgentTrailerServiceTest {
  EmbeddedDatabase database;
  JdbcTemplate db;
  FileStorage storage = mock(FileStorage.class);
  HomeAgentTrailerService service;
  static final String ID = HomeAgentTrailerService.FILE_ID;

  @BeforeEach void setup() {
    database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
    db = new JdbcTemplate(database);
    db.execute("CREATE TABLE studio_files(id VARCHAR(36),project_id VARCHAR(64),storage_key VARCHAR(100),size_bytes BIGINT,sha256 VARCHAR(64),extension VARCHAR(8))");
    db.execute("CREATE TABLE studio_revisions(id VARCHAR(36),project_id VARCHAR(64),item_key VARCHAR(100),file_id VARCHAR(36),kind VARCHAR(16))");
    db.execute("CREATE TABLE studio_items(project_id VARCHAR(64),item_key VARCHAR(100),approved_id VARCHAR(36),head_id VARCHAR(36))");
    service = new HomeAgentTrailerService(db, storage);
  }
  @AfterEach void close() { database.shutdown(); }

  void approved() {
    db.update("INSERT INTO studio_files VALUES (?,'ai-agent-part1','opaque-private-key',100,?,'mp4')", ID, "a".repeat(64));
    db.update("INSERT INTO studio_revisions VALUES ('approved','ai-agent-part1','trailer',?,'VIDEO')", ID);
    db.update("INSERT INTO studio_items VALUES ('ai-agent-part1','trailer','approved','approved')");
  }

  @Test void givenDesignatedCurrentlyApprovedVideo_whenRead_thenReturnsOnlyMetadataWithoutWritesOrStorageAccess() {
    assertThat(service.approvedMedia()).isEmpty();
    approved();
    assertThat(service.approvedMedia()).get().satisfies(m -> {
      assertThat(m.size()).isEqualTo(100);
      assertThat(m.sha256()).isEqualTo("a".repeat(64));
    });
    assertThat(db.queryForObject("SELECT COUNT(*) FROM studio_files", Integer.class)).isEqualTo(1);
    assertThat(db.queryForObject("SELECT approved_id FROM studio_items", String.class)).isEqualTo("approved");
    verifyNoInteractions(storage);
  }

  @Test void givenNewDraftHead_whenPreviousFileStillApproved_thenApprovedPointerControlsPublication() {
    approved();
    db.update("UPDATE studio_items SET head_id='new-draft'");
    assertThat(service.approvedMedia()).isPresent();
    db.update("UPDATE studio_items SET approved_id=NULL");
    assertThat(service.approvedMedia()).isEmpty();
  }

  @Test void givenAnotherFileOrProjectOrKind_whenRead_thenNeverPublishesIt() {
    approved();
    db.update("UPDATE studio_revisions SET file_id='different-file'");
    assertThat(service.approvedMedia()).isEmpty();
    db.update("UPDATE studio_revisions SET file_id=?,project_id='different-project'", ID);
    assertThat(service.approvedMedia()).isEmpty();
    db.update("UPDATE studio_revisions SET project_id='ai-agent-part1',kind='CHARACTER'");
    assertThat(service.approvedMedia()).isEmpty();
    db.update("UPDATE studio_revisions SET kind='VIDEO'");
    db.update("UPDATE studio_files SET project_id='different-project'");
    assertThat(service.approvedMedia()).isEmpty();
  }

  @Test void givenMismatchedItemKeyOrApprovedRevision_whenRead_thenFailClosed() {
    approved();
    db.update("UPDATE studio_revisions SET item_key='other-item'");
    assertThat(service.approvedMedia()).isEmpty();
    db.update("UPDATE studio_revisions SET item_key='trailer'");
    db.update("UPDATE studio_items SET approved_id='missing-revision'");
    assertThat(service.approvedMedia()).isEmpty();
  }

  @Test void givenInvalidMediaMetadata_whenRead_thenDoesNotPublish() {
    approved();
    for (String change : new String[]{"extension='html'", "size_bytes=0", "size_bytes=104857601",
        "sha256='invalid'", "storage_key=''"}) {
      db.update("UPDATE studio_files SET extension='mp4',size_bytes=100,sha256=?,storage_key='opaque-private-key'", "a".repeat(64));
      db.update("UPDATE studio_files SET " + change);
      assertThat(service.approvedMedia()).as(change).isEmpty();
    }
  }

  @Test void givenUnavailableDatabase_whenOptionalHomeSlotRead_thenNoExceptionDetailsOrVideo() {
    JdbcTemplate unavailable = mock(JdbcTemplate.class);
    when(unavailable.query(anyString(), any(RowMapper.class), any(), any()))
        .thenThrow(new DataAccessResourceFailureException("private database details"));
    assertThat(new HomeAgentTrailerService(unavailable, storage).approvedMedia()).isEmpty();
  }
}
