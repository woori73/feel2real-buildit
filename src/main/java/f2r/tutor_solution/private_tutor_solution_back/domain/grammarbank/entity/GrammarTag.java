package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "grammar_tags")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrammarTag extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "tag_id")
  private Long tagId;

  @Column(name = "name_ko", nullable = false, unique = true, length = 60)
  private String nameKo;

  @Column(name = "name_en", length = 60)
  private String nameEn;

  /** 자동 분류용 별칭(쉼표 구분). */
  @Column(length = 255)
  private String aliases;

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder = 0;

  @Builder
  public GrammarTag(String nameKo, String nameEn, String aliases, Integer sortOrder) {
    this.nameKo = nameKo;
    this.nameEn = nameEn;
    this.aliases = aliases;
    this.sortOrder = sortOrder != null ? sortOrder : 0;
  }
}
