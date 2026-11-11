package f2r.tutor_solution.private_tutor_solution_back.domain.academyhome;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 학생명단 이름 ↔ AcademyCraft 학생 연결(원장 확인). V1107 academy_roster_student_links. */
@Entity @Table(name = "academy_roster_student_links") @Getter @NoArgsConstructor
public class AcademyRosterStudentLink {
  public static final String LINKED = "LINKED";
  public static final String EXCLUDED = "EXCLUDED";

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false) private Long academyId;
  @Column(nullable = false) private Integer rosterYear;
  @Column(nullable = false, length = 40) private String rosterName;
  @Column(nullable = false, length = 16) private String linkStatus;
  private Long reaStudentId;
  @Column(nullable = false) private Long confirmedBy;
  @Column(nullable = false) private LocalDateTime confirmedAt;

  public AcademyRosterStudentLink(Long academyId, Integer rosterYear, String rosterName) {
    this.academyId = academyId; this.rosterYear = rosterYear; this.rosterName = rosterName;
  }

  /** 원장이 고른 학생으로 연결하거나(LINKED) 명단에서 제외(EXCLUDED)한다. 같은 이름 행은 덮어쓴다(재처리 중복 없음). */
  public void confirm(String status, Long reaStudentId, Long userId) {
    this.linkStatus = status; this.reaStudentId = LINKED.equals(status) ? reaStudentId : null;
    this.confirmedBy = userId; this.confirmedAt = LocalDateTime.now();
  }
}
