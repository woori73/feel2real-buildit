package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
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
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademyService {

  private static final int MAX_REGION_COUNT = 3;
  private static final int MAX_INTRO_LENGTH = 1000;
  private static final int MAX_PAGE_SETTINGS_URL_LENGTH = 500;
  private static final int MAX_BUSINESS_NAME_LENGTH = 100;
  private static final int MAX_BUSINESS_REPRESENTATIVE_LENGTH = 100;
  private static final int MAX_BUSINESS_ADDRESS_LENGTH = 300;
  private static final int MAX_BUSINESS_NUMBER_LENGTH = 50;
  private static final int MAX_BUSINESS_PHONE_LENGTH = 30;
  private static final int MAX_BUSINESS_EMAIL_LENGTH = 254;
  private static final String FTC_BUSINESS_INFO_URL =
      "https://www.ftc.go.kr/bizCommPop.do?wrkr_no=";
  private static final int MAX_BUILDER_DRAFT_VALUE_LENGTH = 200;
  private static final int MAX_BUILDER_DRAFT_LONG_VALUE_LENGTH = 2000;
  private static final Set<String> BUILDER_DRAFT_KEYS = Set.of(
      // 상단 GNB 학원명. 비우면 academy.name을 쓴다(간판용 짧은 이름을 따로 두기 위한 값).
      "headerTitle",
      // 히어로 학원명 아래 강조 문구. 비우면 대표 과목에서 파생한다.
      "heroAccent",
      // 히어로 대표 수업. lessonId를 선택 순서대로 쉼표 결합하며 최대 4개를 저장한다.
      "featuredLessonIds",
      "introEnglishName",
      "introTextbookTitle",
      "introTextbookPath",
      "introHeading",
      "introMotto",
      "introBodySecondary",
      "introFeatures",
      "philosophyKeyword",
      "philosophyTitle",
      "philosophyItem1",
      "philosophyItem2",
      "philosophyItem3",
      "philosophyItem4",
      "curriculumTitle",
      "curriculumSubtitle",
      "monthlyTuition",
      "scoreImprovement",
      "operationYears",
      "principalName",
      "principalRole",
      "principalDesc",
      "principalQuote",
      "principalCareer",
      "heroVideoId",
      "principalVideoId",
      "principalYoutubeUrl",
      "principalVideoOrientation",
      // 섹션별 독립 제목. 미설정이면 프런트가 학원명 파생값/고정 문구로 fallback한다(마이그레이션 불필요).
      "videosSectionTitle",
      "socialSectionTitle",
      // ── Education Storefront: Featured / Seasonal Program (sec-featured) ──
      // 전부 재사용 가능한 generic 키다. 학교명·연도·학원명을 서버/프런트에 하드코딩하지 않는다.
      "featuredEyebrow",
      "featuredTitle",
      "featuredDescription",
      "featuredItem1",
      "featuredItem2",
      "featuredItem3",
      "featuredItem4",
      "featuredCtaLabel",
      // ── Education Storefront: AI Native Education Process (sec-digitaltwin) ──
      "aiProcessEyebrow",
      "aiProcessTitle",
      "aiProcessDescription",
      "aiProcessStep1",
      "aiProcessStep2",
      "aiProcessStep3",
      "aiProcessStep4",
      "aiProcessStep5",
      "aiProcessNote",
      // ── Education Storefront: Horizontal Content Collection (PHASE 3-B) ──
      // sec-lessons / sec-videos 를 가로 컬렉션(shelf)으로 표시할지. 'horizontal' 이면 opt-in,
      // 그 외/미설정이면 기존 레이아웃(showcase / 재생목록). 다른 학원은 이 키가 없어 불변.
      "lessonsLayout",
      "videosLayout",
      // ── Education Storefront: Mini Lectures (V2-9) ──
      // sec-minilectures 콘텐츠. academy_videos 를 재사용하고, 강의별 제목/학습포인트/대상은
      // JSON 배열 문자열로 이 키에 담는다([{"v":<videoId>,"t":..,"n":..,"g":..}]). 스키마 변경 없음.
      "miniLectures",
      "miniLecturesSectionTitle",
      // Mobile-first brand/typography tokens. Values are registry IDs or bounded pixel numbers.
      "typographyPreset", "headingFont", "bodyFont",
      "logoSizeDesktop", "logoSizeMobile", "headingSizeMobile", "bodySizeMobile",
      // ── Eduholic LMS Phase 1: Builder "LMS 설정" (LmsBuilderSettingsService) ──
      // LMS 전용 엔드포인트가 직접 저장하므로, 일반 Builder 저장이 validateBuilderDraft에서
      // 거부하지 않도록 allowlist에 등록한다. lmsIntro(소개 문구)는 길어질 수 있어 LONG 목록에도 둔다.
      "lmsEnabled", "lmsDisplayName", "lmsMainTitle", "lmsIntro", "lmsSectionName");
  private static final Set<String> LONG_BUILDER_DRAFT_KEYS =
      Set.of(
          "introMotto",
          "introBodySecondary",
          "introFeatures",
          "philosophyTitle",
          "principalDesc",
          "principalQuote",
          "principalCareer",
          "featuredDescription",
          "featuredItem1",
          "featuredItem2",
          "featuredItem3",
          "featuredItem4",
          "aiProcessDescription",
          "aiProcessStep1",
          "aiProcessStep2",
          "aiProcessStep3",
          "aiProcessStep4",
          "aiProcessStep5",
          "aiProcessNote",
          // Mini Lectures 목록 JSON — 강의 수 × (제목/노트/태그)라 200자를 넘는다.
          "miniLectures",
          // LMS 소개 문구 — 문장형이라 200자를 넘을 수 있다.
          "lmsIntro");
  private static final String TEMP_HANDLE_PREFIX = "academy-";
  private static final String TEMP_HANDLE_SALT = "private-tutor-solution-academy-handle-v1";
  private static final String DEFAULT_LOGO_FILENAME = "default-academy-logo.jpg";
  private static final String DEFAULT_LOGO_RESOURCE = "static/img/demo_logo.png";
  static final List<String> DEFAULT_SECTION_ORDER = List.of(
      "sec-hero",
      "sec-featured",
      "sec-intro",
      "sec-principal",
      "sec-philosophy",
      "sec-videos",
      "sec-minilectures",
      "sec-reviews",
      "sec-lessons",
      "sec-curriculum",
      "sec-effect",
      "sec-digitaltwin",
      "sec-market",
      "sec-payment",
      "sec-social");
  private static final Set<String> MANAGED_SECTION_IDS = Set.copyOf(DEFAULT_SECTION_ORDER);
  /**
   * 신규 Education Storefront 섹션. 기존 사이트는 이 키가 없으므로 opt-in(명시적 true)일 때만 렌더된다.
   * (기존 섹션은 반대로 명시적 false일 때만 숨긴다.) 렌더 판정은 프런트 {@code academy_sections.js}가
   * 담당하고, 서버는 순서 목록에만 포함해 레이아웃 저장이 깨지지 않게 한다.
   */
  static final Set<String> OPT_IN_SECTION_IDS =
      Set.of("sec-featured", "sec-digitaltwin", "sec-minilectures");

  private final AcademyRepository academyRepository;
  private final AcademyRegionRepository academyRegionRepository;
  private final LessonProductRepository lessonProductRepository;
  private final LessonApplicationRepository lessonApplicationRepository;
  private final SubjectRepository subjectRepository;
  private final UserRepository userRepository;
  private final FileStorage fileStorage;
  private final ImageSanitizer imageSanitizer;
  private final AuthenticatedUserResolver authResolver;
  private final f2r.tutor_solution.private_tutor_solution_back.config.PublicSiteProperties publicSiteProperties;
  private final AcademyVideoDeletionGuard academyVideoDeletionGuard;
  private final AcademyHandleHistoryService academyHandleHistoryService;

  @Transactional
  public AcademyResponse create(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      AcademyUpsertRequest request,
      MultipartFile logo,
      MultipartFile cover) {
    authResolver.requireTutor(principal);
    if (academyRepository.findByTutor_UserId(principal.userId()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 Academy가 존재합니다.");
    }
    AcademyPageSettings normalizedPageSettings = normalizePageSettingsForWrite(
        request == null ? null : request.pageSettings(), null, false);
    validateCreateRequest(request, normalizedPageSettings);
    String handle = resolveCreateHandle(request.handle(), principal.userId());

    User tutor = userRepository.findById(principal.userId())
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: userId=" + principal.userId()));
    Set<Subject> subjects = loadSubjects(request.subjectIds());
    String slug = generateUniqueSlug(request.name());
    String logoKey = resolveLogoStorageKey(principal.userId(), logo);
    String coverKey = isPresent(cover) ? saveImage(principal.userId(), cover) : null;

    try {
      AuditorContextHolder.set(principal.loginId());
      Academy academy = Academy.builder()
          .tutor(tutor)
          .slug(slug)
          .handle(handle)
          .name(request.name().trim())
          .logoStorageKey(logoKey)
          .coverStorageKey(coverKey)
          .intro(request.intro().trim())
          .status(AcademyStatus.PUBLISHED)
          .subjects(subjects)
          .targetGroups(new HashSet<>(request.targetGroups()))
          .pageSettings(normalizedPageSettings)
          .build();
      Academy saved = academyRepository.save(academy);
      academyHandleHistoryService.recordCreated(saved);
      saveRegions(saved, request.regions());
      return toResponse(saved, false);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public AcademyResponse update(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      AcademyUpsertRequest request,
      MultipartFile logo,
      MultipartFile cover) {
    authResolver.requireTutor(principal);
    Academy academy = academyRepository.findByTutor_UserId(principal.userId())
        .orElseThrow(() -> new IllegalArgumentException("Academy를 찾을 수 없습니다."));
    authResolver.verifyOwnsAcademy(academy);
    AcademyPageSettings normalizedPageSettings = normalizePageSettingsForWrite(
        request == null ? null : request.pageSettings(), academy.getPageSettings(), true);
    boolean businessInfoSupplied = request != null
        && request.pageSettings() != null
        && request.pageSettings().businessInfo() != null;
    validateUpdateRequest(request, academy, logo, normalizedPageSettings, businessInfoSupplied);
    String handle = resolveUpdateHandle(request.handle(), academy);
    String previousHandle = academy.getHandle();

    Set<Subject> subjects = loadSubjects(request.subjectIds());
    try {
      AuditorContextHolder.set(principal.loginId());
      academy.updateHandle(handle);
      academyHandleHistoryService.recordChange(academy, previousHandle, handle);
      academy.updateDetails(request.name().trim(), request.intro().trim(), subjects, new HashSet<>(request.targetGroups()));
      academy.updatePageSettings(normalizedPageSettings);
      if (isPresent(logo)) academy.updateLogo(saveImage(principal.userId(), logo));
      if (isPresent(cover)) academy.updateCover(saveImage(principal.userId(), cover));
      academyRegionRepository.deleteByAcademy_AcademyId(academy.getAcademyId());
      // 기존 region 삭제를 새 region INSERT보다 먼저 DB에 반영한다. flush가 없으면
      // Hibernate가 INSERT를 DELETE보다 먼저 실행해, 변경되지 않은 지역(예: 서울시/ALL)이
      // uk_academy_regions_city_district 유니크 제약과 충돌해 저장이 실패한다.
      academyRegionRepository.flush();
      saveRegions(academy, request.regions());
      return toResponse(academy, false);
    } finally {
      AuditorContextHolder.clear();
    }
  }


  /** Builder 전용 page settings 부분 업데이트. URL null은 기존 값을 유지하고 sectionVisibility만 교체할 수 있다. */
  @Transactional
  public AcademyResponse updatePageSettings(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      AcademyPageSettingsUpdateRequest request) {
    authResolver.requireTutor(principal);
    if (request == null) throw new IllegalArgumentException("페이지 설정 정보가 필요합니다.");
    Academy academy = academyRepository.findByTutor_UserId(principal.userId())
        .orElseThrow(() -> new IllegalArgumentException("Academy를 찾을 수 없습니다."));
    authResolver.verifyOwnsAcademy(academy);

    AcademyPageSettings current = academy.getPageSettings();
    boolean businessInfoSupplied = request.businessInfo() != null;
    AcademyPageSettings merged = new AcademyPageSettings(
        request.blogUrl() == null && current != null ? current.blogUrl() : request.blogUrl(),
        request.storeUrl() == null && current != null ? current.storeUrl() : request.storeUrl(),
        request.naverMapUrl() == null && current != null ? current.naverMapUrl() : request.naverMapUrl(),
        request.heroYoutubeUrl() == null && current != null ? current.heroYoutubeUrl() : request.heroYoutubeUrl(),
        request.sectionVisibility() == null && current != null
            ? current.sectionVisibility()
            : sanitizedSectionVisibility(request.sectionVisibility()),
        request.sectionOrder() == null && current != null
            ? normalizeSectionOrderForWrite(current.sectionOrder())
            : normalizeSectionOrderForWrite(request.sectionOrder()),
        request.builderDraft() == null && current != null
            ? current.builderDraft()
            : sanitizeBuilderDraft(request.builderDraft(), current != null ? current.builderDraft() : null),
        !businessInfoSupplied && current != null
            ? current.businessInfo()
            : normalizeBusinessInfoForWrite(request.businessInfo()));
    validatePageSettings(merged, businessInfoSupplied);
    try {
      AuditorContextHolder.set(principal.loginId());
      academy.updatePageSettings(merged);
      return toResponse(academy, false);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  /**
   * handle 사용 가능 여부(형식 + 예약어 + 중복). setup 폼 실시간 체크용.
   * 형식/예약어 위반이거나 이미 사용 중이면 false.
   */
  public boolean isHandleAvailable(JwtTokenProvider.AuthenticatedPrincipal principal, String handle) {
    authResolver.requireTutor(principal);
    String normalized = handle == null ? null : handle.trim();
    if (!HandlePolicy.isAcceptable(normalized)) {
      return false;
    }
    return academyRepository.findByHandle(normalized)
        .map(academy -> Objects.equals(academy.getTutor().getUserId(), principal.userId()))
        .orElse(true);
  }

  /**
   * create 시 handle을 정규화한다. 명시 입력은 기존처럼 검증하고, 랜딩 발행처럼 비어 있으면
   * tutor userId 기반 임시 handle({@code academy-xxxxxx})을 생성한다.
   */
  private String resolveCreateHandle(String rawHandle, Long tutorUserId) {
    String handle = rawHandle == null ? null : rawHandle.trim();
    if (isBlank(handle)) {
      return generateTemporaryHandle(tutorUserId);
    }
    HandlePolicy.validate(handle);
    if (academyRepository.existsByHandle(handle)) {
      throw new IllegalArgumentException("이미 사용 중인 handle입니다.");
    }
    return handle;
  }

  /** update 시 handle 변경을 허용한다. 빈 값은 거부하고, 기존 값과 같으면 중복 조회 없이 통과한다. */
  private String resolveUpdateHandle(String rawHandle, Academy academy) {
    String handle = rawHandle == null ? null : rawHandle.trim();
    HandlePolicy.validate(handle);
    if (Objects.equals(handle, academy.getHandle())) {
      return handle;
    }
    academyRepository.findByHandle(handle).ifPresent(existing -> {
      if (!Objects.equals(existing.getAcademyId(), academy.getAcademyId())) {
        throw new IllegalArgumentException("이미 사용 중인 handle입니다.");
      }
    });
    return handle;
  }

  private String generateTemporaryHandle(Long tutorUserId) {
    String hash = hashBase36(TEMP_HANDLE_SALT + ":" + tutorUserId);
    int maxFragmentLength = 30 - TEMP_HANDLE_PREFIX.length();
    for (int length = 6; length <= maxFragmentLength && length <= hash.length(); length++) {
      String candidate = TEMP_HANDLE_PREFIX + hash.substring(0, length);
      if (!academyRepository.existsByHandle(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("사용 가능한 임시 handle을 생성하지 못했습니다.");
  }

  private String hashBase36(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return new BigInteger(1, digest).toString(36);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 해시를 사용할 수 없습니다.", e);
    }
  }

  public Optional<AcademyResponse> getMine(JwtTokenProvider.AuthenticatedPrincipal principal) {
    // 공개 스토어프론트(academy_public.js isOwnAcademy)가 방문자 자기소유 여부를 판별하려고
    // 호출한다. 실제 ADMIN principal은 소유 학원이 없어 Optional.empty()(→ 204)로 떨어질 뿐이며,
    // 여기서 403을 던지면 공개 페이지가 admin을 튕겨낸다(운영 회귀). 쓰기 경로는 그대로
    // verifyOwnsAcademy(내부에서 requireTutor)로 TUTOR 본인만 통과한다.
    authResolver.requireTutorOrAdmin(principal);
    return academyRepository.findByTutor_UserId(principal.userId())
        .map(academy -> {
          authResolver.verifyOwnsAcademy(academy);
          return toResponse(academy, false);
        });
  }

  /**
   * TUTOR 본인이 소유한 Academy 엔티티를 반환한다. 레이아웃 생성/수정/삭제 API가
   * 소유권 가드 + 엔티티 확보를 한 번에 하기 위해 사용한다.
   *
   * @throws ResponseStatusException 강사가 아니거나 소유 Academy가 없으면 차단
   */
  public Academy requireMyAcademy(JwtTokenProvider.AuthenticatedPrincipal principal) {
    authResolver.requireTutor(principal);
    Academy academy = academyRepository.findByTutor_UserId(principal.userId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Academy를 찾을 수 없습니다."));
    authResolver.verifyOwnsAcademy(academy);
    return academy;
  }

  /**
   * handle로 지정된 Academy를 소유한 TUTOR만 통과시킨다. 학원 LMS 운영 화면이 사용한다.
   *
   * <p><b>{@link #requireMyAcademy}와 목적이 다르다.</b> 그쪽은 principal로 "내 Academy"를 찾으므로
   * URL의 handle을 아예 보지 않는다 — 학원 B의 URL로 들어온 학원 A 강사에게 A의 화면을 렌더해 주는
   * 조용한 오답이 된다. handle 기반 화면에서는 반드시 이 메서드를 쓴다.
   *
   * <p>미발행/숨김 상태여도 owner는 관리해야 하므로 공개 필터 없이 {@code findByHandle}을 쓴다.
   *
   * @throws ResponseStatusException handle에 해당하는 Academy가 없으면 404
   * @throws org.springframework.security.access.AccessDeniedException 소유자가 아니면 403
   */
  public Academy requireOwnedAcademyByHandle(String handle) {
    Academy academy = academyRepository.findByHandle(handle)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Academy를 찾을 수 없습니다."));
    authResolver.verifyOwnsAcademy(academy);
    return academy;
  }

  /**
   * handle Academy를 소유한 TUTOR의 LMS 관리용 수업 목록을 반환한다.
   *
   * <p>공개 상태와 visible은 편집 가능 여부가 아니다. owner는 DRAFT와 비가시 PUBLISHED도
   * 관리해야 하며, 숨김 처리된 HIDDEN만 제외한다.
   */
  public List<LmsManageLessonResponse> listOwnedLmsLessonsByHandle(String handle) {
    Academy academy = requireOwnedAcademyByHandle(handle);
    return lessonProductRepository
        .findByAcademy_AcademyIdAndStatusInOrderByCreatedAtAscLessonIdAsc(
            academy.getAcademyId(), EnumSet.of(LessonStatus.DRAFT, LessonStatus.PUBLISHED))
        .stream()
        .map(LmsManageLessonResponse::from)
        .toList();
  }

  /**
   * 공개(PUBLISHED·visible) Academy를 handle로 조회한다. 학원 LMS 공개/학생 화면이 사용한다.
   * 미발행 학원의 LMS가 공개로 새지 않도록 owner 화면과 조회 메서드를 의도적으로 분리했다.
   *
   * @throws ResponseStatusException 공개 Academy가 없으면 404
   */
  public Academy getPublishedEntityByHandle(String handle) {
    return academyRepository.findByHandleAndStatusAndVisibleTrue(handle, AcademyStatus.PUBLISHED)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공개 Academy를 찾을 수 없습니다."));
  }

  /**
   * 단일 이미지 슬롯(V940 히어로 / V948 시간표 달력) 저장. {@code storageKey}가 null이면
   * 슬롯을 비워 placeholder로 되돌린다.
   *
   * <p>storage의 실제 파일은 지우지 않고 key만 끊는다 — 로고 교체와 같은 방식이다. 발행본이
   * 캐시된 URL을 아직 들고 있을 수 있어 즉시 물리 삭제하면 그쪽이 깨진다.
   */
  @Transactional
  public void updateHeroMedia(
      JwtTokenProvider.AuthenticatedPrincipal principal, HeroMediaSlot slot, String storageKey) {
    Academy academy = requireMyAcademy(principal);
    AuditorContextHolder.set(principal.loginId());
    academy.updateImageSlot(slot, storageKey);
  }

  /** 슬롯의 storage key. 비어 있으면 null. */
  public static String heroMediaKey(Academy academy, HeroMediaSlot slot) {
    return academy.imageSlotKey(slot);
  }

  /**
   * TUTOR 본인 Academy 삭제.
   *
   * <p>Academy는 tutor_user_id unique 제약 때문에 soft-delete만 하면 같은 강사가 새 Academy를
   * 만들 수 없다. 따라서 수강생이 없는 경우에만 row를 실제 삭제하고, DB FK cascade로 지역/과목/대상/수업을
   * 함께 정리한다. APPROVED 신청/수강생이 있으면 기존 학생-강사 연결 손상을 막기 위해 삭제를 차단한다.
   */
  @Transactional
  public void delete(JwtTokenProvider.AuthenticatedPrincipal principal) {
    authResolver.requireTutor(principal);
    // The same parent-row lock is used by upload-session initiation/quota reservation.
    Academy academy = academyRepository.findForUpdateByTutorUserId(principal.userId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Academy를 찾을 수 없습니다."));
    authResolver.verifyOwnsAcademy(academy);

    if (lessonApplicationRepository.existsByLesson_Academy_AcademyIdAndStatus(
        academy.getAcademyId(), LessonApplicationStatus.APPROVED)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "신청/수강생이 있는 Academy는 삭제할 수 없습니다.");
    }
    academyVideoDeletionGuard.requireFullyRemoved(academy.getAcademyId());

    String logoStorageKey = academy.getLogoStorageKey();
    String coverStorageKey = academy.getCoverStorageKey();
    academyRepository.delete(academy);
    academyRepository.flush();

    fileStorage.delete(logoStorageKey, "IMAGE");
    fileStorage.delete(coverStorageKey, "IMAGE");
  }

  public AcademyResponse getPublic(String slug) {
    Academy academy = findPublished(slug);
    return toResponse(academy, true);
  }

  /**
   * 공개(PUBLISHED·visible) Academy 엔티티를 반환한다. 레이아웃 기반 공개 렌더가
   * {@code resolveLayoutSchema}에 넘길 도메인 엔티티가 필요해 노출한다.
   *
   * @throws ResponseStatusException 공개 Academy가 없으면 404
   */
  public Academy getPublishedEntity(String slug) {
    return findPublished(slug);
  }

  public List<AcademyListResponse> listPublic(
      Integer subjectId,
      TargetGroup targetGroup,
      String city,
      String district) {
    return academyRepository.findPublicList(
            AcademyStatus.PUBLISHED,
            subjectId,
            targetGroup,
            normalizeBlank(city),
            normalizeBlank(district))
        .stream()
        .map(this::toListResponse)
        .toList();
  }

  /**
   * 서빙용 이미지 bytes + Content-Type.
   *
   * <p>Academy는 Content-Type 컬럼이 없으므로 저장 키의 확장자에서 되찾는다
   * ({@link ImageSanitizer#contentTypeForStorageKey}). 확장자를 붙이는 쪽과 읽는 쪽이 같은 규칙을 쓴다.
   */
  public record ServedImage(byte[] bytes, String contentType) {}

  public ServedImage readPublicLogo(String slug) {
    Academy academy = findPublished(slug);
    return servedImage(academy.getLogoStorageKey());
  }

  public ServedImage readPublicCover(String slug) {
    Academy academy = findPublished(slug);
    if (academy.getCoverStorageKey() == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "커버 이미지가 없습니다.");
    }
    return servedImage(academy.getCoverStorageKey());
  }

  private ServedImage servedImage(String storageKey) {
    return new ServedImage(readImage(storageKey), ImageSanitizer.contentTypeForStorageKey(storageKey));
  }

  private Academy findPublished(String slug) {
    return academyRepository.findBySlugAndStatusAndVisibleTrue(slug, AcademyStatus.PUBLISHED)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공개 Academy를 찾을 수 없습니다."));
  }

  private AcademyResponse toResponse(Academy academy, boolean publicOnly) {
    List<AcademyResponse.SubjectSummary> subjects = academy.getSubjects().stream()
        .map(s -> new AcademyResponse.SubjectSummary(s.getSubjectId(), s.getNameKo(), s.getNameEn()))
        .toList();
    List<AcademyRegionResponse> regions = academyRegionRepository
        .findByAcademy_AcademyIdOrderByRegionIdAsc(academy.getAcademyId())
        .stream()
        .map(AcademyRegionResponse::from)
        .toList();
    List<LessonResponse> lessons = (publicOnly
        ? lessonProductRepository.findByAcademy_AcademyIdAndStatusAndVisibleTrueOrderByCreatedAtAscLessonIdAsc(academy.getAcademyId(), LessonStatus.PUBLISHED)
        : lessonProductRepository.findByAcademy_AcademyIdOrderByCreatedAtAscLessonIdAsc(academy.getAcademyId()))
        .stream()
        .filter(lesson -> publicOnly || lesson.getStatus() != LessonStatus.HIDDEN)
        .map(publicOnly ? LessonResponse::fromPublic : LessonResponse::from)
        .toList();
    return AcademyResponse.of(academy, subjects, regions, lessons, publicSiteProperties.baseDomain());
  }

  private AcademyListResponse toListResponse(Academy academy) {
    List<AcademyResponse.SubjectSummary> subjects = academy.getSubjects().stream()
        .map(s -> new AcademyResponse.SubjectSummary(s.getSubjectId(), s.getNameKo(), s.getNameEn()))
        .toList();
    List<AcademyRegionResponse> regions = academyRegionRepository
        .findByAcademy_AcademyIdOrderByRegionIdAsc(academy.getAcademyId())
        .stream()
        .map(AcademyRegionResponse::from)
        .toList();
    return AcademyListResponse.of(academy, subjects, regions);
  }

  private void validateCreateRequest(
      AcademyUpsertRequest request,
      AcademyPageSettings normalizedPageSettings) {
    validateCommonRequest(request, normalizedPageSettings);
  }

  private void validateUpdateRequest(
      AcademyUpsertRequest request,
      Academy academy,
      MultipartFile logo,
      AcademyPageSettings normalizedPageSettings,
      boolean businessInfoSupplied) {
    validateCommonRequest(request, normalizedPageSettings, businessInfoSupplied);
    if (academy.getLogoStorageKey() == null && !isPresent(logo)) {
      throw new IllegalArgumentException("Academy 로고는 필수입니다.");
    }
  }

  private void validateCommonRequest(
      AcademyUpsertRequest request,
      AcademyPageSettings normalizedPageSettings) {
    validateCommonRequest(request, normalizedPageSettings, true);
  }

  private void validateCommonRequest(
      AcademyUpsertRequest request,
      AcademyPageSettings normalizedPageSettings,
      boolean validateBusinessInfo) {
    if (request == null) throw new IllegalArgumentException("Academy 정보가 필요합니다.");
    if (isBlank(request.name())) throw new IllegalArgumentException("Academy 이름은 필수입니다.");
    if (isBlank(request.intro())) throw new IllegalArgumentException("Academy 소개는 필수입니다.");
    if (request.intro().trim().length() > MAX_INTRO_LENGTH) {
      throw new IllegalArgumentException("Academy 소개는 1000자 이하로 입력해야 합니다.");
    }
    if (request.subjectIds() == null || request.subjectIds().isEmpty()) {
      throw new IllegalArgumentException("과목은 1개 이상 선택해야 합니다.");
    }
    if (request.targetGroups() == null || request.targetGroups().isEmpty()) {
      throw new IllegalArgumentException("대상은 1개 이상 선택해야 합니다.");
    }
    validateRegions(request.regions());
    validatePageSettings(normalizedPageSettings, validateBusinessInfo);
  }

  /**
   * 모든 Academy write path가 같은 businessInfo 정규화 규칙을 거치게 한다.
   * Full update에서 pageSettings/businessInfo를 생략한 구형 client는 기존 값을 보존한다.
   */
  private AcademyPageSettings normalizePageSettingsForWrite(
      AcademyPageSettings requested,
      AcademyPageSettings current,
      boolean preserveMissingBusinessInfo) {
    if (requested == null) return preserveMissingBusinessInfo ? current : null;
    AcademyBusinessInfo requestedBusinessInfo = requested.businessInfo();
    boolean preserveCurrentBusinessInfo = preserveMissingBusinessInfo
        && requestedBusinessInfo == null
        && current != null;
    AcademyBusinessInfo businessInfo = preserveCurrentBusinessInfo
        ? current.businessInfo()
        : normalizeBusinessInfoForWrite(requestedBusinessInfo);
    return new AcademyPageSettings(
        requested.blogUrl(),
        requested.storeUrl(),
        requested.naverMapUrl(),
        requested.heroYoutubeUrl(),
        requested.sectionVisibility(),
        normalizeSectionOrderForWrite(requested.sectionOrder()),
        // updatePageSettings()(부분 업데이트)와 같은 보존 규칙: null이면 기존 값을 지키고,
        // 요청이 명시적으로 값(빈 맵 포함)을 보내면 그 값을 따른다. 이 필드만 fallback이
        // 없었다면 이 메서드를 호출하는 어떤 향후 caller가 builderDraft를 생략/null로
        // 보내는 순간 원장 소개·철학·대표수업 등 모든 텍스트가 통째로 사라진다.
        requested.builderDraft() == null && current != null
            ? current.builderDraft()
            : requested.builderDraft(),
        businessInfo);
  }

  /** Incoming URL은 신뢰하지 않고 사업자등록번호 숫자 10자리에서만 canonical URL을 만든다. */
  private AcademyBusinessInfo normalizeBusinessInfoForWrite(AcademyBusinessInfo businessInfo) {
    if (businessInfo == null) return null;
    String registration = businessInfo.businessRegistrationNumber();
    String digits = isBlank(registration) ? "" : registration.replaceAll("\\D", "");
    if (!isBlank(registration) && digits.length() != 10) {
      throw new IllegalArgumentException("사업자등록번호는 숫자 10자리여야 합니다.");
    }
    String businessInfoUrl = digits.isEmpty() ? null : FTC_BUSINESS_INFO_URL + digits;
    return new AcademyBusinessInfo(
        businessInfo.businessName(),
        businessInfo.representative(),
        businessInfo.address(),
        registration,
        businessInfo.mailOrderRegistrationNumber(),
        businessInfoUrl,
        businessInfo.phone(),
        businessInfo.email());
  }


  /**
   * builderDraft 부분 병합. tenant.* 키는 JSON 객체 형태를 검증한다.
   * tenant.contentTools 키는 ArtCraft AI 편집기 설정용 (Phase 1).
   */
  private Map<String, String> sanitizeBuilderDraft(Map<String, String> patch, Map<String, String> current) {
    Map<String, String> merged = new java.util.HashMap<>();
    if (current != null) merged.putAll(current);
    if (patch != null) {
      for (Map.Entry<String, String> e : patch.entrySet()) {
        String k = e.getKey();
        String v = e.getValue();
        if (k != null && k.startsWith("tenant.")) {
          validateTenantSettingJson(k, v);
        }
        if (v == null) {
          merged.remove(k);
        } else {
          merged.put(k, v);
        }
      }
    }
    return merged.isEmpty() ? null : merged;
  }

  private void validateTenantSettingJson(String key, String json) {
    if (json == null || json.isBlank()) return;
    String t = json.trim();
    if (!t.startsWith("{") || !t.endsWith("}")) {
      throw new IllegalArgumentException("테넌트 설정(" + key + ")은 JSON 객체여야 합니다.");
    }
  }

  private void validatePageSettings(
      AcademyPageSettings pageSettings,
      boolean validateBusinessInfo) {
    if (pageSettings == null) return;
    validateUrl(pageSettings.blogUrl());
    validateUrl(pageSettings.storeUrl());
    validateUrl(pageSettings.naverMapUrl());
    validateUrl(pageSettings.heroYoutubeUrl());
    validateSectionVisibility(pageSettings.sectionVisibility());
    validateSectionOrder(pageSettings.sectionOrder());
    validateBuilderDraft(pageSettings.builderDraft());
    if (validateBusinessInfo) validateBusinessInfo(pageSettings.businessInfo());
  }

  private void validateBusinessInfo(AcademyBusinessInfo businessInfo) {
    if (businessInfo == null) return;
    validateBusinessField("상호명", businessInfo.businessName(), MAX_BUSINESS_NAME_LENGTH);
    validateBusinessField("대표", businessInfo.representative(), MAX_BUSINESS_REPRESENTATIVE_LENGTH);
    validateBusinessField("주소", businessInfo.address(), MAX_BUSINESS_ADDRESS_LENGTH);
    validateBusinessField("사업자등록번호", businessInfo.businessRegistrationNumber(), MAX_BUSINESS_NUMBER_LENGTH);
    validateBusinessField("통신판매업신고번호", businessInfo.mailOrderRegistrationNumber(), MAX_BUSINESS_NUMBER_LENGTH);
    validateBusinessField("전화", businessInfo.phone(), MAX_BUSINESS_PHONE_LENGTH);
    validateBusinessField("이메일", businessInfo.email(), MAX_BUSINESS_EMAIL_LENGTH);
    if (!isBlank(businessInfo.email())
        && !businessInfo.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
      throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다.");
    }
  }

  private void validateBusinessField(String label, String value, int maxLength) {
    if (value != null && value.length() > maxLength) {
      throw new IllegalArgumentException(label + "은(는) " + maxLength + "자 이하로 입력해야 합니다.");
    }
  }

  private void validateBuilderDraft(Map<String, String> builderDraft) {
    if (builderDraft == null) return;
    for (Map.Entry<String, String> entry : builderDraft.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (key == null || !BUILDER_DRAFT_KEYS.contains(key)) {
        throw new IllegalArgumentException("올바르지 않은 Builder draft 키입니다: " + key);
      }
      if (value == null) continue;
      int maxLength = LONG_BUILDER_DRAFT_KEYS.contains(key)
          ? MAX_BUILDER_DRAFT_LONG_VALUE_LENGTH
          : MAX_BUILDER_DRAFT_VALUE_LENGTH;
      if (value.length() > maxLength) {
        throw new IllegalArgumentException("Builder draft 값은 " + maxLength + "자 이하로 입력해야 합니다: " + key);
      }
      if ("typographyPreset".equals(key) && !value.isBlank()
          && !Set.of("MODERN", "PREMIUM", "CLEAN", "FRIENDLY", "ACADEMIC").contains(value)) {
        throw new IllegalArgumentException("등록되지 않은 typography preset입니다.");
      }
      if (("headingFont".equals(key) || "bodyFont".equals(key)) && !value.isBlank()
          && !Set.of("PRETENDARD", "SUIT", "NOTO_SANS_KR", "NOTO_SERIF_KR").contains(value)) {
        throw new IllegalArgumentException("등록되지 않은 Builder font입니다.");
      }
      if (Set.of("logoSizeDesktop", "logoSizeMobile", "headingSizeMobile", "bodySizeMobile").contains(key)
          && !value.isBlank() && !value.matches("\\d{2,3}")) {
        throw new IllegalArgumentException("Builder 크기 token은 숫자 px 값이어야 합니다.");
      }
      if ("featuredLessonIds".equals(key) && !value.isBlank()) {
        String[] ids = value.split(",", -1);
        Set<String> uniqueIds = new HashSet<>();
        if (ids.length > 4
            || Arrays.stream(ids).anyMatch(id -> !id.matches("[1-9]\\d*"))
            || Arrays.stream(ids).anyMatch(id -> !uniqueIds.add(id))) {
          throw new IllegalArgumentException("대표 수업 ID는 중복 없이 최대 4개까지 저장할 수 있습니다.");
        }
      }
      if (("heroVideoId".equals(key) || "principalVideoId".equals(key))
          && !value.isBlank()
          && !value.matches("[1-9]\\d*")) {
        throw new IllegalArgumentException("홈페이지 영상 ID는 양의 정수여야 합니다: " + key);
      }
    }
  }

  private Map<String, Boolean> sanitizedSectionVisibility(Map<String, Boolean> sectionVisibility) {
    if (sectionVisibility == null) return null;
    Map<String, Boolean> sanitized = new LinkedHashMap<>();
    sectionVisibility.forEach((key, value) -> {
      if (value != null) sanitized.put(key, value);
    });
    return sanitized;
  }

  /**
   * 레이아웃 저장 시 들어온 순서를 canonical 완전 목록으로 정규화한다.
   *
   * <p>왜 필요한가: {@code DEFAULT_SECTION_ORDER}는 서버 + 3개 JS 파일에 중복 정의돼 있다.
   * 예전에는 {@code validateSectionOrder}가 size-lock이라, 서버에 섹션을 추가하고 클라이언트
   * 배열을 못 맞추면(혹은 브라우저가 옛 JS를 캐시하면) 옛 길이의 순서가 넘어와 레이아웃 저장이
   * 통째로 400이 됐다. 이제는 부분 목록도 받아 빠진 관리 섹션을 canonical 위치에 채워 넣으므로,
   * 그 드리프트가 사용자 저장을 깨지 않는다. 대신 새 섹션은 JS가 따라잡기 전까지 기본 위치에
   * 렌더된다. 입력 형식(빈 ID·중복·미등록 ID·hero 위치)은 여전히 거부한다.
   */
  private List<String> normalizeSectionOrderForWrite(List<String> sectionOrder) {
    if (sectionOrder == null) return null;
    validateSectionOrderInput(sectionOrder);
    LinkedHashSet<String> seen = new LinkedHashSet<>(sectionOrder);
    if (seen.isEmpty() || !"sec-hero".equals(seen.iterator().next())) {
      return new ArrayList<>(DEFAULT_SECTION_ORDER);
    }
    List<String> normalized = new ArrayList<>(seen);
    for (String id : DEFAULT_SECTION_ORDER) {
      if (seen.contains(id)) continue;
      int insertionIndex = normalized.size();
      int defaultIndex = DEFAULT_SECTION_ORDER.indexOf(id);
      for (int next = defaultIndex + 1; next < DEFAULT_SECTION_ORDER.size(); next++) {
        int existingIndex = normalized.indexOf(DEFAULT_SECTION_ORDER.get(next));
        if (existingIndex >= 0) {
          insertionIndex = existingIndex;
          break;
        }
      }
      normalized.add(insertionIndex, id);
    }
    return normalized;
  }

  /** 저장 전 입력 형식 검증. 완전성(모든 관리 섹션 포함)은 {@link #normalizeSectionOrderForWrite}가 보장한다. */
  private void validateSectionOrderInput(List<String> sectionOrder) {
    if (sectionOrder == null || sectionOrder.isEmpty()) return;
    Set<String> seen = new LinkedHashSet<>();
    for (String id : sectionOrder) {
      if (id == null) {
        throw new IllegalArgumentException("섹션 순서에 빈 섹션 ID가 포함되어 있습니다.");
      }
      if (!MANAGED_SECTION_IDS.contains(id)) {
        throw new IllegalArgumentException("올바르지 않은 섹션 ID입니다: " + id);
      }
      if (!seen.add(id)) {
        throw new IllegalArgumentException("섹션 순서에 중복 ID가 포함되어 있습니다: " + id);
      }
    }
    if (seen.contains("sec-hero") && !"sec-hero".equals(sectionOrder.get(0))) {
      throw new IllegalArgumentException("히어로 섹션은 순서의 첫 번째에 있어야 합니다.");
    }
  }

  private void validateSectionVisibility(Map<String, Boolean> sectionVisibility) {
    if (sectionVisibility == null) return;
    for (String key : sectionVisibility.keySet()) {
      if (key == null || key.length() > 50 || !key.matches("sec-[a-z0-9-]+|blogPostsSection")) {
        throw new IllegalArgumentException("올바르지 않은 섹션 ID입니다: " + key);
      }
    }
  }

  private void validateSectionOrder(List<String> sectionOrder) {
    if (sectionOrder == null) return;
    if (sectionOrder.size() != DEFAULT_SECTION_ORDER.size()) {
      throw new IllegalArgumentException("섹션 순서는 모든 관리 섹션을 포함해야 합니다.");
    }
    if (!"sec-hero".equals(sectionOrder.get(0))) {
      throw new IllegalArgumentException("히어로 섹션은 순서의 첫 번째에 있어야 합니다.");
    }
    Set<String> seen = new LinkedHashSet<>();
    for (String id : sectionOrder) {
      if (id == null) {
        throw new IllegalArgumentException("섹션 순서에 빈 섹션 ID가 포함되어 있습니다.");
      }
      if (!MANAGED_SECTION_IDS.contains(id)) {
        throw new IllegalArgumentException("올바르지 않은 섹션 ID입니다: " + id);
      }
      if (!seen.add(id)) {
        throw new IllegalArgumentException("섹션 순서에 중복 ID가 포함되어 있습니다: " + id);
      }
    }
    if (!seen.containsAll(DEFAULT_SECTION_ORDER)) {
      throw new IllegalArgumentException("섹션 순서는 모든 관리 섹션을 포함해야 합니다.");
    }
  }

  private void validateUrl(String url) {
    if (url == null) return;
    if (url.length() > MAX_PAGE_SETTINGS_URL_LENGTH) {
      throw new IllegalArgumentException("URL은 500자 이하로 입력해야 합니다.");
    }
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      throw new IllegalArgumentException("올바른 URL이 아닙니다.");
    }
  }

  private void validateRegions(List<AcademyRegionRequest> regions) {
    if (regions == null || regions.isEmpty()) {
      throw new IllegalArgumentException("지역은 1개 이상 입력해야 합니다.");
    }
    if (regions.size() > MAX_REGION_COUNT) {
      throw new IllegalArgumentException("지역은 최대 3개까지 입력할 수 있습니다.");
    }
    for (AcademyRegionRequest region : regions) {
      if (region == null || isBlank(region.city())) {
        throw new IllegalArgumentException("지역의 시/도는 필수입니다.");
      }
    }
  }

  private Set<Subject> loadSubjects(Set<Integer> subjectIds) {
    List<Subject> subjects = subjectRepository.findAllById(subjectIds);
    if (subjects.size() != subjectIds.size()) {
      throw new IllegalArgumentException("존재하지 않는 과목이 포함되어 있습니다.");
    }
    return new HashSet<>(subjects);
  }

  private void saveRegions(Academy academy, List<AcademyRegionRequest> regions) {
    List<AcademyRegion> entities = regions.stream()
        .map(r -> AcademyRegion.builder()
            .academy(academy)
            .city(r.city().trim())
            .district(normalizeDistrict(r.district()))
            .displayName(displayName(r.city(), r.district()))
            .build())
        .toList();
    academyRegionRepository.saveAll(entities);
  }

  private String saveImage(Long tutorUserId, MultipartFile file) {
    try {
      ImageSanitizer.SanitizedImage sanitized = imageSanitizer.sanitize(file);
      return fileStorage.saveImage(
          tutorUserId, file.getOriginalFilename(), sanitized.bytes(), sanitized.extension());
    } catch (IOException e) {
      throw new UncheckedIOException("Academy 이미지 저장 실패", e);
    }
  }

  private String resolveLogoStorageKey(Long tutorUserId, MultipartFile logo) {
    return isPresent(logo) ? saveImage(tutorUserId, logo) : saveDefaultLogo(tutorUserId);
  }

  private String saveDefaultLogo(Long tutorUserId) {
    try {
      return fileStorage.saveImage(tutorUserId, DEFAULT_LOGO_FILENAME, loadDefaultLogoJpeg());
    } catch (IOException e) {
      throw new UncheckedIOException("기본 Academy 로고 저장 실패", e);
    }
  }

  /**
   * MVP 데모용 기본 로고. 정적 에셋({@code static/img/demo_logo.png})을 읽어 JPEG로 재인코딩한다.
   * 공개 로고 서빙이 {@code image/jpeg}이므로 저장 바이트도 JPEG로 맞춘다.
   * (향후 LLM이 로고를 생성하면 이 폴백은 우회된다 — docs/academy-publish-design.md §2 참고.)
   */
  static byte[] loadDefaultLogoJpeg() throws IOException {
    ClassPathResource resource = new ClassPathResource(DEFAULT_LOGO_RESOURCE);
    try (InputStream in = resource.getInputStream();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      BufferedImage image = ImageIO.read(in);
      if (image == null) {
        throw new IOException("기본 로고 리소스를 읽을 수 없습니다: " + DEFAULT_LOGO_RESOURCE);
      }
      ImageIO.write(image, "jpg", out);
      return out.toByteArray();
    }
  }


  private byte[] readImage(String storageKey) {
    try {
      return fileStorage.readPage(storageKey, "IMAGE", 1);
    } catch (IOException e) {
      throw new UncheckedIOException("Academy 이미지 조회 실패", e);
    }
  }

  private String generateUniqueSlug(String name) {
    String base = slugBase(name);
    String candidate = base;
    int suffix = 2;
    while (academyRepository.existsBySlug(candidate)) {
      candidate = base + "-" + suffix++;
    }
    return candidate;
  }

  private String slugBase(String name) {
    String normalized = name.trim()
        .replaceAll("[^a-zA-Z0-9가-힣]+", "-")
        .replaceAll("^-+|-+$", "")
        .toLowerCase(Locale.ROOT);
    return normalized.isBlank() ? "academy" : normalized;
  }

  private String normalizeDistrict(String district) {
    return isBlank(district) ? AcademyRegion.ALL_DISTRICT : district.trim();
  }

  private String normalizeBlank(String value) {
    return isBlank(value) ? null : value.trim();
  }

  private String displayName(String city, String district) {
    String normalizedDistrict = normalizeDistrict(district);
    return AcademyRegion.ALL_DISTRICT.equals(normalizedDistrict)
        ? city.trim() + " 전 지역"
        : city.trim() + " " + normalizedDistrict;
  }

  private boolean isPresent(MultipartFile file) {
    return file != null && !file.isEmpty();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
