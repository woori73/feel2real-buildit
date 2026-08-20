package f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplication;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LessonApplicationRepository extends JpaRepository<LessonApplication, Long> {

  Optional<LessonApplication> findByStudent_StudentIdAndLesson_LessonId(Long studentId, Long lessonId);

  boolean existsByLesson_LessonIdAndStatus(Long lessonId, LessonApplicationStatus status);

  boolean existsByLesson_Academy_AcademyIdAndStatus(Long academyId, LessonApplicationStatus status);

  List<LessonApplication> findByStudent_StudentIdOrderByUpdatedAtDesc(Long studentId);

  @Query("""
      select a from LessonApplication a
      join fetch a.lesson l
      join fetch l.academy academy
      join fetch a.student student
      join fetch student.user user
      where academy.tutor.userId = :tutorUserId
      order by a.updatedAt desc
      """)
  List<LessonApplication> findByTutorUserId(@Param("tutorUserId") Long tutorUserId);

  @Query("""
      select count(a) > 0 from LessonApplication a
      where a.student.studentId = :studentId
        and a.status = :status
        and a.lesson.academy.tutor.userId = :tutorUserId
        and (:excludeApplicationId is null or a.applicationId <> :excludeApplicationId)
      """)
  boolean existsByTutorAndStudentAndStatusExcluding(
      @Param("tutorUserId") Long tutorUserId,
      @Param("studentId") Long studentId,
      @Param("status") LessonApplicationStatus status,
      @Param("excludeApplicationId") Long excludeApplicationId);

  /**
   * TUTOR 가드 (student 단위) — verifyOwnsConversation, verifyOwnsLearningMaterial, resolveStudentId 공통.
   * TUTOR가 담당하는 학생(APPROVED 신청 존재)인지 판정한다.
   */
  @Query("""
      select count(a) > 0 from LessonApplication a
      where a.student.studentId = :studentId
        and a.lesson.academy.tutor.userId = :tutorUserId
        and a.status = f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplicationStatus.APPROVED
      """)
  boolean existsApprovedTutorStudent(@Param("tutorUserId") Long tutorUserId,
                                     @Param("studentId") Long studentId);

  /**
   * TUTOR 가드 (conversation 단위) — verifyOwnsConversation 전용.
   * 해당 conversation이 강사 본인 academy lesson에 APPROVED로 연결돼 있는지 판정한다.
   * (학생 단위 existsApprovedTutorStudent와 달리, 학생의 다른 미매칭 대화는 통과시키지 않는다.)
   */
  @Query("""
      select count(a) > 0 from LessonApplication a
      where a.conversation.conversationId = :conversationId
        and a.lesson.academy.tutor.userId = :tutorUserId
        and a.status = f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplicationStatus.APPROVED
      """)
  boolean existsApprovedTutorConversation(@Param("tutorUserId") Long tutorUserId,
                                          @Param("conversationId") Long conversationId);

  /**
   * 강사용 수업 신청자 목록 — APPROVED 상태만, 최신순.
   */
  @Query("""
      select a from LessonApplication a
      join fetch a.student s join fetch s.user
      left join fetch a.conversation
      where a.lesson.lessonId = :lessonId
        and a.status = f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplicationStatus.APPROVED
      order by a.appliedAt desc
      """)
  List<LessonApplication> findApprovedByLessonId(@Param("lessonId") Long lessonId);

  /**
   * conversation에 연결된 최신 APPROVED 신청을 반환한다.
   * CANCELED 신청은 conversation_id FK를 유지하므로 status 필터가 필수.
   * "최신 APPROVED 우선" 정책: 동일 conversation을 여러 신청이 참조할 때 appliedAt 내림차순 첫 번째.
   */
  Optional<LessonApplication> findFirstByConversation_ConversationIdAndStatusOrderByAppliedAtDesc(
      Long conversationId, LessonApplicationStatus status);
}
