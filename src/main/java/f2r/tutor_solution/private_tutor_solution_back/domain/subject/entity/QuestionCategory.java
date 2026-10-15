package f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "question_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionCategory extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "category_id")
  private Long categoryId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subject_id", nullable = false)
  private Subject subject;

  @Column(name = "category_name_ko", nullable = false, unique = true, length = 50)
  private String categoryNameKo;

  @Column(name = "category_name_en", unique = true, length = 50)
  private String categoryNameEn;

  @Builder
  public QuestionCategory(Subject subject, String categoryNameKo, String categoryNameEn) {
    this.subject = subject;
    this.categoryNameKo = categoryNameKo;
    this.categoryNameEn = categoryNameEn;
  }
}
