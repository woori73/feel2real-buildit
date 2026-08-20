package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplication;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplicationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.repository.LessonApplicationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.dto.LessonCommunityCategoryRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.dto.LessonCommunityPostRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.LessonCommunityCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.LessonCommunityPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.LessonCommunityPostType;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@DisplayName("LessonCommunityService — 권한/공개범위 정책")
class LessonCommunityServiceTest {

  private LessonProductRepository lessonProductRepository;
  private LessonApplicationRepository lessonApplicationRepository;
  private StudentRepository studentRepository;
  private UserRepository userRepository;
  private LessonCommunityCategoryRepository categoryRepository;
  private LessonCommunityPostRepository postRepository;
  private LessonCommunityCommentRepository commentRepository;
  private LessonCommunityAttachmentRepository attachmentRepository;
  private FileStorage fileStorage;
  private LessonCommunityService service;

  private User tutor;
  private User studentUser;
  private User otherStudentUser;
  private Student student;
  private Student otherStudent;
  private LessonProduct lesson;
  private LessonCommunityPost qnaPrivatePost;
  private LessonCommunityPost generalPost;

  @BeforeEach
  void setUp() {
    lessonProductRepository = mock(LessonProductRepository.class);
    lessonApplicationRepository = mock(LessonApplicationRepository.class);
    studentRepository = mock(StudentRepository.class);
    userRepository = mock(UserRepository.class);
    categoryRepository = mock(LessonCommunityCategoryRepository.class);
    postRepository = mock(LessonCommunityPostRepository.class);
    commentRepository = mock(LessonCommunityCommentRepository.class);
    attachmentRepository = mock(LessonCommunityAttachmentRepository.class);
    fileStorage = mock(FileStorage.class);
    service = new LessonCommunityService(
        lessonProductRepository,
        lessonApplicationRepository,
        studentRepository,
        userRepository,
        categoryRepository,
        postRepository,
        commentRepository,
        attachmentRepository,
        fileStorage);

    tutor = user(10L, "tutor", "강사", Role.TUTOR);
    studentUser = user(20L, "student", "학생", Role.STUDENT);
    otherStudentUser = user(21L, "other", "다른학생", Role.STUDENT);
    student = student(200L, studentUser);
    otherStudent = student(201L, otherStudentUser);
    Academy academy = Academy.builder()
        .tutor(tutor)
        .slug("academy")
        .name("테스트 학원")
        .logoStorageKey("logo")
        .intro("intro")
        .status(AcademyStatus.PUBLISHED)
        .build();
    ReflectionTestUtils.setField(academy, "academyId", 300L);
    lesson = LessonProduct.builder()
        .academy(academy)
        .name("중등 영어")
        .lessonType(LessonType.SUBSCRIPTION)
        .priceUnit(PriceUnit.MONTH)
        .priceSummary("월 10만원")
        .maxCapacity(10)
        .scheduleType(ScheduleType.WEEKLY)
        .scheduleSummary("주 2회")
        .status(LessonStatus.PUBLISHED)
        .build();
    ReflectionTestUtils.setField(lesson, "lessonId", 400L);

    qnaPrivatePost = LessonCommunityPost.builder()
        .lesson(lesson)
        .author(studentUser)
        .postType(LessonCommunityPostType.QNA)
        .title("비공개 질문")
        .content("질문")
        .qnaPublic(false)
        .build();
    ReflectionTestUtils.setField(qnaPrivatePost, "postId", 500L);

    generalPost = LessonCommunityPost.builder()
        .lesson(lesson)
        .author(studentUser)
        .postType(LessonCommunityPostType.GENERAL)
        .title("일반글")
        .content("본문")
        .qnaPublic(false)
        .build();
    ReflectionTestUtils.setField(generalPost, "postId", 501L);

    when(studentRepository.findByUser_UserId(20L)).thenReturn(Optional.of(student));
    when(studentRepository.findByUser_UserId(21L)).thenReturn(Optional.of(otherStudent));
    when(lessonApplicationRepository.findByStudent_StudentIdAndLesson_LessonId(200L, 400L))
        .thenReturn(Optional.of(approved(student)));
    when(lessonApplicationRepository.findByStudent_StudentIdAndLesson_LessonId(201L, 400L))
        .thenReturn(Optional.of(approved(otherStudent)));
    when(attachmentRepository.findByPost_PostIdAndVisibleTrueOrderByAttachmentIdAsc(anyLong())).thenReturn(List.of());
    when(commentRepository.findVisibleByPostId(anyLong())).thenReturn(List.of());
  }

