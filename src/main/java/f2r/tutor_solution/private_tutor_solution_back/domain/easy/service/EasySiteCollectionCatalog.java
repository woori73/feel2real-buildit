package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 사용자가 직접 로그인해 거래 자료를 내려받는 기관 사이트 목록(유료 수집 API 대신 쓰는 경로).
 *
 * <p>주소는 각 기관의 공식 대표 주소만 둔다. 메뉴 이름은 사이트 개편으로 바뀔 수 있어 안내 문구로만 제공하며, 로그인·인증서·OTP 처리는
 * 언제나 사용자 본인이 한다(AI·서버는 인증 정보를 받지 않는다).
 */
public final class EasySiteCollectionCatalog {

  /** 장부에 저장할 수 있는 자료 종류. */
  public enum Dataset {
    /** 사업용 통장 입출금. */
    BANK_ACCOUNT,
    /** 사업용 카드 사용(매입)내역 — 비용. */
    CARD_PURCHASE,
    /** 카드 매출(가맹점) 내역 — 수입. */
    CARD_SALES
  }

  public enum Category {
    BANK,
    CARD,
    HOMETAX,
    CREFIA
  }

  /**
   * @param businessUrl 기업·개인사업자 전용 인터넷뱅킹 주소(없으면 null)
   * @param datasets 이 사이트에서 받아 장부에 저장할 수 있는 자료
   * @param note 사이트별 주의 사항(없으면 null)
   */
  public record Site(String code, Category category, String name, String url, String businessUrl,
      List<Dataset> datasets, String note) {}

  private static final String APP_ONLY = "앱 중심 은행이라 PC에서 거래내역 파일 저장이 안 될 수 있습니다. 앱에서 내려받은 파일을 쓰세요.";

  private static final List<Site> SITES = List.of(
      bank("KB_BANK", "KB국민은행", "https://www.kbstar.com/", "https://obiz.kbstar.com/", null),
      bank("SHINHAN_BANK", "신한은행", "https://www.shinhan.com/", "https://bizbank.shinhan.com/", null),
      bank("WOORI_BANK", "우리은행", "https://www.wooribank.com/", "https://nbi.wooribank.com/", null),
      bank("HANA_BANK", "하나은행", "https://www.kebhana.com/", "https://biz.kebhana.com/", null),
      bank("IBK_BANK", "IBK기업은행", "https://www.ibk.co.kr/", "https://kiup.ibk.co.kr/", null),
      bank("NH_BANK", "NH농협은행", "https://banking.nonghyup.com/", "https://ibz.nonghyup.com/", null),
      bank("SC_BANK", "SC제일은행", "https://www.standardchartered.co.kr/", null, null),
      bank("POST_BANK", "우체국예금", "https://www.epostbank.go.kr/", null, null),
      bank("KFCC", "MG새마을금고", "https://www.kfcc.co.kr/", null, null),
      bank("SUHYUP_BANK", "Sh수협은행", "https://www.suhyup-bank.com/", null, null),
      bank("BUSAN_BANK", "BNK부산은행", "https://www.busanbank.co.kr/", null, null),
      bank("KN_BANK", "BNK경남은행", "https://www.knbank.co.kr/", null, null),
      bank("IM_BANK", "iM뱅크(옛 대구은행)", "https://www.imbank.co.kr/", null, null),
      bank("KJ_BANK", "광주은행", "https://www.kjbank.com/", null, null),
      bank("JB_BANK", "전북은행", "https://www.jbbank.co.kr/", null, null),
      bank("K_BANK", "케이뱅크", "https://www.kbanknow.com/", null, APP_ONLY),
      bank("KAKAO_BANK", "카카오뱅크", "https://www.kakaobank.com/", null, APP_ONLY),
      bank("TOSS_BANK", "토스뱅크", "https://www.tossbank.com/", null, APP_ONLY),
      card("SHINHAN_CARD", "신한카드", "https://www.shinhancard.com/"),
      card("KB_CARD", "KB국민카드", "https://card.kbcard.com/"),
      card("SAMSUNG_CARD", "삼성카드", "https://www.samsungcard.com/"),
      card("HYUNDAI_CARD", "현대카드", "https://www.hyundaicard.com/"),
      card("LOTTE_CARD", "롯데카드", "https://www.lottecard.co.kr/"),
      card("WOORI_CARD", "우리카드", "https://www.wooricard.com/"),
      card("HANA_CARD", "하나카드", "https://www.hanacard.co.kr/"),
      card("BC_CARD", "BC카드", "https://www.bccard.com/"),
      card("NH_CARD", "NH농협카드", "https://card.nonghyup.com/"),
      new Site("HOMETAX", Category.HOMETAX, "국세청 홈택스", "https://www.hometax.go.kr/", null,
          List.of(Dataset.CARD_PURCHASE),
          "사업용 신용카드 사용내역은 카드사 사이트와 같은 자료입니다. 한 곳에서만 올리세요. 전자세금계산서·현금영수증은 은행 거래와 겹쳐"
              + " 두 번 잡히므로 장부에 올리지 말고 대조용으로만 봅니다."),
      new Site("CREFIA_CARDSALES", Category.CREFIA, "여신금융협회 카드매출 조회", "https://www.cardsales.or.kr/",
          null, List.of(Dataset.CARD_SALES),
          "카드 매출(승인·매입)만 올립니다. 카드사가 통장에 넣어 준 입금(정산)은 은행 거래로 들어오며 매출로 두 번 잡지 않습니다."));

