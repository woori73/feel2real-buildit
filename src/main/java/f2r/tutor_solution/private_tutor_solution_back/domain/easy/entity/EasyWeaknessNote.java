package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 학생 약점 정리 한 건(쌓아 두고 최신 것을 현재 정리로 본다). 관리자 전용. */
@Entity
@Table(name = "easy_student_weakness_notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyWeaknessNote extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "note_id")
  private Long noteId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_id", nullable = false)
  private EasyStudent student;

  @Column(name = "summary", nullable = false, length = 3000)
  private String summary;

  /** JSON 배열 [{"topic":"관계대명사","level":"HIGH","evidence":"11/20 3·7번"}]. */
  @Column(name = "topics", columnDefinition = "TEXT")
  private String topics;

  @Column(name = "source", nullable = false, length = 10)
  private String source;

  public EasyWeaknessNote(EasyBusiness business, EasyStudent student, String summary, String topics, String source) {
    this.business = business;
    this.student = student;
    this.summary = summary;
    this.topics = topics;
    this.source = source == null ? "WEB" : source;
  }
}
