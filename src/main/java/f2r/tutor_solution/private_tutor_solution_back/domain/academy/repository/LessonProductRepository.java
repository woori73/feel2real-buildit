package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface LessonProductRepository extends JpaRepository<LessonProduct, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select l from LessonProduct l join fetch l.academy a join fetch a.tutor where l.lessonId = :lessonId")
  Optional<LessonProduct> findForUpdateByLessonId(@Param("lessonId") Long lessonId);

  @Query("""
      select l.lessonId as lessonId, l.creationKey as creationKey
      from LessonProduct l
      where l.academy.academyId = :academyId
        and l.creationKey in :creationKeys
      """)
  List<LessonCreationKeyBindingProjection> findCreationKeyBindings(
      @Param("academyId") Long academyId,
      @Param("creationKeys") Set<String> creationKeys);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select l from LessonProduct l
      where l.academy.academyId = :academyId
        and l.lessonId in :lessonIds
      order by l.lessonId asc
      """)
  List<LessonProduct> findAllForUpdateByAcademyIdAndLessonIds(
      @Param("academyId") Long academyId,
      @Param("lessonIds") Collection<Long> lessonIds);

  /**
   * 대표 영상으로 이 영상을 쓰는 수업상품 이름들. 영상 삭제 차단 메시지에 그대로 나간다.
   *
   * <p>{@code PESSIMISTIC_WRITE}인 이유: 삭제 판정은 read-then-write다. 락 없이 읽으면
   * "미사용" 판정 직후 다른 트랜잭션이 그 영상을 수업상품에 연결할 수 있고, 그 뒤 이어지는
   * 물리 삭제가 mp4와 썸네일을 지워 복구 불가능한 상태를 만든다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select l.name from LessonProduct l
      where l.mediaVideoId = :videoId or l.otVideoId = :videoId
      order by l.lessonId asc
      """)
  List<String> findNamesForUpdateByMediaVideoId(@Param("videoId") Long videoId);

  /** 강사 영상 목록의 "사용 중" 배지용 배치 조회. 영상마다 던지지 않는다(N+1 금지). */
  @Query("""
      select l.mediaVideoId as videoId, l.name as lessonName
      from LessonProduct l
      where l.mediaVideoId in :videoIds
      order by l.lessonId asc
      """)
  List<LessonMediaUsageProjection> findMediaUsages(@Param("videoIds") Collection<Long> videoIds);

  /** OT 영상 사용 배지용 배치 조회. 대표 미디어와 별도 객체라 별도 projection으로 합친다. */
  @Query("""
      select l.otVideoId as videoId, l.name as lessonName
      from LessonProduct l
      where l.otVideoId in :videoIds
      order by l.lessonId asc
      """)
  List<LessonMediaUsageProjection> findOtVideoUsages(@Param("videoIds") Collection<Long> videoIds);

  /** academy 범위를 조건에 포함해 타 학원 수업상품 접근을 쿼리 단계에서 차단한다. */
  Optional<LessonProduct> findByLessonIdAndAcademy_AcademyId(Long lessonId, Long academyId);

  List<LessonProduct> findByAcademy_AcademyIdOrderByCreatedAtDesc(Long academyId);

  /** 편집기/공개 탭 순서. 새 수업을 뒤에 붙이며 같은 생성 시각은 id로 안정화한다. */
  List<LessonProduct> findByAcademy_AcademyIdOrderByCreatedAtAscLessonIdAsc(Long academyId);

  /** LMS 강좌 관리에서 해당 Academy의 관리 가능 상태만 안정적으로 조회한다. */
  List<LessonProduct> findByAcademy_AcademyIdAndStatusInOrderByCreatedAtAscLessonIdAsc(
      Long academyId,
      Collection<LessonStatus> statuses);

  List<LessonProduct> findByAcademy_AcademyIdAndStatusAndVisibleTrueOrderByCreatedAtDesc(
      Long academyId,
      LessonStatus status);

  /** 공개 탭도 편집기와 같은 생성 순서를 유지한다. */
  List<LessonProduct> findByAcademy_AcademyIdAndStatusAndVisibleTrueOrderByCreatedAtAscLessonIdAsc(
      Long academyId,
      LessonStatus status);


  @Query("""
      select distinct l from LessonProduct l
      join fetch l.academy a
      where a.status = :academyStatus
        and a.visible = true
        and l.status = :lessonStatus
        and l.visible = true
        and (:subjectId is null or exists (
          select s from a.subjects s where s.subjectId = :subjectId
        ))
        and (:targetGroup is null or l.targetGroup = :targetGroup)
        and (:city is null or exists (
          select r from AcademyRegion r
          where r.academy = a
            and r.city = :city
            and (:district is null or r.district = :district or r.district = 'ALL')
        ))
        and (:lessonType is null or l.lessonType = :lessonType)
        and (:weekday is null or :weekday member of l.weekdays)
        and (:maxPrice is null or l.priceAmount is null or l.priceAmount <= :maxPrice)
        and (:weeklyCount is null or l.weeklyCount = :weeklyCount)
      order by l.createdAt desc
      """)
  List<LessonProduct> searchPublishedLessons(
      @Param("academyStatus") AcademyStatus academyStatus,
      @Param("lessonStatus") LessonStatus lessonStatus,
      @Param("subjectId") Integer subjectId,
      @Param("targetGroup") TargetGroup targetGroup,
      @Param("city") String city,
      @Param("district") String district,
      @Param("lessonType") LessonType lessonType,
      @Param("weekday") LessonWeekday weekday,
      @Param("maxPrice") Integer maxPrice,
      @Param("weeklyCount") Integer weeklyCount);

  @Query("""
      select distinct l from LessonProduct l
      join fetch l.academy a
      join fetch a.tutor tutor
      where (:keyword is null
        or lower(l.name) like lower(concat('%', :keyword, '%'))
        or lower(a.name) like lower(concat('%', :keyword, '%'))
        or lower(tutor.name) like lower(concat('%', :keyword, '%'))
        or exists (
          select p from LessonCommunityPost p
          join p.author author
          where p.lesson = l
            and p.visible = true
            and (
              lower(p.title) like lower(concat('%', :keyword, '%'))
              or lower(cast(p.content as string)) like lower(concat('%', :keyword, '%'))
              or lower(author.name) like lower(concat('%', :keyword, '%'))
            )
        ))
      order by l.createdAt desc
      """)
  List<LessonProduct> searchAdminCommunities(@Param("keyword") String keyword);

}
