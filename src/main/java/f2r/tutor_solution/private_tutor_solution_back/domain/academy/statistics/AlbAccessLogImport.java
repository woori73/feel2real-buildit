package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "alb_access_log_imports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlbAccessLogImport {
  @Id
  @Column(name = "object_key_hash", length = 64)
  private String objectKeyHash;

  @Column(name = "object_key", nullable = false, length = 1024)
  private String objectKey;

  @Column(name = "etag", length = 128)
  private String etag;

  @Column(name = "imported_request_count", nullable = false)
  private long importedRequestCount;

  @Column(name = "imported_at", nullable = false, insertable = false, updatable = false)
  private LocalDateTime importedAt;
}
