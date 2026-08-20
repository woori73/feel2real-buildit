package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "academies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Academy extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "academy_id")
  private Long academyId;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tutor_user_id", nullable = false, unique = true)
  private User tutor;

  @Column(name = "slug", nullable = false, unique = true, length = 100)
  private String slug;

  /**
   * 영문 서브도메인 식별자. {@code {handle}.feel2real.co.kr} 공개 URL에 쓰인다.
   * slug(한글 허용, 내부 식별)와 분리해 서브도메인 라우팅 전용으로 둔다.
   * 전환기 동안 nullable — handle이 없는 기존 Academy는 경로형(/academies/{slug})으로 fallback.
   */
  @Column(name = "handle", length = 30, unique = true)
  private String handle;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "logo_storage_key", nullable = false, length = 500)
  private String logoStorageKey;

  @Column(name = "cover_storage_key", length = 500)
  private String coverStorageKey;

  /**
   * 히어로(V8 시안) 전용 이미지 슬롯. logo/cover와 별개로 개별 관리한다(V940).
   *
   * <p>교재 표지는 시안이 3장 고정 배치라 가변 테이블 대신 슬롯 컬럼 3개를 둔다.
   * 값은 storage key이며 응답 DTO에 싣지 않고 전용 조회 endpoint로만 바이트를 노출한다.
   */
  @Column(name = "principal_photo_storage_key", length = 500)
  private String principalPhotoStorageKey;

  @Column(name = "textbook_cover1_storage_key", length = 500)
  private String textbookCover1StorageKey;

  @Column(name = "textbook_cover2_storage_key", length = 500)
  private String textbookCover2StorageKey;

  @Column(name = "textbook_cover3_storage_key", length = 500)
  private String textbookCover3StorageKey;

  /**
   * 개설 시간표 섹션의 달력 이미지(V948).
   *
   * <p>수업상품에서 자동으로 만드는 행 목록은 달력형 시간표로 보이지 않는다는 요구에서 나왔다.
   * 원장이 만든 달력 이미지를 시간표 토글 영역 안에 얹는다. 히어로 슬롯과 같은 단일 이미지라
   * 업로드/서빙 경로를 그대로 공유한다.
   */
  @Column(name = "schedule_image_storage_key", length = 500)
  private String scheduleImageStorageKey;

  @Lob
  @Column(name = "intro", nullable = false, columnDefinition = "TEXT")
  private String intro;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private AcademyStatus status = AcademyStatus.PUBLISHED;

  @Column(name = "is_visible", nullable = false)
  private boolean visible = true;

  @ManyToMany
  @JoinTable(
      name = "academy_subjects",
      joinColumns = @JoinColumn(name = "academy_id"),
      inverseJoinColumns = @JoinColumn(name = "subject_id"))
  private Set<Subject> subjects = new HashSet<>();

  @ElementCollection(targetClass = TargetGroup.class)
  @CollectionTable(name = "academy_target_groups", joinColumns = @JoinColumn(name = "academy_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "target_group", nullable = false, length = 20)
  private Set<TargetGroup> targetGroups = new HashSet<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "page_settings", columnDefinition = "json")
  private AcademyPageSettings pageSettings;

  @Builder
  public Academy(User tutor, String slug, String handle, String name, String logoStorageKey,
      String coverStorageKey, String intro, AcademyStatus status,
      Boolean visible, Set<Subject> subjects, Set<TargetGroup> targetGroups,
      AcademyPageSettings pageSettings) {
    this.tutor = tutor;
    this.slug = slug;
    this.handle = handle;
    this.name = name;
    this.logoStorageKey = logoStorageKey;
    this.coverStorageKey = coverStorageKey;
    this.intro = intro;
    this.status = status != null ? status : AcademyStatus.PUBLISHED;
    this.visible = visible == null || visible;
    if (subjects != null) this.subjects = subjects;
    if (targetGroups != null) this.targetGroups = targetGroups;
    this.pageSettings = pageSettings;
  }

  public void updateDetails(String name, String intro, Set<Subject> subjects, Set<TargetGroup> targetGroups) {
    this.name = name;
    this.intro = intro;
    this.subjects.clear();
    if (subjects != null) this.subjects.addAll(subjects);
    this.targetGroups.clear();
    if (targetGroups != null) this.targetGroups.addAll(targetGroups);
  }

  public void updateLogo(String logoStorageKey) {
    this.logoStorageKey = logoStorageKey;
  }

  public void updateCover(String coverStorageKey) {
    this.coverStorageKey = coverStorageKey;
  }

  /** 원장 대표사진 교체. null이면 슬롯을 비워 placeholder로 되돌린다. */
  public void updatePrincipalPhoto(String storageKey) {
    this.principalPhotoStorageKey = storageKey;
  }

  /** 대표 교재 표지 교체. slot은 1~3, null이면 슬롯을 비워 placeholder로 되돌린다. */
  public void updateTextbookCover(int slot, String storageKey) {
    switch (slot) {
      case 1 -> this.textbookCover1StorageKey = storageKey;
      case 2 -> this.textbookCover2StorageKey = storageKey;
      case 3 -> this.textbookCover3StorageKey = storageKey;
      default -> throw new IllegalArgumentException("교재 표지 슬롯은 1~3만 허용합니다: " + slot);
    }
  }

  /** 개설 시간표 달력 이미지 교체. null이면 슬롯을 비워 자동 시간표만 남긴다. */
  public void updateScheduleImage(String storageKey) {
    this.scheduleImageStorageKey = storageKey;
  }

  /** 대표 교재 표지 저장 key. slot은 1~3. 비어 있으면 null. */
  public String getTextbookCover(int slot) {
    return switch (slot) {
      case 1 -> textbookCover1StorageKey;
      case 2 -> textbookCover2StorageKey;
      case 3 -> textbookCover3StorageKey;
      default -> throw new IllegalArgumentException("교재 표지 슬롯은 1~3만 허용합니다: " + slot);
    };
  }

  /**
   * 단일 이미지 슬롯 저장. {@code storageKey}가 null이면 슬롯을 비운다.
   *
   * <p>슬롯→컬럼 매핑을 엔티티 안에 한 벌만 둔다. 호출부(서비스·응답 DTO)마다 분기를 복제하면
   * 슬롯이 늘 때 한쪽만 고쳐진다 — 실제로 V948 schedule 추가에서 그 위험이 드러났다.
   */
  public void updateImageSlot(HeroMediaSlot slot, String storageKey) {
    switch (slot) {
      case PRINCIPAL -> updatePrincipalPhoto(storageKey);
      case SCHEDULE -> updateScheduleImage(storageKey);
      default -> updateTextbookCover(slot.textbookSlot(), storageKey);
    }
  }

  /** 단일 이미지 슬롯의 storage key. 비어 있으면 null. */
  public String imageSlotKey(HeroMediaSlot slot) {
    return switch (slot) {
      case PRINCIPAL -> principalPhotoStorageKey;
      case SCHEDULE -> scheduleImageStorageKey;
      default -> getTextbookCover(slot.textbookSlot());
    };
  }

  public void updateVisibility(boolean visible) {
    this.visible = visible;
  }

  public void updateHandle(String handle) {
    this.handle = handle;
  }

  public void updatePageSettings(AcademyPageSettings pageSettings) {
    this.pageSettings = pageSettings;
  }
}
