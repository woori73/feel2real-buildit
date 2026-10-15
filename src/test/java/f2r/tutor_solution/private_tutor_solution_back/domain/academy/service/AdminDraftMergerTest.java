package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("관리자 작업본 ↔ 공개 내용 필드 단위 병합")
class AdminDraftMergerTest {

  private static AcademyPageSettings s(Map<String, String> draft, String blog) {
    return new AcademyPageSettings(blog, null, null, null, null, null, draft, null);
  }

  @Test
  @DisplayName("given 관리자는 Y만, 공개는 X만 바꿈, when 병합하면, then 둘 다 반영되고 충돌이 없다")
  void disjointChangesBothSurvive() {
    var base = s(Map.of("introHeading", "기준", "introMotto", "기준모토"), null);
    var mine = s(Map.of("introHeading", "기준", "introMotto", "관리자모토"), null);
    var live = s(Map.of("introHeading", "회원제목", "introMotto", "기준모토"), null);
    var r = AdminDraftMerger.merge("소개", base, "소개", mine, "소개", live, true);
    assertThat(r.settings().builderDraft()).containsEntry("introHeading", "회원제목").containsEntry("introMotto", "관리자모토");
    assertThat(r.conflicts()).isEmpty();
    assertThat(r.keptMine()).containsExactly("builderDraft.introMotto");
  }

  @Test
  @DisplayName("given 양쪽이 같은 키를 다르게 바꿈, when 병합하면, then 공개 값을 유지하고 충돌로 알린다 (조용히 덮어쓰지 않음)")
  void sameKeyConflictKeepsLive() {
    var base = s(Map.of("introHeading", "기준"), null);
    var mine = s(Map.of("introHeading", "관리자"), null);
    var live = s(Map.of("introHeading", "회원"), null);
    var r = AdminDraftMerger.merge("a", base, "a", mine, "a", live, true);
    assertThat(r.settings().builderDraft()).containsEntry("introHeading", "회원");
    assertThat(r.conflicts()).containsExactly("builderDraft.introHeading");
    assertThat(r.keptMine()).isEmpty();
  }

  @Test
  @DisplayName("given 양쪽이 같은 값으로 바꿈, when 병합하면, then 충돌이 아니다")
  void sameValueIsNotConflict() {
    var base = s(Map.of("introHeading", "기준"), null);
    var both = s(Map.of("introHeading", "동일"), null);
    var r = AdminDraftMerger.merge("a", base, "a", both, "a", both, true);
    assertThat(r.conflicts()).isEmpty();
    assertThat(r.settings().builderDraft()).containsEntry("introHeading", "동일");
  }

  @Test
  @DisplayName("given 소개와 URL 필드, when 관리자만/공개만/둘 다 바꿈, then 필드별로 같은 규칙이 적용된다")
  void introAndUrlFields() {
    var base = s(Map.of(), "https://base");
    var mine = s(Map.of(), "https://mine");
    var live = s(Map.of(), "https://base");
    var r1 = AdminDraftMerger.merge("기준", base, "관리자 소개", mine, "기준", live, true);
    assertThat(r1.intro()).isEqualTo("관리자 소개");
    assertThat(r1.settings().blogUrl()).isEqualTo("https://mine");

    var liveChanged = s(Map.of(), "https://live");
    var r2 = AdminDraftMerger.merge("기준", base, "관리자 소개", mine, "회원 소개", liveChanged, true);
    assertThat(r2.intro()).isEqualTo("회원 소개");
    assertThat(r2.settings().blogUrl()).isEqualTo("https://live");
    assertThat(r2.conflicts()).containsExactlyInAnyOrder("intro", "blogUrl");
  }

  @Test
  @DisplayName("given 관리자가 키를 추가/삭제, when 병합하면, then 추가는 유지되고 공개가 바꾼 키는 건드리지 않는다")
  void addedAndRemovedKeys() {
    var base = s(Map.of("a", "1", "b", "2"), null);
    var mine = s(Map.of("a", "1", "c", "3"), null); // b 삭제, c 추가
    var live = s(Map.of("a", "회원", "b", "2"), null); // a 변경
    var r = AdminDraftMerger.merge("x", base, "x", mine, "x", live, true);
    assertThat(r.settings().builderDraft()).containsEntry("a", "회원").containsEntry("c", "3").doesNotContainKey("b");
  }

  @Test
  @DisplayName("given 기준을 모름(null), when 병합하면, then 공개 값을 우선하고 다른 값은 충돌로 알린다")
  void unknownBaseKeepsLive() {
    var mine = s(Map.of("introHeading", "관리자"), null);
    var live = s(Map.of("introHeading", "공개"), null);
    var r = AdminDraftMerger.merge(null, null, "m", mine, "l", live, false);
    assertThat(r.settings().builderDraft()).containsEntry("introHeading", "공개");
    assertThat(r.intro()).isEqualTo("l");
    assertThat(r.conflicts()).containsExactlyInAnyOrder("intro", "builderDraft.introHeading");
  }
}
