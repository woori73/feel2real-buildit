package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionSource;
import java.util.List;

/** 수집원 카탈로그 표시 — 어떤 source가 실제로 활성화되어 있는지 구분하기 위해 active를 노출한다. */
public record EasyCollectionSourceResponse(
    String code,
    String name,
    String adapterType,
    boolean active
) {
  public static EasyCollectionSourceResponse from(EasyCollectionSource source) {
    return new EasyCollectionSourceResponse(
        source.getCode(), source.getName(), source.getAdapterType(), source.isActive());
  }

  public static List<EasyCollectionSourceResponse> listOf(List<EasyCollectionSource> sources) {
    return sources.stream().map(EasyCollectionSourceResponse::from).toList();
  }
}
