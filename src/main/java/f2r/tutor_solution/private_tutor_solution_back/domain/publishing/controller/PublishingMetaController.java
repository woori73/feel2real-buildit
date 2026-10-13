package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob.Template;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DesignCraft 출판 메타 API (갭-G8).
 *
 * <p>Qoder 프론트의 {@code constants()}를 대체한다.
 * 템플릿 3종·문단 스타일 13종·글자 스타일 5종 목록을 제공한다.
 */
@RestController
@RequestMapping("/api/publishing")
@RequiredArgsConstructor
public class PublishingMetaController {

  /** 템플릿 목록. */
  @GetMapping("/templates")
  public ResponseEntity<Map<String, List<Map<String, String>>>> listTemplates() {
    List<Map<String, String>> templates = List.of(
        Map.of("code", Template.KO_TEXTBOOK_A4.getCode(),
            "name", "A4 교재", "size", "210×297mm"),
        Map.of("code", Template.KO_WORKBOOK_B5.getCode(),
            "name", "B5 문제집", "size", "182×257mm"),
        Map.of("code", Template.KO_BOOK_46.getCode(),
            "name", "46판 단행본", "size", "188×257mm")
    );
    return ResponseEntity.ok(Map.of("templates", templates));
  }

  /** 스타일 목록 (문단 13종·글자 5종). */
  @GetMapping("/styles")
  public ResponseEntity<Map<String, Object>> listStyles() {
    List<String> paragraph = List.of(
        "ko-body", "ko-heading1", "ko-heading2", "ko-heading3",
        "ko-passage", "ko-choice", "ko-footnote", "ko-quote",
        "ko-caption", "ko-qnum", "ko-explain", "ko-vocab", "ko-time"
    );
    List<String> character = List.of(
        "ko-char-emph", "ko-char-strong", "ko-char-code",
        "ko-char-caption", "ko-char-note"
    );
    return ResponseEntity.ok(Map.of(
        "paragraph", paragraph,
        "character", character,
        "fontFamily", "Noto Sans KR"
    ));
  }
}
