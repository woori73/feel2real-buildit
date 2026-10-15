package f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "subjects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subject extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "subject_id")
  private Integer subjectId;

  @Column(name = "name_en", nullable = false, unique = true, length = 50)
  private String nameEn;

  @Column(name = "name_ko", nullable = false, unique = true, length = 50)
  private String nameKo;

  @Builder
  public Subject(String nameEn, String nameKo) {
    this.nameEn = nameEn;
    this.nameKo = nameKo;
  }
}