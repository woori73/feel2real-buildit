package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.fssagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guided Agent 14단계 워크플로 계약 — handoff §3 POC 실측 결과(STEP 1~7)와 매뉴얼
 * 미검증 단계(STEP 8~14) 구분, honest UX 금지 문구, 최종 제출 비자동화.
 */
@DisplayName("FSS guided workflow — POC facts, manual-only marking, honest UX")
class FssGuidedWorkflowTest {

  @Test
  void hasFourteenStepsNumberedSequentially() {
    List<FssGuidedStep> steps = FssGuidedWorkflow.steps();
    assertThat(steps).hasSize(14);
    for (int i = 0; i < steps.size(); i++) {
      assertThat(steps.get(i).number()).isEqualTo(i + 1);
      assertThat(steps.get(i).title()).isNotBlank();
      assertThat(steps.get(i).checkAction()).isNotBlank();
      assertThat(steps.get(i).confirmLabel()).isNotBlank();
    }
    assertThat(FssGuidedWorkflow.total()).isEqualTo(14);
    assertThatThrownBy(() -> FssGuidedWorkflow.step(15))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("STEP 1~4 = POC 화면 확인, STEP 5~8 = 화면 조사 확인, STEP 9~14 = 매뉴얼 미검증")
  void evidenceMatchesPocBoundary() {
    assertThat(FssGuidedWorkflow.step(1).evidence()).isEqualTo(FssStepEvidence.POC_VERIFIED);
    assertThat(FssGuidedWorkflow.step(4).evidence()).isEqualTo(FssStepEvidence.POC_VERIFIED);
    assertThat(FssGuidedWorkflow.step(5).evidence()).isEqualTo(FssStepEvidence.POC_SCREEN_VERIFIED);
    assertThat(FssGuidedWorkflow.step(7).evidence()).isEqualTo(FssStepEvidence.POC_SCREEN_VERIFIED);
    // 처리회신방법(rsltRplyMthdDiv)은 2026-09-24 아카데미 Chrome 조사로 라벨을 확인했다.
    assertThat(FssGuidedWorkflow.step(8).evidence()).isEqualTo(FssStepEvidence.POC_SCREEN_VERIFIED);
    for (int n = 9; n <= 14; n++) {
      FssGuidedStep s = FssGuidedWorkflow.step(n);
      assertThat(s.evidence()).isEqualTo(FssStepEvidence.MANUAL_UNVERIFIED);
      assertThat(s.verified()).isFalse();
      // 미검증 단계는 반드시 현재 화면과 다를 가능성을 고지한다(추측 자동화 금지).
      assertThat(s.caution()).contains("다를 수 있습니다");
    }
  }

  @Test
  @DisplayName("화면 조사 사실을 그대로 반영: minwonChk [네]/[아니요] · #popup-alert · rsltReceiveYn · 인증 popup 4수단")
  void carriesPocVerifiedFacts() {
    FssGuidedStep s3 = FssGuidedWorkflow.step(3);
    assertThat(s3.where()).contains("forInsertSelect.do");
    assertThat(s3.checkAction()).contains("사실확인 필요한 민원입니까").contains("[네]").contains("[아니요]");
    assertThat(s3.caution()).contains("minwonChk").contains("checkForm");

    FssGuidedStep s4 = FssGuidedWorkflow.step(4);
    assertThat(s4.where()).contains("forInsertAgre.do");
    assertThat(s4.checkAction()).contains("agreechkYn").contains("sinfoSndYn").contains("agreechkRgnoYn");
    assertThat(s4.checkAction()).contains("#popup-alert").contains("민원신청");

    FssGuidedStep s5 = FssGuidedWorkflow.step(5);
    assertThat(s5.where()).contains("forInsertStep1.do");
    FssGuidedStep s6 = FssGuidedWorkflow.step(6);
    assertThat(s6.where()).contains("forInsertAgre.do").contains("같은 한 화면");
    assertThat(s6.checkAction()).contains("민원자율조정").contains("[없다]");
    assertThat(s6.caution()).contains("rsltReceiveYn");
    FssGuidedStep s7 = FssGuidedWorkflow.step(7);
    assertThat(s7.checkAction()).contains("간편인증").contains("공동인증서").contains("휴대폰").contains("아이핀");
    FssGuidedStep s8 = FssGuidedWorkflow.step(8);
    assertThat(s8.checkAction()).contains("처리결과회신방법").contains("전자통지").contains("서면 우편통지")
        .contains("인터넷");
    assertThat(s8.caution()).contains("rsltRplyMthdDiv");
  }

  @Test
  void officialEntryUrlMatchesPocAndManual() {
    assertThat(FssGuidedWorkflow.OFFICIAL_ENTRY_URL)
        .isEqualTo("https://www.fss.or.kr/fss/minwon/search.jsp?query=&collection=simcase"
            + "&searchField=ALL&sort=&pageNum=1&rgnlCd=&cvplCd=&cvplCd2=");
  }

  @Test
  @DisplayName("honest UX: 화면 감지·자동 진행을 주장하는 문구 금지 — '확인해주세요/눌러주세요' 형태만 허용")
  void bannedDetectionPhrasesAbsentFromAllUserFacingText() {
    for (FssGuidedStep s : FssGuidedWorkflow.steps()) {
      String all = s.title() + " " + s.where() + " " + s.checkAction() + " " + s.confirmLabel()
          + " " + (s.caution() == null ? "" : s.caution());
      assertThat(all)
          .doesNotContain("확인했습니다")
          .doesNotContain("감지했습니다")
          .doesNotContain("찾았습니다")
          .doesNotContain("자동으로")
          .doesNotContain("사용자를 대신해");
    }
    assertThat(FssGuidedWorkflow.PC_CHROME_NOTICE).isEqualTo("금감원 민원 Agent는 PC의 Chrome 브라우저에서 이용할 수 있습니다.");
    assertThat(FssGuidedWorkflow.EXPLANATION)
        .contains("단계별로 안내합니다")
        .contains("사용자가 직접 진행합니다");
  }

  @Test
  @DisplayName("인증·개인정보·최종 제출은 사용자 영역 — Agent 대행 불가 문구가 단계에 존재")
  void userOwnedBoundariesAreStated() {
    FssGuidedStep auth = FssGuidedWorkflow.step(7);
    assertThat(auth.caution()).contains("NewStart는 인증 수단을 고르거나 인증을 대신하지 않습니다");
    assertThat(FssGuidedWorkflow.step(5).caution()).contains("직접");
    FssGuidedStep last = FssGuidedWorkflow.step(14);
    assertThat(last.caution()).contains("최종 제출은 사용자가 직접 결정합니다").contains("NewStart는 제출 버튼을 누르지 않으며");
  }
}
