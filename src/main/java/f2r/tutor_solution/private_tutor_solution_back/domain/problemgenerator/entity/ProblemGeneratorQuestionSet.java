package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "problem_generator_question_sets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemGeneratorQuestionSet extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "set_id")
  private Long setId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_user_id", nullable = false)
  private User owner;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "material_id")
  private ProblemGeneratorMaterial material;

  @Column(nullable = false, length = 180)
  private String title;

  @Lob
  @Column(columnDefinition = "TEXT")
  private String grammar;

  @Lob
  @Column(name = "prompt_passage", columnDefinition = "LONGTEXT")
  private String promptPassage;

  @Column(length = 80)
  private String model;

  @Column(name = "raw_response_json", columnDefinition = "JSON")
  private String rawResponseJson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private MaterialVisibility visibility;

  @Builder
  public ProblemGeneratorQuestionSet(User owner, ProblemGeneratorMaterial material, String title,
      String grammar, String promptPassage, String model, String rawResponseJson,
      MaterialVisibility visibility) {
    this.owner = owner;
    this.material = material;
    this.title = title;
    this.grammar = grammar;
    this.promptPassage = promptPassage;
    this.model = model;
    this.rawResponseJson = rawResponseJson;
    this.visibility = visibility != null ? visibility : MaterialVisibility.PRIVATE;
  }

  public boolean isPublic() { return visibility == MaterialVisibility.PUBLIC; }
  public boolean isOwnedBy(Long userId) {
    return owner != null && owner.getUserId() != null && owner.getUserId().equals(userId);
  }
  public void updateMetadata(String title, String grammar, String promptPassage, MaterialVisibility visibility) {
    this.title = title;
    this.grammar = grammar;
    this.promptPassage = promptPassage;
    this.visibility = visibility != null ? visibility : MaterialVisibility.PRIVATE;
  }
}
