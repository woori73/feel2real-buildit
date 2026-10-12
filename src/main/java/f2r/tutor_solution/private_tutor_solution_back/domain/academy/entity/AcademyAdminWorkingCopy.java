package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 관리자가 대상 학원에 쓰는 비공개 작업본. 회원 본인의 작업본({@link AcademyWorkingCopy})과 별개 테이블이라
 * 관리자의 미게시 수정은 회원 화면에 보이지 않고 회원의 게시에 딸려 나가지도 않는다.
 */
@Entity
@Table(
    name = "academy_admin_working_copy",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_admin_working_copy_academy", columnNames = "academy_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyAdminWorkingCopy {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "working_copy_id")
  private Long workingCopyId;

  @Column(name = "academy_id", nullable = false)
  private Long academyId;

  @Version
  @Column(name = "revision", nullable = false)
  private Long revision;

  @Column(name = "intro", columnDefinition = "TEXT")
  private String intro;

  @Column(name = "page_settings_json", columnDefinition = "MEDIUMTEXT")
  private String pageSettingsJson;

  @Column(name = "base_fingerprint", nullable = false, length = 64)
  private String baseFingerprint;

  /** 작업본의 기준이 된 공개 소개/설정(병합 기준). V1063 이전 행은 null. */
  @Column(name = "base_intro", columnDefinition = "TEXT")
  private String baseIntro;

  @Column(name = "base_page_settings_json", columnDefinition = "MEDIUMTEXT")
  private String basePageSettingsJson;

  @Column(name = "created_by", nullable = false, length = 100, updatable = false)
  private String createdBy;

  @Column(name = "updated_by", nullable = false, length = 100)
  private String updatedBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public AcademyAdminWorkingCopy(
      Long academyId, String intro, String pageSettingsJson, String baseFingerprint, String actor) {
    this.academyId = academyId;
    this.intro = intro;
    this.pageSettingsJson = pageSettingsJson;
    this.baseFingerprint = baseFingerprint;
    this.baseIntro = intro;
    this.basePageSettingsJson = pageSettingsJson;
    this.createdBy = actor;
    this.updatedBy = actor;
  }

  public void replaceContent(String intro, String pageSettingsJson, String actor) {
    this.intro = intro;
    this.pageSettingsJson = pageSettingsJson;
    this.updatedBy = actor;
  }

  /** 공개 내용이 기준이 되도록 지문과 기준 내용을 함께 갱신한다(게시·되돌리기·공개 변경 반영 후). */
  public void rebase(String baseFingerprint, String baseIntro, String basePageSettingsJson, String actor) {
    this.baseFingerprint = baseFingerprint;
    this.baseIntro = baseIntro;
    this.basePageSettingsJson = basePageSettingsJson;
    this.updatedBy = actor;
  }
}
