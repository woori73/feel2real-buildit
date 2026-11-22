package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 학습지 문항(V1137 easy_worksheet_items). 문항 내용은 복사하지 않고 출처만 가리킨다. */
@Entity
@Table(name = "easy_worksheet_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorksheetItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "item_id")
  private Long itemId;

  @Column(name = "worksheet_id", nullable = false)
  private Long worksheetId;

  @Column(name = "order_no", nullable = false)
  private int orderNo;

  /** NAESIN(내신 예상문제) / GRAMMAR(예비고1 어법). */
  @Column(name = "source_type", nullable = false, length = 10)
  private String sourceType;

  @Column(name = "source_ref", nullable = false, length = 40)
  private String sourceRef;

  public WorksheetItem(Long worksheetId, int orderNo, String sourceType, String sourceRef) {
    this.worksheetId = worksheetId;
    this.orderNo = orderNo;
    this.sourceType = sourceType;
    this.sourceRef = sourceRef;
  }

  /** 화면·API에서 쓰는 문항 주소. 예: NAESIN:1234, GRAMMAR:3-1:7 */
  public String ref() {
    return sourceType + ":" + sourceRef;
  }
}
