package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AcademyImageIntentDetector 테스트")
class AcademyImageIntentDetectorTest {

  private final AcademyImageIntentDetector detector = new AcademyImageIntentDetector();

  @Test
  @DisplayName("밝게 해달라는 요청을 감지한다")
  void detectBrighten() {
    var intent = detector.detect("메인 사진을 밝은 분위기로 바꿔줘");

    assertThat(intent.detected()).isTrue();
    assertThat(intent.slotKey()).isEqualTo("HERO_BANNER");
    assertThat(intent.operations()).hasSize(1);
    assertThat(intent.operations().get(0).op()).isEqualTo("brightness");
  }

  @Test
  @DisplayName("어둡게 해달라는 요청을 감지한다")
  void detectDarken() {
    var intent = detector.detect("배너 이미지를 어둡게 해줘");

    assertThat(intent.detected()).isTrue();
    assertThat(intent.operations().get(0).op()).isEqualTo("brightness");
    int value = (Integer) intent.operations().get(0).params().get("value");
    assertThat(value).isNegative();
  }

  @Test
  @DisplayName("채도 조절 요청을 감지한다")
  void detectVibrance() {
    var intent = detector.detect("사진 색감을 선명하게 해줘");

    assertThat(intent.detected()).isTrue();
    assertThat(intent.operations())
        .anyMatch(op -> op.op().equals("vibrance"));
  }

  @Test
  @DisplayName("이미지와 무관한 요청은 감지하지 않는다")
  void ignoreNonImage() {
    var intent = detector.detect("학원 소개 문구를 바꿔줘");

    assertThat(intent.detected()).isFalse();
  }

  @Test
  @DisplayName("배경 슬롯을 추론한다")
  void inferBackgroundSlot() {
    var intent = detector.detect("섹션 배경 이미지를 밝게 해줘");

    assertThat(intent.detected()).isTrue();
    assertThat(intent.slotKey()).isEqualTo("SECTION_BG");
  }

  @Test
  @DisplayName("숫자로 강도를 지정할 수 있다")
  void extractIntensity() {
    var intent = detector.detect("메인 사진 밝기를 30 올려줘");

    assertThat(intent.detected()).isTrue();
    int value = (Integer) intent.operations().get(0).params().get("value");
    assertThat(value).isEqualTo(30);
  }
}
