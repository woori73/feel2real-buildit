package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** DB 없이 판정하는 삭제 금지·폐지 후보 규칙. */
class StudioCleanupServiceTest {

  private Map<String, Object> row(String id, String key) {
    var r = new HashMap<String, Object>();
    r.put("id", id);
    r.put("item_key", key);
    r.put("origin", "AI_PROPOSAL");
    r.put("kind", "STORYBOARD");
    r.put("work_status", "REVIEW");
    r.put("head_id", "head");
    r.put("approved_id", null);
    return r;
  }

  @Test
  void givenHeadRevision_whenProtectionChecked_thenBlockedAsHead() {
    // given
    var r = row("head", "EP01_V6_GPT_S11R11_05_end");
    // when
    String reason = StudioCleanupService.protection(r);
    // then
    assertEquals("최신본(head)", reason);
  }

  @Test
  void givenApprovedOlderRevision_whenProtectionChecked_thenBlockedAsApproved() {
    // given
    var r = row("old", "EP01_V5_GPT_HF_C_first");
    r.put("approved_id", "old");
    // when
    String reason = StudioCleanupService.protection(r);
    // then
    assertEquals("승인본", reason);
  }

  @Test
  void givenSourceOrOriginalRevision_whenProtectionChecked_thenBlockedAsOriginal() {
    // given
    var source = row("old", "source-09d62dee-abc");
    var original = row("old", "EP01_V6_AGENT_NOTES");
    original.put("origin", "ORIGINAL");
    // when / then
    assertEquals("원본", StudioCleanupService.protection(source));
    assertEquals("원본", StudioCleanupService.protection(original));
  }

  @Test
  void givenJobRequest_whenInProgressOrDone_thenOnlyInProgressBlocked() {
    // given
    var active = row("old", "EP01_V6_GPT_REQUEST_X");
    active.put("kind", "JOB");
    active.put("work_status", "WORKING");
    var done = row("old", "EP01_V6_GPT_REQUEST_X");
    done.put("kind", "JOB");
    done.put("work_status", "READY");
    // when / then
    assertEquals("진행 중인 JOB 요청서", StudioCleanupService.protection(active));
    assertNull(StudioCleanupService.protection(done));
  }

  @Test
  void givenCurrentReferenceMaterials_whenProtectionChecked_thenAllBlocked() {
    // given
    var keys =
        List.of(
            "EP01_PRODUCTION_PRINCIPLES_EMOTION",
            "EP01_V6_S11R11_CONTI",
            "EP01_V6_S11R11_BLOCKING_MAP",
            "EP01_V6_GPT_S11R11_KID_FACE_SHEET",
            "EP01_V6_S11R2_REF_BIKE_SIDE",
            "EP01_V6_GPT_S11R11_SUNI_INJURED_REF",
            "EP01_V6_S11R10_HANDOFF",
            "EP01_V5_PRODUCTION_LOG");
    // when / then
    for (String key : keys)
      assertEquals("현재 쓰는 기준 자료", StudioCleanupService.protection(row("old", key)), key);
  }

  @Test
  void givenOrdinaryOlderRevision_whenProtectionChecked_thenNotBlocked() {
    // given
    var r = row("old", "EP01_V6_GPT_S11R11_07_end");
    // when / then
    assertNull(StudioCleanupService.protection(r));
  }

  @Test
  void givenOlderRevisionWithNeedsChangesOrRejectOrFailed_whenCandidateChecked_thenProposed() {
    // given
    var ai = row("old", "EP01_V6_GPT_S11R11_07_end");
    ai.put("ai_verdict", "NEEDS_CHANGES");
    var human = row("old", "EP01_V6_GPT_S11R11_07_end");
    human.put("human_review", "REJECT");
    var failed = row("old", "EP01_V6_GPT_S11R11_07_end");
    failed.put("work_status", "FAILED");
    // when / then
    assertTrue(StudioCleanupService.candidateReason(ai).contains("NEEDS_CHANGES"));
    assertTrue(StudioCleanupService.candidateReason(human).contains("반려"));
    assertTrue(StudioCleanupService.candidateReason(failed).contains("FAILED"));
  }

  @Test
  void givenHeadWithNeedsChanges_whenCandidateChecked_thenNotProposedByReviewRule() {
    // given — 최신본은 검수 결과와 관계없이 '앞선 버전' 후보가 아니다.
    var head = row("head", "EP01_V6_GPT_S11R11_07_end");
    head.put("ai_verdict", "NEEDS_CHANGES");
    // when / then
    assertNull(StudioCleanupService.candidateReason(head));
  }

  @Test
  void givenS11WorkIds_whenTrialPatternChecked_thenOnlyR1ToR10Match() {
    // given
    var trials =
        List.of("EP01_V6_S11R10_LTX_X1", "EP01_V6_GPT_S11R10_X5_end", "EP01_V6_S11_R9", "EP01_V6_GPT_S11R2_MASTER_A1");
    var current = List.of("EP01_V6_GPT_S11R11_05_end", "EP01_V6_S11R11_CONTI", "EP01_V6_GPT_S11R12_01_start");
    // when / then
    for (String key : trials) assertNotNull(StudioCleanupService.candidateReason(row("head", key)), key);
    for (String key : current) assertNull(StudioCleanupService.candidateReason(row("head", key)), key);
  }
}
