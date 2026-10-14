package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.ImageEditFlowDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("PhotoCraft 이미지 편집 플로우 테스트")
class PhotoCraftFlowTest {

  @Mock
  private AcademyWorkingCopyService workingCopyService;

  @Mock
  private ImageEditApprovalService approvalService;

  @InjectMocks
  private PhotoCraftChatBridgeService bridgeService;

  @Test
  @DisplayName("밝게 해달라는 채팅을 이미지 편집 의도로 감지한다")
  void detectBrightenIntent() {
    var response = bridgeService.detectImageIntent(null, "메인 사진을 밝은 분위기로 바꿔줘");

    assertThat(response.imageEditDetected()).isTrue();
    assertThat(response.slotKey()).isEqualTo("HERO_BANNER");
    assertThat(response.operations()).hasSize(1);
  }

  @Test
  @DisplayName("어둡게 해달라는 채팅을 감지한다")
  void detectDarkenIntent() {
    var response = bridgeService.detectImageIntent(null, "배너 이미지를 어둡게 해줘");

    assertThat(response.imageEditDetected()).isTrue();
    assertThat(response.suggestedEditDescription()).contains("밝기 -");
  }

  @Test
  @DisplayName("이미지와 무관한 채팅은 감지하지 않는다")
  void ignoreNonImageChat() {
    var response = bridgeService.detectImageIntent(null, "학원 소개 문구를 바꿔줘");

    assertThat(response.imageEditDetected()).isFalse();
  }

  @Test
  @DisplayName("배경 슬롯을 추론한다")
  void inferBackgroundSlot() {
    var response = bridgeService.detectImageIntent(null, "섹션 배경 이미지를 밝게 해줘");

    assertThat(response.imageEditDetected()).isTrue();
    assertThat(response.slotKey()).isEqualTo("SECTION_BG");
  }

  @Test
  @DisplayName("숫자로 강도를 지정할 수 있다")
  void extractCustomIntensity() {
    var response = bridgeService.detectImageIntent(null, "메인 사진 밝기를 30 올려줘");

    assertThat(response.imageEditDetected()).isTrue();
    assertThat(response.suggestedEditDescription()).contains("30");
  }

  @Test
  @DisplayName("승인되지 않은 이미지는 홈페이지에 노출되지 않는다")
  void unapprovedImageNotExposed() {
    // ImageEditApprovalService의 실제 인스턴스로 검증
    var realApprovalService = new ImageEditApprovalService(workingCopyService);

    // 등록만 하고 승인하지 않음
    realApprovalService.registerEditedImage(1L, "HERO_BANNER", "test-key");

    // 홈페이지 노출 여부 확인
    assertThat(realApprovalService.isExposedToHomepage(1L)).isFalse();
  }

  @Test
  @DisplayName("승인된 이미지만 WorkingCopy 스테이징이 가능하다")
  void onlyApprovedCanStage() {
    var realApprovalService = new ImageEditApprovalService(workingCopyService);

    // 승인 없이 스테이징 시도
    realApprovalService.registerEditedImage(2L, "HERO_BANNER", "test-key");

    assertThatThrownBy(() ->
        realApprovalService.stageApprovedImage(null, 2L, 1L))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("승인되지 않은 이미지");
  }
}
