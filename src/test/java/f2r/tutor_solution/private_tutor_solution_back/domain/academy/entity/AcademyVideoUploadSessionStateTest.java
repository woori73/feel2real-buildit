package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AcademyVideoUploadSession — abort versus acceptance terminal barrier")
class AcademyVideoUploadSessionStateTest {

  @Test
  @DisplayName("given abort wins while COMPLETING when late acceptance arrives then acceptance is impossible")
  void abort_first_is_terminal() {
    // given
    AcademyVideoUploadSession session = initiated();
    LocalDateTime now = LocalDateTime.now();
    session.beginFinalization("55555555-5555-5555-5555-555555555555", "[]", "hash", now);

    // when
    boolean won = session.abort(now.plusSeconds(1));

    // then
    assertThat(won).isTrue();
    assertThat(session.getState()).isEqualTo(VideoUploadSessionState.ABORTED);
    assertThat(session.getFinalizationToken()).isNull();
  }

  @Test
  @DisplayName("given acceptance wins when abort arrives then accepted video remains stable")
  void acceptance_first_wins() {
    // given
    AcademyVideoUploadSession session = initiated();
    AcademyVideo video = AcademyVideo.readyRaw(
        session.getAcademy(), "raw.webm", "staging/key", "video/webm",
        10, 1_000, 10, 10);
    ReflectionTestUtils.setField(video, "videoId", 7L);
    LocalDateTime now = LocalDateTime.now();
    session.beginFinalization("55555555-5555-5555-5555-555555555555", "[]", "hash", now);
    session.accept(video, now);

    // when
    boolean changed = session.abort(now.plusSeconds(1));

    // then
    assertThat(changed).isFalse();
    assertThat(session.getState()).isEqualTo(VideoUploadSessionState.ACCEPTED);
    assertThat(session.getAcceptedVideo().getVideoId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("given an active recovery lease when another owner claims then the loser is fenced")
  void active_recovery_lease_fences_other_owner() {
    // given
    AcademyVideoUploadSession session = initiated();
    LocalDateTime now = LocalDateTime.now();
    session.beginFinalization("55555555-5555-5555-5555-555555555555", "[]", "hash",
        now.minusSeconds(3));

    // when
    boolean firstClaim = session.claimRecovery("worker-a", now, now.plusSeconds(15));
    boolean competingClaim = session.claimRecovery(
        "worker-b", now.plusSeconds(1), now.plusSeconds(16));

    // then
    assertThat(firstClaim).isTrue();
    assertThat(competingClaim).isFalse();
    assertThat(session.isRecoveryOwnedBy("worker-a", now.plusSeconds(1))).isTrue();
    assertThat(session.isRecoveryOwnedBy("worker-b", now.plusSeconds(1))).isFalse();
  }

  @Test
  @DisplayName("given a recovery heartbeat when lease is current then only its owner can renew")
  void recovery_heartbeat_is_owner_fenced() {
    // given
    AcademyVideoUploadSession session = initiated();
    LocalDateTime now = LocalDateTime.now();
    session.beginFinalization("55555555-5555-5555-5555-555555555555", "[]", "hash",
        now.minusSeconds(3));
    assertThat(session.claimRecovery("worker-a", now, now.plusSeconds(15))).isTrue();

    // when / then
    assertThat(session.renewRecoveryLease("worker-b", now.plusSeconds(1), now.plusSeconds(16)))
        .isFalse();
    assertThat(session.renewRecoveryLease("worker-a", now.plusSeconds(1), now.plusSeconds(16)))
        .isTrue();
    assertThat(session.isRecoveryOwnedBy("worker-a", now.plusSeconds(15))).isTrue();
  }

  @Test
  @DisplayName("given an expired recovery lease when a new owner claims then old owner cannot mutate")
  void expired_recovery_lease_can_be_reclaimed() {
    // given
    AcademyVideoUploadSession session = initiated();
    LocalDateTime now = LocalDateTime.now();
    session.beginFinalization("55555555-5555-5555-5555-555555555555", "[]", "hash",
        now.minusSeconds(3));
    assertThat(session.claimRecovery("worker-a", now, now.plusSeconds(2))).isTrue();

    // when
    boolean reclaimed = session.claimRecovery(
        "worker-b", now.plusSeconds(3), now.plusSeconds(18));

    // then
    assertThat(reclaimed).isTrue();
    assertThat(session.isRecoveryOwnedBy("worker-a", now.plusSeconds(3))).isFalse();
    assertThat(session.isRecoveryOwnedBy("worker-b", now.plusSeconds(3))).isTrue();
  }

  private AcademyVideoUploadSession initiated() {
    Academy academy = Academy.builder()
        .tutor(f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User.builder()
            .loginId("t").password("p").name("n").phone("010").build())
        .slug("academy").name("academy").logoStorageKey("logo").intro("intro").build();
    ReflectionTestUtils.setField(academy, "academyId", 1L);
    return AcademyVideoUploadSession.builder()
        .sessionId("11111111-1111-1111-1111-111111111111")
        .academy(academy)
        .requestedFilename("raw.webm")
        .declaredSizeBytes(10)
        .state(VideoUploadSessionState.INITIATED)
        .stagingKey("staging/11111111-1111-1111-1111-111111111111/raw")
        .multipartUploadId("22222222-2222-2222-2222-222222222222")
        .partSizeBytes(20L * 1024 * 1024)
        .expectedPartCount(1)
        .deadlineAt(LocalDateTime.now().plusMinutes(3))
        .build();
  }
}
