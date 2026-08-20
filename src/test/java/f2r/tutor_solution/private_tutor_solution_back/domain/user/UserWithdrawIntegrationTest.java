package f2r.tutor_solution.private_tutor_solution_back.domain.user;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity.LearningMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity.LearningMaterialAnswer;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.repository.LearningMaterialAnswerRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.repository.LearningMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ContentKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request.WithdrawRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("회원탈퇴 통합 테스트")
class UserWithdrawIntegrationTest {

  @Autowired UserService userService;
  @Autowired UserRepository userRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired SourceRepository sourceRepository;
  @Autowired ConversationRepository conversationRepository;
  @Autowired LearningMaterialRepository learningMaterialRepository;
  @Autowired LearningMaterialAnswerRepository learningMaterialAnswerRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired EntityManager em;

  @Test
  @DisplayName("STUDENT 탈퇴는 학생/자료/학습자료 연결을 FK 정책으로 삭제한다")
  void withdraw_student_cascades_owned_data() {
    // given
    String suffix = String.valueOf(System.nanoTime());
    User user = userRepository.save(User.builder()
        .loginId("withdraw-student-" + suffix)
        .password(passwordEncoder.encode("pw-1234"))
        .name("탈퇴학생")
        .role(Role.STUDENT)
        .build());
    Student student = studentRepository.save(Student.builder()
        .user(user)
        .schoolName("탈퇴중")
        .grade((byte) 1)
        .build());
    Source problem = sourceRepository.save(source(user, "problem-" + suffix + ".pdf", SourceType.HOMEWORK_PROBLEM));
    Source answer = sourceRepository.save(source(user, "answer-" + suffix + ".pdf", SourceType.HOMEWORK_ANSWER));
    Conversation conversation = conversationRepository.save(Conversation.builder()
        .student(student)
        .title("탈퇴 테스트")
        .metadata("{}")
        .build());
    LearningMaterial material = learningMaterialRepository.save(LearningMaterial.builder()
        .student(student)
        .conversation(conversation)
        .problemSource(problem)
        .assignedBy(user)
        .title("탈퇴 과제")
        .build());
    LearningMaterialAnswer materialAnswer = learningMaterialAnswerRepository.save(LearningMaterialAnswer.builder()
        .material(material)
        .source(answer)
        .orderNum(1)
        .build());
    learningMaterialAnswerRepository.flush();
    em.clear();

    // when
    userService.withdraw(user.getUserId(), new WithdrawRequest("pw-1234"));
    em.clear();

    // then
    assertThat(userRepository.findById(user.getUserId())).isEmpty();
    assertThat(studentRepository.findById(student.getStudentId())).isEmpty();
    assertThat(sourceRepository.findById(problem.getSourceId())).isEmpty();
    assertThat(sourceRepository.findById(answer.getSourceId())).isEmpty();
    assertThat(conversationRepository.findById(conversation.getConversationId())).isEmpty();
    assertThat(learningMaterialRepository.findById(material.getMaterialId())).isEmpty();
    assertThat(learningMaterialAnswerRepository.findById(materialAnswer.getAnswerId())).isEmpty();
  }

  @Test
  @DisplayName("TUTOR 탈퇴는 담당 학습자료의 assignedBy만 NULL로 남긴다")
  void withdraw_tutor_nulls_learning_material_assignee() {
    // given
    String suffix = String.valueOf(System.nanoTime());
    User tutor = userRepository.save(User.builder()
        .loginId("withdraw-tutor-" + suffix)
        .password(passwordEncoder.encode("pw-1234"))
        .name("탈퇴강사")
        .role(Role.TUTOR)
        .build());
    User studentUser = userRepository.save(User.builder()
        .loginId("withdraw-tutor-student-" + suffix)
        .password(passwordEncoder.encode("pw-1234"))
        .name("보존학생")
        .role(Role.STUDENT)
        .build());
    Student student = studentRepository.save(Student.builder()
        .user(studentUser)
        .schoolName("보존중")
        .grade((byte) 2)
        .build());
    Source problem = sourceRepository.save(source(studentUser, "problem-" + suffix + ".pdf", SourceType.HOMEWORK_PROBLEM));
    Conversation conversation = conversationRepository.save(Conversation.builder()
        .student(student)
        .title("강사 탈퇴 테스트")
        .metadata("{}")
        .build());
    LearningMaterial material = learningMaterialRepository.save(LearningMaterial.builder()
        .student(student)
        .conversation(conversation)
        .problemSource(problem)
        .assignedBy(tutor)
        .title("보존 과제")
        .build());
    learningMaterialRepository.flush();
    em.clear();

    // when
    userService.withdraw(tutor.getUserId(), new WithdrawRequest("pw-1234"));
    em.clear();

    // then
    LearningMaterial found = learningMaterialRepository.findById(material.getMaterialId()).orElseThrow();
    assertThat(userRepository.findById(tutor.getUserId())).isEmpty();
    assertThat(found.getAssignedBy()).isNull();
    assertThat(found.getStudent().getStudentId()).isEqualTo(student.getStudentId());

    userService.withdraw(studentUser.getUserId(), new WithdrawRequest("pw-1234"));
  }

  private Source source(User uploadedBy, String fileName, SourceType sourceType) {
    return Source.builder()
        .uploadedBy(uploadedBy)
        .originalFileName(fileName)
        .storageKey("withdraw/" + fileName)
        .fileType("PDF")
        .fileSize(100L)
        .sourceType(sourceType)
        .contentKind(ContentKind.GENERAL)
        .build();
  }
}
