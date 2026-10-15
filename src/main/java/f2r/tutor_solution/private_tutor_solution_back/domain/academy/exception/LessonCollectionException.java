package f2r.tutor_solution.private_tutor_solution_back.domain.academy.exception;

import lombok.Getter;

@Getter
public class LessonCollectionException extends RuntimeException {

  public static final String AUTHORING_STALE = "LESSON_AUTHORING_STALE";
  public static final String CREATION_KEY_BINDING_CONFLICT =
      "LESSON_CREATION_KEY_BINDING_CONFLICT";
  public static final String CREATION_KEY_RACE = "LESSON_CREATION_KEY_RACE";
  public static final String AUTHORING_LOCK_CONFLICT = "LESSON_AUTHORING_LOCK_CONFLICT";

  private final String code;
  private final boolean retryable;

  private LessonCollectionException(String code, String message, boolean retryable) {
    super(message);
    this.code = code;
    this.retryable = retryable;
  }

  public static LessonCollectionException stale() {
    return new LessonCollectionException(
        AUTHORING_STALE,
        "다른 편집에서 수업 상품이 변경되었습니다. 최신 내용을 확인해 주세요.",
        false);
  }

  public static LessonCollectionException creationKeyBindingConflict() {
    return new LessonCollectionException(
        CREATION_KEY_BINDING_CONFLICT,
        "이미 저장된 수업 상품과 생성 요청 내용이 일치하지 않습니다.",
        false);
  }

  public static LessonCollectionException creationKeyRace() {
    return new LessonCollectionException(
        CREATION_KEY_RACE,
        "수업 상품 생성이 동시에 처리되었습니다. 같은 요청을 다시 시도해 주세요.",
        true);
  }

  public static LessonCollectionException authoringLockConflict() {
    return new LessonCollectionException(
        AUTHORING_LOCK_CONFLICT,
        "수업 상품이 다른 요청에서 변경 중입니다. 잠시 후 다시 시도해 주세요.",
        true);
  }
}
