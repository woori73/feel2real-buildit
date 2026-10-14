package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.AcademyEasyBusinessLink;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.AcademyEasyBusinessLinkRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EasyTutorAccessServiceTest {

  @Mock AuthenticatedUserResolver authResolver;
  @Mock AcademyRepository academyRepository;
  @Mock EasyBusinessRepository businessRepository;
  @Mock AcademyEasyBusinessLinkRepository linkRepository;

  private EasyTutorAccessService service;
  private JwtTokenProvider.AuthenticatedPrincipal principal;
  private Academy academy;
  private EasyBusiness academyBusiness;
  private EasyBusiness otherBusiness;

  @BeforeEach
  void setUp() {
    service = new EasyTutorAccessService(
        authResolver, academyRepository, businessRepository, linkRepository);
    User tutor = User.builder()
        .loginId("owner-a")
        .password("encoded")
        .name("A 원장")
        .role(Role.TUTOR)
        .build();
    ReflectionTestUtils.setField(tutor, "userId", 10L);
    academy = Academy.builder()
        .tutor(tutor)
        .slug("academy-a")
        .name("A 학원")
        .logoStorageKey("logo")
        .intro("intro")
        .status(AcademyStatus.PUBLISHED)
        .visible(true)
        .build();
    ReflectionTestUtils.setField(academy, "academyId", 100L);
    academyBusiness = EasyBusiness.builder()
        .code("ACADEMY_A").name("A 학원 장부").active(true).sortOrder(1).build();
    ReflectionTestUtils.setField(academyBusiness, "businessId", 1000L);
    otherBusiness = EasyBusiness.builder()
        .code("ACADEMY_B").name("B 학원 장부").active(true).sortOrder(2).build();
    ReflectionTestUtils.setField(otherBusiness, "businessId", 2000L);
    principal = new JwtTokenProvider.AuthenticatedPrincipal(10L, "owner-a", "TUTOR");
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
  }

  @Test
  @DisplayName("A 원장은 서버 연결표에 있는 A 학원 장부에 접근한다")
  void ownerAccessesLinkedBusiness() {
    when(linkRepository.findByAcademy_AcademyIdAndBusiness_BusinessId(100L, 1000L))
        .thenReturn(Optional.of(new AcademyEasyBusinessLink(academy, academyBusiness)));

    assertThat(service.requireAllowedBusiness(principal, 1000L)).isSameAs(academyBusiness);
  }

  @Test
  @DisplayName("A 원장은 businessId를 바꿔 B 학원 장부에 접근할 수 없다")
  void ownerCannotBypassIsolationWithAnotherBusinessId() {
    when(linkRepository.findByAcademy_AcademyIdAndBusiness_BusinessId(100L, 2000L))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireAllowedBusiness(principal, 2000L))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("미연결 원장은 접근할 수 없다")
  void unlinkedOwnerDenied() {
    when(linkRepository.findByAcademy_AcademyIdAndBusiness_BusinessId(100L, 1000L))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireAllowedBusiness(principal, 1000L))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("비활성 학원은 연결이 있어도 접근할 수 없다")
  void inactiveAcademyDenied() {
    Academy inactive = Academy.builder()
        .tutor(academy.getTutor()).slug("inactive").name("비활성")
        .logoStorageKey("logo").intro("intro")
        .status(AcademyStatus.DRAFT).visible(false).build();
    ReflectionTestUtils.setField(inactive, "academyId", 100L);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(inactive));

    assertThatThrownBy(() -> service.requireAllowedBusiness(principal, 1000L))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("비활성 사업체는 연결이 있어도 접근할 수 없다")
  void inactiveBusinessDenied() {
    EasyBusiness inactive = EasyBusiness.builder()
        .code("INACTIVE").name("비활성 장부").active(false).sortOrder(3).build();
    ReflectionTestUtils.setField(inactive, "businessId", 3000L);
    when(linkRepository.findByAcademy_AcademyIdAndBusiness_BusinessId(100L, 3000L))
        .thenReturn(Optional.of(new AcademyEasyBusinessLink(academy, inactive)));

    assertThatThrownBy(() -> service.requireAllowedBusiness(principal, 3000L))
        .isInstanceOf(AccessDeniedException.class);
  }
}
