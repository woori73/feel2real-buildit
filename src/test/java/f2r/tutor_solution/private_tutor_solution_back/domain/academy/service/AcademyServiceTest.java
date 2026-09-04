package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRegionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics.AcademyHandleHistoryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplicationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.repository.LessonApplicationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AcademyServiceTest {

  private AcademyRepository academyRepository;
  private AcademyRegionRepository regionRepository;
  private LessonProductRepository lessonProductRepository;
  private LessonApplicationRepository lessonApplicationRepository;
  private SubjectRepository subjectRepository;
  private UserRepository userRepository;
  private FileStorage fileStorage;
  private ImageSanitizer imageSanitizer;
  private AuthenticatedUserResolver authResolver;
  private AcademyVideoDeletionGuard academyVideoDeletionGuard;
  private AcademyHandleHistoryService academyHandleHistoryService;
  private AcademyService service;

  private final JwtTokenProvider.AuthenticatedPrincipal principal =
      new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor1", Role.TUTOR.name());

  @BeforeEach
  void setUp() {
    academyRepository = mock(AcademyRepository.class);
    regionRepository = mock(AcademyRegionRepository.class);
    lessonProductRepository = mock(LessonProductRepository.class);
    lessonApplicationRepository = mock(LessonApplicationRepository.class);
    subjectRepository = mock(SubjectRepository.class);
    userRepository = mock(UserRepository.class);
    fileStorage = mock(FileStorage.class);
    imageSanitizer = mock(ImageSanitizer.class);
    authResolver = mock(AuthenticatedUserResolver.class);
    academyVideoDeletionGuard = mock(AcademyVideoDeletionGuard.class);
    academyHandleHistoryService = mock(AcademyHandleHistoryService.class);
    service = new AcademyService(
        academyRepository,
        regionRepository,
        lessonProductRepository,
        lessonApplicationRepository,
        subjectRepository,
        userRepository,
        fileStorage,
        imageSanitizer,
        authResolver,
        // baseDomain=null → publicUrl 경로형(/academies/{slug}). 서브도메인 형식은 통합/DTO 테스트에서 별도 검증.
        new f2r.tutor_solution.private_tutor_solution_back.config.PublicSiteProperties(null),
        academyVideoDeletionGuard,
        academyHandleHistoryService);
    when(lessonProductRepository.findByAcademy_AcademyIdOrderByCreatedAtAscLessonIdAsc(anyLong()))
        .thenReturn(List.of());
    when(lessonProductRepository.findByAcademy_AcademyIdAndStatusAndVisibleTrueOrderByCreatedAtAscLessonIdAsc(anyLong(), any()))
        .thenReturn(List.of());
  }

  @Test
  void delete_deletesAcademyAndStorageWhenNoApprovedApplications() {
    // given
    Academy academy = academy("logo-key", "cover-key");
    when(academyRepository.findForUpdateByTutorUserId(10L)).thenReturn(Optional.of(academy));
    when(lessonApplicationRepository.existsByLesson_Academy_AcademyIdAndStatus(
        100L, LessonApplicationStatus.APPROVED)).thenReturn(false);

    // when
    service.delete(principal);

    // then
    verify(authResolver).requireTutor(principal);
    verify(authResolver).verifyOwnsAcademy(academy);
    InOrder guardedDelete = inOrder(academyRepository, academyVideoDeletionGuard);
    guardedDelete.verify(academyRepository).findForUpdateByTutorUserId(10L);
    guardedDelete.verify(academyVideoDeletionGuard).requireFullyRemoved(100L);
    guardedDelete.verify(academyRepository).delete(academy);
    verify(academyRepository).flush();
    verify(fileStorage).delete("logo-key", "IMAGE");
    verify(fileStorage).delete("cover-key", "IMAGE");
  }

  @Test
  void delete_throwsConflictWhenApprovedApplicationsExist() {
    // given
    Academy academy = academy("logo-key", "cover-key");
    when(academyRepository.findForUpdateByTutorUserId(10L)).thenReturn(Optional.of(academy));
    when(lessonApplicationRepository.existsByLesson_Academy_AcademyIdAndStatus(
        100L, LessonApplicationStatus.APPROVED)).thenReturn(true);

    // when & then
    assertThatThrownBy(() -> service.delete(principal))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("신청/수강생");
    verify(academyRepository, never()).delete(any());
    verify(fileStorage, never()).delete(any(), any());
  }

  @Test
  void create_publishesAcademyAndHidesStorageKeyInResponse() throws Exception {
    // given
    User tutor = tutor();
    Subject subject = subject();
    AcademyUpsertRequest request = validRequest();
    MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", new byte[]{1});
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject));
    when(academyRepository.existsBySlug("테스트-academy")).thenReturn(false);
    when(imageSanitizer.sanitize(logo))
        .thenReturn(new ImageSanitizer.SanitizedImage(new byte[]{9, 9}, "image/jpeg", "jpg"));
    when(fileStorage.saveImage(10L, "logo.png", new byte[]{9, 9}, "jpg")).thenReturn("secret/logo.jpg");
    when(academyRepository.save(any(Academy.class))).thenAnswer(invocation -> {
      Academy academy = invocation.getArgument(0);
      ReflectionTestUtils.setField(academy, "academyId", 100L);
      return academy;
    });
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    AcademyResponse response = service.create(principal, request, logo, null);

    // then
    assertThat(response.status()).isEqualTo(AcademyStatus.PUBLISHED);
    assertThat(response.logoUrl()).isEqualTo("/api/academies/테스트-academy/logo");
    assertThat(response.toString()).doesNotContain("secret/logo.jpg");
    verify(academyHandleHistoryService).recordCreated(any(Academy.class));
    verify(regionRepository).saveAll(argThat(regions -> {
      List<?> list = new ArrayList<>((Collection<?>) regions);
      return list.size() == 1 && ((AcademyRegion) list.get(0)).getDistrict().equals(AcademyRegion.ALL_DISTRICT);
    }));
  }

  @Test
  void create_persistsPageSettingsAndExposesInResponse() throws Exception {
    // given: 외부 연동 URL + 섹션 표시토글을 담은 요청.
    User tutor = tutor();
    Subject subject = subject();
    AcademyPageSettings pageSettings = new AcademyPageSettings(
        "https://blog.naver.com/sample", "https://smartstore.naver.com/sample",
        "https://www.youtube.com/watch?v=abc123XYZ09",
        Map.of("sec-principal", false));
    AcademyUpsertRequest request = validRequestWithPageSettings(pageSettings);
    MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", new byte[]{1});
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject));
    when(academyRepository.existsBySlug("테스트-academy")).thenReturn(false);
    when(imageSanitizer.sanitize(logo))
        .thenReturn(new ImageSanitizer.SanitizedImage(new byte[]{9, 9}, "image/jpeg", "jpg"));
    when(fileStorage.saveImage(10L, "logo.png", new byte[]{9, 9}, "jpg")).thenReturn("secret/logo.jpg");
    ArgumentCaptor<Academy> academyCaptor = ArgumentCaptor.forClass(Academy.class);
    when(academyRepository.save(academyCaptor.capture())).thenAnswer(invocation -> {
      Academy academy = invocation.getArgument(0);
      ReflectionTestUtils.setField(academy, "academyId", 100L);
      return academy;
    });
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    AcademyResponse response = service.create(principal, request, logo, null);

    // then
    assertThat(academyCaptor.getValue().getPageSettings()).isEqualTo(pageSettings);
    assertThat(response.pageSettings()).isEqualTo(pageSettings);
  }

  @Test
  void create_throwsWhenBlogUrlIsNotHttpOrHttps() {
    // given: blogUrl이 http/https로 시작하지 않는 잘못된 형식.
    AcademyPageSettings invalid = new AcademyPageSettings("ftp://blog.naver.com/sample", null, null, null);
    AcademyUpsertRequest request = validRequestWithPageSettings(invalid);
    MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", new byte[]{1});
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.create(principal, request, logo, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("올바른 URL");
  }

  @Test
  void create_usesDefaultLogoWhenLogoMissing() throws Exception {
    // given
    User tutor = tutor();
    Subject subject = subject();
    AcademyUpsertRequest request = validRequest();
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject));
    when(academyRepository.existsBySlug("테스트-academy")).thenReturn(false);
    when(fileStorage.saveImage(eq(10L), eq("default-academy-logo.jpg"), any(byte[].class)))
        .thenReturn("default/logo.jpg");
    when(academyRepository.save(any(Academy.class))).thenAnswer(invocation -> {
      Academy academy = invocation.getArgument(0);
      ReflectionTestUtils.setField(academy, "academyId", 100L);
      return academy;
    });
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    AcademyResponse response = service.create(principal, request, null, null);

    // then
    assertThat(response.logoUrl()).isEqualTo("/api/academies/테스트-academy/logo");
    ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(fileStorage).saveImage(eq(10L), eq("default-academy-logo.jpg"), bytesCaptor.capture());
    assertJpeg(bytesCaptor.getValue());
    verify(imageSanitizer, never()).sanitize(any(MultipartFile.class));
  }

  @Test
  void create_throwsWhenRegionsExceedThree() {
    // given
    AcademyUpsertRequest request = new AcademyUpsertRequest(
        "테스트 Academy",
        "test-academy",
        "소개",
        Set.of(1),
        Set.of(TargetGroup.MIDDLE),
        List.of(
            new AcademyRegionRequest("서울시", "서초구"),
            new AcademyRegionRequest("서울시", "강남구"),
            new AcademyRegionRequest("서울시", "송파구"),
            new AcademyRegionRequest("서울시", "마포구")),
        null);
    MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", new byte[]{1});

    // when & then
    assertThatThrownBy(() -> service.create(principal, request, logo, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("최대 3개");
  }

  @Test
  void create_throwsWhenIntroExceedsOneThousandCharacters() {
    // given
    AcademyUpsertRequest request = new AcademyUpsertRequest(
        "테스트 Academy",
        "test-academy",
        "가".repeat(1001),
        Set.of(1),
        Set.of(TargetGroup.MIDDLE),
        List.of(new AcademyRegionRequest("서울시", null)),
        null);
    MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", new byte[]{1});

    // when & then
    assertThatThrownBy(() -> service.create(principal, request, logo, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1000자");
  }

  @Test
  void create_addsSlugSuffixWhenCollisionExists() throws Exception {
    // given
    User tutor = tutor();
    Subject subject = subject();
    AcademyUpsertRequest request = validRequest();
    MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", new byte[]{1});
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject));
    when(academyRepository.existsBySlug("테스트-academy")).thenReturn(true);
    when(academyRepository.existsBySlug("테스트-academy-2")).thenReturn(false);
    when(imageSanitizer.sanitize(logo))
        .thenReturn(new ImageSanitizer.SanitizedImage(new byte[]{9}, "image/jpeg", "jpg"));
    when(fileStorage.saveImage(anyLong(), anyString(), any())).thenReturn("logo");
    when(academyRepository.save(any(Academy.class))).thenAnswer(invocation -> {
      Academy academy = invocation.getArgument(0);
      ReflectionTestUtils.setField(academy, "academyId", 100L);
      return academy;
    });
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    AcademyResponse response = service.create(principal, request, logo, null);

    // then
    assertThat(response.slug()).isEqualTo("테스트-academy-2");
  }

  @Test
  void createWithoutLogo_publishesWithDefaultLogoAndCollectedTargetsAndRegion() throws Exception {
    // given
    User tutor = tutor();
    Subject subject = subject();
    AcademyUpsertRequest request = validRequest();
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject));
    when(academyRepository.existsBySlug("테스트-academy")).thenReturn(false);
    when(fileStorage.saveImage(eq(10L), eq("default-academy-logo.jpg"), any(byte[].class)))
        .thenReturn("default/logo.jpg");
    when(academyRepository.save(any(Academy.class))).thenAnswer(invocation -> {
      Academy academy = invocation.getArgument(0);
      ReflectionTestUtils.setField(academy, "academyId", 100L);
      return academy;
    });
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when: 로고 미첨부(JSON create) → 서버 기본 로고
    AcademyResponse response = service.create(principal, request, null, null);

    // then: 기본 로고도 기존 FileStorage 경로로 저장되고, 응답에는 storage key가 노출되지 않는다.
    assertThat(response.status()).isEqualTo(AcademyStatus.PUBLISHED);
    assertThat(response.logoUrl()).isEqualTo("/api/academies/테스트-academy/logo");
    assertThat(response.toString()).doesNotContain("default/logo.jpg");
    ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(fileStorage).saveImage(eq(10L), eq("default-academy-logo.jpg"), bytesCaptor.capture());
    assertJpeg(bytesCaptor.getValue());
    verify(regionRepository).saveAll(argThat(regions ->
        new ArrayList<>((Collection<?>) regions).size() == 1));
    verify(authResolver).requireTutor(principal);
  }

  @Test
  void create_throwsConflictWhenAcademyAlreadyExists() throws Exception {
    // given
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy("logo", null)));

    // when & then: 중복 발행은 409 CONFLICT.
    assertThatThrownBy(() -> service.create(principal, validRequest(), null, null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    verify(academyRepository, never()).save(any());
    verify(fileStorage, never()).saveImage(anyLong(), anyString(), any());
  }

  @Test
  void createWithoutLogo_throwsWhenTargetGroupsMissing() {
    // given: 대상 미수집 → 공개 badge 가짜 금지 원칙에 따라 필수 검증 위임.
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());
    AcademyUpsertRequest request = new AcademyUpsertRequest(
        "테스트 Academy", "test-academy", "소개", Set.of(1), Set.of(),
        List.of(new AcademyRegionRequest("서울시", null)), null);

    // when & then
    assertThatThrownBy(() -> service.create(principal, request, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("대상은 1개 이상");
  }

  @Test
  void createWithoutLogo_throwsWhenRegionMissing() {
    // given: 지역 미수집 → 필수 검증 위임.
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());
    AcademyUpsertRequest request = new AcademyUpsertRequest(
        "테스트 Academy", "test-academy", "소개", Set.of(1), Set.of(TargetGroup.MIDDLE), List.of(), null);

    // when & then
    assertThatThrownBy(() -> service.create(principal, request, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("지역은 1개 이상");
  }

  @Test
  void getPublic_throwsNotFoundForDraftOrMissingAcademy() {
    // given
    when(academyRepository.findBySlugAndStatusAndVisibleTrue("draft", AcademyStatus.PUBLISHED))
        .thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.getPublic("draft"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void getPublic_throwsNotFoundWhenAcademyIsHiddenByAdmin() {
    // given: repository method itself applies status=PUBLISHED and visible=true.
    when(academyRepository.findBySlugAndStatusAndVisibleTrue("hidden-academy", AcademyStatus.PUBLISHED))
        .thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.getPublic("hidden-academy"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void getPublic_includesOnlyPublishedLessonsAndOmitsInternalAuthoringMetadata() throws Exception {
    // given
    Academy academy = academy("logo.jpg", null);
    LessonProduct lesson = LessonProduct.builder()
        .academy(academy)
        .name("중3 영어 내신반")
        .lessonType(LessonType.SUBSCRIPTION)
        .priceAmount(500_000)
        .priceUnit(PriceUnit.MONTH)
        .priceSummary("50만원/1개월")
        .maxCapacity(15)
        .scheduleType(ScheduleType.WEEKLY)
        .weeklyCount(2)
        .scheduleSummary("주2회 [월,수]")
        .status(LessonStatus.PUBLISHED)
        .creationKey("5fd2e8f8-37f0-4f8d-a56c-fd42ed54c553")
        .authoringRevision(7L)
        .weekdays(new HashSet<>(Set.of(LessonWeekday.MONDAY, LessonWeekday.WEDNESDAY)))
        .build();
    ReflectionTestUtils.setField(lesson, "lessonId", 200L);
    when(academyRepository.findBySlugAndStatusAndVisibleTrue("테스트-academy", AcademyStatus.PUBLISHED))
        .thenReturn(Optional.of(academy));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());
    when(lessonProductRepository.findByAcademy_AcademyIdAndStatusAndVisibleTrueOrderByCreatedAtAscLessonIdAsc(100L, LessonStatus.PUBLISHED))
        .thenReturn(List.of(lesson));

    // when
    AcademyResponse response = service.getPublic("테스트-academy");

    // then
    assertThat(response.lessons()).hasSize(1);
    assertThat(response.lessons().get(0).name()).isEqualTo("중3 영어 내신반");
    assertThat(response.lessons().get(0).creationKey()).isNull();
    assertThat(response.lessons().get(0).authoringRevision()).isNull();
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode publicLessonJson = objectMapper.readTree(
        objectMapper.writeValueAsBytes(response)).path("lessons").get(0);
    assertThat(publicLessonJson.has("creationKey")).isFalse();
    assertThat(publicLessonJson.has("authoringRevision")).isFalse();
    assertThat(publicLessonJson.path("name").asText()).isEqualTo("중3 영어 내신반");
    verify(lessonProductRepository).findByAcademy_AcademyIdAndStatusAndVisibleTrueOrderByCreatedAtAscLessonIdAsc(100L, LessonStatus.PUBLISHED);
  }

  @Test
  void getMine_keepsCreationOrder_soNewLessonStaysAtTheEnd() {
    // given — 편집기는 신규 탭을 마지막에 붙인다. 저장 후 다시 조회해도 같은 순서여야 한다.
    Academy academy = academy("logo.jpg", null);
    LessonProduct first = LessonProduct.builder()
        .academy(academy).name("첫 수업").status(LessonStatus.PUBLISHED).build();
    LessonProduct newlyCreated = LessonProduct.builder()
        .academy(academy).name("새로 만든 수업").status(LessonStatus.PUBLISHED).build();
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(lessonProductRepository.findByAcademy_AcademyIdOrderByCreatedAtAscLessonIdAsc(100L))
        .thenReturn(List.of(first, newlyCreated));

    // when
    AcademyResponse response = service.getMine(principal).orElseThrow();

    // then
    assertThat(response.lessons()).extracting(LessonResponse::name)
        .containsExactly("첫 수업", "새로 만든 수업");
    verify(lessonProductRepository)
        .findByAcademy_AcademyIdOrderByCreatedAtAscLessonIdAsc(100L);
  }

  // getMine은 공개 스토어프론트의 자기소유 판별(academy_public.js isOwnAcademy)이 호출한다.
  // 실제 authResolver(mock 아님)를 물려 ADMIN principal이 403 없이 빈 결과로 떨어지는지,
  // STUDENT는 계속 차단되는지 확인한다.
  private AcademyService serviceWithRealAuthResolver() {
    AuthenticatedUserResolver realResolver = new AuthenticatedUserResolver(
        mock(f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository.class),
        lessonApplicationRepository,
        mock(f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository.class),
        mock(f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository.class));
    return new AcademyService(
        academyRepository, regionRepository, lessonProductRepository, lessonApplicationRepository,
        subjectRepository, userRepository, fileStorage, imageSanitizer, realResolver,
        new f2r.tutor_solution.private_tutor_solution_back.config.PublicSiteProperties(null),
        academyVideoDeletionGuard, academyHandleHistoryService);
  }

  @Test
  void getMine_allowsAdminPrincipalAndReturnsEmptyWhenAdminOwnsNoAcademy() {
    // given
    AcademyService svc = serviceWithRealAuthResolver();
    JwtTokenProvider.AuthenticatedPrincipal admin =
        new JwtTokenProvider.AuthenticatedPrincipal(999L, "admin-f2r", Role.ADMIN.name());
    when(academyRepository.findByTutor_UserId(999L)).thenReturn(Optional.empty());

    // when & then: no AccessDeniedException — just an empty result → controller returns 204.
    assertThat(svc.getMine(admin)).isEmpty();
  }

  @Test
  void getMine_stillRejectsStudentPrincipal() {
    // given
    AcademyService svc = serviceWithRealAuthResolver();
    JwtTokenProvider.AuthenticatedPrincipal student =
        new JwtTokenProvider.AuthenticatedPrincipal(5L, "student1", Role.STUDENT.name());

    // when & then
    assertThatThrownBy(() -> svc.getMine(student))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void listPublic_returnsPublishedAcademiesWithFiltersAndPublicUrls() {
    // given
    Academy academy = academy("logo.jpg", null);
    AcademyRegion region = AcademyRegion.builder()
        .academy(academy)
        .city("서울시")
        .district("서초구")
        .displayName("서울시 서초구")
        .build();
    when(academyRepository.findPublicList(AcademyStatus.PUBLISHED, 1, TargetGroup.MIDDLE, "서울시", "서초구"))
        .thenReturn(List.of(academy));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of(region));

    // when
    List<AcademyListResponse> responses = service.listPublic(1, TargetGroup.MIDDLE, " 서울시 ", " 서초구 ");

    // then
    assertThat(responses).hasSize(1);
    AcademyListResponse response = responses.get(0);
    assertThat(response.name()).isEqualTo("테스트 Academy");
    assertThat(response.logoUrl()).isEqualTo("/api/academies/테스트-academy/logo");
    assertThat(response.subjects()).extracting(AcademyResponse.SubjectSummary::nameKo).containsExactly("영어");
    assertThat(response.targetGroups()).containsExactly(TargetGroup.MIDDLE);
    assertThat(response.regions()).extracting(AcademyRegionResponse::displayName).containsExactly("서울시 서초구");
    verify(academyRepository).findPublicList(AcademyStatus.PUBLISHED, 1, TargetGroup.MIDDLE, "서울시", "서초구");
  }


  @Test
  void create_rejectsUnsupportedBuilderDraftKey() {
    // given: 공개 페이지 JSON 확장 필드는 서버가 허용 key만 받는다.
    AcademyPageSettings pageSettings = new AcademyPageSettings(
        null, null, null, null, Map.of("unknownHeroStat", "bad"));
    AcademyUpsertRequest request = validRequestWithPageSettings(pageSettings);

    // when & then
    assertThatThrownBy(() -> service.create(principal, request, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Builder draft 키");
  }

  @Test
  void create_rejectsTooLongBuilderDraftValue() {
    // given: 히어로 지표는 짧은 public display string만 허용한다.
    AcademyPageSettings pageSettings = new AcademyPageSettings(
        null, null, null, null, Map.of("scoreImprovement", "x".repeat(201)));
    AcademyUpsertRequest request = validRequestWithPageSettings(pageSettings);

    // when & then
    assertThatThrownBy(() -> service.create(principal, request, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("200자");
  }

  @Test
  void create_acceptsIntroFeaturesAsMultilineBuilderDraft() throws Exception {
    // given: 학원 특징(sec-intro 가변 리스트)은 개행 결합 문자열이라 long key(2000자)로 저장된다.
    String features = "1:1 맞춤 개별 지도\n내신·수능 동시 대비\n" + "특".repeat(300);
    User tutor = tutor();
    Subject subject = subject();
    AcademyPageSettings pageSettings = new AcademyPageSettings(
        null, null, null, null, Map.of("introFeatures", features));
    AcademyUpsertRequest request = validRequestWithPageSettings(pageSettings);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject));
    when(academyRepository.existsBySlug("테스트-academy")).thenReturn(false);
    when(fileStorage.saveImage(eq(10L), eq("default-academy-logo.jpg"), any(byte[].class)))
        .thenReturn("secret/default-logo.jpg");
    when(academyRepository.save(any(Academy.class))).thenAnswer(invocation -> {
      Academy academy = invocation.getArgument(0);
      ReflectionTestUtils.setField(academy, "academyId", 100L);
      return academy;
    });
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    AcademyResponse response = service.create(principal, request, null, null);

    // then
    assertThat(response.pageSettings().builderDraft()).containsEntry("introFeatures", features);
  }

  @Test
  void create_acceptsHeaderTitleAndHeroAccentBuilderDrafts() throws Exception {
    // given: GNB 학원명과 히어로 강조 문구는 학원명·과목 설정과 별개로 직접 입력한다.
    User tutor = tutor();
    Subject subject = subject();
    AcademyPageSettings pageSettings = new AcademyPageSettings(
        null, null, null, null, Map.of("headerTitle", "에듀홀릭", "heroAccent", "영어 전문 학원"));
    AcademyUpsertRequest request = validRequestWithPageSettings(pageSettings);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject));
    when(academyRepository.existsBySlug("테스트-academy")).thenReturn(false);
    when(fileStorage.saveImage(eq(10L), eq("default-academy-logo.jpg"), any(byte[].class)))
        .thenReturn("secret/default-logo.jpg");
    when(academyRepository.save(any(Academy.class))).thenAnswer(invocation -> {
      Academy academy = invocation.getArgument(0);
      ReflectionTestUtils.setField(academy, "academyId", 100L);
      return academy;
    });
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    AcademyResponse response = service.create(principal, request, null, null);

    // then
    assertThat(response.pageSettings().builderDraft())
        .containsEntry("headerTitle", "에듀홀릭")
        .containsEntry("heroAccent", "영어 전문 학원");
  }

  @Test
  void create_rejectsUnknownBuilderDraftKey() {
    // given: 화이트리스트에 없는 키는 저장 전에 막는다.
    AcademyPageSettings pageSettings = new AcademyPageSettings(
        null, null, null, null, Map.of("헛된키", "값"));
    AcademyUpsertRequest request = validRequestWithPageSettings(pageSettings);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> service.create(principal, request, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("올바르지 않은 Builder draft 키");
  }

  @Test
  void create_rejectsInvalidFeaturedLessonIds() {
    // given: 대표 수업은 양의 lesson ID를 중복 없이 최대 4개까지만 저장한다.
    AcademyPageSettings pageSettings = new AcademyPageSettings(
        null, null, null, null, Map.of("featuredLessonIds", "1,2,2,4,5"));
    AcademyUpsertRequest request = validRequestWithPageSettings(pageSettings);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> service.create(principal, request, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("대표 수업 ID");
  }

  @Test
  void create_rejectsInvalidHomepageVideoId() {
    // given: 홈페이지 영상 참조는 업로드 영상의 양의 정수 ID만 저장한다.
    AcademyPageSettings pageSettings = new AcademyPageSettings(
        null, null, null, null, Map.of("heroVideoId", "../other-academy"));
    AcademyUpsertRequest request = validRequestWithPageSettings(pageSettings);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> service.create(principal, request, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("홈페이지 영상 ID");
  }

  @Test
  void create_rejectsTooLongIntroFeatures() {
    // given: long key 상한(2000자)을 넘는 특징 목록은 거부한다.
    AcademyPageSettings pageSettings = new AcademyPageSettings(
        null, null, null, null, Map.of("introFeatures", "x".repeat(2001)));
    AcademyUpsertRequest request = validRequestWithPageSettings(pageSettings);

    // when & then
    assertThatThrownBy(() -> service.create(principal, request, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("2000자");
  }

  @Test
  void updatePageSettings_mergesExistingUrlsAndReplacesSectionVisibility() {
    // given
    Academy academy = academy("old/logo.jpg", null);
    AcademyPageSettings current = new AcademyPageSettings(
        "https://blog.example.com",
        "https://store.example.com",
        "https://www.youtube.com/watch?v=abc123XYZ09",
        Map.of("sec-effect", true));
    academy.updatePageSettings(current);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, null, null, Map.of("sec-effect", false, "sec-payment", false, "blogPostsSection", false), null);

    // when
    AcademyResponse response = service.updatePageSettings(principal, request);

    // then
    assertThat(response.pageSettings().blogUrl()).isEqualTo("https://blog.example.com");
    assertThat(response.pageSettings().storeUrl()).isEqualTo("https://store.example.com");
    assertThat(response.pageSettings().naverMapUrl()).isNull();
    assertThat(response.pageSettings().heroYoutubeUrl()).isEqualTo("https://www.youtube.com/watch?v=abc123XYZ09");
    assertThat(response.pageSettings().sectionVisibility())
        .containsEntry("sec-effect", false)
        .containsEntry("sec-payment", false)
        .containsEntry("blogPostsSection", false);
    verify(authResolver).requireTutor(principal);
    verify(authResolver).verifyOwnsAcademy(academy);
  }

  @Test
  void updatePageSettings_updatesNaverMapUrlAndPreservesExistingUrls() {
    // given: 네이버 지도 URL만 저장할 때 기존 블로그/스토어/영상 URL은 유지한다.
    Academy academy = academy("old/logo.jpg", null);
    AcademyPageSettings current = new AcademyPageSettings(
        "https://blog.example.com",
        "https://store.example.com",
        "https://www.youtube.com/watch?v=abc123XYZ09",
        Map.of("sec-social", true));
    academy.updatePageSettings(current);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, "https://map.naver.com/p/entry/place/1422644365", null, null, null);

    // when
    AcademyResponse response = service.updatePageSettings(principal, request);

    // then
    assertThat(response.pageSettings().blogUrl()).isEqualTo("https://blog.example.com");
    assertThat(response.pageSettings().storeUrl()).isEqualTo("https://store.example.com");
    assertThat(response.pageSettings().naverMapUrl()).isEqualTo("https://map.naver.com/p/entry/place/1422644365");
    assertThat(response.pageSettings().heroYoutubeUrl()).isEqualTo("https://www.youtube.com/watch?v=abc123XYZ09");
  }

  @Test
  void updatePageSettings_replacesBusinessInfoAndPreservesOtherSettings() {
    // given: 기존 URL/섹션 설정은 유지하면서 footer 사업자 정보만 학원별 값으로 바꾼다.
    Academy academy = academy("old/logo.jpg", null);
    AcademyBusinessInfo previousBusinessInfo = new AcademyBusinessInfo(
        "기존 학원", "기존 대표", "기존 주소", "000-00-00000", "기존 신고번호",
        "https://www.ftc.go.kr/bizCommPop.do?wrkr_no=0000000000", "000-0000-0000", "old@example.com");
    AcademyPageSettings current = new AcademyPageSettings(
        "https://blog.example.com", "https://store.example.com", null,
        "https://www.youtube.com/watch?v=abc123XYZ09", Map.of("sec-social", true), null, null,
        previousBusinessInfo);
    AcademyBusinessInfo requestedBusinessInfo = new AcademyBusinessInfo(
        " 개별 학원 ", " 개별 대표 ", " 00시 00구 ", "000-00-00000", "0000-00-0000-0000",
        "https://example.invalid/ignored", "000-0000-0000", "academy@example.com");
    academy.updatePageSettings(current);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, null, null, null, null, requestedBusinessInfo);

    // when
    AcademyResponse response = service.updatePageSettings(principal, request);

    // then
    assertThat(response.pageSettings().blogUrl()).isEqualTo("https://blog.example.com");
    assertThat(response.pageSettings().sectionVisibility()).containsEntry("sec-social", true);
    assertThat(response.pageSettings().businessInfo().businessName()).isEqualTo("개별 학원");
    assertThat(response.pageSettings().businessInfo().representative()).isEqualTo("개별 대표");
    assertThat(response.pageSettings().businessInfo().email()).isEqualTo("academy@example.com");
    assertThat(response.pageSettings().businessInfo().businessInfoUrl())
        .isEqualTo("https://www.ftc.go.kr/bizCommPop.do?wrkr_no=0000000000");
  }

  @Test
  void updatePageSettings_preservesBusinessInfoWhenRequestOmitsIt() {
    // given: 섹션만 수정하는 요청은 새 규칙에 맞지 않는 legacy footer도 재검증하지 않는다.
    Academy academy = academy("old/logo.jpg", null);
    AcademyBusinessInfo businessInfo = new AcademyBusinessInfo(
        "개별 학원", "대표", "주소", "legacy-registration", "신고번호",
        "https://legacy.example/ftc-link", "000-0000-0000", "academy@example.com");
    academy.updatePageSettings(new AcademyPageSettings(
        null, null, null, null, Map.of("sec-effect", true), null, null, businessInfo));
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, null, null, Map.of("sec-effect", false), null, null);

    // when
    AcademyResponse response = service.updatePageSettings(principal, request);

    // then
    assertThat(response.pageSettings().businessInfo()).isEqualTo(businessInfo);
  }

  @Test
  void updatePageSettings_rejectsInvalidRegistrationWithoutMutatingExistingSettings() {
    // given: partial write validation failure must leave the entity snapshot unchanged.
    Academy academy = academy("old/logo.jpg", null);
    AcademyPageSettings existing = new AcademyPageSettings(
        "https://blog.example.com", null, null, null);
    academy.updatePageSettings(existing);
    AcademyBusinessInfo invalid = new AcademyBusinessInfo(
        "마스킹 학원", "대표자명", "00시 00구", "000-00-0000", "신고번호",
        "https://example.invalid/ignored", "000-0000-0000", "academy@example.com");
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, null, null, null, null, invalid);

    // when & then
    assertThatThrownBy(() -> service.updatePageSettings(principal, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("숫자 10자리");
    assertThat(academy.getPageSettings()).isEqualTo(existing);
  }

  @Test
  void updatePageSettings_rejectsOversizedBusinessFieldWithoutMutatingExistingSettings() {
    // given: 직접 API를 호출한 client가 Builder maxlength를 우회한다.
    Academy academy = academy("old/logo.jpg", null);
    AcademyPageSettings existing = new AcademyPageSettings(
        "https://blog.example.com", null, null, null);
    academy.updatePageSettings(existing);
    AcademyBusinessInfo oversized = new AcademyBusinessInfo(
        "가".repeat(101), "대표자명", "00시 00구", "000-00-00000", "신고번호",
        null, "000-0000-0000", "academy@example.com");
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, null, null, null, null, oversized);

    // when & then
    assertThatThrownBy(() -> service.updatePageSettings(principal, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("100자 이하");
    assertThat(academy.getPageSettings()).isEqualTo(existing);
  }

  @Test
  void create_rejectsInvalidBusinessInfoEmail() {
    // given: 공개 footer 이메일은 mailto 링크로 렌더되므로 잘못된 형식을 거부한다.
    AcademyBusinessInfo businessInfo = new AcademyBusinessInfo(
        "개별 학원", "대표", "주소", "000-00-00000", "신고번호",
        "https://example.invalid/ignored", "000-0000-0000", "not-an-email");
    AcademyPageSettings settings = new AcademyPageSettings(
        null, null, null, null, null, null, null, businessInfo);

    // when & then
    assertThatThrownBy(() -> service.create(principal, validRequestWithPageSettings(settings), null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이메일");
  }

  @Test
  void create_derivesBusinessInfoUrlFromRegistrationAndIgnoresIncomingUrl() throws Exception {
    // given: 신규 발행 payload에는 별도 URL이 들어와도 사업자등록번호만 canonical source다.
    AcademyBusinessInfo businessInfo = new AcademyBusinessInfo(
        " 마스킹 학원 ", " 대표자명 ", " 00시 00구 ", "000-00-00000", "0000-00-0000-0000",
        "https://example.invalid/ignored", "000-0000-0000", "academy@example.com");
    AcademyPageSettings settings = new AcademyPageSettings(
        null, null, null, null, null, null, null, businessInfo);
    User tutor = tutor();
    Subject subject = subject();
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject));
    when(academyRepository.existsBySlug("테스트-academy")).thenReturn(false);
    when(fileStorage.saveImage(eq(10L), eq("default-academy-logo.jpg"), any(byte[].class)))
        .thenReturn("default/logo.jpg");
    when(academyRepository.save(any(Academy.class))).thenAnswer(invocation -> {
      Academy saved = invocation.getArgument(0);
      ReflectionTestUtils.setField(saved, "academyId", 100L);
      return saved;
    });
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    AcademyResponse response = service.create(
        principal, validRequestWithPageSettings(settings), null, null);

    // then
    AcademyBusinessInfo saved = response.pageSettings().businessInfo();
    assertThat(saved.businessName()).isEqualTo("마스킹 학원");
    assertThat(saved.businessInfoUrl())
        .isEqualTo("https://www.ftc.go.kr/bizCommPop.do?wrkr_no=0000000000");
  }

  @Test
  void create_rejectsNonBlankRegistrationWithoutTenDigitsBeforePersistence() {
    // given: 숫자가 10자리가 아닌 사업자등록번호.
    AcademyBusinessInfo businessInfo = new AcademyBusinessInfo(
        "마스킹 학원", "대표자명", "00시 00구", "000-00-0000", "신고번호",
        "https://example.invalid/ignored", "000-0000-0000", "academy@example.com");
    AcademyPageSettings settings = new AcademyPageSettings(
        null, null, null, null, null, null, null, businessInfo);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.create(
        principal, validRequestWithPageSettings(settings), null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("숫자 10자리");
    verify(academyRepository, never()).save(any());
  }

  @Test
  void update_preservesExistingBusinessInfoWhenFullRequestOmitsIt() {
    // given: 구형 full-update client가 businessInfo를 생략하면 invalid legacy 값도 그대로 보존한다.
    Academy academy = academy("old/logo.jpg", null);
    AcademyBusinessInfo existing = new AcademyBusinessInfo(
        "기존 학원", "대표자명", "00시 00구", "legacy-registration", "신고번호",
        "https://legacy.example/ftc-link", "000-0000-0000", "academy@example.com");
    academy.updatePageSettings(new AcademyPageSettings(
        "https://blog.example.com", null, null, null, null, null, null, existing));
    AcademyPageSettings requestedSettings = new AcademyPageSettings(
        "https://new-blog.example.com", null, null, null, null, null, null, null);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject()));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    AcademyResponse response = service.update(
        principal, validRequestWithPageSettings(requestedSettings), null, null);

    // then
    assertThat(response.pageSettings().blogUrl()).isEqualTo("https://new-blog.example.com");
    assertThat(response.pageSettings().businessInfo()).isEqualTo(existing);
  }

  @Test
  void update_preservesExistingBuilderDraftWhenFullRequestOmitsIt() {
    // given: 원장 소개/철학 등 builderDraft는 별도 UI(원장 영상 선택 등)가 전체 pageSettings를
    // 다시 조립해 이 엔드포인트로 보낼 때 실수로 null을 보내도 통째로 사라지면 안 된다.
    // (updatePageSettings 부분 업데이트 엔드포인트가 이미 지키는 것과 같은 보존 규칙.)
    Academy academy = academy("old/logo.jpg", null);
    Map<String, String> existingDraft = Map.of(
        "principalName", "박정민 원장", "principalDesc", "학생 개개인에 맞춘 지도");
    academy.updatePageSettings(new AcademyPageSettings(
        null, null, null, null, null, null, existingDraft, null));
    AcademyPageSettings requestedSettings = new AcademyPageSettings(
        "https://new-blog.example.com", null, null, null, null, null, null, null);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject()));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    AcademyResponse response = service.update(
        principal, validRequestWithPageSettings(requestedSettings), null, null);

    // then
    assertThat(response.pageSettings().blogUrl()).isEqualTo("https://new-blog.example.com");
    assertThat(response.pageSettings().builderDraft()).isEqualTo(existingDraft);
  }

  @Test
  void update_storesExplicitBlankBusinessFieldsAndClearsDerivedUrl() {
    // given: 7개 direct field를 명시적으로 blank로 보내 clear한다.
    Academy academy = academy("old/logo.jpg", null);
    AcademyBusinessInfo blanks = new AcademyBusinessInfo("", "", "", "", "", "ignored", "", "");
    AcademyPageSettings requestedSettings = new AcademyPageSettings(
        null, null, null, null, null, null, null, blanks);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject()));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    AcademyResponse response = service.update(
        principal, validRequestWithPageSettings(requestedSettings), null, null);

    // then
    AcademyBusinessInfo saved = response.pageSettings().businessInfo();
    assertThat(saved.businessName()).isEmpty();
    assertThat(saved.businessRegistrationNumber()).isEmpty();
    assertThat(saved.businessInfoUrl()).isNull();
  }

  @Test
  void update_rejectsInvalidBusinessInfoWithoutMutatingExistingPageSettings() {
    // given: invalid full-update는 기존 entity settings를 건드리면 안 된다.
    Academy academy = academy("old/logo.jpg", null);
    AcademyPageSettings existing = new AcademyPageSettings(
        "https://blog.example.com", null, null, null);
    academy.updatePageSettings(existing);
    AcademyBusinessInfo invalid = new AcademyBusinessInfo(
        "학원", "대표", "주소", "invalid", "신고번호", null, "000-0000-0000", "academy@example.com");
    AcademyPageSettings requestedSettings = new AcademyPageSettings(
        null, null, null, null, null, null, null, invalid);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));

    // when & then
    assertThatThrownBy(() -> service.update(
        principal, validRequestWithPageSettings(requestedSettings), null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("숫자 10자리");
    assertThat(academy.getPageSettings()).isEqualTo(existing);
  }

  @Test
  void updatePageSettings_replacesSectionOrderAndPreservesVisibilityAndDraft() {
    // given: 기존 URL/표시토글/builderDraft가 있고, 레이아웃 탭에서 순서만 저장한다.
    Academy academy = academy("old/logo.jpg", null);
    Map<String, String> draft = Map.of("introHeading", "기존 소개");
    AcademyPageSettings current = new AcademyPageSettings(
        "https://blog.example.com",
        "https://store.example.com",
        "https://www.youtube.com/watch?v=abc123XYZ09",
        Map.of("sec-effect", false),
        List.of("sec-hero", "sec-intro", "sec-principal", "sec-philosophy", "sec-videos", "sec-reviews", "sec-lessons", "sec-curriculum",
            "sec-effect", "sec-digitaltwin", "sec-market", "sec-payment", "sec-social"),
        draft);
    academy.updatePageSettings(current);
    // 신규 sec-featured를 뺀 옛 길이의 순서를 보낸다(구형/캐시된 클라이언트 시나리오).
    List<String> reordered = List.of("sec-hero", "sec-intro", "sec-lessons", "sec-principal", "sec-philosophy", "sec-videos", "sec-reviews", "sec-curriculum",
        "sec-effect", "sec-digitaltwin", "sec-market", "sec-payment", "sec-social");
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, null, null, null, reordered);

    // when
    AcademyResponse response = service.updatePageSettings(principal, request);

    // then — URL/표시토글/draft는 보존하고, 빠진 관리 섹션(sec-featured)은 canonical 위치에 채워 넣는다.
    assertThat(response.pageSettings().blogUrl()).isEqualTo("https://blog.example.com");
    assertThat(response.pageSettings().sectionVisibility()).containsEntry("sec-effect", false);
    assertThat(response.pageSettings().builderDraft()).isEqualTo(draft);
    assertThat(response.pageSettings().sectionOrder())
        .containsExactlyInAnyOrderElementsOf(AcademyService.DEFAULT_SECTION_ORDER)
        .startsWith("sec-hero", "sec-featured")
        .containsSubsequence("sec-intro", "sec-lessons", "sec-principal");
  }

  @Test
  void updatePageSettings_acceptsSectionOrderContainingVideos() {
    // given: sec-videos가 없던 기존 Academy가 이 섹션을 포함한 순서를 저장한다.
    // validateSectionOrder는 size-lock이라 DEFAULT_SECTION_ORDER에 sec-videos가 등록되어
    // 있지 않으면 400으로 거부된다(빌더의 순서 저장이 통째로 막힌다).
    Academy academy = academy("old/logo.jpg", null);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());
    List<String> withVideos = List.of("sec-hero", "sec-intro", "sec-principal", "sec-philosophy", "sec-videos",
        "sec-reviews", "sec-lessons", "sec-curriculum", "sec-effect", "sec-digitaltwin",
        "sec-market", "sec-payment", "sec-social");
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, null, null, null, withVideos);

    // when
    AcademyResponse response = service.updatePageSettings(principal, request);

    // then
    assertThat(response.pageSettings().sectionOrder()).contains("sec-videos");
    assertThat(AcademyService.DEFAULT_SECTION_ORDER).contains("sec-videos");
  }

  @Test
  void updatePageSettings_backfillsNewlyAddedSectionWhenStaleClientOmitsIt() {
    // given: 서버에 섹션을 추가한 뒤 옛 JS를 캐시한 클라이언트가 그 섹션 없이 순서를 저장한다.
    // 예전 size-lock에서는 이 요청 전체가 400이었다(빌더 레이아웃 저장이 통째로 막힘).
    Academy academy = academy("old/logo.jpg", null);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());
    List<String> withoutFeatured = new ArrayList<>(AcademyService.DEFAULT_SECTION_ORDER);
    withoutFeatured.remove("sec-featured");
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, null, null, null, withoutFeatured);

    // when
    AcademyResponse response = service.updatePageSettings(principal, request);

    // then: 400 없이 저장되고, 빠진 섹션은 canonical 위치(sec-hero 바로 뒤)에 채워진다.
    assertThat(response.pageSettings().sectionOrder())
        .containsExactlyElementsOf(AcademyService.DEFAULT_SECTION_ORDER);
  }

  @Test
  void updatePageSettings_acceptsOptInStorefrontSectionVisibilityToggle() {
    // given: Education Storefront 신규 섹션은 명시적 true로 켤 수 있어야 한다.
    Academy academy = academy("old/logo.jpg", null);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, null, null, Map.of("sec-featured", true, "sec-digitaltwin", true), null);

    // when
    AcademyResponse response = service.updatePageSettings(principal, request);

    // then
    assertThat(response.pageSettings().sectionVisibility())
        .containsEntry("sec-featured", true)
        .containsEntry("sec-digitaltwin", true);
  }

  @Test
  void create_acceptsEducationStorefrontBuilderDraftKeys() throws Exception {
    // given: Featured / AI Native 교육 과정 섹션의 generic 입력 키.
    User tutor = tutor();
    Subject subject = subject();
    Map<String, String> draft = Map.of(
        "featuredEyebrow", "2026 FALL",
        "featuredTitle", "우리 학교 2학기 내신",
        "featuredItem1", "압구정중",
        "aiProcessTitle", "경험 있는 교육자 + AI",
        "aiProcessStep1", "학교 자료",
        // 섹션별 독립 제목 키
        "videosSectionTitle", "에듀홀릭의 실제 수업 현장",
        "socialSectionTitle", "학원 소식과 오시는 길");
    AcademyPageSettings pageSettings = new AcademyPageSettings(
        null, null, null, null, draft);
    AcademyUpsertRequest request = validRequestWithPageSettings(pageSettings);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject));
    when(academyRepository.existsBySlug("테스트-academy")).thenReturn(false);
    when(fileStorage.saveImage(eq(10L), eq("default-academy-logo.jpg"), any(byte[].class)))
        .thenReturn("secret/default-logo.jpg");
    when(academyRepository.save(any(Academy.class))).thenAnswer(invocation -> {
      Academy academy = invocation.getArgument(0);
      ReflectionTestUtils.setField(academy, "academyId", 100L);
      return academy;
    });
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    AcademyResponse response = service.create(principal, request, null, null);

    // then
    assertThat(response.pageSettings().builderDraft())
        .containsEntry("featuredEyebrow", "2026 FALL")
        .containsEntry("featuredItem1", "압구정중")
        .containsEntry("aiProcessStep1", "학교 자료")
        .containsEntry("videosSectionTitle", "에듀홀릭의 실제 수업 현장")
        .containsEntry("socialSectionTitle", "학원 소식과 오시는 길");
  }

  @Test
  void updatePageSettings_backfillsPhilosophyIntoLegacyStoredSectionOrderWhenOnlyVisibilityChanges() {
    // given: sec-philosophy 추가 전 저장된 sectionOrder가 있어도 토글 저장이 막히면 안 된다.
    Academy academy = academy("old/logo.jpg", null);
    AcademyPageSettings legacy = new AcademyPageSettings(
        null,
        null,
        null,
        Map.of("sec-intro", true),
        List.of("sec-hero", "sec-intro", "sec-principal", "sec-videos", "sec-reviews", "sec-lessons",
            "sec-curriculum", "sec-effect", "sec-digitaltwin", "sec-market", "sec-payment", "sec-social"),
        Map.of("introHeading", "기존"));
    academy.updatePageSettings(legacy);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, null, null, Map.of("sec-philosophy", false), null);

    // when
    AcademyResponse response = service.updatePageSettings(principal, request);

    // then
    assertThat(response.pageSettings().sectionVisibility()).containsEntry("sec-philosophy", false);
    assertThat(response.pageSettings().sectionOrder()).containsSubsequence("sec-principal", "sec-philosophy", "sec-videos");
  }

  @Test
  void updatePageSettings_acceptsVideosSectionVisibilityToggle() {
    // given: 빌더의 sec-videos ON/OFF 토글이 섹션 ID 검증을 통과해야 한다.
    Academy academy = academy("old/logo.jpg", null);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, null, null, Map.of("sec-videos", false), null);

    // when
    AcademyResponse response = service.updatePageSettings(principal, request);

    // then
    assertThat(response.pageSettings().sectionVisibility()).containsEntry("sec-videos", false);
  }

  @Test
  void updatePageSettings_rejectsSectionOrderWithoutHeroAtFirst() {
    // given: hero가 첫 번째가 아닌 순서는 서버에서 거부한다.
    Academy academy = academy("old/logo.jpg", null);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, null, null, null,
        List.of("sec-intro", "sec-hero", "sec-principal", "sec-philosophy", "sec-videos", "sec-reviews", "sec-lessons", "sec-curriculum",
            "sec-effect", "sec-digitaltwin", "sec-market", "sec-payment", "sec-social"));

    // when & then
    assertThatThrownBy(() -> service.updatePageSettings(principal, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("히어로");
  }

  @Test
  void updatePageSettings_rejectsNullSectionOrderElementAsIllegalArgumentException() {
    // given: JSON 배열에 null 섹션 ID가 들어와도 copy 단계 NPE가 아니라 검증 예외로 거부한다.
    Academy academy = academy("old/logo.jpg", null);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, null, null, null,
        Arrays.asList("sec-hero", "sec-intro", null, "sec-philosophy", "sec-videos", "sec-lessons", "sec-reviews", "sec-curriculum",
            "sec-effect", "sec-digitaltwin", "sec-market", "sec-payment", "sec-social"));

    // when & then
    assertThatThrownBy(() -> service.updatePageSettings(principal, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("빈 섹션 ID");
  }

  @Test
  void updatePageSettings_rejectsDuplicateOrMissingSectionOrderIds() {
    // given: 중복이 있으면 전체 관리 섹션 순서를 보존할 수 없으므로 거부한다.
    Academy academy = academy("old/logo.jpg", null);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, null, null, null,
        List.of("sec-hero", "sec-intro", "sec-intro", "sec-philosophy", "sec-videos", "sec-lessons", "sec-reviews", "sec-curriculum",
            "sec-effect", "sec-digitaltwin", "sec-market", "sec-payment", "sec-social"));

    // when & then
    assertThatThrownBy(() -> service.updatePageSettings(principal, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("중복");
  }

  @Test
  void updatePageSettings_rejectsInvalidSectionId() {
    // given
    Academy academy = academy("old/logo.jpg", null);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, null, null, Map.of("bad", false), null);

    // when & then
    assertThatThrownBy(() -> service.updatePageSettings(principal, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("섹션 ID");
  }

  @Test
  void update_keepsExistingLogoAndChecksOwnerWhenLogoIsNotProvided() throws Exception {
    // given
    Academy academy = academy("old/logo.jpg", null);
    AcademyUpsertRequest request = validRequest();
    Subject subject = subject();
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    AcademyResponse response = service.update(principal, request, null, null);

    // then
    assertThat(response.logoUrl()).isEqualTo("/api/academies/테스트-academy/logo");
    assertThat(response.handle()).isEqualTo("test-academy");
    assertThat(academy.getLogoStorageKey()).isEqualTo("old/logo.jpg");
    verify(authResolver).verifyOwnsAcademy(academy);
    verify(imageSanitizer, never()).sanitize(any(MultipartFile.class));
    verify(regionRepository).deleteByAcademy_AcademyId(100L);
  }

  @Test
  void update_flushesRegionDeletionBeforeSavingNewRegions() throws Exception {
    // given: 기존 지역이 있는 academy를 수정한다. 기존 지역 삭제(delete)가 새 지역
    // 저장(saveAll)보다 먼저 DB에 반영(flush)되지 않으면 unique 제약과 충돌한다.
    Academy academy = academy("old/logo.jpg", null);
    AcademyUpsertRequest request = validRequest();
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject()));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    service.update(principal, request, null, null);

    // then: delete → flush → saveAll 순서가 보장돼야 한다.
    InOrder inOrder = inOrder(regionRepository);
    inOrder.verify(regionRepository).deleteByAcademy_AcademyId(100L);
    inOrder.verify(regionRepository).flush();
    inOrder.verify(regionRepository).saveAll(anyList());
  }

  @Test
  void readPublicLogo_readsStoredImageWithoutExposingStorageKey() throws Exception {
    // given
    Academy academy = academy("secret/logo.jpg", null);
    when(academyRepository.findBySlugAndStatusAndVisibleTrue("테스트-academy", AcademyStatus.PUBLISHED))
        .thenReturn(Optional.of(academy));
    when(fileStorage.readPage("secret/logo.jpg", "IMAGE", 1)).thenReturn(new byte[]{1, 2, 3});

    // when
    AcademyService.ServedImage served = service.readPublicLogo("테스트-academy");

    // then
    assertThat(served.bytes()).containsExactly(1, 2, 3);
    assertThat(served.contentType()).isEqualTo("image/jpeg");
  }

  @Test
  void readPublicLogo_servesGifContentTypeWhenStoredAsGif() throws Exception {
    // given: 애니메이션 GIF로 저장된 로고 (저장 키 확장자가 .gif)
    Academy academy = academy("secret/logo.gif", null);
    when(academyRepository.findBySlugAndStatusAndVisibleTrue("테스트-academy", AcademyStatus.PUBLISHED))
        .thenReturn(Optional.of(academy));
    when(fileStorage.readPage("secret/logo.gif", "IMAGE", 1)).thenReturn(new byte[]{7, 8});

    // when
    AcademyService.ServedImage served = service.readPublicLogo("테스트-academy");

    // then: image/jpeg로 내리면 브라우저가 움직이는 로고를 렌더링하지 못한다
    assertThat(served.contentType()).isEqualTo("image/gif");
    assertThat(served.bytes()).containsExactly(7, 8);
  }

  @Test
  void readPublicCover_throwsNotFoundWhenCoverIsMissing() {
    // given
    Academy academy = academy("logo.jpg", null);
    when(academyRepository.findBySlugAndStatusAndVisibleTrue("테스트-academy", AcademyStatus.PUBLISHED))
        .thenReturn(Optional.of(academy));

    // when & then
    assertThatThrownBy(() -> service.readPublicCover("테스트-academy"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void create_persistsHandleWhenValid() throws Exception {
    // given: 유효한 handle을 담은 생성 요청.
    User tutor = tutor();
    Subject subject = subject();
    AcademyUpsertRequest request = requestWithHandle("minji-eng");
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject));
    when(academyRepository.existsByHandle("minji-eng")).thenReturn(false);
    when(fileStorage.saveImage(eq(10L), eq("default-academy-logo.jpg"), any(byte[].class)))
        .thenReturn("default/logo.jpg");
    ArgumentCaptor<Academy> academyCaptor = ArgumentCaptor.forClass(Academy.class);
    when(academyRepository.save(academyCaptor.capture())).thenAnswer(invocation -> {
      Academy academy = invocation.getArgument(0);
      ReflectionTestUtils.setField(academy, "academyId", 100L);
      return academy;
    });
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    AcademyResponse response = service.create(principal, request, null, null);

    // then
    assertThat(academyCaptor.getValue().getHandle()).isEqualTo("minji-eng");
    assertThat(response.handle()).isEqualTo("minji-eng");
  }

  @Test
  void create_autoGeneratesTemporaryHandleWhenHandleIsBlank() throws Exception {
    // given: 랜딩 발행 JSON처럼 handle 없이 생성 요청이 들어온다.
    User tutor = tutor();
    Subject subject = subject();
    AcademyUpsertRequest request = requestWithHandle(null);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject));
    when(fileStorage.saveImage(eq(10L), eq("default-academy-logo.jpg"), any(byte[].class)))
        .thenReturn("default/logo.jpg");
    when(academyRepository.existsByHandle(anyString())).thenReturn(false);
    ArgumentCaptor<Academy> academyCaptor = ArgumentCaptor.forClass(Academy.class);
    when(academyRepository.save(academyCaptor.capture())).thenAnswer(invocation -> {
      Academy academy = invocation.getArgument(0);
      ReflectionTestUtils.setField(academy, "academyId", 100L);
      return academy;
    });
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    AcademyResponse response = service.create(principal, request, null, null);

    // then
    assertThat(response.handle()).matches("^academy-[a-z0-9]{6,22}$");
    assertThat(academyCaptor.getValue().getHandle()).isEqualTo(response.handle());
    verify(academyRepository).existsByHandle(response.handle());
  }

  @Test
  void update_allowsChangingHandleWhenAvailable() throws Exception {
    // given: 기존 academy의 임시 handle을 사용자가 예쁜 주소로 변경한다.
    Academy academy = academy("old/logo.jpg", null);
    AcademyUpsertRequest request = requestWithHandle("minji-eng");
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(academyRepository.findByHandle("minji-eng")).thenReturn(Optional.empty());
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject()));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    AcademyResponse response = service.update(principal, request, null, null);

    // then
    assertThat(academy.getHandle()).isEqualTo("minji-eng");
    assertThat(response.handle()).isEqualTo("minji-eng");
    verify(academyHandleHistoryService).recordChange(academy, "test-academy", "minji-eng");
  }

  @Test
  void update_keepsSameHandleWithoutDuplicateLookup() throws Exception {
    // given: 기존 handle과 같은 값을 저장한다.
    Academy academy = academy("old/logo.jpg", null);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(subjectRepository.findAllById(Set.of(1))).thenReturn(List.of(subject()));
    when(regionRepository.findByAcademy_AcademyIdOrderByRegionIdAsc(100L)).thenReturn(List.of());

    // when
    AcademyResponse response = service.update(principal, validRequest(), null, null);

    // then
    assertThat(response.handle()).isEqualTo("test-academy");
    verify(academyRepository, never()).findByHandle(anyString());
  }

  @Test
  void update_rejectsDuplicateHandleOwnedByAnotherAcademy() {
    // given: 바꾸려는 handle을 다른 academy가 이미 사용 중이다.
    Academy academy = academy("old/logo.jpg", null);
    Academy other = academy("other/logo.jpg", null);
    ReflectionTestUtils.setField(other, "academyId", 200L);
    other.updateHandle("minji-eng");
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(academyRepository.findByHandle("minji-eng")).thenReturn(Optional.of(other));

    // when & then
    assertThatThrownBy(() -> service.update(principal, requestWithHandle("minji-eng"), null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미 사용");
  }

  @Test
  void create_rejectsUppercaseHandle() {
    // given: 대문자 포함 handle은 형식 위반.
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.create(principal, requestWithHandle("MinjiEng"), null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("handle");
    verify(academyRepository, never()).save(any());
  }

  @Test
  void create_rejectsTooShortHandle() {
    // given: 2자 handle은 정규식(최소 3자) 위반.
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.create(principal, requestWithHandle("ab"), null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("handle");
    verify(academyRepository, never()).save(any());
  }

  @Test
  void create_rejectsReservedHandle() {
    // given: 예약어 handle은 서브도메인 충돌 방지로 차단.
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.create(principal, requestWithHandle("admin"), null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("예약");
    verify(academyRepository, never()).save(any());
  }

  @Test
  void create_rejectsDuplicateHandle() {
    // given: 이미 사용 중인 handle.
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.empty());
    when(academyRepository.existsByHandle("taken")).thenReturn(true);

    // when & then
    assertThatThrownBy(() -> service.create(principal, requestWithHandle("taken"), null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미 사용");
    verify(academyRepository, never()).save(any());
  }

  @Test
  void isHandleAvailable_trueWhenAcceptableAndNotTaken() {
    // given
    when(academyRepository.findByHandle("free-handle")).thenReturn(Optional.empty());

    // when
    boolean available = service.isHandleAvailable(principal, "free-handle");

    // then
    assertThat(available).isTrue();
    verify(authResolver).requireTutor(principal);
  }

  @Test
  void isHandleAvailable_trueWhenOwnedByCurrentTutor() {
    // given: setup 화면에서 자기 기존 handle을 체크하면 사용 가능으로 본다.
    Academy academy = academy("old/logo.jpg", null);
    when(academyRepository.findByHandle("test-academy")).thenReturn(Optional.of(academy));

    // when & then
    assertThat(service.isHandleAvailable(principal, "test-academy")).isTrue();
  }

  @Test
  void isHandleAvailable_falseWhenTakenByAnotherTutor() {
    // given
    Academy academy = academy("old/logo.jpg", null);
    ReflectionTestUtils.setField(academy.getTutor(), "userId", 99L);
    academy.updateHandle("taken");
    when(academyRepository.findByHandle("taken")).thenReturn(Optional.of(academy));

    // when & then
    assertThat(service.isHandleAvailable(principal, "taken")).isFalse();
  }

  @Test
  void isHandleAvailable_falseWhenFormatInvalid() {
    // given: 형식 위반이면 DB 조회 없이 false.
    // when & then
    assertThat(service.isHandleAvailable(principal, "Bad_Handle")).isFalse();
    verify(academyRepository, never()).findByHandle(any());
  }

  private AcademyUpsertRequest requestWithHandle(String handle) {
    return new AcademyUpsertRequest(
        "테스트 Academy",
        handle,
        "소개",
        Set.of(1),
        Set.of(TargetGroup.MIDDLE),
        List.of(new AcademyRegionRequest("서울시", null)),
        null);
  }

  private AcademyUpsertRequest validRequest() {
    return new AcademyUpsertRequest(
        "테스트 Academy",
        "test-academy",
        "소개",
        Set.of(1),
        Set.of(TargetGroup.MIDDLE),
        List.of(new AcademyRegionRequest("서울시", null)),
        null);
  }

  private AcademyUpsertRequest validRequestWithPageSettings(AcademyPageSettings pageSettings) {
    return new AcademyUpsertRequest(
        "테스트 Academy",
        "test-academy",
        "소개",
        Set.of(1),
        Set.of(TargetGroup.MIDDLE),
        List.of(new AcademyRegionRequest("서울시", null)),
        pageSettings);
  }

  private User tutor() {
    User tutor = User.builder()
        .loginId("tutor1")
        .password("pw")
        .name("강사")
        .role(Role.TUTOR)
        .build();
    ReflectionTestUtils.setField(tutor, "userId", 10L);
    return tutor;
  }

  private Subject subject() {
    Subject subject = Subject.builder()
        .nameEn("english")
        .nameKo("영어")
        .build();
    ReflectionTestUtils.setField(subject, "subjectId", 1);
    return subject;
  }

  private static void assertJpeg(byte[] bytes) {
    assertThat(bytes).isNotEmpty();
    assertThat(bytes[0] & 0xFF).isEqualTo(0xFF);
    assertThat(bytes[1] & 0xFF).isEqualTo(0xD8);
  }

  private Academy academy(String logoStorageKey, String coverStorageKey) {
    Academy academy = Academy.builder()
        .tutor(tutor())
        .slug("테스트-academy")
        .handle("test-academy")
        .name("테스트 Academy")
        .logoStorageKey(logoStorageKey)
        .coverStorageKey(coverStorageKey)
        .intro("소개")
        .status(AcademyStatus.PUBLISHED)
        .subjects(new HashSet<>(Set.of(subject())))
        .targetGroups(new HashSet<>(Set.of(TargetGroup.MIDDLE)))
        .build();
    ReflectionTestUtils.setField(academy, "academyId", 100L);
    return academy;
  }
}
