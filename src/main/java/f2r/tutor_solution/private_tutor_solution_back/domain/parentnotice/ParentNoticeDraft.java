package f2r.tutor_solution.private_tutor_solution_back.domain.parentnotice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "parent_notice_drafts")
@Getter
@NoArgsConstructor
public class ParentNoticeDraft {
  @Id
  @Column(length = 36)
  private String id;

  @Column(nullable = false)
  private Long ownerId;

  @Column(nullable = false, length = 10)
  private String ownerRole;

  @Column(nullable = false, length = 12)
  private String kind;

  @Column(nullable = false, length = 80)
  private String title;

  @Column(nullable = false, length = 200)
  private String body;

  @Column(nullable = false, length = 12)
  private String source;

  @Column(nullable = false)
  private LocalDateTime createdAt;
}
