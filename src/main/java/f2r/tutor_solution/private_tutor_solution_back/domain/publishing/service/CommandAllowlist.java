package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PublishingCommand;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * DesignCraft CLI 명령 허용 목록.
 *
 * <p>두 계층을 엄격히 구분한다:
 * <ul>
 *   <li>{@link #AI_ALLOWED} — AI(자연어 수정)가 계획할 수 있는 명령. 판형·마스터·폰트 변경 불가
 *   <li>{@link #INTERNAL} — Worker·시스템 내부 실행 전용 (export, open, preflight 등).
 *       AI plan에 포함되면 거부한다
 * </ul>
 *
 * <p>실측 검증된 CLI 0.8.0 명령 id만 사용한다 ({@code backend-verification.md}).
 */
@Component
public class CommandAllowlist {

  /**
   * AI가 계획할 수 있는 명령 (13개 검증 명령 중 편집용 subset).
   *
   * <p>원고 반입·스타일·목차·검수·실행취소만 허용. 파일 열기·내보내기·페이지 렌더링은
   * 시스템 전용이므로 AI가 요청해도 거부된다.
   */
  private static final Set<String> AI_ALLOWED = Set.of(
      "document.replaceText",
      "file.place",
      "style.paragraph.create",
      "style.paragraph.apply",
      "toc.generate",
      "layout.parents.new",
      "edit.undo"
  );

  /**
   * 내부 실행 전용 명령. AI plan 검증 시 이 목록에 있으면 거부한다.
   */
  private static final Set<String> INTERNAL = Set.of(
      "file.open",
      "file.exportPdf",
      "file.exportEpub",
      "file.exportHtml",
      "file.exportIdml",
      "file.printBooklet",
      "preflight.run"
  );

  /**
   * AI plan의 명령 목록을 검증한다.
   *
   * @throws ResponseStatusException 403 - 허용되지 않은 명령이 포함된 경우
   */
  public void validateAiPlan(List<PublishingCommand> commands) {
    if (commands == null || commands.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "명령 목록이 비어 있습니다.");
    }
    for (PublishingCommand cmd : commands) {
      String id = cmd.id();
      if (INTERNAL.contains(id)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "AI가 실행할 수 없는 명령입니다 (시스템 전용): " + id);
      }
      if (!AI_ALLOWED.contains(id)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "허용되지 않은 명령입니다: " + id);
      }
      validateAiParams(id, cmd.params());
    }
  }

  /**
   * 내부 실행 명령인지 확인 (Worker·시스템 경로용).
   */
  public boolean isInternal(String commandId) {
    return INTERNAL.contains(commandId);
  }

  /**
   * AI 명령의 파라미터를 추가 검증한다.
   *
   * <p>예: style.paragraph.create에서 fontFamily 변경 시도는 거부
   * (한글 폰트는 Noto Sans KR 고정 정책).
   */
  private void validateAiParams(String id, Map<String, Object> params) {
    if ("style.paragraph.apply".equals(id)
        && (params == null || !(params.get("page") instanceof Number page) || page.intValue() < 1)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "스타일을 적용할 쪽 번호(page)가 필요합니다.");
    }
    if ("document.replaceText".equals(id)) {
      if (params == null || !(params.get("story") instanceof Number)
          || !(params.get("before") instanceof String before) || before.isEmpty()
          || before.length() > 12000 || !(params.get("after") instanceof String after)
          || after.length() > 12000) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "텍스트 수정에는 story, 비어 있지 않은 before, after가 필요합니다 (각 12000자 이하).");
      }
    }
    if (params == null) {
      return;
    }
    if ("style.paragraph.create".equals(id) || "style.paragraph.apply".equals(id)) {
      Object chars = params.get("chars");
      if (chars instanceof Map<?, ?> charsMap) {
        Object font = charsMap.get("fontFamily");
        if (font instanceof String fontName
            && !fontName.contains("Noto Sans KR")
            && !fontName.contains("NotoSansKR")) {
          throw new ResponseStatusException(HttpStatus.FORBIDDEN,
              "폰트 변경은 허용되지 않습니다 (Noto Sans KR 고정): " + fontName);
        }
      }
    }
    if ("file.place".equals(id)) {
      Object path = params.get("path");
      if (path instanceof String p && (p.contains("..") || p.startsWith("/"))) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "원고 경로는 작업 디렉토리 내부로 제한됩니다.");
      }
    }
  }
}
