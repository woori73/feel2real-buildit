package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("수업 상품 전용 편집기 진입 계약")
class LessonProductEditorEntryContractTest {

  private static final Path TEMPLATE_DIR = Path.of("src/main/resources/templates/tutor");
  private static final Path SCRIPT_DIR = Path.of("src/main/resources/static/js");

  @Test
  @DisplayName("given Academy 화면 when 강좌 등록 진입 then 자체 편집기 링크 없이 LMS로 보낸다")
  void academy_delegates_lesson_creation_to_lms() throws Exception {
    // given: 강좌 등록·관리 진입점을 LMS로 이관했다(docs/academy-lms-design.md §0).
    //        생성 진입 계약 자체는 lmsManage_uses_same_dedicated_editor_for_create가 이어받는다.
    String html = Files.readString(TEMPLATE_DIR.resolve("tutor_academy.html"));
    String script = Files.readString(SCRIPT_DIR.resolve("tutor_academy.js"));

    // when & then: 이 화면에는 생성/편집 진입이 더 이상 없다
    assertThat(html)
        .doesNotContain("createLessonProductLink")
        .doesNotContain("/tutor/academy/lessons/editor")
        .contains("id=\"lmsLink\"");
    // handle이 없으면 /lms/{handle}이 성립하지 않으므로(404) 카드를 감춘다
    assertThat(script)
        .doesNotContain("/tutor/academy/lessons/editor")
        .contains("lmsLink.href = '/lms/' + encodeURIComponent(handle)")
        .contains("lmsCard.classList.add('hidden')");
  }

  @Test
  @DisplayName("given LMS 강좌관리 when 강좌 등록 진입 then Academy 화면과 같은 전용 편집기 URL을 쓴다")
  void lmsManage_uses_same_dedicated_editor_for_create() throws Exception {
    // given: 강사 진입점이 LMS로 이관되는 중이라 두 화면의 생성 동작이 같아야 한다
    String lmsHtml = Files.readString(
        Path.of("src/main/resources/templates/lms/lms_manage.html"));
    String lmsScript = Files.readString(SCRIPT_DIR.resolve("lms_manage.js"));
    String seam = Files.readString(SCRIPT_DIR.resolve("lms_data.js"));

    // when & then: /tutor/academy의 "수업 상품 생성 +"와 동일하게 파라미터 없는 링크다
    assertThat(lmsHtml)
        .contains("href=\"/tutor/academy/lessons/editor\"")
        .doesNotContain("href=\"/tutor/academy/lessons/editor?new=true\"");
    // MOCK 배열에 행을 추가하던 경로는 남아 있으면 안 된다(두 동작이 공존하면 표가 갈린다)
    assertThat(lmsScript).doesNotContain("addCourse");
    assertThat(seam).doesNotContain("addCourse:");
  }

  @Test
  @DisplayName("given 수업 상세 when 수정 진입 then 현재 lessonId를 전용 편집기에 전달한다")
  void detail_uses_dedicated_editor_for_current_lesson() throws Exception {
    // given
    String html = Files.readString(TEMPLATE_DIR.resolve("tutor_lesson_detail.html"));
    String script = Files.readString(SCRIPT_DIR.resolve("tutor_lesson_detail.js"));

    // when & then
    assertThat(html)
        .contains("id=\"editLessonProductLink\"")
        .contains("href=\"/tutor/academy/lessons/editor\"");
    assertThat(script)
        .contains("editLessonProductLink.href = '/tutor/academy/lessons/editor?lessonId='")
        .contains("+ encodeURIComponent(lessonId)");
  }

