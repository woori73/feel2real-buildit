package f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout;

import java.util.List;

/**
 * Academy 페이지 레이아웃의 semantic 표현. layout_json 으로 직렬화된다.
 *
 * @param version 레이아웃 스키마 버전
 * @param blocks  순서대로 정렬된 block 목록
 */
public record AcademyPageLayoutSchema(int version, List<AcademyLayoutBlock> blocks) {
}
