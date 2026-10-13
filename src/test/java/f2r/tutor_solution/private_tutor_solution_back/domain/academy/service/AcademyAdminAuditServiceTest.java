package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("관리자 감사 기록 상세 계산 (순수 로직)")
class AcademyAdminAuditServiceTest {

  private static AcademyPageSettings settings(Map<String, String> draft, String blogUrl) {
    return new AcademyPageSettings(blogUrl, null, null, null, null, null, draft, null);
  }

  @Test
  @DisplayName("given 변경 없음, when 비교하면, then 소개·설정·builderDraft 변경이 모두 비어 있다")
  void noChange() {
    var s = settings(Map.of("introHeading", "A"), null);
    Map<String, Object> d = AcademyAdminAuditService.describeChange("소개", "소개", s, s);
    assertThat(d.get("introChanged")).isEqualTo(false);
    assertThat((List<?>) d.get("changedSettings")).isEmpty();
    assertThat((List<?>) d.get("builderDraftChanges")).isEmpty();
  }

  @Test
  @DisplayName("given 소개와 builderDraft 키가 바뀜, when 비교하면, then 전/후 값이 키별로 남는다")
  void recordsBeforeAfter() {
    var before = settings(Map.of("introHeading", "옛 제목", "introMotto", "그대로"), null);
    var after = settings(Map.of("introHeading", "새 제목", "introMotto", "그대로", "ctaPhone", "010"), null);
    Map<String, Object> d = AcademyAdminAuditService.describeChange("옛 소개", "새 소개", before, after);
    assertThat(d.get("introChanged")).isEqualTo(true);
    assertThat(d.get("introBefore")).isEqualTo("옛 소개");
    assertThat(d.get("introAfter")).isEqualTo("새 소개");
    @SuppressWarnings("unchecked")
    List<Map<String, String>> changes = (List<Map<String, String>>) d.get("builderDraftChanges");
    assertThat(changes.stream().map(c -> c.get("key")).toList()).containsExactly("ctaPhone", "introHeading");
    assertThat(changes.get(1).get("before")).isEqualTo("옛 제목");
    assertThat(changes.get(1).get("after")).isEqualTo("새 제목");
  }

  @Test
  @DisplayName("given URL 같은 비-builderDraft 설정이 바뀜, when 비교하면, then 필드 이름만 남고 값은 남지 않는다")
  void settingsFieldNamesOnly() {
    var before = settings(Map.of(), "https://a.example");
    var after = settings(Map.of(), "https://b.example");
    Map<String, Object> d = AcademyAdminAuditService.describeChange("x", "x", before, after);
    @SuppressWarnings("unchecked") List<String> cs = (List<String>) d.get("changedSettings");
    assertThat(cs).containsExactly("blogUrl");
    assertThat(d.toString()).doesNotContain("https://a.example").doesNotContain("https://b.example");
  }

  @Test
  @DisplayName("given 긴 값, when 기록하면, then 길이가 제한된다")
  void truncatesLongValues() {
    String longText = "가".repeat(1000);
    Map<String, Object> d = AcademyAdminAuditService.describeChange("a", longText, null, null);
    assertThat(((String) d.get("introAfter")).length()).isLessThanOrEqualTo(301);
    assertThat(AcademyAdminAuditService.truncate(null, 10)).isNull();
    assertThat(AcademyAdminAuditService.truncate("abc", 10)).isEqualTo("abc");
  }
}
