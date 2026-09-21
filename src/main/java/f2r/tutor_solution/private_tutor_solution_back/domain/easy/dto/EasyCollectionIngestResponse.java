package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import java.util.List;

/** CSV 인제스트 결과 — run 요약 + 정규화 실패 행 메시지(원문 그대로 보관되었음을 보증). */
public record EasyCollectionIngestResponse(
    EasyCollectionRunResponse run,
    List<String> rowErrors
) {
  public static EasyCollectionIngestResponse of(EasyCollectionRun run, List<String> rowErrors) {
    return new EasyCollectionIngestResponse(EasyCollectionRunResponse.from(run),
        List.copyOf(rowErrors));
  }
}
