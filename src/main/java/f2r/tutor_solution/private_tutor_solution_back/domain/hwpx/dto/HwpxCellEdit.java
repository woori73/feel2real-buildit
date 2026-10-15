package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 표 셀 수정 지정. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HwpxCellEdit {
  private int table;
  private int row;
  private int col;
  private String text;
}