  /** 범주별 내려받기 순서(메뉴 이름은 사이트마다 조금씩 다르다). */
  private static final Map<Category, List<String>> STEPS = steps();

  private EasySiteCollectionCatalog() {}

  public static List<Site> sites() {
    return SITES;
  }

  public static Optional<Site> find(String code) {
    if (code == null) {
      return Optional.empty();
    }
    return SITES.stream().filter(site -> site.code().equals(code.trim())).findFirst();
  }

  public static List<String> steps(Category category) {
    return STEPS.get(category);
  }

  /** AI가 사이트 작업 전에 반드시 지킬 규칙. */
  public static List<String> safetyRules() {
    return List.of(
        "로그인·공동/금융인증서·비밀번호·OTP·보안카드·문자인증은 사용자 본인이 직접 입력합니다. AI는 대신 입력하거나 물어보지 않습니다.",
        "비밀번호·인증서 암호·계좌 전체 번호·카드 전체 번호를 대화창이나 MCP 도구 인자에 적지 않습니다.",
        "거래내역 조회·파일 내려받기만 합니다. 이체·결제·해지·설정 변경 버튼은 누르지 않습니다.",
        "공식 주소(아래 url)로만 접속합니다. 검색 광고나 문자로 받은 주소는 쓰지 않습니다.",
        "추가 인증·보안 프로그램 설치·접속 제한 화면이 나오면 멈추고 사용자에게 넘깁니다.",
        "저장할 때 사이트 화면이나 파일에 표시된 조회 건수와 금액 합계를 expectedCount·expectedTotal로 함께 보냅니다."
            + " 합계는 입금과 출금을 서로 빼지 않고 모두 더한 값입니다. 둘이 맞지 않으면 저장하지 않습니다.");
  }

  private static Site bank(String code, String name, String url, String businessUrl, String note) {
    return new Site(code, Category.BANK, name, url, businessUrl, List.of(Dataset.BANK_ACCOUNT), note);
  }

  private static Site card(String code, String name, String url) {
    return new Site(code, Category.CARD, name, url, null, List.of(Dataset.CARD_PURCHASE), null);
  }

  private static Map<Category, List<String>> steps() {
    Map<Category, List<String>> steps = new LinkedHashMap<>();
    steps.put(Category.BANK, List.of(
        "사업용 통장을 쓰는 은행의 공식 주소(개인사업자·법인은 businessUrl)로 들어갑니다.",
        "사용자가 직접 로그인합니다.",
        "조회 → 거래내역조회에서 사업용 계좌와 기간(최대 1년)을 고릅니다.",
        "화면의 조회 건수·입금 합계·출금 합계를 확인하고 엑셀(파일) 저장을 누릅니다.",
        "파일 내용을 행 단위(날짜·입금/출금·금액·적요·잔액)로 정리해 save_easy_site_rows로 저장합니다."));
    steps.put(Category.CARD, List.of(
        "카드사 공식 주소로 들어가 사용자가 직접 로그인합니다. 법인·사업자 카드는 사업자(기업) 회원으로 로그인해야 전체 사용내역이 보입니다.",
        "이용내역(승인내역) 조회에서 사업용 카드와 기간을 고릅니다.",
        "조회 건수·합계를 확인하고 엑셀 저장을 누릅니다.",
        "행 단위(이용일·가맹점·금액·승인번호, 취소는 IN)로 정리해 dataset=CARD_PURCHASE로 저장합니다."));
    steps.put(Category.HOMETAX, List.of(
        "홈택스 공식 주소로 들어가 사용자가 직접 로그인합니다(공동·금융인증서 또는 간편인증).",
        "조회/발급 메뉴에서 사업용 신용카드 사용내역을 엽니다(카드사에서 이미 올린 카드는 건너뜁니다).",
        "기간을 고르고 내려받기를 누른 뒤 dataset=CARD_PURCHASE로 저장합니다.",
        "전자세금계산서·현금영수증 목록은 대조용으로만 보고 장부에는 올리지 않습니다."));
    steps.put(Category.CREFIA, List.of(
        "여신금융협회 카드매출 조회 공식 주소로 들어가 가맹점 회원으로 사용자가 직접 로그인합니다.",
        "매출조회에서 승인(또는 매입) 내역과 기간을 고릅니다.",
        "조회 건수·합계를 확인하고 내려받습니다.",
        "행 단위(거래일·카드사·금액·승인번호, 취소는 OUT)로 정리해 dataset=CARD_SALES로 저장합니다."));
    return Map.copyOf(steps);
  }
}
