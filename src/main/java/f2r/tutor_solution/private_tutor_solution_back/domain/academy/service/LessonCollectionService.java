package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.exception.LessonCollectionException;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LessonCollectionService {

  static final String CREATION_KEY_CONSTRAINT =
      "uk_lesson_products_academy_creation_key";

  private final LessonProductRepository lessonProductRepository;
  private final AcademyRepository academyRepository;
  private final AcademyVideoRepository academyVideoRepository;
  private final AuthenticatedUserResolver authResolver;

  @Transactional
  public LessonCollectionSaveResponse save(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      LessonCollectionSaveRequest request) {
    try {
      long startedAtNanos = System.nanoTime();
      authResolver.requireTutor(principal);
      List<PreparedItem> items = prepareAndValidate(request);

      Academy academy = academyRepository.findByTutor_UserId(principal.userId())
          .orElseThrow(() -> new IllegalArgumentException("Academy를 먼저 생성해야 합니다."));
      authResolver.verifyOwnsAcademy(academy);
      requireOwnedVideos(academy.getAcademyId(), items);

      Resolution resolution = resolveAndLock(academy.getAcademyId(), items);
      SaveResult result = applyAndFlush(academy, items, resolution);
      registerAfterCommitLog(
          academy.getAcademyId(),
          items.size(),
          result.createdCount(),
          result.updatedCount(),
          result.replayedCount(),
          startedAtNanos);

      List<LessonResponse> responses = items.stream()
          .map(item -> LessonResponse.from(result.lessonFor(item)))
          .toList();
      return new LessonCollectionSaveResponse(
          responses,
          items.size(),
          result.createdCount(),
          result.updatedCount(),
          result.replayedCount());
    } catch (PessimisticLockingFailureException exception) {
      throw LessonCollectionException.authoringLockConflict();
    }
  }

  private List<PreparedItem> prepareAndValidate(LessonCollectionSaveRequest request) {
    if (request == null || request.items() == null) {
      throw new IllegalArgumentException("수업 상품 모음 정보가 필요합니다.");
    }

    Set<Long> lessonIds = new HashSet<>();
    Set<String> creationKeys = new HashSet<>();
    List<PreparedItem> prepared = new ArrayList<>(request.items().size());

    for (LessonCollectionItemRequest item : request.items()) {
      if (item == null) {
        throw new IllegalArgumentException("수업 상품 항목은 null일 수 없습니다.");
      }
      boolean existing = item.lessonId() != null;
      boolean hasCreationKey = item.creationKey() != null && !item.creationKey().isBlank();
      if (existing == hasCreationKey) {
        throw new IllegalArgumentException("lessonId와 creationKey 중 하나만 필요합니다.");
      }

      String creationKey = null;
      if (existing) {
        if (item.lessonId() <= 0 || !lessonIds.add(item.lessonId())) {
          throw new IllegalArgumentException("lessonId가 올바르지 않거나 중복되었습니다.");
        }
        if (item.expectedAuthoringRevision() == null
            || item.expectedAuthoringRevision() < 0) {
          throw new IllegalArgumentException(
              "기존 수업 상품에는 유효한 expectedAuthoringRevision이 필요합니다.");
        }
      } else {
        if (item.expectedAuthoringRevision() != null) {
          throw new IllegalArgumentException(
              "신규 수업 상품에는 expectedAuthoringRevision을 보낼 수 없습니다.");
        }
        creationKey = normalizeCreationKey(item.creationKey());
        if (!creationKeys.add(creationKey)) {
          throw new IllegalArgumentException("creationKey가 중복되었습니다.");
        }
      }

      LessonAuthoringCanonicalState state = LessonAuthoringCanonicalState.from(item);
      validateState(state);
      prepared.add(new PreparedItem(item, creationKey, state));
    }
    return List.copyOf(prepared);
  }

  private Resolution resolveAndLock(Long academyId, List<PreparedItem> items) {
    Set<String> creationKeys = new HashSet<>();
    Set<Long> unionLessonIds = new HashSet<>();
    for (PreparedItem item : items) {
      if (item.isExisting()) {
        unionLessonIds.add(item.request().lessonId());
      } else {
        creationKeys.add(item.creationKey());
      }
    }

    Map<String, Long> boundIdsByCreationKey = new HashMap<>();
    if (!creationKeys.isEmpty()) {
      for (LessonCreationKeyBindingProjection binding
          : lessonProductRepository.findCreationKeyBindings(academyId, creationKeys)) {
        Long duplicate = boundIdsByCreationKey.put(binding.getCreationKey(), binding.getLessonId());
        if (duplicate != null && !duplicate.equals(binding.getLessonId())) {
          throw LessonCollectionException.creationKeyBindingConflict();
        }
        unionLessonIds.add(binding.getLessonId());
      }
    }

    Map<Long, LessonProduct> lockedById = new LinkedHashMap<>();
    if (!unionLessonIds.isEmpty()) {
      List<LessonProduct> locked =
          lessonProductRepository.findAllForUpdateByAcademyIdAndLessonIds(
              academyId,
              unionLessonIds);
      for (LessonProduct lesson : locked) {
        lockedById.put(lesson.getLessonId(), lesson);
      }
    }
    if (lockedById.size() != unionLessonIds.size()) {
      throw lessonNotFound();
    }

    Set<Long> resolvedTargets = new HashSet<>();
    for (PreparedItem item : items) {
      Long resolvedId = item.isExisting()
          ? item.request().lessonId()
          : boundIdsByCreationKey.get(item.creationKey());
      if (resolvedId != null && !resolvedTargets.add(resolvedId)) {
        throw new IllegalArgumentException("같은 수업 상품이 요청에 중복되었습니다.");
      }
    }

    for (Map.Entry<String, Long> binding : boundIdsByCreationKey.entrySet()) {
      LessonProduct locked = lockedById.get(binding.getValue());
      if (locked == null || !binding.getKey().equals(locked.getCreationKey())) {
        throw LessonCollectionException.creationKeyBindingConflict();
      }
    }
    return new Resolution(lockedById, boundIdsByCreationKey);
  }

  private SaveResult applyAndFlush(
      Academy academy,
      List<PreparedItem> items,
      Resolution resolution) {
    Map<Long, LessonProduct> responsesByLessonId = new HashMap<>();
    Map<String, LessonProduct> responsesByCreationKey = new HashMap<>();
    List<PreparedItem> newItems = new ArrayList<>();
    int updated = 0;
    int replayed = 0;

    for (PreparedItem item : items) {
      if (item.isExisting()) {
        LessonProduct lesson = resolution.lockedById().get(item.request().lessonId());
        if (lesson == null) {
          throw lessonNotFound();
        }
        boolean equivalent =
            item.state().equals(LessonAuthoringCanonicalState.from(lesson));
        long expectedRevision = item.request().expectedAuthoringRevision();
        long currentRevision = lesson.getEffectiveAuthoringRevision();
        if (expectedRevision != currentRevision && !equivalent) {
          throw LessonCollectionException.stale();
        }
        if (equivalent) {
          replayed++;
        } else {
          item.state().applyTo(lesson);
          updated++;
        }
        responsesByLessonId.put(lesson.getLessonId(), lesson);
        continue;
      }

      Long boundLessonId = resolution.boundIdsByCreationKey().get(item.creationKey());
      if (boundLessonId == null) {
        newItems.add(item);
        continue;
      }
      LessonProduct lesson = resolution.lockedById().get(boundLessonId);
      if (lesson == null
          || !item.state().equals(LessonAuthoringCanonicalState.from(lesson))) {
        throw LessonCollectionException.creationKeyBindingConflict();
      }
      responsesByCreationKey.put(item.creationKey(), lesson);
      replayed++;
    }

    newItems.sort(Comparator.comparing(PreparedItem::creationKey));
    try {
      for (PreparedItem item : newItems) {
        LessonProduct lesson = newLesson(academy, item);
        LessonProduct saved = lessonProductRepository.save(lesson);
        responsesByCreationKey.put(item.creationKey(), saved);
      }
      lessonProductRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      if (isCreationKeyConstraintViolation(exception)) {
        throw LessonCollectionException.creationKeyRace();
      }
      throw exception;
    }

    return new SaveResult(
        responsesByLessonId,
        responsesByCreationKey,
        newItems.size(),
        updated,
        replayed);
  }

  private LessonProduct newLesson(Academy academy, PreparedItem item) {
    LessonAuthoringCanonicalState state = item.state();
    return LessonProduct.builder()
        .academy(academy)
        .name(state.name())
        .description(state.description())
        .schoolName(state.schoolName())
        .gradeLabel(state.gradeLabel())
        .lessonType(state.lessonType())
        .targetGroup(state.targetGroup())
        .priceAmount(state.priceAmount())
        .priceUnit(state.priceUnit())
        .priceSummary(state.priceSummary())
        .maxCapacity(state.maxCapacity())
        .currentCount(0)
        .scheduleType(state.scheduleType())
        .weeklyCount(state.weeklyCount())
        .weekdays(new HashSet<>(state.weekdays()))
        .startDate(state.startDate())
        .endDate(state.endDate())
        .durationWeeks(state.durationWeeks())
        .durationMonths(state.durationMonths())
        .scheduleSummary(state.scheduleSummary())
        .status(state.status())
        .visible(true)
        .tabGuidanceLabel(state.tabGuidanceLabel())
        .tabShortName(state.tabShortName())
        .kicker(state.kicker())
        .highlight(state.highlight())
        .learnerNeed(state.learnerNeed())
        .coreDetailTitle(state.coreDetailTitle())
        .explanation1(state.explanation1())
        .explanation2(state.explanation2())
        .explanation3(state.explanation3())
        .theme(state.theme())
        // 신규 항목(creationKey 경로)은 applyTo()를 거치지 않고 이 빌더만 탄다.
        // 여기서 미디어를 빠뜨리면 request -> canonical state까지 흘러온 값이 조용히 버려지고
        // created 응답이 나가 강사의 업로드가 사라진다.
        .mediaImageKey(state.mediaImageKey())
        .mediaVideoId(state.mediaVideoId())
        .otVideoId(state.otVideoId())
        .creationKey(item.creationKey())
        .authoringRevision(1L)
        .build();
  }

  /**
   * 대표 영상이 모두 이 학원 소유인지 확인한다(IDOR 차단).
   *
   * <p>클라이언트가 보낸 videoId를 그대로 믿으면 남의 학원 영상을 자기 수업상품에 걸 수 있다.
   * academy가 로드된 뒤에만 판정할 수 있어 {@code validateState}가 아니라 여기에 둔다.
   * 조회는 id 집합 하나로 묶어 한 번만 던진다(N+1 금지).
   */
  private void requireOwnedVideos(Long academyId, List<PreparedItem> items) {
    Set<Long> videoIds = items.stream()
        .flatMap(item -> java.util.stream.Stream.of(
            item.state().mediaVideoId(), item.state().otVideoId()))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    if (videoIds.isEmpty()) {
      return;
    }
    List<Long> orderedVideoIds = videoIds.stream().sorted().toList();
    List<AcademyVideo> owned = academyVideoRepository
        .findAllForUpdateByVideoIdsAndAcademyId(orderedVideoIds, academyId);
    if (owned.size() != videoIds.size()) {
      throw new IllegalArgumentException("수업상품 영상으로 지정할 수 없는 영상이 포함되어 있습니다.");
    }
  }

  private void validateState(LessonAuthoringCanonicalState state) {
    // 대표 미디어는 이미지 "또는" 영상 하나만. 소유권 검사는 여기서 못 한다 —
    // 이 메서드는 prepareAndValidate에서 불리고, 그 시점엔 academy가 아직 로드되지 않았다.
    // 소유권은 save()에서 academy 확보 후 requireOwnedVideos(...)가 배치로 검사한다.
    if (state.mediaImageKey() != null && state.mediaVideoId() != null) {
      throw new IllegalArgumentException("대표 미디어는 이미지와 영상 중 하나만 지정할 수 있습니다.");
    }
    requireMaxLength(state.mediaImageKey(), 500, "대표 이미지");
    requireMaxLength(state.name(), 100, "수업명");
    requireMaxLength(state.schoolName(), 100, "학교명");
    requireMaxLength(state.gradeLabel(), 50, "학년");
    requireMaxLength(state.tabGuidanceLabel(), 120, "탭 안내 문구");
    requireMaxLength(state.tabShortName(), 80, "탭 짧은 이름");
    requireMaxLength(state.kicker(), 200, "키커");
    requireMaxLength(state.highlight(), 300, "강조 문구");
    requireMaxLength(state.learnerNeed(), 300, "학습자 요구");
    requireMaxLength(state.coreDetailTitle(), 200, "핵심 상세 제목");
    requireMaxLength(state.explanation1(), 500, "설명 1");
    requireMaxLength(state.explanation2(), 500, "설명 2");
    requireMaxLength(state.explanation3(), 500, "설명 3");
    requireMaxLength(state.priceSummary(), 100, "가격 설명");
    requireMaxLength(state.scheduleSummary(), 200, "일정 설명");

    // 편집기는 예시 HTML 기준 필드만 받으므로 정원·가격은 비어 있을 수 있다.
    // 값이 들어온 경우에만 범위를 검사한다. 정원 null은 무제한 접수로 해석된다.
    if (state.maxCapacity() != null && state.maxCapacity() <= 0) {
      throw new IllegalArgumentException("정원은 1명 이상이어야 합니다.");
    }
    if (state.priceAmount() != null && state.priceAmount() <= 0) {
      throw new IllegalArgumentException("고정 가격은 1원 이상이어야 합니다.");
    }
    if (state.scheduleType() == ScheduleType.WEEKLY) {
      if (state.weeklyCount() == null || state.weeklyCount() <= 0) {
        throw new IllegalArgumentException("주간 수업 횟수는 1회 이상이어야 합니다.");
      }
      if (state.weekdays().isEmpty()) {
        throw new IllegalArgumentException("주간 일정은 요일을 1개 이상 선택해야 합니다.");
      }
    }
    if (state.scheduleType() == ScheduleType.PERIOD) {
      boolean hasDateRange = state.startDate() != null
          && state.endDate() != null
          && !state.endDate().isBefore(state.startDate());
      boolean hasDuration = positive(state.durationWeeks()) || positive(state.durationMonths());
      if (!hasDateRange && !hasDuration) {
        throw new IllegalArgumentException("기간형 수업은 날짜 범위 또는 기간을 입력해야 합니다.");
      }
    }
  }

  private String normalizeCreationKey(String creationKey) {
    try {
      String candidate = creationKey.trim();
      String normalized = UUID.fromString(candidate).toString();
      if (candidate.length() != 36 || !normalized.equalsIgnoreCase(candidate)) {
        throw new IllegalArgumentException("creationKey는 유효한 UUID여야 합니다.");
      }
      return normalized;
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("creationKey는 유효한 UUID여야 합니다.");
    }
  }

  static boolean isCreationKeyConstraintViolation(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException constraintViolation) {
        return CREATION_KEY_CONSTRAINT.equals(
            finalConstraintIdentifier(constraintViolation.getConstraintName()));
      }
    }
    return false;
  }

  private static String finalConstraintIdentifier(String constraintName) {
    if (constraintName == null) {
      return null;
    }
    int qualifierSeparator = constraintName.lastIndexOf('.');
    String identifier = constraintName.substring(qualifierSeparator + 1).trim();
    if (identifier.length() < 2) {
      return identifier;
    }
    char first = identifier.charAt(0);
    char last = identifier.charAt(identifier.length() - 1);
    if ((first == '`' && last == '`')
        || (first == '"' && last == '"')
        || (first == '[' && last == ']')) {
      return identifier.substring(1, identifier.length() - 1);
    }
    return identifier;
  }

  private void registerAfterCommitLog(
      Long academyId,
      int requested,
      int created,
      int updated,
      int replayed,
      long startedAtNanos) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        long durationMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L;
        log.info(
            "lesson_collection_outcome code=LESSON_COLLECTION_SAVE_COMMITTED "
                + "academyId={} requested={} created={} updated={} replayed={} durationMs={}",
            academyId,
            requested,
            created,
            updated,
            replayed,
            durationMillis);
      }
    });
  }

  private ResponseStatusException lessonNotFound() {
    return new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "수업 상품을 찾을 수 없습니다.");
  }

  private void requireMaxLength(String value, int maxLength, String fieldName) {
    if (value != null && value.length() > maxLength) {
      throw new IllegalArgumentException(
          fieldName + "은(는) " + maxLength + "자 이하여야 합니다.");
    }
  }

  private boolean positive(Integer value) {
    return value != null && value > 0;
  }

  private record PreparedItem(
      LessonCollectionItemRequest request,
      String creationKey,
      LessonAuthoringCanonicalState state
  ) {
    boolean isExisting() {
      return request.lessonId() != null;
    }
  }

  private record Resolution(
      Map<Long, LessonProduct> lockedById,
      Map<String, Long> boundIdsByCreationKey
  ) {
  }

  private record SaveResult(
      Map<Long, LessonProduct> responsesByLessonId,
      Map<String, LessonProduct> responsesByCreationKey,
      int createdCount,
      int updatedCount,
      int replayedCount
  ) {
    LessonProduct lessonFor(PreparedItem item) {
      LessonProduct lesson = item.isExisting()
          ? responsesByLessonId.get(item.request().lessonId())
          : responsesByCreationKey.get(item.creationKey());
      if (lesson == null) {
        throw new IllegalStateException("저장된 수업 상품 응답을 찾을 수 없습니다.");
      }
      return lesson;
    }
  }
}
