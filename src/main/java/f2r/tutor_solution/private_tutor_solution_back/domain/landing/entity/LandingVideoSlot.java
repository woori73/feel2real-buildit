package f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 랜딩 페이지 고정 영상 슬롯의 현재 상태.
 *
 * <p>asset 필드가 비어 있으면 번들 영상을 사용한다. 비공개 상태는 asset 존재 여부와 독립적이며,
 * 교체 시에도 현재 공개 상태를 보존한다.
 */
@Entity
@Table(name = "landing_video_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LandingVideoSlot extends BaseEntity {

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "slot", nullable = false, length = 40)
  private LandingVideoSlotKey slot;

  @Column(name = "original_filename", length = 255)
  private String originalFilename;

  /** 변환된 mp4의 불투명 FileStorage key. */
  @Column(name = "storage_key", length = 500)
  private String storageKey;

  /** poster jpg의 불투명 FileStorage key. */
  @Column(name = "thumbnail_key", length = 500)
  private String thumbnailKey;

  @Column(name = "duration_sec")
  private Integer durationSec;

  /** 변환 산출물의 바이트 크기. */
  @Column(name = "size_bytes")
  private Long sizeBytes;

  @Column(name = "width")
  private Integer width;

  @Column(name = "height")
  private Integer height;

  @Column(name = "is_visible", nullable = false)
  private boolean visible = true;

  private LandingVideoSlot(LandingVideoSlotKey slot, boolean visible) {
    this.slot = slot;
    this.visible = visible;
  }

  public static LandingVideoSlot bundled(LandingVideoSlotKey slot, boolean visible) {
    if (slot == null) {
      throw new IllegalArgumentException("slot은 필수입니다.");
    }
    return new LandingVideoSlot(slot, visible);
  }

  public void replaceAsset(
      String originalFilename,
      String storageKey,
      String thumbnailKey,
      Integer durationSec,
      Long sizeBytes,
      Integer width,
      Integer height) {
    this.originalFilename = originalFilename;
    this.storageKey = storageKey;
    this.thumbnailKey = thumbnailKey;
    this.durationSec = durationSec;
    this.sizeBytes = sizeBytes;
    this.width = width;
    this.height = height;
  }

  public void updateVisibility(boolean visible) {
    this.visible = visible;
  }

  public boolean hasManagedAsset() {
    return storageKey != null && !storageKey.isBlank();
  }
}
