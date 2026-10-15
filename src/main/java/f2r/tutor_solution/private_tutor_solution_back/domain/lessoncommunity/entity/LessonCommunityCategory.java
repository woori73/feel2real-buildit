package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lesson_community_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonCommunityCategory extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "category_id")
  private Long categoryId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lesson_id", nullable = false)
  private LessonProduct lesson;

  @Column(name = "name", nullable = false, length = 50)
  private String name;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "is_visible", nullable = false)
  private boolean visible = true;

  public void update(String name, Integer sortOrder) {
    this.name = name;
    this.sortOrder = sortOrder == null ? this.sortOrder : sortOrder;
  }

  @Builder
  public LessonCommunityCategory(LessonProduct lesson, String name, Integer sortOrder) {
    this.lesson = lesson;
    this.name = name;
    this.sortOrder = sortOrder == null ? 0 : sortOrder;
  }
}
