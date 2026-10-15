package f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout;

import java.util.Map;

/**
 * 레이아웃 내 단일 block.
 *
 * @param type   block 타입 id(allowlist, {@link AcademyLayoutBlockType#id()})
 * @param order  렌더링 순서(0-based, 안정 정렬)
 * @param config block 별 최소 설정/콘텐츠. 텍스트 값은 user-generated content 로 간주해
 *               이후 공개 렌더 단계에서 escape/sanitize 한다.
 */
public record AcademyLayoutBlock(String type, int order, Map<String, Object> config) {
}
