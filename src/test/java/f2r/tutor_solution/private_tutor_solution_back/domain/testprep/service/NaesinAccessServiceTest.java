package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NaesinAccessServiceTest {

  @Test
  @DisplayName("given 시험 코드, when isOpenRound면 1회(-R01)만 누구나 풀 수 있는 회차다")
  void openRoundIsOnlyR01() {
    assertThat(NaesinAccessService.isOpenRound("TP-NAESIN-APM2-2602-R01")).isTrue();
    assertThat(NaesinAccessService.isOpenRound("TP-NAESIN-APM2-2602-R02")).isFalse();
    assertThat(NaesinAccessService.isOpenRound("TP-NAESIN-APM2-2602-R11")).isFalse();
    assertThat(NaesinAccessService.isOpenRound(null)).isFalse();
  }

  @Test
  @DisplayName("given 여러 표기의 휴대전화, when digits면 숫자만 남기고 +82를 0으로 바꾼다")
  void digitsNormalizesPhone() {
    assertThat(NaesinAccessService.digits("010-1234-5678")).isEqualTo("01012345678");
    assertThat(NaesinAccessService.digits("+82 10-1234-5678")).isEqualTo("01012345678");
    assertThat(NaesinAccessService.digits(null)).isEmpty();
    assertThat(NaesinAccessService.trim(" 김 민준 ")).isEqualTo("김민준");
  }

  @Test
  @DisplayName("given 재원생 앱 코드 로그인(STUDENT_APP) when 전 회차 권한을 보면 then 열린다(배정 확인은 StudentExamAppService) — 회원 조회 없이")
  void studentAppHasFullAccess() {
    // given
    UserRepository users = mock(UserRepository.class);
    EasyStudentRepository students = mock(EasyStudentRepository.class);
    NaesinAccessService service = new NaesinAccessService(users, students);
    // when
    boolean full = service.hasFullAccess(new AuthenticatedPrincipal(-7L, "app:3", "STUDENT_APP", null));
    // then
    assertThat(full).isTrue();
    verifyNoInteractions(users, students);
  }

  @Test
  @DisplayName("given 타과목 시험 코드 when 일반 회원이 2회를 풀려 하면 then 열린다 — 영어 내신 2회는 그대로 막힌다(회원 조회 없이)")
  void subjectExamsOpenToEveryMember() {
    // given
    UserRepository users = mock(UserRepository.class);
    EasyStudentRepository students = mock(EasyStudentRepository.class);
    NaesinAccessService service = new NaesinAccessService(users, students);
    AuthenticatedPrincipal member = new AuthenticatedPrincipal(9L, "member", "STUDENT", null);

    // when & then
    assertThat(service.canTake(member, "TP-NAESIN-KOR-APM2-2602-R02")).isTrue();
    assertThat(service.canTake(member, "TP-NAESIN-SCI-APM2-2602-R07")).isTrue();
    assertThat(service.canTake(member, "TP-NAESIN-APM2-2602-R01")).isTrue();
    assertThat(service.canTake(member, "TP-NAESIN-APM2-2602-R02")).isFalse();
    assertThat(NaesinAccessService.isSubjectExam("TP-NAESIN-MATH-X-R03")).isTrue();
    assertThat(NaesinAccessService.isSubjectExam("TP-NAESIN-SGM2-2602-R03")).isFalse();
    assertThat(NaesinAccessService.isSubjectExam(null)).isFalse();
  }

  @Test
  @DisplayName("given 문항 그림 폴더 이름 when isSubjectImageFolder then kor-·math-·soc-·sci-로 시작하는 폴더만 타과목이다")
  void subjectImageFolders() {
    // given & when & then
    assertThat(NaesinAccessService.isSubjectImageFolder("kor-apgujeong-m2-2026-2")).isTrue();
    assertThat(NaesinAccessService.isSubjectImageFolder("sci-m2")).isTrue();
    assertThat(NaesinAccessService.isSubjectImageFolder("apgujeong-m2-2026-2")).isFalse();
    assertThat(NaesinAccessService.isSubjectImageFolder(null)).isFalse();
  }
}
