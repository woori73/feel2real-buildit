package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학원 홈페이지용 이미지 자산.
 *
 * <p>HeroMediaSlot(원장사진·교재표지·시간표)과 구분된다.
 * 홈페이지 배너·배경·섹션 이미지용으로, 원본과 편집본을 구분하여 저장한다.
 *
 * <p>편집 이력은 version으로 관리하며, previousAssetId로 되돌리기가 가능하다.
 * AcademyWorkingCopy.stagedMediaJson에 스테이징된 후 발행 시 반영된다.
 */
@Entity
@Table(name = "academy_image_asset",
    indexes = {
        @Index(name = "idx_academy_image_asset_academy", columnList = "academyId"),
        @Index(name = "idx_academy_image_asset_slot", columnList = "academyId, slotKey")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyImageAsset extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 소속 학원 ID (학원별 격리). */
  @Column(nullable = false)
  private Long academyId;

  /** 이미지 슬롯 (HERO_BANNER, SECTION_BG 등). HeroMediaSlot과 구분. */
  @Column(nullable = false, length = 50)
  private String slotKey;

  /** 원본 이미지 저장 경로. */
  @Column(nullable = false, length = 500)
  private String originalPath;

  /** 편집본 이미지 저장 경로 (편집 전에는 null). */
  @Column(length = 500)
  private String editedPath;

  /** 버전 (1부터 시작, 편집할 때마다 증가). */
  @Column(nullable = false)
  private Integer version = 1;

  /** 이전 버전 자산 ID (되돌리기용, 최초 버전은 null). */
  private Long previousAssetId;

  /** 편집 작업 설명 (예: "밝기 +20, 채도 +10"). */
  @Column(length = 500)
  private String editDescription;

  /** 미리보기에 적용 여부 (승인 전). */
  @Column(nullable = false)
  private Boolean stagedForPreview = false;

  /** 발행 여부. */
  @Column(nullable = false)
  private Boolean published = false;

  public static AcademyImageAsset createOriginal(Long academyId, String slotKey, String originalPath) {
    AcademyImageAsset asset = new AcademyImageAsset();
    asset.academyId = academyId;
    asset.slotKey = slotKey;
    asset.originalPath = originalPath;
    asset.version = 1;
    return asset;
  }

  public AcademyImageAsset createEditedVersion(String editedPath, String editDescription) {
    AcademyImageAsset edited = new AcademyImageAsset();
    edited.academyId = this.academyId;
    edited.slotKey = this.slotKey;
    edited.originalPath = this.originalPath;
    edited.editedPath = editedPath;
    edited.version = this.version + 1;
    edited.previousAssetId = this.getId();
    edited.editDescription = editDescription;
    return edited;
  }

  public void markStagedForPreview() {
    this.stagedForPreview = true;
  }

  public void markPublished() {
    this.published = true;
    this.stagedForPreview = false;
  }

  /** 현재 유효한 이미지 경로 (편집본 우선). */
  public String getEffectivePath() {
    return editedPath != null ? editedPath : originalPath;
  }
}
