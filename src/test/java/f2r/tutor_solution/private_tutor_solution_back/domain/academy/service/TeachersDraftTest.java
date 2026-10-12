package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TeachersDraft — 강사진 입력 검증")
class TeachersDraftTest {

  private static void rejects(String json, String messagePart) {
    assertThatThrownBy(() -> TeachersDraft.validate(json))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(messagePart);
  }

  @Test
  @DisplayName("given 비어 있거나 null일 때 when 검증하면 then 통과(강사진 없음)")
  void blankIsAllowed() {
    assertThatCode(() -> TeachersDraft.validate(null)).doesNotThrowAnyException();
    assertThatCode(() -> TeachersDraft.validate("")).doesNotThrowAnyException();
    assertThatCode(() -> TeachersDraft.validate("  ")).doesNotThrowAnyException();
    assertThatCode(() -> TeachersDraft.validate("[]")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("given 이름만 있는 강사와 모든 필드가 있는 강사 when 검증하면 then 통과")
  void validTeachers() {
    String json = "[{\"n\":\"김서연\"},"
        + "{\"n\":\"박지훈\",\"r\":\"영어 · 내신\",\"c\":[\"연세대 졸업\",\"내신 12년\"],\"p\":\"https://cdn.example.com/a.jpg\"}]";
    assertThatCode(() -> TeachersDraft.validate(json)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("given 배열이 아니거나 깨진 JSON when 검증하면 then 거부")
  void rejectsNonArrayAndBrokenJson() {
    rejects("{\"n\":\"가\"}", "목록");
    rejects("[{\"n\":\"가\"", "형식");
    rejects("그냥 문자열", "형식");
    rejects("[\"가\"]", "항목 형식");
  }

  @Test
  @DisplayName("given 이름이 없거나 공백 when 검증하면 then 거부")
  void nameRequired() {
    rejects("[{\"r\":\"수학\"}]", "이름은 비워둘 수 없습니다");
    rejects("[{\"n\":\"  \"}]", "이름은 비워둘 수 없습니다");
  }

  @Test
  @DisplayName("given 9명 when 검증하면 then 거부, 8명은 통과")
  void maxTeachers() {
    StringBuilder ok = new StringBuilder("[");
    for (int i = 0; i < 8; i++) ok.append(i > 0 ? "," : "").append("{\"n\":\"강사").append(i).append("\"}");
    assertThatCode(() -> TeachersDraft.validate(ok + "]")).doesNotThrowAnyException();
    rejects(ok + ",{\"n\":\"아홉째\"}]", "최대 8명");
  }

  @Test
  @DisplayName("given 길이 한도 초과 필드 when 검증하면 then 거부")
  void fieldLengths() {
    rejects("[{\"n\":\"" + "가".repeat(TeachersDraft.MAX_NAME + 1) + "\"}]", "강사 이름");
    rejects("[{\"n\":\"가\",\"r\":\"" + "가".repeat(TeachersDraft.MAX_ROLE + 1) + "\"}]", "강사 역할");
    rejects("[{\"n\":\"가\",\"c\":[\"" + "가".repeat(TeachersDraft.MAX_CAREER + 1) + "\"]}]", "경력 한 줄");
    rejects("[{\"n\":\"가\",\"c\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]}]", "경력은 최대 5개");
  }

  @Test
  @DisplayName("given 전체 길이가 한도 초과 when 검증하면 then 거부")
  void totalLength() {
    rejects("[" + "{\"n\":\"가\"},".repeat(1000) + "{\"n\":\"가\"}]", "6000자");
  }

  @Test
  @DisplayName("given 허용되지 않은 필드나 숫자 값 when 검증하면 then 거부")
  void unknownFieldsAndTypes() {
    rejects("[{\"n\":\"가\",\"html\":\"<b>x</b>\"}]", "허용되지 않는 필드");
    rejects("[{\"n\":123}]", "문자열");
    rejects("[{\"n\":\"가\",\"c\":\"한 줄\"}]", "경력");
  }

  @Test
  @DisplayName("given 사진 주소 when 검증하면 then https만 통과하고 javascript:/data:/http:/공백/따옴표는 거부")
  void photoUrl() {
    assertThat(TeachersDraft.isAllowedPhotoUrl("https://cdn.example.com/a.jpg?x=1")).isTrue();
    for (String bad : new String[] {"javascript:alert(1)", "data:image/png;base64,AAAA", "http://ex.com/a.jpg",
        "//ex.com/a.jpg", "https://", "https://ex.com/a b.jpg", "https://ex.com/\"onerror=1", "https://ex.com/<x>"}) {
      assertThat(TeachersDraft.isAllowedPhotoUrl(bad)).as(bad).isFalse();
      rejects("[{\"n\":\"가\",\"p\":\"" + bad.replace("\"", "\\\"") + "\"}]", "https://");
    }
    assertThat(TeachersDraft.isAllowedPhotoUrl(null)).isFalse();
  }

  @Test
  @DisplayName("given HTML/스크립트가 든 이름·경력 when 검증하면 then 저장은 허용(원문 보존), 렌더 때 이스케이프된다")
  void htmlValuesAreStoredAsTextAndEscapedByRenderer() {
    String json = "[{\"n\":\"<script>alert(1)</script>\",\"c\":[\"<img src=x onerror=alert(2)>\"]}]";
    assertThatCode(() -> TeachersDraft.validate(json)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("given 섹션 제목 when 검증하면 then 60자까지 통과")
  void sectionTitle() {
    assertThatCode(() -> TeachersDraft.validateSectionTitle(null)).doesNotThrowAnyException();
    assertThatCode(() -> TeachersDraft.validateSectionTitle("가".repeat(60))).doesNotThrowAnyException();
    assertThatThrownBy(() -> TeachersDraft.validateSectionTitle("가".repeat(61)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
