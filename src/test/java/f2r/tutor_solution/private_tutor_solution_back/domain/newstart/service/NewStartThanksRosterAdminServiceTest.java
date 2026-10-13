package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksRoster;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksRosterRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksRosterAdminService.AddResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 감사회원 명단 수동 관리 서비스: 입력 검증·증복 거부·연결된 행 삭제 금지를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class NewStartThanksRosterAdminServiceTest {

  @Mock NewStartThanksRosterRepository rosterRepository;
  @InjectMocks NewStartThanksRosterAdminService service;

  private NewStartThanksRoster row(String tag, String nickname, String prefix, boolean linked) {
    NewStartThanksRoster r = NewStartThanksRoster.builder()
        .cafeMemberTag(tag).naverIdPrefix(prefix).nickname(nickname).grade("감사멤버")
        .gradeChangedAt(LocalDate.of(2026, 10, 12)).memberLevel(150).source("MANUAL")
        .snapshotDate(LocalDate.of(2026, 10, 12)).sourceUrl(null).sourceFile(null)
        .matchStatus(linked ? NewStartThanksRoster.MATCH_MATCHED : NewStartThanksRoster.MATCH_UNMATCHED)
        .build();
    if (linked) {
      r.link(7L, "naver_abc");
    }
    return r;
  }

  @Test
  void addManual_savesWithManualDefaults() {
    when(rosterRepository.findByNickname("비비")).thenReturn(List.of());
    when(rosterRepository.existsByCafeMemberTag(any())).thenReturn(false);
    when(rosterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    AddResult r = service.addManual("비비", "sooj", LocalDate.of(2026, 10, 12));

    assertTrue(r.ok());
    ArgumentCaptor<NewStartThanksRoster> cap = ArgumentCaptor.forClass(NewStartThanksRoster.class);
    verify(rosterRepository).save(cap.capture());
    NewStartThanksRoster saved = cap.getValue();
    assertEquals("비비", saved.getNickname());
    assertEquals("sooj", saved.getNaverIdPrefix());
    assertEquals("감사멤버", saved.getGrade());
    assertEquals(150, saved.getMemberLevel());
    assertEquals("MANUAL", saved.getSource());
    assertEquals(NewStartThanksRoster.MATCH_UNMATCHED, saved.getMatchStatus());
    assertNotNull(saved.getCafeMemberTag());
    assertFalse(saved.getCafeMemberTag().isBlank());
  }

  @Test
  void addManual_rejectsDuplicateUnlinked() {
    when(rosterRepository.findByNickname("비비"))
        .thenReturn(List.of(row("tag000000001", "비비", "sooj", false)));

    AddResult r = service.addManual("비비", "sooj", LocalDate.of(2026, 10, 12));

    assertFalse(r.ok());
    verify(rosterRepository, never()).save(any());
  }

  @Test
  void addManual_rejectsBlankInput() {
    assertFalse(service.addManual(" ", "sooj", null).ok());
    assertFalse(service.addManual("비비", " ", null).ok());
    verify(rosterRepository, never()).save(any());
  }

  @Test
  void removeManual_deletesOnlyUnlinkedManual() {
    NewStartThanksRoster unlinked = row("tag000000002", "비비", "sooj", false);
    when(rosterRepository.findById(11L)).thenReturn(Optional.of(unlinked));

    assertTrue(service.removeManual(11L).ok());
    verify(rosterRepository).delete(unlinked);
  }

  @Test
  void removeManual_refusesLinkedRow() {
    NewStartThanksRoster linked = row("tag000000003", "비비", "sooj", true);
    when(rosterRepository.findById(12L)).thenReturn(Optional.of(linked));

    assertFalse(service.removeManual(12L).ok());
    verify(rosterRepository, never()).delete(any());
  }

  @Test
  void removeManual_refusesNonManualRow() {
    NewStartThanksRoster cafe = NewStartThanksRoster.builder()
        .cafeMemberTag("tag000000004").naverIdPrefix("jeon").nickname("동글동글").grade("감사멤버")
        .gradeChangedAt(LocalDate.of(2026, 10, 8)).memberLevel(150).source("NAVER_CAFE")
        .snapshotDate(LocalDate.of(2026, 10, 8)).sourceUrl(null).sourceFile(null)
        .matchStatus(NewStartThanksRoster.MATCH_UNMATCHED).build();
    when(rosterRepository.findById(13L)).thenReturn(Optional.of(cafe));

    assertFalse(service.removeManual(13L).ok());
    verify(rosterRepository, never()).delete(any());
  }
}
