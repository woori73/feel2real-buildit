package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/** JDBC state transitions, immutable ORM schema contracts. No cascading delete mappings. */
public final class CollaborationSchema {
  private CollaborationSchema() {}

  public static class MemberId implements java.io.Serializable {
    public String workspaceId;
    public long userId;

    public MemberId() {}

    @Override
    public boolean equals(Object o) {
      return o instanceof MemberId m
          && java.util.Objects.equals(workspaceId, m.workspaceId)
          && userId == m.userId;
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(workspaceId, userId);
    }
  }

  public static class ChunkId implements java.io.Serializable {
    public String uploadId;
    public int chunkIndex;

    public ChunkId() {}

    @Override
    public boolean equals(Object o) {
      return o instanceof ChunkId c
          && java.util.Objects.equals(uploadId, c.uploadId)
          && chunkIndex == c.chunkIndex;
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(uploadId, chunkIndex);
    }
  }

  public static class SubmissionId implements java.io.Serializable {
    public String grantId;
    public String requestId;

    public SubmissionId() {}

    @Override
    public boolean equals(Object o) {
      return o instanceof SubmissionId s
          && java.util.Objects.equals(grantId, s.grantId)
          && java.util.Objects.equals(requestId, s.requestId);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(grantId, requestId);
    }
  }
}

@Entity
@Immutable
@Table(name = "ai_collaboration_workspaces")
class CollaborationWorkspaceEntity {
  @Id
  @Column(length = 64)
  String id;

  @Column(length = 150, nullable = false)
  String title;

  @Column(name = "owner_id")
  Long ownerId;

  @Column(name = "created_at", nullable = false)
  java.time.Instant createdAt;
}

@Entity
@Immutable
@Table(name = "ai_collaboration_files")
class CollaborationFileEntity {
  @Id
  @Column(length = 36)
  String id;

  @Column(name = "workspace_id", length = 64, nullable = false)
  String workspaceId;

  @Column(name = "parent_id", length = 36)
  String parentId;

  @Column(name = "storage_key", length = 1024, nullable = false)
  String storageKey;

  @Column(length = 255, nullable = false)
  String filename;

  @Column(length = 8, nullable = false)
  String extension;

  @Column(length = 64, nullable = false)
  String sha256;

  @Column(name = "size_bytes", nullable = false)
  long sizeBytes;

  @Column(length = 16, nullable = false)
  String status;

  @Column(name = "registered_by", nullable = false)
  long registeredBy;

  @Column(name = "registered_agent", length = 16)
  String registeredAgent;

  @Column(name = "source_grant_id", length = 36)
  String sourceGrantId;

  @Column(name = "created_at", nullable = false)
  java.time.Instant createdAt;
}

@Entity
@Immutable
@Table(name = "ai_collaboration_workspace_events")
class CollaborationMemoryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "workspace_id", length = 64, nullable = false)
  String workspaceId;

  @Column(name = "actor_id", nullable = false)
  long actorId;

  @Column(name = "actor_agent", length = 20)
  String actorAgent;

  @Column(name = "event_type", length = 32, nullable = false)
  String eventType;

  @Column(columnDefinition = "TEXT", nullable = false)
  String body;

  @Column(name = "created_at", nullable = false)
  java.time.Instant createdAt;
}

@Entity
@Immutable
@Table(name = "mcp_collaboration_bindings")
class CollaborationBindingEntity {
  @Id
  @Column(name = "grant_id", length = 36)
  String grantId;

  @Column(name = "workspace_id", length = 64, nullable = false)
  String workspaceId;

  @Column(length = 16, nullable = false)
  String agent;

  @Column(name = "bound_by", nullable = false)
  long boundBy;

  @Column(name = "created_at", nullable = false)
  java.time.Instant createdAt;
}

@Entity
@Immutable
@Table(name = "ai_collaboration_uploads")
class CollaborationUploadEntity {
  @Id
  @Column(length = 36)
  String id;

  @Column(name = "grant_id", length = 36, nullable = false)
  String grantId;

  @Column(name = "workspace_id", length = 64, nullable = false)
  String workspaceId;

  @Column(name = "task_id", length = 36, nullable = false)
  String taskId;

  @Column(length = 16, nullable = false)
  String agent;

  @Column(length = 255, nullable = false)
  String filename;

  @Column(name = "expected_sha", length = 64, nullable = false)
  String expectedSha;

  @Column(name = "expected_size", nullable = false)
  long expectedSize;

  @Column(name = "received_size", nullable = false)
  long receivedSize;

  @Column(name = "next_chunk", nullable = false)
  int nextChunk;

  @Column(length = 16, nullable = false)
  String status;

  @Column(name = "parent_id", length = 36)
  String parentId;

  @Column(name = "file_id", length = 36)
  String fileId;

  @Column(name = "created_at", nullable = false)
  java.time.Instant createdAt;
}

@Entity
@Immutable
@Table(name = "ai_collaboration_workspace_members")
@IdClass(CollaborationSchema.MemberId.class)
class CollaborationMemberEntity {
  @Id
  @Column(name = "workspace_id", length = 64)
  String workspaceId;

  @Id
  @Column(name = "user_id")
  long userId;

  @Column(length = 16, nullable = false)
  String permission;
}

@Entity
@Immutable
@Table(name = "ai_collaboration_chunks")
@IdClass(CollaborationSchema.ChunkId.class)
class CollaborationChunkEntity {
  @Id
  @Column(name = "upload_id", length = 36)
  String uploadId;

  @Id
  @Column(name = "chunk_index")
  int chunkIndex;

  @Column(length = 64, nullable = false)
  String sha256;

  @Column(columnDefinition = "MEDIUMBLOB", nullable = false)
  byte[] data;
}

@Entity
@Immutable
@Table(name = "ai_collaboration_submissions")
@IdClass(CollaborationSchema.SubmissionId.class)
class CollaborationSubmissionEntity {
  @Id
  @Column(name = "grant_id", length = 36)
  String grantId;

  @Id
  @Column(name = "request_id", length = 64)
  String requestId;

  @Column(name = "task_id", length = 36, nullable = false)
  String taskId;

  @Column(name = "body_sha", length = 64, nullable = false)
  String bodySha;

  @Column(name = "result_json", columnDefinition = "TEXT", nullable = false)
  String resultJson;

  @Column(name = "created_at", nullable = false)
  java.time.Instant createdAt;
}
