package f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * /wiki 일 단위 방문 집계 — 날짜당 1행. {@code academy_daily_visits} 와 동일 패턴.
 * 개인정보(IP 등)는 저장하지 않는다. "오늘 몇 명 / 늘고 있나" 운영 지표 전용.
 */
@Entity
@Table(
    name = "wiki_daily_visits",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_wiki_daily_visits_date",
        columnNames = {"visit_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WikiDailyVisit extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "wiki_daily_visit_id")
  private Long wikiDailyVisitId;

  @Column(name = "visit_date", nullable = false)
  private LocalDate visitDate;

  @Column(name = "visit_count", nullable = false)
  private long visitCount;
}
