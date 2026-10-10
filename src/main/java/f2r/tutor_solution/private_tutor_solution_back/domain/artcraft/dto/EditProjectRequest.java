package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto;

import java.util.List;

/**
 * {@code POST /api/artcraft/projects/{id}/edit} 요청 본문.
 *
 * @param expectedVersion 편집 기준 버전 (필수). 서버의 현재 버전과 다르면
 *        409 + {@code currentVersion}을 반환하며 편집은 수행되지 않는다.
 * @param commands 실행할 CLI 명령 목록 (필수, 1건 이상). 화이트리스트 검증 후
 *        순서대로 steps.json에 기록되어 CLI로 실행된다.
 * @param actor 편집 주체 식별자 (선택, 기본값: 요청자의 loginId).
 *        {@code ai-}로 시작하는 actor가 {@code last_editor=user}인 프로젝트를
 *        편집하면 경고가 응답에 포함된다 (사용자 수정 후 5분 내 자동 덮어쓰기 주의).
 * @param desc 변경 설명 (선택). 스냅샷/히스토리에 기록된다.
 */
public record EditProjectRequest(
    Integer expectedVersion,
    List<EditCommandRequest> commands,
    String actor,
    String desc) {
}
