package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateDetailResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateDetailResponse.ContentState;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.Category;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.RelevancePriority;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.repository.NewStartOfficialUpdateRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * NewStart 내부 상세보기 계약:
 * (1) "공식 출처 확인" 보조 링크는 검증된 공식 호스트 URL에서만 노출되고,
 * (2) 본문은 저장된 데이터(content > 잘리지 않은 summary > 잘린 summary)만으로 FULL/PARTIAL/MISSING 을 판정하며,
 * (3) 저장되지 않은 내용은 결코 생성하지 않는다.
 */
class NewStartUpdateDetailPolicyTest {

  @Test
  void doubleDomainPollutedUrlIsRepairedToCanonicalOfficialUrl() {
    // 2026-09-23 Production 실측: 저장된 오염 URL 클릭 시 newstartfund.or.kr 가 400 반환
    assertThat(OfficialSourceLinkPolicy.verifiedUrl(SourceType.NEWSTART_FUND,
        "https://www.newstartfund.or.kr/https://www.newstartfund.or.kr/Faq/List.do?pageIndex=1&schPrcdRnk=005"))
        .isEqualTo("https://www.newstartfund.or.kr/Faq/List.do?pageIndex=1&schPrcdRnk=005");
  }

  @Test
  void bareOfficialHostIsPromotedToWwwCanonical() {
    assertThat(OfficialSourceLinkPolicy.verifiedUrl(SourceType.NEWSTART_FUND,
        "https://newstartfund.or.kr/Faq/List.do"))
        .isEqualTo("https://www.newstartfund.or.kr/Faq/List.do");
  }

  @Test
  void nonOfficialOrMalformedUrlsNeverProduceLink() {
    assertThat(OfficialSourceLinkPolicy.verifiedUrl(SourceType.NEWSTART_FUND, null)).isNull();
    assertThat(OfficialSourceLinkPolicy.verifiedUrl(SourceType.NEWSTART_FUND, "  ")).isNull();
    assertThat(OfficialSourceLinkPolicy.verifiedUrl(SourceType.NEWSTART_FUND,
        "javascript:alert(1)")).isNull();
    assertThat(OfficialSourceLinkPolicy.verifiedUrl(SourceType.NEWSTART_FUND,
        "http://evil.example.com/Faq/List.do")).isNull();
    assertThat(OfficialSourceLinkPolicy.verifiedUrl(SourceType.NEWSTART_FUND,
        "https://not-official.example.com/Faq/List.do")).isNull();
    // FSC 데이터에 새출발기금 호스트 등 크로스 출처 URL은 노출하지 않는다.
    assertThat(OfficialSourceLinkPolicy.verifiedUrl(SourceType.FSC,
        "https://www.newstartfund.or.kr/Faq/List.do")).isNull();
  }

  @Test
  void sourceTypesWithoutDocumentedHostsHideLinkEntirely() {
    assertThat(OfficialSourceLinkPolicy.verifiedUrl(SourceType.PUBLIC_AGENCY, "https://example.go.kr/a"))
        .isNull();
  }

  @Test
  void documentedNewSourceTypesExposeVerifiedOfficialLinks() {
    assertThat(OfficialSourceLinkPolicy.verifiedUrl(SourceType.FSS, "https://www.fss.or.kr/a"))
        .isEqualTo("https://www.fss.or.kr/a");
    assertThat(OfficialSourceLinkPolicy.verifiedUrl(SourceType.FSS, "https://fines.fss.or.kr/a"))
        .isEqualTo("https://fines.fss.or.kr/a");
    assertThat(OfficialSourceLinkPolicy.verifiedUrl(SourceType.CREDIT4U, "https://www.credit4u.or.kr/a"))
        .isEqualTo("https://www.credit4u.or.kr/a");
    assertThat(OfficialSourceLinkPolicy.verifiedUrl(SourceType.COURT, "https://ecfs.scourt.go.kr/a"))
        .isEqualTo("https://ecfs.scourt.go.kr/a");
    assertThat(OfficialSourceLinkPolicy.verifiedUrl(SourceType.HOPE_RETURN_PACKAGE,
        "https://www.bizinfo.go.kr/sii/siia/selectSIIA200Detail.do?pblancId=PBLN_1"))
        .isEqualTo("https://www.bizinfo.go.kr/sii/siia/selectSIIA200Detail.do?pblancId=PBLN_1");
    // 크로스 출처 URL은 여전히 노출하지 않는다.
    assertThat(OfficialSourceLinkPolicy.verifiedUrl(SourceType.COURT, "https://www.fss.or.kr/a"))
        .isNull();
  }

  @Test
  void detailBodyPrefersStoredContentOverSummary() {
    NewStartOfficialUpdate update = faq("약정 이후에도 조사가 진행되나요?",
        "전체 공식 답변 원문입니다.", "요약 니펫...");
    NewStartOfficialUpdateDetailResponse detail = NewStartOfficialUpdateDetailResponse.from(update, null);
    assertThat(detail.contentState()).isEqualTo(ContentState.FULL);
    assertThat(detail.body()).isEqualTo("전체 공식 답변 원문입니다.");
  }

  @Test
  void untruncatedSummaryCountsAsFullStoredContent() {
    // 현행 production FAQ 행: 답변이 300자 미만이라 summary 가 곧 수집된 공식 답변 전문이다.
    NewStartOfficialUpdate update = faq("도덕적 해이 심사에 대해 설명해주세요.",
        null, "도덕적 해이 조사 제도란 새출발기금 채무조정 성실 상환자를 대상으로 운영되는 확인 절차입니다.");
    NewStartOfficialUpdateDetailResponse detail = NewStartOfficialUpdateDetailResponse.from(update, null);
    assertThat(detail.contentState()).isEqualTo(ContentState.FULL);
    assertThat(detail.body()).startsWith("도덕적 해이 조사 제도란");
    assertThat(detail.bodyIsHtml()).isFalse();
  }

