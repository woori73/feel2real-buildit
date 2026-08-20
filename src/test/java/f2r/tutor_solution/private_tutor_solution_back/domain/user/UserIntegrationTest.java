package f2r.tutor_solution.private_tutor_solution_back.domain.user;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("User 통합 테스트")
class UserIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired EntityManager em;

    @Test
    @Transactional
    @DisplayName("User 저장 및 조회")
    void saveAndFind() {
        // given
        User user = User.builder()
            .loginId("tutor_save01")
            .password("encoded_pw")
            .name("홍길동")
            .phone("010-1234-5678")
            .role(Role.ADMIN)
            .build();

        // when
        User saved = userRepository.save(user);
        em.flush();
        em.clear();

        // then
        User found = userRepository.findById(saved.getUserId()).orElseThrow();
        assertThat(found.getLoginId()).isEqualTo("tutor_save01");
        assertThat(found.getName()).isEqualTo("홍길동");
        assertThat(found.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @Transactional
    @DisplayName("loginId 중복 저장 시 DataIntegrityViolationException 발생")
    void duplicateLoginId() {
        // given
        userRepository.save(User.builder()
            .loginId("dup_login01")
            .password("encoded_pw")
            .name("홍길동")
            .role(Role.ADMIN)
            .build());
        em.flush();

        // when & then
        assertThatThrownBy(() -> {
            userRepository.save(User.builder()
                .loginId("dup_login01")
                .password("encoded_pw")
                .name("이순신")
                .role(Role.STUDENT)
                .build());
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("Auditing - createdAt / updatedAt 자동 세팅")
    void auditingFields() {
        // given
        User user = User.builder()
            .loginId("audit_user01")
            .password("encoded_pw")
            .name("감사자")
            .role(Role.ADMIN)
            .build();

        // when
        userRepository.save(user);
        em.flush();
        em.clear();

        // then
        User found = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("findByLoginId 정상 조회")
    void findByLoginId() {
        // given
        userRepository.save(User.builder()
            .loginId("find_by_login01")
            .password("encoded_pw")
            .name("조회자")
            .role(Role.ADMIN)
            .build());
        em.flush();
        em.clear();

        // when
        Optional<User> result = userRepository.findByLoginId("find_by_login01");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("조회자");
    }

    @Test
    @Transactional
    @DisplayName("existsByLoginId - 존재하는 loginId 확인")
    void existsByLoginId() {
        // given
        userRepository.save(User.builder()
            .loginId("exists_login01")
            .password("encoded_pw")
            .name("존재자")
            .role(Role.STUDENT)
            .build());
        em.flush();

        // when & then
        assertThat(userRepository.existsByLoginId("exists_login01")).isTrue();
        assertThat(userRepository.existsByLoginId("nonexistent_id")).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("updatePassword - 비밀번호 변경")
    void updatePassword() {
        // given
        User user = userRepository.save(User.builder()
            .loginId("pw_update01")
            .password("old_password")
            .name("비밀번호변경자")
            .role(Role.ADMIN)
            .build());

        // when
        user.updatePassword("new_password");
        em.flush();
        em.clear();

        // then
        User found = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(found.getPassword()).isEqualTo("new_password");
    }

    @Test
    @Transactional
    @DisplayName("updatePhone - 전화번호 변경")
    void updatePhone() {
        // given
        User user = userRepository.save(User.builder()
            .loginId("phone_update01")
            .password("encoded_pw")
            .name("전화번호변경자")
            .phone("010-0000-0000")
            .role(Role.ADMIN)
            .build());

        // when
        user.updatePhone("010-9999-9999");
        em.flush();
        em.clear();

        // then
        User found = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(found.getPhone()).isEqualTo("010-9999-9999");
    }

    @Test
    @Transactional
    @DisplayName("Auditing - created_by에 loginId 자동 세팅")
    void auditingCreatedBy() {
        // given
        String loginId = "audit_createdby01";
        AuditorContextHolder.set(loginId);
        User user = User.builder()
            .loginId(loginId)
            .password("encoded_pw")
            .name("감사자")
            .role(Role.ADMIN)
            .build();

        // when
        userRepository.save(user);
        em.flush();
        em.clear();
        AuditorContextHolder.clear();

        // then
        User found = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(found.getCreatedBy()).isEqualTo(loginId);
    }

    @Test
    @Transactional
    @DisplayName("loginId null이면 저장 실패")
    void loginIdNotNull() {
        // given
        User user = User.builder()
            .password("encoded_pw")
            .name("홍길동")
            .role(Role.ADMIN)
            .build();

        // when & then
        assertThatThrownBy(() -> {
            userRepository.save(user);
            em.flush();
        }).isInstanceOf(Exception.class);
    }
}