  @Test
  @DisplayName("given 비공개 Q&A when 다른 수강 학생이 상세 조회 then 404로 숨긴다")
  void privateQnaIsHiddenFromOtherApprovedStudent() {
    // given
    when(postRepository.findDetailById(500L)).thenReturn(Optional.of(qnaPrivatePost));
    JwtTokenProvider.AuthenticatedPrincipal other = principal(otherStudentUser);

    // when / then
    assertThatThrownBy(() -> service.getPost(other, 500L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404 NOT_FOUND");
  }

  @Test
  @DisplayName("given 다른 학생 글 when 수강 학생이 수정 then 작성자 전용 정책으로 거부한다")
  void onlyAuthorCanUpdatePost() {
    // given
    when(postRepository.findDetailById(501L)).thenReturn(Optional.of(generalPost));
    JwtTokenProvider.AuthenticatedPrincipal other = principal(otherStudentUser);
    LessonCommunityPostRequest request = new LessonCommunityPostRequest(
        LessonCommunityPostType.GENERAL, "수정", "수정 본문", null, false);

    // when / then
    assertThatThrownBy(() -> service.updatePost(other, 501L, request))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("작성자만");
  }


  @Test
  @DisplayName("given 강사 커스텀 탭 삭제 when 삭제 then 글은 일반글로 이동하고 탭은 하드 삭제한다")
  void deleteCustomTabMovesPostsToGeneralAndDeletesTab() {
    // given
    LessonCommunityCategory customTab = LessonCommunityCategory.builder()
        .lesson(lesson)
        .name("숙제방")
        .sortOrder(1)
        .build();
    ReflectionTestUtils.setField(customTab, "categoryId", 600L);
    when(lessonProductRepository.findById(400L)).thenReturn(Optional.of(lesson));
    when(categoryRepository.findByCategoryIdAndLesson_LessonId(600L, 400L)).thenReturn(Optional.of(customTab));

    // when
    service.deleteCategory(principal(tutor), 400L, 600L);

    // then
    verify(postRepository).moveCategoryPostsToGeneral(400L, 600L);
    verify(categoryRepository).delete(customTab);
  }

  @Test
  @DisplayName("given 기본 탭 이름 when 커스텀 탭 생성 then 거부한다")
  void defaultTabNameCannotBeCreatedAsCustomTab() {
    // given
    when(lessonProductRepository.findById(400L)).thenReturn(Optional.of(lesson));

    // when / then
    assertThatThrownBy(() -> service.createCategory(
        principal(tutor),
        400L,
        new LessonCommunityCategoryRequest("공지", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("기본 탭");
  }

  private User user(Long id, String loginId, String name, Role role) {
    User user = User.builder().loginId(loginId).password("pw").name(name).role(role).build();
    ReflectionTestUtils.setField(user, "userId", id);
    return user;
  }

  private Student student(Long id, User user) {
    Student student = Student.builder().user(user).schoolName("학교").grade((byte) 2).build();
    ReflectionTestUtils.setField(student, "studentId", id);
    return student;
  }

  private LessonApplication approved(Student student) {
    return LessonApplication.builder()
        .lesson(lesson)
        .student(student)
        .status(LessonApplicationStatus.APPROVED)
        .build();
  }

  private JwtTokenProvider.AuthenticatedPrincipal principal(User user) {
    return new JwtTokenProvider.AuthenticatedPrincipal(
        user.getUserId(), user.getLoginId(), user.getRole().name());
  }
}
