package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.Set;

/**
 * 강사진(sec-teachers) 데이터 검증. 값은 {@code builderDraft.teachersJson}(JSON 배열 문자열)에 담긴다.
 *
 * <pre>[{"n":"이름","r":"역할/과목","c":["경력1","경력2"],"p":"https://사진"}]</pre>
 *
 * <p>회원(또는 회원을 대신한 관리자)이 입력한 값만 저장한다. 이 클래스는 값을 만들어 내지 않고 형식·길이만 검사한다.
 * 비어 있는 값은 "강사진 없음"이며 섹션은 렌더되지 않는다. HTML 이스케이프는 렌더러가 항상 한다(저장 값은 원문).
 * 순수 정적 유틸리티라 컨테이너 없이 단위 테스트할 수 있다.
 */
final class TeachersDraft {

  static final int MAX_JSON_LENGTH = 6000;
  static final int MAX_TEACHERS = 8;
  static final int MAX_NAME = 30;
  static final int MAX_ROLE = 50;
  static final int MAX_CAREERS = 5;
  static final int MAX_CAREER = 80;
  static final int MAX_PHOTO_URL = 300;
  static final int MAX_SECTION_TITLE = 60;
  private static final Set<String> ALLOWED_KEYS = Set.of("n", "r", "c", "p");
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private TeachersDraft() {}

  /** teachersJson 검증. 비어 있으면 통과(강사진 없음). 잘못된 값은 IllegalArgumentException(메시지는 사용자에게 보인다). */
  static void validate(String json) {
    if (json == null || json.isBlank()) return;
    if (json.length() > MAX_JSON_LENGTH) {
      throw new IllegalArgumentException("강사진 데이터는 " + MAX_JSON_LENGTH + "자 이하로 입력해야 합니다.");
    }
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("강사진 데이터 형식이 올바르지 않습니다.");
    }
    if (root == null || !root.isArray()) {
      throw new IllegalArgumentException("강사진 데이터는 목록(JSON 배열)이어야 합니다.");
    }
    if (root.size() > MAX_TEACHERS) {
      throw new IllegalArgumentException("강사는 최대 " + MAX_TEACHERS + "명까지 등록할 수 있습니다.");
    }
    for (JsonNode teacher : root) {
      validateTeacher(teacher);
    }
  }

  private static void validateTeacher(JsonNode teacher) {
    if (teacher == null || !teacher.isObject()) {
      throw new IllegalArgumentException("강사 항목 형식이 올바르지 않습니다.");
    }
    Iterator<String> names = teacher.fieldNames();
    while (names.hasNext()) {
      if (!ALLOWED_KEYS.contains(names.next())) {
        throw new IllegalArgumentException("강사 항목에 허용되지 않는 필드가 있습니다.");
      }
    }
    String name = text(teacher, "n", MAX_NAME, "강사 이름");
    if (name.isBlank()) {
      throw new IllegalArgumentException("강사 이름은 비워둘 수 없습니다.");
    }
    text(teacher, "r", MAX_ROLE, "강사 역할");
    JsonNode careers = teacher.get("c");
    if (careers != null && !careers.isNull()) {
      if (!careers.isArray() || careers.size() > MAX_CAREERS) {
        throw new IllegalArgumentException("강사 경력은 최대 " + MAX_CAREERS + "개까지 입력할 수 있습니다.");
      }
      for (JsonNode career : careers) {
        if (!career.isTextual() || career.asText().length() > MAX_CAREER) {
          throw new IllegalArgumentException("강사 경력 한 줄은 " + MAX_CAREER + "자 이하의 문자열이어야 합니다.");
        }
      }
    }
    String photo = text(teacher, "p", MAX_PHOTO_URL, "강사 사진 주소");
    if (!photo.isBlank() && !isAllowedPhotoUrl(photo)) {
      throw new IllegalArgumentException("강사 사진 주소는 https:// 로 시작해야 합니다.");
    }
  }

  /** 사진 주소는 https만 허용한다(javascript:/data:/http: 차단). 렌더러도 같은 규칙으로 한 번 더 거른다. */
  static boolean isAllowedPhotoUrl(String url) {
    if (url == null) return false;
    String u = url.trim();
    if (!u.regionMatches(true, 0, "https://", 0, 8) || u.length() <= 8) return false;
    return u.chars().noneMatch(c -> c <= 0x20 || c == '"' || c == '\'' || c == '<' || c == '>' || c == '`' || c == '\\');
  }

  /** 섹션 제목 검증. 길이만 본다(렌더러가 이스케이프). */
  static void validateSectionTitle(String title) {
    if (title != null && title.length() > MAX_SECTION_TITLE) {
      throw new IllegalArgumentException("강사진 섹션 제목은 " + MAX_SECTION_TITLE + "자 이하로 입력해야 합니다.");
    }
  }

  private static String text(JsonNode node, String field, int max, String label) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return "";
    if (!value.isTextual()) {
      throw new IllegalArgumentException(label + "은(는) 문자열이어야 합니다.");
    }
    String s = value.asText().trim();
    if (s.length() > max) {
      throw new IllegalArgumentException(label + "은(는) " + max + "자 이하로 입력해야 합니다.");
    }
    return s;
  }
}
