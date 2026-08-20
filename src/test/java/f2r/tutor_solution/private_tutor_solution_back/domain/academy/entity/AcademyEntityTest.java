package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AcademyEntityTest {

  @Test
  void academyBuilder_defaultsToPublished_whenStatusIsNull() {
    // given
    AcademyStatus status = null;

    // when
    Academy academy = Academy.builder()
        .slug("english-master")
        .name("English Master")
        .logoStorageKey("/logo.jpg")
        .intro("소개")
        .status(status)
        .build();

    // then
    assertThat(academy.getStatus()).isEqualTo(AcademyStatus.PUBLISHED);
  }

  @Test
  void academyRegionBuilder_usesAllDistrict_whenDistrictIsNull() {
    // given
    String district = null;

    // when
    AcademyRegion region = AcademyRegion.builder()
        .city("서울시")
        .district(district)
        .displayName("서울시 전 지역")
        .build();

    // then
    assertThat(region.getDistrict()).isEqualTo(AcademyRegion.ALL_DISTRICT);
  }

  @Test
  void updateImageSlot_슬롯마다_제_컬럼에_저장한다() {
    // given: 슬롯→컬럼 매핑은 엔티티에만 있다. 여기서 어긋나면 올린 이미지가 다른 자리에 뜬다
    Academy academy = academyFixture();

    // when
    academy.updateImageSlot(HeroMediaSlot.PRINCIPAL, "principal.jpg");
    academy.updateImageSlot(HeroMediaSlot.TEXTBOOK2, "textbook2.jpg");
    academy.updateImageSlot(HeroMediaSlot.SCHEDULE, "schedule.jpg");

    // then
    assertThat(academy.getPrincipalPhotoStorageKey()).isEqualTo("principal.jpg");
    assertThat(academy.getTextbookCover(2)).isEqualTo("textbook2.jpg");
    assertThat(academy.getScheduleImageStorageKey()).isEqualTo("schedule.jpg");
    // 시간표 달력을 올려도 히어로 슬롯을 덮지 않는다
    assertThat(academy.getTextbookCover(1)).isNull();
    assertThat(academy.getTextbookCover(3)).isNull();
  }

  @Test
  void imageSlotKey_저장한_슬롯을_그대로_되돌려준다() {
    // given
    Academy academy = academyFixture();
    academy.updateImageSlot(HeroMediaSlot.SCHEDULE, "schedule.jpg");

    // when & then: AcademyResponse.heroMediaSlots가 이 값으로 채운 슬롯을 가린다
    assertThat(academy.imageSlotKey(HeroMediaSlot.SCHEDULE)).isEqualTo("schedule.jpg");
    assertThat(academy.imageSlotKey(HeroMediaSlot.PRINCIPAL)).isNull();
    assertThat(academy.imageSlotKey(HeroMediaSlot.TEXTBOOK1)).isNull();
  }

  @Test
  void updateImageSlot_null이면_슬롯을_비운다() {
    // given: 삭제는 storage 파일을 지우지 않고 key만 끊는다
    Academy academy = academyFixture();
    academy.updateImageSlot(HeroMediaSlot.SCHEDULE, "schedule.jpg");

    // when
    academy.updateImageSlot(HeroMediaSlot.SCHEDULE, null);

    // then
    assertThat(academy.getScheduleImageStorageKey()).isNull();
    assertThat(academy.imageSlotKey(HeroMediaSlot.SCHEDULE)).isNull();
  }

  private static Academy academyFixture() {
    return Academy.builder()
        .slug("english-master")
        .name("English Master")
        .logoStorageKey("/logo.jpg")
        .intro("소개")
        .build();
  }
}
