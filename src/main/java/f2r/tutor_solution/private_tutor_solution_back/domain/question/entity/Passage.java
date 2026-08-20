package f2r.tutor_solution.private_tutor_solution_back.domain.question.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "passages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Passage extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "passage_id")
  private Long passageId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subject_id", nullable = false)
  private Subject subject;

  @Column(name = "title", length = 255)
  private String title;

  @Lob
  @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
  private String content;

  @Column(name = "source_origin", length = 255)
  private String sourceOrigin;

  @Builder
  public Passage(Subject subject, String title, String content, String sourceOrigin) {
    this.subject = subject;
    this.title = title;
    this.content = content;
    this.sourceOrigin = sourceOrigin;
  }

  public void updateContent(String title, String content) {
    this.title = title;
    this.content = content;
  }
}
