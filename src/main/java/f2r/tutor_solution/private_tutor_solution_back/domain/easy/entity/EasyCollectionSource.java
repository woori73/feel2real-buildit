package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자동수집원 카탈로그 — 실제 동작 여부와 무관하게 source 코드 정의를 먼저 등록한다.
 * PHASE 1에서 active한 것은 EXCEL_FILE뿐이다.
 */
@Entity
@Table(name = "easy_collection_sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyCollectionSource extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "source_id")
  private Long sourceId;

  @Column(name = "code", nullable = false, length = 40, unique = true)
  private String code;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "adapter_type", nullable = false, length = 40)
  private String adapterType;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Builder
  public EasyCollectionSource(String code, String name, String adapterType, boolean active) {
    this.code = code;
    this.name = name;
    this.adapterType = adapterType;
    this.active = active;
  }
}
