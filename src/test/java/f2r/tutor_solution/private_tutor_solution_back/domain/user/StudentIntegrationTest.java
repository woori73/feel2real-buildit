package f2r.tutor_solution.private_tutor_solution_back.domain.user;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;


import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("Student 통합 테스트")
class StudentIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired EntityManager em;

    @Test
    @Transactional
    @DisplayName("Student 저장 - 독자 PK 생성 및 User FK 연결")
    void saveAndFind() {
        // given
        User user = userRepository.save(User.builder()
            .loginId("std_save01")
            .password("encoded_pw")
            .name("학생A")
            .role(Role.STUDENT)
            .build());

        Student student = Student.builder()
            .user(user)
            .schoolName("서울중학교")
            .grade((byte) 2)
            .build();

        // when
        Student saved = studentRepository.save(student);
        em.flush();
        em.clear();

        // then
        Student found = studentRepository.findById(saved.getStudentId()).orElseThrow();
        assertThat(found.getStudentId()).isNotNull();
        assertThat(found.getStudentId()).isNotEqualTo(user.getUserId());
        assertThat(found.getUser().getUserId()).isEqualTo(user.getUserId());
        assertThat(found.getSchoolName()).isEqualTo("서울중학교");
        assertThat(found.getGrade()).isEqualTo((byte) 2);
    }

    @Test
    @Transactional
    @DisplayName("Student - parentName/parentPhone 없이도 저장 가능 (nullable)")
    void saveWithoutParent() {
        // given
        User user = userRepository.save(User.builder()
            .loginId("std_noparent01")
            .password("encoded_pw")
            .name("학생B")
            .role(Role.STUDENT)
            .build());

        Student student = Student.builder()
            .user(user)
            .schoolName("부산초등학교")
            .grade((byte) 5)
            .build();

        // when & then
        assertThatCode(() -> {
            studentRepository.save(student);
            em.flush();
        }).doesNotThrowAnyException();
    }

    @Test
    @Transactional
    @DisplayName("Student - parentName/parentPhone 저장 및 조회")
    void saveWithParentInfo() {
        // given
        User studentUser = userRepository.save(User.builder()
            .loginId("std_withparent01")
            .password("encoded_pw")
            .name("학생C")
            .role(Role.STUDENT)
            .build());

        Student student = Student.builder()
            .user(studentUser)
            .schoolName("광주고등학교")
            .grade((byte) 1)
            .parentName("홍부모")
            .parentPhone("01098765432")
            .build();

        // when
        Student saved = studentRepository.save(student);
        em.flush();
        em.clear();

        // then
        Student found = studentRepository.findById(saved.getStudentId()).orElseThrow();
        assertThat(found.getParentName()).isEqualTo("홍부모");
        assertThat(found.getParentPhone()).isEqualTo("01098765432");
    }

    @Test
    @Transactional
    @DisplayName("updateSchoolInfo - 학교/학년 변경")
    void updateSchoolInfo() {
        // given
        User user = userRepository.save(User.builder()
            .loginId("std_school01")
            .password("encoded_pw")
            .name("학생D")
            .role(Role.STUDENT)
            .build());

        Student student = studentRepository.save(Student.builder()
            .user(user)
            .schoolName("인천중학교")
            .grade((byte) 1)
            .build());

        // when
        student.updateSchoolInfo("대전고등학교", (byte) 2);
        em.flush();
        em.clear();

        // then
        Student found = studentRepository.findById(student.getStudentId()).orElseThrow();
        assertThat(found.getSchoolName()).isEqualTo("대전고등학교");
        assertThat(found.getGrade()).isEqualTo((byte) 2);
    }

    @Test
    @Transactional
    @DisplayName("updateParentInfo - 학부모 정보 변경")
    void updateParentInfo() {
        // given
        User studentUser = userRepository.save(User.builder()
            .loginId("std_parent_chg01")
            .password("encoded_pw")
            .name("학생E")
            .role(Role.STUDENT)
            .build());

        Student student = studentRepository.save(Student.builder()
            .user(studentUser)
            .schoolName("서울초등학교")
            .grade((byte) 3)
            .parentName("기존부모")
            .parentPhone("01011112222")
            .build());

        // when
        student.updateParentInfo("새부모", "01033334444");
        em.flush();
        em.clear();

        // then
        Student found = studentRepository.findById(student.getStudentId()).orElseThrow();
        assertThat(found.getParentName()).isEqualTo("새부모");
        assertThat(found.getParentPhone()).isEqualTo("01033334444");
    }
}
