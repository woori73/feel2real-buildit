package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto;

import java.util.Map;

/**
 * AI 편집 명령 1건. {@code pdfcraft-cli run --script}의 steps.json 원소와 1:1 대응한다.
 *
 * @param tool 실행할 CLI 도구명. 화이트리스트에 있는 것만 허용된다:
 *        {@code doc_open}, {@code page_add_text}, {@code content_update},
 *        {@code doc_save}, {@code page_render}, {@code doc_info}, {@code edit_undo}.
 *        그 외 도구는 400으로 거부된다.
 * @param args 도구 인자 (불투명 JSON 객체, 예: {@code {"doc":1,"page":1,"text":"..."}}).
 *        {@code doc_save}의 {@code path}가 상대 경로면 프로젝트 디렉토리 기준으로 해석된다.
 */
public record EditCommandRequest(
    String tool,
    Map<String, Object> args) {
}
