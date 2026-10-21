package f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공개 BuildIt Wiki(/wiki)의 "모의고사 학습 자료" — 일반 회원이 올리는 어휘/Workbook/변형문제/기타.
 *
 * <p>{@code wiki_analysis_materials}(ADMIN 전용 공식 분석 자료)와 목적·권한이 달라 별도 테이블/도메인이다.
 * 목록·제목은 공개, 파일 다운로드는 회원(로그인) 전용. 업로더는 서버 authenticated principal 로만 기록한다.
 * 파일은 {@code FileStorage.saveRaw} 로 원본 그대로 저장하고 다운로드로만 제공한다.
 */
@Entity
@Table(name = "wiki_learning_materials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WikiLearningMaterial extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "wiki_learning_material_id")
  private Long wikiLearningMaterialId;

  @Enumerated(EnumType.STRING)
  @Column(name = "data_type", nullable = false, length = 20)
  private WikiLearningMaterialType dataType;

  @Column(nullable = false)
  private Integer grade;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(name = "content_type", nullable = false, length = 120)
  private String contentType;

  @Column(name = "original_file_name", nullable = false, length = 255)
  private String originalFileName;

  @Column(name = "storage_key", nullable = false, length = 500)
  private String storageKey;

  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  @Column(name = "uploader_user_id", nullable = false)
  private Long uploaderUserId;

  @Column(name = "uploader_name", nullable = false, length = 60)
  private String uploaderName;

  @Column(name = "is_deleted", nullable = false)
  private boolean deleted;

  @Builder
  public WikiLearningMaterial(WikiLearningMaterialType dataType, Integer grade, String title,
      String contentType, String originalFileName, String storageKey, Long fileSize,
      Long uploaderUserId, String uploaderName) {
    this.dataType = dataType;
    this.grade = grade;
    this.title = title;
    this.contentType = contentType;
    this.originalFileName = originalFileName;
    this.storageKey = storageKey;
    this.fileSize = fileSize;
    this.uploaderUserId = uploaderUserId;
    this.uploaderName = uploaderName;
    this.deleted = false;
  }

  /** ADMIN 관리(전체 삭제/관리) — row 는 남기고 목록/다운로드에서 제외. storage 는 서비스가 별도 처리. */
  public void softDelete() {
    this.deleted = true;
  }

  /** ADMIN 수정 — 제목·학년·자료 종류만 바꾼다. 파일 교체는 {@link #replaceFile}가 담당. */
  public void updateMetadata(WikiLearningMaterialType dataType, Integer grade, String title) {
    this.dataType = dataType;
    this.grade = grade;
    this.title = title;
  }

  /** ADMIN 파일 교체 — 새 파일 정보를 반영하고 기존 storage key 를 반환한다(서비스가 구 파일 삭제). */
  public String replaceFile(String contentType, String originalFileName, String storageKey, Long fileSize) {
    String oldStorageKey = this.storageKey;
    this.contentType = contentType;
    this.originalFileName = originalFileName;
    this.storageKey = storageKey;
    this.fileSize = fileSize;
    return oldStorageKey;
  }
}
