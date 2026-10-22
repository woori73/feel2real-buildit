package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

public final class CollaborationProductionSchema {
  private CollaborationProductionSchema() {}

  public static class CutId implements java.io.Serializable {
    public String importId;
    public int cutNumber;

    public CutId() {}

    @Override
    public boolean equals(Object o) {
      return o instanceof CutId c
          && java.util.Objects.equals(importId, c.importId)
          && cutNumber == c.cutNumber;
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(importId, cutNumber);
    }
  }
}

@Entity
@Immutable
@Table(name = "ai_collaboration_imports")
class CollaborationImportEntity {
  @Id
  @Column(length = 36)
  String id;

  @Column(name = "workspace_id", length = 64, nullable = false)
  String workspaceId;

  @Column(name = "source_ref", length = 40, nullable = false)
  String sourceRef;

  @Column(name = "manifest_sha", length = 64, nullable = false)
  String manifestSha;

  @Column(name = "inventory_sha", length = 64, nullable = false)
  String inventorySha;

  @Column(name = "expected_files", nullable = false)
  int expectedFiles;

  @Column(name = "registered_files", nullable = false)
  int registeredFiles;

  @Column(name = "registered_by", nullable = false)
  long registeredBy;

  @Column(length = 16, nullable = false)
  String status;

  @Column(name = "created_at", nullable = false)
  java.time.Instant createdAt;
}

@Entity
@Immutable
@Table(name = "ai_collaboration_sources")
class CollaborationSourceEntity {
  @Id
  @Column(length = 36)
  String id;

  @Column(name = "import_id", length = 36, nullable = false)
  String importId;

  @Column(name = "file_id", length = 36, nullable = false)
  String fileId;

  @Column(name = "source_path", length = 512, nullable = false)
  String sourcePath;

  @Column(name = "version_label", length = 32, nullable = false)
  String versionLabel;

  @Column(length = 32, nullable = false)
  String category;

  @Column(name = "source_approval", length = 64, nullable = false)
  String sourceApproval;
}

@Entity
@Immutable
@Table(name = "ai_collaboration_storyboard_cuts")
@IdClass(CollaborationProductionSchema.CutId.class)
class CollaborationCutEntity {
  @Id
  @Column(name = "import_id", length = 36)
  String importId;

  @Id
  @Column(name = "cut_number")
  int cutNumber;

  @Column(name = "panel_file_id", length = 36, nullable = false)
  String panelFileId;

  @Column(name = "record_json", columnDefinition = "LONGTEXT", nullable = false)
  String recordJson;

  @Column(name = "comparison_status", length = 24, nullable = false)
  String comparisonStatus;

  @Column(name = "pass_confirmed", nullable = false)
  boolean passConfirmed;
}
