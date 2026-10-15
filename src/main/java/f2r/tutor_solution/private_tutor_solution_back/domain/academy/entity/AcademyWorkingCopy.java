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
 * BuildIt 통합 화면의 작업본. 공개 홈페이지({@code academies.intro}, {@code page_settings})와
 * 분리된 편집용 복사본이며, 게시할 때만 공개본에 반영된다.
 *
 * <p>동시 수정은 JPA {@code @Version}(낙관적 잠금)으로 막는다 — 시각 편집기 초안과 같은 방식.
 */
@Entity
@Table(
    name = "academy_working_copy",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_working_copy_academy", columnNames = "academy_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyWorkingCopy {

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

  /** {@link AcademyPageSettings}를 JSON 문자열로 저장한다. */
  @Column(name = "page_settings_json", columnDefinition = "MEDIUMTEXT")
  private String pageSettingsJson;

  /** 게시 전 이미지 교체/삭제 예약(JSON slot→storage key, 빈 문자열=비우기). null이면 예약 없음. */
  @Column(name = "staged_media_json", columnDefinition = "TEXT")
  private String stagedMediaJson;

  /** 작업본이 기준으로 삼은 공개 내용의 지문. 공개 내용이 바뀌었는지 게시 시 비교한다. */
  @Column(name = "base_fingerprint", nullable = false, length = 64)
  private String baseFingerprint;

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

  public AcademyWorkingCopy(
      Long academyId, String intro, String pageSettingsJson, String baseFingerprint, String actor) {
    this.academyId = academyId;
    this.intro = intro;
    this.pageSettingsJson = pageSettingsJson;
    this.baseFingerprint = baseFingerprint;
    this.createdBy = actor;
    this.updatedBy = actor;
  }

  /** 작업본 내용을 바꾼다. revision은 Hibernate가 UPDATE 시 올린다. */
  public void replaceContent(String intro, String pageSettingsJson, String actor) {
    this.intro = intro;
    this.pageSettingsJson = pageSettingsJson;
    this.updatedBy = actor;
  }

  /** 이미지 예약을 바꾼다. 값이 달라지면 Hibernate가 UPDATE하며 revision이 오른다. */
  public void replaceStagedMedia(String stagedMediaJson, String actor) {
    this.stagedMediaJson = stagedMediaJson;
    this.updatedBy = actor;
  }

  /** 게시 또는 되돌린 뒤 공개 내용 지문을 새 기준으로 맞춘다. */
  public void rebase(String baseFingerprint, String actor) {
    this.baseFingerprint = baseFingerprint;
    this.updatedBy = actor;
  }
}
