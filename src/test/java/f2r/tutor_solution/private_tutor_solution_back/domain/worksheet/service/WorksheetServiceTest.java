package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity.Worksheet;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity.WorksheetAssignment;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity.WorksheetFavorite;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity.WorksheetItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.repository.WorksheetAssignmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.repository.WorksheetFavoriteRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.repository.WorksheetItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.repository.WorksheetRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetQuestionBank.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetService.Row;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetService.SaveRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("WorksheetService — 학습지 만들기·고치기·목록 구분·휴지통·즐겨찾기")
class WorksheetServiceTest {

  static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 22, 10, 0);
  static final AuthenticatedPrincipal OWNER = new AuthenticatedPrincipal(10L, "teacher-a", "TUTOR", null);
  static final AuthenticatedPrincipal OTHER = new AuthenticatedPrincipal(20L, "teacher-b", "TUTOR", null);
  static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(1L, "admin", "ADMIN", null);

  WorksheetRepository worksheets;
  WorksheetItemRepository items;
  WorksheetFavoriteRepository favorites;
  WorksheetAssignmentRepository assignments;
  WorksheetQuestionBank bank;
  WorksheetService service;

  @BeforeEach
  void setUp() {
    worksheets = mock(WorksheetRepository.class);
    items = mock(WorksheetItemRepository.class);
    favorites = mock(WorksheetFavoriteRepository.class);
    assignments = mock(WorksheetAssignmentRepository.class);
    bank = mock(WorksheetQuestionBank.class);
    service = new WorksheetService(worksheets, items, favorites, assignments, bank, mock(UserRepository.class));
    when(worksheets.save(any())).thenAnswer(i -> {
      Worksheet w = i.getArgument(0);
      ReflectionTestUtils.setField(w, "worksheetId", 100L);
      return w;
    });
    when(bank.resolve(anyList())).thenAnswer(i -> {
      List<Question> out = new ArrayList<>();
      for (String ref : i.<List<String>>getArgument(0)) if (!ref.endsWith(":404")) out.add(question(ref));
      return out;
    });
  }

  static Question question(String ref) {
    return new Question(ref, ref.substring(0, ref.indexOf(':')), "SET", "묶음", 1, "어법", "", null, "", "", null,
        List.of(), "①", "");
  }

  private Worksheet saved(long id, long ownerId, Worksheet.Status status) {
    Worksheet w = new Worksheet(3L, ownerId, "선생님", "학습지 " + id, null, status, 2, NOW);
    ReflectionTestUtils.setField(w, "worksheetId", id);
    when(worksheets.findById(id)).thenReturn(Optional.of(w));
    return w;
  }

  @Test
  @DisplayName("given 이름과 문항 주소 when create then 학습지와 문항을 고른 순서대로 저장하고 중복 주소는 한 번만 넣는다")
  void create_savesItemsInOrder() {
    // given
    SaveRequest req = new SaveRequest(" 중2 어법 복습 ", " ", null, List.of("NAESIN:5", "GRAMMAR:1-1:3", "NAESIN:5"));

    // when
    Row row = service.create(3L, req, OWNER);

    // then
    assertThat(row.title()).isEqualTo("중2 어법 복습");
    assertThat(row.status()).isEqualTo("ACTIVE");
    assertThat(row.questionCount()).isEqualTo(2);
    assertThat(row.mine()).isTrue();
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<WorksheetItem>> captor = ArgumentCaptor.forClass(List.class);
    verify(items).saveAll(captor.capture());
    assertThat(captor.getValue()).extracting(WorksheetItem::ref).containsExactly("NAESIN:5", "GRAMMAR:1-1:3");
    assertThat(captor.getValue()).extracting(WorksheetItem::getOrderNo).containsExactly(1, 2);
    assertThat(captor.getValue().get(1).getSourceRef()).isEqualTo("1-1:3");
  }

  @Test
  @DisplayName("given 이름 없음·문항 없음·없는 문항·100문항 초과·잘못된 상태 when create then 모두 거절하고 저장하지 않는다")
  void create_rejectsInvalid() {
    // given
    List<String> tooMany = new ArrayList<>();
    for (int i = 0; i < 101; i++) tooMany.add("NAESIN:" + i);

    // when & then
    assertThatThrownBy(() -> service.create(3L, new SaveRequest(" ", null, null, List.of("NAESIN:1")), OWNER))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("이름");
    assertThatThrownBy(() -> service.create(3L, new SaveRequest("가", null, null, List.of()), OWNER))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("한 개 이상");
    assertThatThrownBy(() -> service.create(3L, new SaveRequest("가", null, null, List.of("NAESIN:404")), OWNER))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("찾을 수 없는 문항");
    assertThatThrownBy(() -> service.create(3L, new SaveRequest("가", null, null, tooMany), OWNER))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("100문항");
    assertThatThrownBy(() -> service.create(3L, new SaveRequest("가", null, "OPEN", List.of("NAESIN:1")), OWNER))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("상태");
    verify(worksheets, never()).save(any());
  }

  @Test
  @DisplayName("given 남이 만든 학습지 when update then 거절, 만든 사람·관리자는 문항을 통째로 바꿔 저장한다")
  void update_onlyOwnerOrAdmin() {
    // given
    Worksheet w = saved(100L, 10L, Worksheet.Status.ACTIVE);
    SaveRequest req = new SaveRequest("새 이름", "설명", "DRAFT", List.of("NAESIN:9"));

    // when & then
    assertThatThrownBy(() -> service.update(3L, 100L, req, OTHER)).isInstanceOf(AccessDeniedException.class);
    Row row = service.update(3L, 100L, req, ADMIN);
    assertThat(row.title()).isEqualTo("새 이름");
    assertThat(row.status()).isEqualTo("DRAFT");
    assertThat(w.getQuestionCount()).isEqualTo(1);
    verify(items).deleteAllByWorksheetId(100L);
  }

  @Test
  @DisplayName("given 다른 사업체의 학습지 번호 when 고치기·보기 then 없는 학습지로 거절")
  void otherBusiness_isNotFound() {
    // given
    saved(100L, 10L, Worksheet.Status.ACTIVE);

    // when & then
    assertThatThrownBy(() -> service.detail(4L, 100L, OWNER)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.trash(4L, 100L, OWNER)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 사용 중·숨김 학습지 when 목록 then 우리 학원에는 남의 숨김이 안 보이고, 내 학습지에는 내 것만, 즐겨찾기에는 별표한 것만")
  void list_filtersByTab() {
    // given
    Worksheet mineHidden = saved(1L, 10L, Worksheet.Status.HIDDEN);
    Worksheet othersActive = saved(2L, 20L, Worksheet.Status.ACTIVE);
    Worksheet othersHidden = saved(3L, 20L, Worksheet.Status.HIDDEN);
    when(worksheets.findAllByBusinessIdAndDeletedAtIsNullOrderByUpdatedAtDesc(3L))
        .thenReturn(List.of(mineHidden, othersActive, othersHidden));
    when(favorites.findAllByUserId(10L)).thenReturn(List.of(new WorksheetFavorite(2L, 10L, NOW)));

    // when & then
    assertThat(service.list(3L, "ACADEMY", OWNER)).extracting(Row::worksheetId).containsExactly(1L, 2L);
    assertThat(service.list(3L, "ACADEMY", ADMIN)).extracting(Row::worksheetId).containsExactly(1L, 2L, 3L);
    assertThat(service.list(3L, "MINE", OWNER)).extracting(Row::worksheetId).containsExactly(1L);
    assertThat(service.list(3L, "FAVORITE", OWNER)).extracting(Row::worksheetId).containsExactly(2L);
    assertThat(service.list(3L, "ACADEMY", OWNER).get(1).canEdit()).isFalse();
    assertThatThrownBy(() -> service.list(3L, "ALL", OWNER)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 배정이 있는 학습지 when 목록 then 상태별 배정 수를 함께 준다")
  void list_countsAssignments() {
    // given
    Worksheet w = saved(1L, 10L, Worksheet.Status.ACTIVE);
    when(worksheets.findAllByBusinessIdAndDeletedAtIsNullOrderByUpdatedAtDesc(3L)).thenReturn(List.of(w));
    WorksheetAssignment a = new WorksheetAssignment(1L, 3L, 11L, null, 1L, NOW);
    WorksheetAssignment b = new WorksheetAssignment(1L, 3L, 12L, null, 1L, NOW);
    b.changeStatus(WorksheetAssignment.Status.DONE, NOW);
    when(assignments.findAllByWorksheetIdInAndRemovedAtIsNull(List.of(1L))).thenReturn(List.of(a, b));

    // when
    Row row = service.list(3L, "ACADEMY", OWNER).get(0);

    // then
    assertThat(row.assigned().total()).isEqualTo(2);
    assertThat(row.assigned().notStarted()).isEqualTo(1);
    assertThat(row.assigned().done()).isEqualTo(1);
  }

  @Test
  @DisplayName("given 학습지 when 휴지통으로 → 복구 then 지운 시각이 생겼다 사라지고, 휴지통 목록은 만든 사람·관리자에게만 보인다")
  void trashAndRestore() {
    // given
    Worksheet w = saved(100L, 10L, Worksheet.Status.ACTIVE);
    when(worksheets.findAllByBusinessIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(3L)).thenReturn(List.of(w));

    // when
    service.trash(3L, 100L, OWNER);

    // then
    assertThat(w.isDeleted()).isTrue();
    assertThat(w.getDeletedBy()).isEqualTo(10L);
    assertThat(service.list(3L, "TRASH", OWNER)).hasSize(1);
    assertThat(service.list(3L, "TRASH", OTHER)).isEmpty();
    assertThatThrownBy(() -> service.update(3L, 100L, new SaveRequest("가", null, null, List.of("NAESIN:1")), OWNER))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("복구");
    assertThatThrownBy(() -> service.restore(3L, 100L, OTHER)).isInstanceOf(AccessDeniedException.class);
    service.restore(3L, 100L, OWNER);
    assertThat(w.isDeleted()).isFalse();
  }

  @Test
  @DisplayName("given 휴지통에 없는 학습지·배정이 남은 학습지 when 영구 삭제 then 거절, 조건이 맞으면 문항·즐겨찾기까지 지운다")
  void purge_requiresTrashAndNoAssignments() {
    // given
    Worksheet w = saved(100L, 10L, Worksheet.Status.ACTIVE);

    // when & then
    assertThatThrownBy(() -> service.purge(3L, 100L, OWNER)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("휴지통");
    service.trash(3L, 100L, OWNER);
    when(assignments.findAllByWorksheetIdInAndRemovedAtIsNull(List.of(100L)))
        .thenReturn(List.of(new WorksheetAssignment(100L, 3L, 11L, null, 1L, NOW)));
    assertThatThrownBy(() -> service.purge(3L, 100L, OWNER)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("배정");
    verify(worksheets, never()).delete(any());
    when(assignments.findAllByWorksheetIdInAndRemovedAtIsNull(List.of(100L))).thenReturn(List.of());
    service.purge(3L, 100L, OWNER);
    verify(items).deleteAllByWorksheetId(100L);
    verify(favorites).deleteAllByWorksheetId(100L);
    verify(worksheets).delete(w);
  }

  @Test
  @DisplayName("given 즐겨찾기 없는 학습지 when 켜기 → 끄기 then 한 줄 생겼다 지워지고, 남의 숨김 학습지는 거절")
  void favorite_toggles() {
    // given
    saved(100L, 20L, Worksheet.Status.ACTIVE);
    saved(101L, 20L, Worksheet.Status.HIDDEN);
    when(favorites.findByWorksheetIdAndUserId(100L, 10L)).thenReturn(Optional.empty());

    // when & then
    assertThat(service.favorite(3L, 100L, true, OWNER)).isTrue();
    verify(favorites).save(any(WorksheetFavorite.class));
    WorksheetFavorite existing = new WorksheetFavorite(100L, 10L, NOW);
    when(favorites.findByWorksheetIdAndUserId(100L, 10L)).thenReturn(Optional.of(existing));
    assertThat(service.favorite(3L, 100L, false, OWNER)).isFalse();
    verify(favorites).delete(existing);
    assertThatThrownBy(() -> service.favorite(3L, 101L, true, OWNER)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("given 문항 3개 중 1개가 출처에서 없어진 학습지 when detail then 남은 문항과 빠진 수를 준다")
  void detail_reportsMissing() {
    // given
    saved(100L, 10L, Worksheet.Status.ACTIVE);
    when(items.findAllByWorksheetIdOrderByOrderNoAsc(100L)).thenReturn(List.of(
        new WorksheetItem(100L, 1, "NAESIN", "1"), new WorksheetItem(100L, 2, "NAESIN", "404"),
        new WorksheetItem(100L, 3, "GRAMMAR", "1-1:3")));

    // when
    var detail = service.detail(3L, 100L, OTHER);

    // then
    assertThat(detail.questions()).extracting(Question::ref).containsExactly("NAESIN:1", "GRAMMAR:1-1:3");
    assertThat(detail.missingCount()).isEqualTo(1);
  }
}
