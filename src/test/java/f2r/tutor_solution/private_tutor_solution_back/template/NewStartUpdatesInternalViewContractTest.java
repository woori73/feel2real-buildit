package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateDetailResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateDetailResponse.ContentState;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * NewStart "최신 정보" 내부 상세보기 계약 (2026-09-23 방향 변경):
 * 카드 클릭은 외부 기관 URL(newstartfund.or.kr 400 Bad Request 재현 경로)이 아니라
 * NewStart 내부 상세 라우트로 이동해야 하며, NewStart footer에서는 사업자 정보 블록 전체를
 * 렌더하지 않는다(2026-09-30 지시). 다른 서비스(BuildIt landing, F2R home)의 footer는
 * 그대로 유지되어야 한다. 섹션 명칭은 사용자 화면에서 "관련 기관 공시 업데이트"로 통일한다.
 */
class NewStartUpdatesInternalViewContractTest {

  private static final String POLLUTED_OFFICIAL_URL =
      "https://www.newstartfund.or.kr/https://www.newstartfund.or.kr/Faq/List.do?pageIndex=1&schPrcdRnk=005";
  private static final String ADDRESS = "서울특별시 강남구 압구정로34길 11, 2층 206호";

  private static TemplateEngine engine() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    return engine;
  }

  private static NewStartOfficialUpdateResponse faqItem(long id, String title, String summary) {
    return new NewStartOfficialUpdateResponse(id, "NEWSTART_FUND", "새출발기금 공식 FAQ", title, summary,
        POLLUTED_OFFICIAL_URL, "NSF-FAQ-" + id, null, null, null, "ELIGIBILITY", "NORMAL",
        "PUBLISHED", false, null, null, null);
  }

  private static String renderHome(List<NewStartOfficialUpdateResponse> latestUpdates) {
    Context context = new Context();
    context.setVariable("latestUpdates", latestUpdates);
    return engine().process("newstart/index", context);
  }

  // ---- A/B/F/G: 홈 ----

  @Test
  @DisplayName("A. 홈 최신 정보 카드의 클릭 대상은 external source URL이 아니라 NewStart 내부 상세 라우트다")
  void homeUpdateCardsLinkToInternalDetailRoute() {
    String html = renderHome(List.of(
        faqItem(43L, "채무조정 약정 체결 이후에도 도덕적 해이 조사가 진행되나요?", "약정 이후에도..."),
        faqItem(42L, "도덕적 해이 심사 결과에 대해 이의신청이 가능한가요?", "이의신청은...")));

    assertThat(html).contains("href=\"/newstart/updates/43\"");
    assertThat(html).contains("href=\"/newstart/updates/42\"");
    // 카드가 외부 기관 도메인으로 곧장 이동하면 안 된다 (400 Bad Request 재현 경로 차단).
    assertThat(html).doesNotContain("newstartfund.or.kr");
    // 2026-09-30 명칭 지시: 기관 공시 영역 사용자 라벨은 '관련 기관 공시 업데이트'로 통일.
    assertThat(html).contains("관련 기관 공시 업데이트").doesNotContain("최신 정보");
    assertThat(html).contains("공시 업데이트 전체 보기");
  }

  @Test
  @DisplayName("F. NewStart 홈 footer에 사업자 주소가 표시되지 않는다")
  void newStartHomeFooterHidesAddress() {
    String html = renderHome(List.of());
    assertThat(html).doesNotContain(ADDRESS);
    assertThat(html).doesNotContain("가락동");
  }

  @Test
  @DisplayName("G. NewStart 홈 footer는 사업자/출판사 정보 블록 자체를 렌더하지 않는다 (2026-09-30 사용자 지시)")
  void newStartHomeFooterOmitsBusinessInfoBlock() {
    String html = renderHome(List.of());
    assertThat(html).doesNotContain("상호명: 압구정에듀홀릭출판사");
    assertThat(html).doesNotContain("대표: 박정민");
    assertThat(html).doesNotContain("사업자등록번호: 146-99-01347");
    assertThat(html).doesNotContain("통신판매업신고번호: 2026-서울송파-1411");
    assertThat(html).doesNotContain("010-8507-1453");
    assertThat(html).doesNotContain("woorinaesin@naver.com");
    // display:none이 아니라 미렌더 — 빈 footer-biz 컨테이너가 남으면 안 된다.
    assertThat(html).doesNotContain("footer-biz");
    // 브랜드·약관/정책·저작권 행은 유지되어 하단이 갑자기 사라지지 않는다.
    assertThat(html).contains("© 2026 Feel 2 Real. All rights reserved.");
  }

  @Test
  @DisplayName("G. 다른 서비스(BuildIt footer, F2R 공용 footer)는 학원 사업자 정보와 주소 표기를 유지한다 (2026-10-10 압구정에듀홀릭학원으로 변경)")
  void otherServiceFootersStillShowAddress() {
    TemplateEngine engine = engine();
    String buildIt = engine.process(
        new TemplateSpec("fragments/_public_footer", "publicFooterBuildIt"), new Context());
    assertThat(buildIt).contains(ADDRESS);
    String plain = engine.process(
        new TemplateSpec("fragments/_public_footer", "publicFooter"), new Context());
    assertThat(plain).contains(ADDRESS);
    assertThat(plain).contains("상호명: 압구정에듀홀릭학원").contains("사업자등록번호: 759-91-01977");
  }

  // ---- B/C/D: 내부 상세 화면 ----

  private static String renderDetail(NewStartOfficialUpdateDetailResponse detail) {
    Context context = new Context();
    context.setVariable("detail", detail);
    return engine().process("newstart/update_detail", context);
  }

  private static NewStartOfficialUpdateDetailResponse fullStoredFaq(String verifiedSourceUrl) {
    // 2026-09-23 production id=42 실측 형태: 300자 미만 저장 답변 전문(summary) — CONTENT 없음 생성 없음.
    return new NewStartOfficialUpdateDetailResponse(42L, "NEWSTART_FUND", "새출발기금", "새출발기금 공식 FAQ",
        "도덕적 해이 심사 결과에 대해 이의신청이 가능한가요?",
        "심사 결과에 이의가 있는 경우 통지일로부터 소명자료를 제출하실 수 있습니다.",
        false, ContentState.FULL, null, null, verifiedSourceUrl);
  }

  @Test
  @DisplayName("B. 내부 상세화면에 출처 기관/수집 출처/제목/공식 본문이 표시된다")
  void detailShowsAgencySourceTitleAndOfficialBody() {
    String html = renderDetail(fullStoredFaq("https://www.newstartfund.or.kr/Faq/List.do?pageIndex=1&schPrcdRnk=005"));
    assertThat(html).contains("새출발기금</p>"); // 출처 기관
    assertThat(html).contains("새출발기금 공식 FAQ"); // 수집 출처/출처 표기
    assertThat(html).contains("도덕적 해이 심사 결과에 대해 이의신청이 가능한가요?");
    assertThat(html).contains("소명자료를 제출하실 수 있습니다"); // 저장된 공식 본문
    assertThat(html).contains("공식 출처 확인");
    assertThat(html).contains("target=\"_blank\"");
    assertThat(html).contains("rel=\"noopener noreferrer\"");
  }

  @Test
  @DisplayName("C. source URL이 오염/invalid여도 사용자는 내부 상세에서 공식 본문을 읽을 수 있다")
  void detailReadableEvenWhenSourceUrlInvalid() {
    NewStartOfficialUpdateDetailResponse broken = new NewStartOfficialUpdateDetailResponse(
        41L, "NEWSTART_FUND", "새출발기금", "새출발기금 공식 FAQ",
        "도덕적 해이 심사에 대해 설명해주세요.",
        "도덕적 해이 조도는 상환 능력 대비 과소 상환 여부 등을 확인하는 절차입니다.",
        false, ContentState.FULL, null, null, null); // 검증 실패 → 외부 링크 없음
    String html = renderDetail(broken);
    assertThat(html).contains("상환 능력 대비 과소 상환 여부");
    assertThat(html).doesNotContain("공식 출처 확인");
    assertThat(html).doesNotContain(POLLUTED_OFFICIAL_URL);
    assertThat(html).contains("공식 원문 링크는 확인되지 않아 표시하지 않습니다");
  }

  @Test
  @DisplayName("D. 검증되지 않은 source URL에서는 '공식 출처 확인' 외부 링크 버튼이 렌더링되지 않는다")
  void invalidSourceUrlHidesExternalLinkButton() {
    String html = renderDetail(fullStoredFaq(null));
    assertThat(html).doesNotContain("공식 출처 확인");
    assertThat(html).doesNotContain("<a href=\"http");
    // 단, 내부 본문 열람과 출처 표기는 유지된다.
    assertThat(html).contains("소명자료를 제출하실 수 있습니다");
    assertThat(html).contains("출처:");
  }

  @Test
  @DisplayName("본문 미저장 항목은 제목만 보이되 CONTENT MISSING으로 표시하고 내용을 생성하지 않는다")
  void missingContentIsFlaggedNotFabricated() {
    NewStartOfficialUpdateDetailResponse missing = new NewStartOfficialUpdateDetailResponse(
        99L, "NEWSTART_FUND", "새출발기금", "새출발기금 공식 FAQ",
        "본문이 미수집인 항목", null, false, ContentState.MISSING, null, null, null);
    String html = renderDetail(missing);
    assertThat(html).contains("CONTENT MISSING");
    assertThat(html).contains("아직 공식 원문 본문이 수집·저장되지 않았습니다");
    assertThat(html).contains("본문이 미수집인 항목"); // 제목은 표시하되
    assertThat(html).doesNotContain("ns-detail-body"); // 본문 블록(생성 내용)은 없다
    assertThat(missing.body()).isNull(); // 생성된 본문 없음
  }

  // ---- E: 전체 업데이트 목록 화면 ----

  @Test
  @DisplayName("E. 전체 업데이트 목록 JS도 각 항목을 내부 상세보기로 라우팅한다")
  void updatesListJsRoutesInternally() throws Exception {
    String js = new String(new ClassPathResource("static/js/newstart-updates.js").getInputStream().readAllBytes());
    assertThat(js).contains("href=\"/newstart/updates/");
    assertThat(js).doesNotContain("item.originalUrl");
    assertThat(js).doesNotContain("target=\"_blank\"");
  }
}
