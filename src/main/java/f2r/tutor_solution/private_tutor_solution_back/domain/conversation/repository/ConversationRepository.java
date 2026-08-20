package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
  @Query("""
      select c from Conversation c
      where c.student.studentId = :studentId
      order by coalesce(c.lastMessageAt, c.createdAt) desc
      """)
  List<Conversation> findRecentByStudentId(@Param("studentId") Long studentId);


  @Query("""
      select c from Conversation c
      where c.tutorUser.userId = :tutorUserId
      order by coalesce(c.lastMessageAt, c.createdAt) desc
      """)
  List<Conversation> findRecentByTutorUserId(@Param("tutorUserId") Long tutorUserId);

  /** 어드민 대시보드 활동 요약용 — 학생의 대화 개수. */
  long countByStudent_StudentId(Long studentId);
}
