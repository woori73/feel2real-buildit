package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "academy_easy_business_links",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_academy_easy_business",
        columnNames = {"academy_id", "business_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyEasyBusinessLink extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "link_id")
  private Long linkId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "academy_id", nullable = false)
  private Academy academy;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  public AcademyEasyBusinessLink(Academy academy, EasyBusiness business) {
    this.academy = academy;
    this.business = business;
  }
}
