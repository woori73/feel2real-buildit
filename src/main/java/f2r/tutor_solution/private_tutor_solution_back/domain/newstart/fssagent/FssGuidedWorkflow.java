package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.fssagent;

import java.util.List;

/**
 * 금감원 민원 Guided Agent 14단계 정의 (Phase 1 = C: 사용자 PC Chrome + 안내만).
 *
 * <p>STEP 1~7의 화면 사실은 HOME READ-ONLY POC 실측 결과(handoff §3, 2026-09-24)를 그대로
 * 사용한다. 2026-09-24 아카데미 PC Chrome 재조사로 STEP 3(minwonChk [네]/[아니요]),
 * STEP 6(rsltReceiveYn), STEP 8(처리결과회신방법 rsltRplyMthdDiv)의 실제 라벨을 확인했다.
 * STEP 9~14는 인증 이후 입력 화면(금융회사명·상품종류·유형·제목/요지/본문)을 조사하지 못했으므로
 * 카페 매뉴얼(2961) 텍스트 기준 {@link FssStepEvidence#MANUAL_UNVERIFIED}로 두고, 매 단계
 * "현재 화면과 다를 수 있습니다"를 고지한다 — 추측 selector·자동화 없음.
 *
 * <p>honest UX: 서버는 사용자 금감원 탭을 볼 수 없다. 모든 문장은
 * "금감원 화면에서 ○○을 확인해주세요" + "완료했다면 아래 버튼을 눌러주세요" 형태이고,
 * "확인했습니다/감지했습니다/찾았습니다" 류 완료·감지 표현을 금지한다(회귀 테스트로 고정).
 */
public final class FssGuidedWorkflow {

  /** POC STEP 1과 매뉴얼 ①이 동일하게 확인한 진입 URL. */
  public static final String OFFICIAL_ENTRY_URL =
      "https://www.fss.or.kr/fss/minwon/search.jsp?query=&collection=simcase&searchField=ALL"
          + "&sort=&pageNum=1&rgnlCd=&cvplCd=&cvplCd2=";

  public static final String PC_CHROME_NOTICE =
      "금감원 민원 Agent는 PC의 Chrome 브라우저에서 이용할 수 있습니다.";

  public static final String EXPLANATION =
      "NewStart가 금융감독원 민원 신청 과정을 단계별로 안내합니다.\n"
          + "개인정보 입력, 본인인증 및 최종 제출은 금융감독원 공식사이트에서 사용자가 직접 진행합니다.";

  /** STEP 5~7은 사용자 직접 입력·인증 영역 — Agent는 안내만 하고 대행·입력·저장하지 않는다. */
  public static final String PERSONAL_DATA_BANNER =
      "이 단계의 개인정보 입력과 본인인증은 금감원 화면에서 사용자가 직접 진행합니다. "
          + "NewStart는 이를 대신 입력·저장·기록하지 않습니다.";