  @Test
  void truncatedSummaryIsPartialAndKeepsStoredTextVerbatim() {
    NewStartOfficialUpdate update = faq("공공정보 해제는 언제 이루어지나요?",
        null, "303자에서 잘린 답변 텍스트...");
    NewStartOfficialUpdateDetailResponse detail = NewStartOfficialUpdateDetailResponse.from(update, null);
    assertThat(detail.contentState()).isEqualTo(ContentState.PARTIAL);
    assertThat(detail.body()).endsWith("...");
  }

  @Test
  void titleOnlyOrBlankSummariesAreMissingNotFabricated() {
    // 수집 어댑터의 빈 답변 폴백: summary = title
    NewStartOfficialUpdate sameAsTitle = faq("이의신청이 가능한가요?", null, "이의신청이 가능한가요?");
    assertThat(NewStartOfficialUpdateDetailResponse.from(sameAsTitle, null).contentState())
        .isEqualTo(ContentState.MISSING);
    assertThat(NewStartOfficialUpdateDetailResponse.from(sameAsTitle, null).body()).isNull();

    NewStartOfficialUpdate blank = faq("이의신청이 가능한가요?", null, "   ");
    assertThat(NewStartOfficialUpdateDetailResponse.from(blank, null).contentState())
        .isEqualTo(ContentState.MISSING);
  }

  @Test
  void agencyNamesComeFromRegisteredSourceTypesOnly() {
    NewStartOfficialUpdate fund = faq("t", null, "s");
    assertThat(NewStartOfficialUpdateDetailResponse.from(fund, null).agencyName()).isEqualTo("새출발기금");
    assertThat(agencyNameOf(SourceType.FSS)).isEqualTo("금융감독원");
    assertThat(agencyNameOf(SourceType.CREDIT4U)).isEqualTo("Credit4U");
    assertThat(agencyNameOf(SourceType.COURT)).isEqualTo("법원");
    assertThat(agencyNameOf(SourceType.CCRS)).isEqualTo("신용회복위원회");
    assertThat(agencyNameOf(SourceType.HOPE_RETURN_PACKAGE)).isEqualTo("희망리턴패키지");
    assertThat(agencyNameOf(SourceType.PUBLIC_AGENCY)).isNull();
  }

  private static String agencyNameOf(SourceType sourceType) {
    NewStartOfficialUpdate update = NewStartOfficialUpdate.review(
        sourceType, "출처", "제목", "요약 본문", null,
        "https://www.example.go.kr/a", "DOC-1", null, null, null, Category.NOTICE,
        RelevancePriority.NORMAL, "DOC:" + sourceType.name() + ":doc-1", LocalDateTime.now());
    return NewStartOfficialUpdateDetailResponse.from(update, null).agencyName();
  }

  @Test
  void sanitizeContentStripsScriptsButKeepsOfficialText() {
    String cleaned = NewStartOfficialUpdateService.sanitizeContent(
        "<p>공식 답변</p><script>alert('xss')</script>");
    assertThat(cleaned).contains("공식 답변");
    assertThat(cleaned).doesNotContain("<script");
    assertThat(cleaned).doesNotContain("alert");
  }

  @Test
  void publicDetailRejectsNonPublishedRows() {
    NewStartOfficialUpdateRepository repository = org.mockito.Mockito.mock(NewStartOfficialUpdateRepository.class);
    NewStartOfficialUpdateService service = new NewStartOfficialUpdateService(repository);
    // review() 는 REVIEW 상태로 생성된다 — 게시 전 항목은 내부 상세에서도 열람 불가.
    org.mockito.Mockito.when(repository.findById(41L)).thenReturn(java.util.Optional.of(faq("t", null, "s")));
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.publicUpdateDetail(41L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("게시된");
  }

  @Test
  void publicDetailServesPublishedRowAndRepairsSourceUrlForAuxLink() {
    NewStartOfficialUpdateRepository repository = org.mockito.Mockito.mock(NewStartOfficialUpdateRepository.class);
    NewStartOfficialUpdateService service = new NewStartOfficialUpdateService(repository);
    NewStartOfficialUpdate update = faq("도덕적 해이 심사 결과에 대해 이의신청이 가능한가요?",
        null, "이의신청 절차 안내 전문 텍스트");
    update.publish("admin");
    org.mockito.Mockito.when(repository.findById(41L)).thenReturn(java.util.Optional.of(update));

    NewStartOfficialUpdateDetailResponse detail = service.publicUpdateDetail(41L);
    assertThat(detail.contentState()).isEqualTo(ContentState.FULL);
    // 오염된 저장 URL(400 반환)이 그대로 외부 링크로 나가선 안 된다: 복구된 canonical URL만 노출.
    assertThat(detail.verifiedSourceUrl())
        .isEqualTo("https://www.newstartfund.or.kr/Faq/List.do?pageIndex=1&schPrcdRnk=005");
  }

  private static NewStartOfficialUpdate faq(String title, String content, String summary) {
    NewStartOfficialUpdate update = NewStartOfficialUpdate.review(
        SourceType.NEWSTART_FUND, "새출발기금 공식 FAQ", title, summary, content,
        "https://www.newstartfund.or.kr/https://www.newstartfund.or.kr/Faq/List.do?pageIndex=1&schPrcdRnk=005",
        "NSF-FAQ-1", null, null, null, Category.ELIGIBILITY,
        RelevancePriority.NORMAL, "DOC:NEWSTART_FUND:nsf-faq-1", LocalDateTime.now());
    org.springframework.test.util.ReflectionTestUtils.setField(update, "updateId", 41L);
    return update;
  }
}