  @Test
  @DisplayName("given Academy와 수업 상세 when 렌더링 then 레거시 편집 modal 계약이 제거된다")
  void legacy_lesson_modals_are_removed() throws Exception {
    // given
    String academyHtml = Files.readString(TEMPLATE_DIR.resolve("tutor_academy.html"));
    String detailHtml = Files.readString(TEMPLATE_DIR.resolve("tutor_lesson_detail.html"));
    String academyScript = Files.readString(SCRIPT_DIR.resolve("tutor_academy.js"));
    String detailScript = Files.readString(SCRIPT_DIR.resolve("tutor_lesson_detail.js"));

    // when & then
    assertThat(academyHtml)
        .doesNotContain("id=\"lessonModal\"")
        .doesNotContain("id=\"lessonForm\"");
    assertThat(detailHtml)
        .doesNotContain("id=\"lessonModal\"")
        .doesNotContain("id=\"lessonForm\"");
    assertThat(academyScript)
        .doesNotContain("openLessonModal")
        .doesNotContain("submitLesson");
    assertThat(detailScript)
        .doesNotContain("openLessonModal")
        .doesNotContain("submitLesson");
  }

  @Test
  @DisplayName("given 수업 상품 섹션 제거 when 기존 화면 동작 검사 then 삭제/신청/상세 이동이 사라지지 않는다")
  void existing_delete_application_and_detail_contracts_are_preserved() throws Exception {
    // given: Academy 화면의 판매용 카드와 수정/삭제 메뉴를 걷어냈다. 그 기능들이 어디로 갔는지
    //        여기서 못 박는다 — 하나라도 갈 곳이 없으면 기능 상실이다.
    String academyScript = Files.readString(SCRIPT_DIR.resolve("tutor_academy.js"));
    String editorScript = Files.readString(SCRIPT_DIR.resolve("lesson_product_editor.js"));
    String detailScript = Files.readString(SCRIPT_DIR.resolve("tutor_lesson_detail.js"));

    // when & then: Academy 화면에는 수업 운영 진입만 남는다.
    //   /tutor/lesson/{id}(신청·수강생, 수업 커뮤니티)로 가는 링크는 전체 코드베이스에서 여기뿐이라
    //   제거하면 그 화면이 고아가 된다.
    //   (Academy 삭제는 이 화면에 그대로 남으므로 method: 'DELETE' 자체를 금지하면 안 된다.
    //    사라져야 하는 건 "수업" 삭제와 신청 조회다.)
    assertThat(academyScript)
        .contains("href=\"/tutor/lesson/${lesson.lessonId}\"")
        .contains("fetchAuth('/api/tutor/academy', { method: 'DELETE' })")
        .doesNotContain("/api/tutor/academy/lessons/", "/applications");
    // 수업 삭제는 전용 편집기가 계속 담당한다
    assertThat(editorScript)
        .contains("/api/tutor/academy/lessons/${lessonId}")
        .contains("method: 'DELETE'");
    assertThat(detailScript)
        .contains("'/api/tutor/academy/lessons/' + lessonId")
        .contains("'/api/tutor/lessons/' + lessonId + '/applications'")
        .contains("location.href = '/conversation/dashboard?id='")
        .contains("href=\"/lessons/${lessonId}/community\"");
  }

  @Test
  @DisplayName("given 수업 운영 목록 when 키보드 사용 then 앵커라 별도 접근성 배선이 필요 없다")
  void lesson_operation_list_uses_plain_anchors_for_keyboard_access() throws Exception {
    // given: 카드 안에 메뉴 버튼이 있던 구조에서는 role/tabindex/keydown/stopPropagation을 손으로
    //        맞춰야 했다. 목록을 링크로 축소하면서 그 배선 자체가 불필요해졌다.
    String script = Files.readString(SCRIPT_DIR.resolve("tutor_academy.js"));

    // when & then
    assertThat(script)
        .contains("<a class=\"lesson-card lesson-card-clickable\" href=\"/tutor/lesson/${lesson.lessonId}\">");
    assertThat(script)
        .doesNotContain("role=\"button\"", "tabindex=\"0\"", "aria-expanded", "stopPropagation");
  }
}
