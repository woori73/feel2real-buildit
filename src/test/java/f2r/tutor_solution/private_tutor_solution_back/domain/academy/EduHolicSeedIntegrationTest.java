package f2r.tutor_solution.private_tutor_solution_back.domain.academy;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.HandlePolicy;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V928 시드 검증. Flyway가 실제로 적용된 상태를 확인한다.
 */
@SpringBootTest
@Transactional
@DisplayName("edu-holic 시드 통합 테스트")
class EduHolicSeedIntegrationTest {

  @Autowired
  private AcademyRepository academyRepository;

  @Autowired
  private UserRepository userRepository;

  /** V928이 시드한 handle. 전용 템플릿 분기가 사라진 뒤로는 일반 Academy와 동일하게 서빙된다. */
  private static final String SEEDED_HANDLE = "edu-holic";

  @Test
  void v928_seedsTutorAccount() {
    // given: V928이 적용된 DB

    // when
    Optional<User> tutor = userRepository.findByLoginId("edu-holic");

    // then: nickname NOT NULL(V920) 제약을 만족하며 TUTOR 역할이어야 한다
    assertThat(tutor).isPresent();
    assertThat(tutor.get().getRole()).isEqualTo(Role.TUTOR);
    assertThat(tutor.get().getNickname()).isNotBlank();
  }

  @Test
  void v928_seedsAcademyWithHandle() {
    // given: V928이 적용된 DB

    // when
    Optional<Academy> academy = academyRepository.findByHandle(SEEDED_HANDLE);

    // then: 서브도메인 라우팅(handle)과 공개 노출(status/visible) 조건을 모두 만족해야 한다
    assertThat(academy).isPresent();
    assertThat(academy.get().getStatus()).isEqualTo(AcademyStatus.PUBLISHED);
    assertThat(academy.get().getSlug()).isEqualTo("edu-holic-academy");
    assertThat(academy.get().getTutor().getLoginId()).isEqualTo("edu-holic");
  }

  @Test
  void seededHandleSatisfiesHandlePolicy() {
    // given: 시드된 handle
    Optional<Academy> academy = academyRepository.findByHandle(SEEDED_HANDLE);
    assertThat(academy).isPresent();

    // when
    boolean acceptable = HandlePolicy.isAcceptable(academy.get().getHandle());

    // then: 정책을 어기면 서브도메인이 성립하지 않는다
    assertThat(acceptable).isTrue();
  }
}
