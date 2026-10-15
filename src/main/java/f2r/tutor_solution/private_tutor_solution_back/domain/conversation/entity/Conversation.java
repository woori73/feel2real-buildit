package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "conversation_id")
  private Long conversationId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_id")
  private Student student;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tutor_user_id")
  private User tutorUser;

  @Column(name = "title", length = 200)
  private String title;

  @Column(name = "metadata", columnDefinition = "JSON")
  private String metadata;

  @Column(name = "last_message_at")
  private LocalDateTime lastMessageAt;

  @Builder
  public Conversation(Student student, User tutorUser, String title, String metadata) {
    if ((student == null) == (tutorUser == null)) {
      throw new IllegalArgumentException("대화는 학생 또는 강사 중 정확히 한 소유자만 가져야 합니다.");
    }
    this.student = student;
    this.tutorUser = tutorUser;
    this.title = title;
    this.metadata = metadata;
  }

  public static Conversation forStudent(Student student, String title, String metadata) {
    return Conversation.builder()
        .student(student)
        .title(title)
        .metadata(metadata)
        .build();
  }

  public static Conversation forTutor(User tutorUser, String title, String metadata) {
    return Conversation.builder()
        .tutorUser(tutorUser)
        .title(title)
        .metadata(metadata)
        .build();
  }

  public boolean isStudentOwned() {
    return student != null && tutorUser == null;
  }

  public boolean isTutorOwned() {
    return tutorUser != null && student == null;
  }

  public void updateTitle(String title) {
    this.title = title;
  }

  public void updateMetadata(String metadata) {
    this.metadata = metadata;
  }

  public void updateLastMessageAt(LocalDateTime lastMessageAt) {
    this.lastMessageAt = lastMessageAt;
  }
}
