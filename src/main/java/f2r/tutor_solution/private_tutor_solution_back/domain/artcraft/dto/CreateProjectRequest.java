package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * {@code POST /api/artcraft/projects} 요청 본문.
 *
 * @param name 프로젝트 표시 이름 (필수)
 * @param projectId 클라이언트 지정 프로젝트 ID (선택).
 *        {@code ^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$} 형식을 만족해야 하며,
 *        생략하면 서버가 UUID를 발급한다.
 * @param academyId 대상 학원 ID (선택, ADMIN 전용).
 *        TUTOR가 자신의 학원과 다른 값을 넘기면 403.
 * @param items 초기 문항/콘텐츠 아이템 배열 (선택, 그대로 저장되는 불투명 JSON)
 */
public record CreateProjectRequest(
    String name,
    String projectId,
    Long academyId,
    JsonNode items) {
}
