package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "academy_regions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_academy_regions_city_district",
        columnNames = {"academy_id", "city", "district"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyRegion extends BaseEntity {

  public static final String ALL_DISTRICT = "ALL";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "region_id")
  private Long regionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "academy_id", nullable = false)
  private Academy academy;

  @Column(name = "city", nullable = false, length = 50)
  private String city;

  @Column(name = "district", nullable = false, length = 50)
  private String district;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Builder
  public AcademyRegion(Academy academy, String city, String district, String displayName) {
    this.academy = academy;
    this.city = city;
    this.district = district != null ? district : ALL_DISTRICT;
    this.displayName = displayName;
  }
}
