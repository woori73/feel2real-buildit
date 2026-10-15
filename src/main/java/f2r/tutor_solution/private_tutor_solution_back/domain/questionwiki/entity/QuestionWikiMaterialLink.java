package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문항 ↔ 기존 Wiki 자료({@code wiki_exam_originals} / {@code wiki_analysis_materials}) 연결.
 * 자료 자체는 이미 자료실에 있고 여기서는 링크(참조)만 건다 — 자료 시스템을 재설계하지 않는다.
 */
@Entity
@Table(name = "question_wiki_material_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionWikiMaterialLink extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "link_id")
  private Long linkId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "question_wiki_id", nullable = false)
  private QuestionWiki questionWiki;

  @Enumerated(EnumType.STRING)
  @Column(name = "material_kind", nullable = false, length = 30)
  private QuestionWikiMaterialKind materialKind;

  @Column(name = "material_id", nullable = false)
  private Long materialId;

  @Column(name = "label", length = 200)
  private String label;

  @Builder
  public QuestionWikiMaterialLink(QuestionWiki questionWiki, QuestionWikiMaterialKind materialKind,
      Long materialId, String label) {
    this.questionWiki = questionWiki;
    this.materialKind = materialKind;
    this.materialId = materialId;
    this.label = label;
  }
}
