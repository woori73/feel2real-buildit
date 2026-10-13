package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyRegionRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyUpsertRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAdminAuditService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAdminManagementService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 감사 기록과 변경 작업의 트랜잭션 일관성 — 감사 기록이 실패하면 작업도 롤백된다.
 * 테스트 전체를 감싸는 트랜잭션이 없어야 실제 커밋/롤백을 볼 수 있으므로 데이터를 직접 정리한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("관리자 작업·감사 기록 트랜잭션 일관성")
class AdminAcademyAuditTransactionMySqlTest {

  @Autowired private AcademyAdminManagementService service;
  @Autowired private AcademyService academyService;
  @Autowired private UserRepository userRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private AcademyAdminAuditService audit;

  private User admin;
  private User owner;
  private AcademyResponse target;
  private JwtTokenProvider.AuthenticatedPrincipal adminPrincipal;

  @BeforeEach
  void setUp() {
    String s = UUID.randomUUID().toString().substring(0, 8);
    admin = userRepository.save(new User("txa_" + s, "pw", "관리자", "관리", null, null, Role.ADMIN));
    owner = userRepository.save(new User("txo_" + s, "pw", "원장", "원장", null, null, Role.TUTOR));
    adminPrincipal = new JwtTokenProvider.AuthenticatedPrincipal(admin.getUserId(), admin.getLoginId(), "ADMIN");
    var ownerPrincipal = new JwtTokenProvider.AuthenticatedPrincipal(owner.getUserId(), owner.getLoginId(), "TUTOR");
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(ownerPrincipal, null, List.of()));
    Subject subject = subjectRepository.findByNameKo("영어")
        .orElseGet(() -> subjectRepository.save(new Subject("ENGLISH", "영어")));
    target = academyService.createDraft(ownerPrincipal,
        new AcademyUpsertRequest("트랜잭션학원", null, "원래 소개", Set.of(subject.getSubjectId()),
            Set.of(TargetGroup.MIDDLE), List.of(new AcademyRegionRequest("서울특별시", "강남구")),
            new AcademyPageSettings(null, null, null, null, null, null, Map.of("designPreset", "ENGLISH"), null)),
        "tx-" + UUID.randomUUID());
    jdbc.update("UPDATE academies SET status='PUBLISHED' WHERE academy_id=?", target.academyId());
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    jdbc.update("DELETE FROM academy_admin_audit WHERE academy_id=?", target.academyId());
    jdbc.update("DELETE FROM academy_page_versions WHERE academy_id=?", target.academyId());
    jdbc.update("DELETE FROM academies WHERE academy_id=?", target.academyId()); // 작업본은 CASCADE
    jdbc.update("DELETE FROM users WHERE user_id IN (?, ?)", admin.getUserId(), owner.getUserId());
  }

  private String adminWorkingCopyIntro() {
    return jdbc.queryForObject(
        "SELECT intro FROM academy_admin_working_copy WHERE academy_id=?", String.class, target.academyId());
  }

  @Test
  @DisplayName("given 감사 기록 실패, when 작업본을 저장하면, then 작업도 롤백되어 작업본과 감사 기록 모두 그대로다")
  void saveRollsBackWhenAuditFails() {
    long rev = service.open(adminPrincipal, target.academyId()).state().revision();
    doThrow(new IllegalStateException("audit down")).when(audit)
        .record(any(), any(), eq(AcademyAdminAuditService.SAVE), any(), anyBoolean(), any(), any());

    assertThatThrownBy(() -> service.save(adminPrincipal, target.academyId(), rev, "저장되면 안 되는 소개", null))
        .isInstanceOf(IllegalStateException.class);

    assertThat(adminWorkingCopyIntro()).isEqualTo("원래 소개");
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_admin_audit WHERE academy_id=? AND action='SAVE'", Integer.class,
        target.academyId())).isZero();
  }

  @Test
  @DisplayName("given 감사 기록 실패, when 게시하면, then 공개본·버전 보관이 롤백되어 공개 홈페이지가 바뀌지 않는다")
  void publishRollsBackWhenAuditFails() {
    long rev = service.open(adminPrincipal, target.academyId()).state().revision();
    var saved = service.save(adminPrincipal, target.academyId(), rev, "게시될 소개", null);
    doThrow(new IllegalStateException("audit down")).when(audit)
        .record(any(), any(), eq(AcademyAdminAuditService.PUBLISH), any(), anyBoolean(), any(), any());

    assertThatThrownBy(() -> service.publish(adminPrincipal, target.academyId(), saved.revision(), false,
        target.academyId(), target.handle(), false)).isInstanceOf(IllegalStateException.class);

    assertThat(jdbc.queryForObject("SELECT intro FROM academies WHERE academy_id=?", String.class, target.academyId()))
        .isEqualTo("원래 소개");
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_page_versions WHERE academy_id=?", Integer.class, target.academyId())).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_admin_audit WHERE academy_id=? AND action='PUBLISH'", Integer.class,
        target.academyId())).isZero();
  }

  @Test
  @DisplayName("given 감사 기록 정상, when 저장하면, then 작업과 감사 기록이 함께 커밋된다")
  void saveAndAuditCommitTogether() {
    long rev = service.open(adminPrincipal, target.academyId()).state().revision();
    service.save(adminPrincipal, target.academyId(), rev, "함께 커밋될 소개", null);
    assertThat(adminWorkingCopyIntro()).isEqualTo("함께 커밋될 소개");
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_admin_audit WHERE academy_id=? AND action='SAVE'", Integer.class,
        target.academyId())).isEqualTo(1);
  }
}
