package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

/**
 * Academy 단일 이미지 슬롯(V940 히어로, V948 시간표 달력).
 *
 * <p>URL path variable로 그대로 받으므로 허용값을 여기서 닫는다. 문자열을 그대로 쓰면
 * 오타가 런타임까지 흘러가고, 슬롯이 늘 때 분기를 빠뜨리기 쉽다.
 *
 * <p>이름은 히어로에서 출발했지만 실제 의미는 "Academy에 1장씩 붙는 이미지 슬롯"이다.
 * 시간표 달력(V948)은 히어로 밖에 있으나 업로드·삭제·서빙 경로가 동일해 여기에 함께 둔다
 * — 슬롯마다 endpoint를 복제하는 것보다 분기 한 곳이 낫다.
 */
public enum HeroMediaSlot {

  /** 원장 대표사진 1장. */
  PRINCIPAL("principal"),
  /** 대표 교재 표지 3장. 시안이 3장 고정 배치라 슬롯을 고정한다. */
  TEXTBOOK1("textbook1"),
  TEXTBOOK2("textbook2"),
  TEXTBOOK3("textbook3"),
  /** 개설 시간표 섹션의 달력 이미지 1장(V948). 히어로가 아니라 sec-lessons 하단에 렌더된다. */
  SCHEDULE("schedule");

  private final String key;

  HeroMediaSlot(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }

  /** 교재 표지 슬롯이면 1~3, 그 밖(원장사진·시간표 달력)이면 0. */
  public int textbookSlot() {
    return switch (this) {
      case TEXTBOOK1 -> 1;
      case TEXTBOOK2 -> 2;
      case TEXTBOOK3 -> 3;
      default -> 0;
    };
  }

  public static HeroMediaSlot from(String raw) {
    for (HeroMediaSlot slot : values()) {
      if (slot.key.equals(raw)) return slot;
    }
    throw new IllegalArgumentException("올바르지 않은 히어로 이미지 슬롯입니다: " + raw);
  }

  /** 저장 파일명 힌트. storage에 슬롯이 드러나 디버깅이 쉬워진다. */
  public String filenameHint() {
    return "academy-hero-" + key;
  }
}
