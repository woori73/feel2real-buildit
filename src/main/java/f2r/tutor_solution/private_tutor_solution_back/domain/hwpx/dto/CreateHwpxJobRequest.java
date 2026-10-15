package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** HWPX 작업 생성 요청. */
@Getter
@NoArgsConstructor
public class CreateHwpxJobRequest {
  private String title;
  private List<HwpxCellEdit> edits;
}