  private static final List<FssGuidedStep> STEPS = List.of(
      new FssGuidedStep(1, "유사사례 검색 화면에서 [신청] 찾기",
          "금감원 공식사이트 유사사례 검색 화면(search.jsp)",
          "금감원 화면 아래쪽에 [신청] 버튼이 보이는지 확인해주세요. "
              + "사전 단계로 통합검색에서 관련 유사사례·분쟁정보를 미리 살펴볼 수 있습니다.",
          "이 단계를 완료했습니다",
          null, FssStepEvidence.POC_VERIFIED, null),
      new FssGuidedStep(2, "[신청] 클릭과 pop-up [확인]",
          "같은 화면(search.jsp) — 새 화면이 아닙니다",
          "[신청]을 누르면 pop-up 창이 뜹니다. 그 안의 [확인]을 눌러주세요.",
          "pop-up의 [확인]을 눌렀습니다",
          null, FssStepEvidence.POC_VERIFIED, null),
      new FssGuidedStep(3, "사실확인 필요 민원 질문 (minwonChk)",
          "민원 성격 질문 화면(forInsertSelect.do)",
          "이 화면에서 질문이 보일 것입니다: "
              + "\"본인의 금융거래와 관련하여 금융회사에 사실확인 필요한 민원입니까?\" "
              + "선택지는 [네](값 Y)와 [아니요](값 N) 두 개입니다(minwonChk). "
              + "화면에는 \"대리제출인 경우 아니요를 누르세요\" 라는 안내도 있습니다. "
              + "현재 화면에서 직접 확인하고 고른 뒤 [다음]을 눌러주세요.",
          "선택하고 [다음]을 눌렀습니다",
          "과거 매뉴얼의 [확인]→[아니요] 버튼 흐름이 현재 화면에는 없습니다. "
              + "2026-09-24 조사에서 확인한 현재 흐름은 minwonChk [네]/[아니요] 라디오 + [다음](checkForm)입니다.",
          FssStepEvidence.POC_VERIFIED, null),
      new FssGuidedStep(4, "개인정보 동의 3종",
          "개인정보 동의 화면(forInsertAgre.do)",
          "이 화면에 동의 항목이 3개 있습니다: "
              + "① 개인정보 수집·이용 동의(agreechkYn), "
              + "② 민감정보 처리 동의(sinfoSndYn), "
              + "③ 고유식별정보 처리 동의(agreechkRgnoYn). "
              + "각 항목의 [동의]를 선택하고 [다음]을 눌러주세요. "
              + "그러면 \"재설정 / 민원신청 / 닫기\" 버튼이 있는 pop-up 창(#popup-alert)이 뜹니다"
              + " — [민원신청]을 눌러주세요.",
          "동의하고 [민원신청]을 눌렀습니다",
          "과거 매뉴얼에는 없는 pop-up 창(#popup-alert)이 현재 추가되었습니다. "
              + "[민원신청]을 누르면 개인정보 입력 화면으로 진행됩니다(자동 제출이 아닙니다).",
          FssStepEvidence.POC_VERIFIED, null),
      new FssGuidedStep(5, "개인정보 입력",
          "개인정보 입력 화면(forInsertStep1.do)",
          "이 화면에서 직접 본인 정보를 입력합니다. "
              + "POC에서 확인한 입력란: 성명, 생년월일, 연락처, 주소"
              + "(주소는 [주소찾기]를 누르면 다음 지도 주소 검색 팝업(새 창)에서 찾을 수 있습니다). "
              + "2026-09-24 조사에서는 회신방법·이메일(필수 입력) 항목과 가상키패드 사용 선택지도 함께 보였습니다.",
          "개인정보 입력을 마쳤습니다",
          PERSONAL_DATA_BANNER,
          FssStepEvidence.POC_SCREEN_VERIFIED,
          "이 단계의 개인정보 입력란은 사용자가 직접 채웁니다. "
              + "NewStart 화면에 개인정보를 적지 말아 주세요."),
      new FssGuidedStep(6, "과거 동일 내용 민원 [없다] 선택",
          "개인정보 입력과 같은 한 화면(forInsertAgre.do) — 별도 화면이 아닙니다",
          "\"과거동일내용의 민원접수 여부 안내\" 항목과 [있다]/[없다]/[기타(자율조정, 취하, "
              + "이첩처리 등)] 선택지가 있습니다. 해당 없으면 [없다]를 고르세요. "
              + "비슷한 질문이 하나 더 있습니다(\"민원자율조정(신속공유 포함) 및 민원이첩 제도 안내\"의 "
              + "[있다]/[없다]) — 무엇을 묻는지 제목을 직접 확인해주세요. "
              + "그 다음 [본인인증받기] 버튼을 눌러주세요.",
          "[없다]를 선택하고 인증 준비를 마쳤습니다",
          "과거 매뉴얼처럼 별도 화면이 아니라 한 화면 안의 항목이라고 합니다. "
              + "2026-09-24 조사에서 확인한 선택지 이름은 rsltReceiveYn입니다.",
          FssStepEvidence.POC_SCREEN_VERIFIED, null),
      new FssGuidedStep(7, "본인인증 (새 창으로 열리는 인증 화면)",
          "[본인인증받기]를 누르면 새 창(certificationPop.do)이 뜹니다",
          "인증 새 창에서 인증 수단을 선택합니다(POC에서 확인한 선택지: "
              + "간편인증, 공동인증서, 휴대폰, 아이핀). 창 안에서 직접 본인인증을 마친 뒤, "
              + "NewStart 탭으로 돌아와 아래 버튼을 눌러주세요.",
          "본인인증을 완료했습니다",
          "NewStart는 인증 수단을 고르거나 인증을 대신하지 않습니다. "
              + "인증서 비밀번호·휴대폰 인증번호는 어떤 화면에도 적지 마세요.",
          FssStepEvidence.POC_SCREEN_VERIFIED, null),
      new FssGuidedStep(8, "처리회신 수단 선택",
          "민원 작성 화면의 \"처리결과회신방법\" 항목",
          "\"필수 입력 처리결과회신방법\" 항목이 보이면 세 선택지 중에서 고르세요: "
              + "[인터넷 : 금감원 홈페이지 인터넷 민원접수 화면에서 확인], "
              + "[전자통지 : 회신문을 카카오톡으로 발송], "
              + "[서면 우편통지 : 등기우편으로 발송]. "
              + "무엇을 받을 것인지 직접 비교해서 선택해주세요.",
          "회신방법을 선택했습니다",
          "2026-09-24 조사에서는 이 항목(rsltRplyMthdDiv)이 인증 이전의 동의 화면에서 이미 보였습니다. "
              + "인증 이후 일부 메뉴명이나 위치는 현재 금융감독원 화면과 다를 수 있습니다.",
          FssStepEvidence.POC_SCREEN_VERIFIED, null),
      new FssGuidedStep(9, "금융회사명 선택",
          "민원 작성 화면 — 금융회사 선택 단계",
          "화면에서 \"금융회사명\" 선택이 보일 것입니다. 카드사인 경우 [기타]에서 [카드]를 "
              + "선택한 후 [카드회사]를 누르고 [다음단계]를 눌러주세요. "
              + "현재 화면에서 직접 확인하고 선택해주세요.",
          "금융회사 선택을 마쳤습니다",
          "인증 이후 일부 메뉴명이나 위치는 현재 금융감독원 화면과 다를 수 있습니다.",
          FssStepEvidence.MANUAL_UNVERIFIED, null),
      new FssGuidedStep(10, "금융상품종류 선택",
          "민원 작성 화면 — 상품 종류 단계",
          "이 화면에서 [신용카드]를 고르고, 적절한 [금융상품종류]를 선택한 뒤 "
              + "[다음단계]를 눌러주세요.",
          "이 단계를 완료했습니다",
          "인증 이후 일부 메뉴명이나 위치는 현재 금융감독원 화면과 다를 수 있습니다.",
          FssStepEvidence.MANUAL_UNVERIFIED, null),
      new FssGuidedStep(11, "[해당없음] 선택",
          "민원 작성 화면 — 선택 단계",
          "화면에서 [해당없음]을 누른 다음 [다음단계]를 눌러주세요.",
          "이 단계를 완료했습니다",
          "인증 이후 일부 메뉴명이나 위치는 현재 금융감독원 화면과 다를 수 있습니다.",
          FssStepEvidence.MANUAL_UNVERIFIED, null),
      new FssGuidedStep(12, "민원 유형 선택",
          "민원 작성 화면 — 유형 선택 단계",
          "화면에서 유형 선택이 보일 것입니다. 카드 추심 금지 민원이면 "
              + "[카드] → [기타] → [추심금지]를 눌러주세요. 다른 유형이면 해당하는 것을 누르세요.",
          "이 단계를 완료했습니다",
          "인증 이후 일부 메뉴명이나 위치는 현재 금융감독원 화면과 다를 수 있습니다.",
          FssStepEvidence.MANUAL_UNVERIFIED, null),
      new FssGuidedStep(13, "제목·요지 붙여넣기",
          "민원 작성 화면 — 제목/요지 단계",
          "화면에서 [제목]과 [요지] 입력란이 보일 것입니다. NewStart AI가 정리한 민원문 중 "
              + "제목과 요지를 직접 복사해 붙여넣고, [다음단계]를 눌러주세요.",
          "붙여넣기를 마쳤습니다",
          "인증 이후 일부 메뉴명이나 위치는 현재 금융감독원 화면과 다를 수 있습니다. "
              + "AI 정리문을 그대로 쓰지 마시고, 없는 사실이 섞였거나 확인할 값([ … 확인 필요 ])을 "
              + "직접 고친 뒤 붙여넣으세요.",
          FssStepEvidence.MANUAL_UNVERIFIED, null),
      new FssGuidedStep(14, "본문 작성 — 최종 제출 직전",
          "민원 작성 화면 — 본문 단계(마지막)",
          "본문 입력란에 복사해 둔 내용을 붙여넣어 주세요. 화면에 [추심원 연락처]"
              + "(가능하면 이름까지)와 채권사 이름을 꼭 남기세요. 첨부파일은 선택이니 없어도 됩니다. "
              + "여기까지 왔으면 화면 아래 [등록]이나 [최종 제출] 버튼이 보일 것입니다. "
              + "이 버튼을 누르기 전에 내용을 한 번 더 직접 확인해주세요.",
          "내용을 직접 살펴봤습니다",
          "인증 이후 일부 메뉴명이나 위치는 현재 금융감독원 화면과 다를 수 있습니다. "
              + "최종 제출은 사용자가 직접 결정합니다. NewStart는 제출 버튼을 누르지 않으며, "
              + "제출 이후 처리 결과는 NewStart가 알지 못합니다.",
          FssStepEvidence.MANUAL_UNVERIFIED, null));

  private FssGuidedWorkflow() {}

  public static List<FssGuidedStep> steps() {
    return STEPS;
  }

  public static FssGuidedStep step(int number) {
    return STEPS.stream().filter(s -> s.number() == number).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unknown fss guided step: " + number));
  }

  public static int total() {
    return STEPS.size();
  }
}
