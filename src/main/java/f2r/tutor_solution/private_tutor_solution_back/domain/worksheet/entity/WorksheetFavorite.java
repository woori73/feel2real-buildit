package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 학습지 즐겨찾기(V1134 easy_worksheet_favorites). 선생님마다 따로 둔다. */
@Entity
@Table(name = "easy_worksheet_favorites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorksheetFavorite {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "favorite_id")
  private Long favoriteId;

  @Column(name = "worksheet_id", nullable = false)
  private Long worksheetId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  public WorksheetFavorite(Long worksheetId, Long userId, LocalDateTime now) {
    this.worksheetId = worksheetId;
    this.userId = userId;
    this.createdAt = now;
  }
}
