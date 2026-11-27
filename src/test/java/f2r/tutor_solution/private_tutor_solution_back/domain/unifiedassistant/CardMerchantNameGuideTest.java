package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CardMerchantNameGuideTest {
    @Test void givenUnrelatedDocumentOrNoNamedIssuer_whenRead_thenLeavesItForGeneralReview() {
        assertThat(CardMerchantNameGuide.create(null)).isEmpty();
        assertThat(CardMerchantNameGuide.create("삼성카드 가맹점 결제 영수증")).isEmpty();
        assertThat(CardMerchantNameGuide.create("가맹점 상호명 변경. 계약된 카드사의 서류 확인.")).isEmpty();
    }

    @Test void givenPartialIssuerGuide_whenRead_thenUsesOnlyItsOwnAttributedSection() {
        var guide = CardMerchantNameGuide.create("""
            가맹점 상호명 변경 신청 안내
            하나카드 신고 방법 하나은행 영업점에서 접수. 필요 서류 사업자등록증 사본과 대표자 신분증.
            수수료 확인 필요. 출처: 하나카드 가맹점 정보변경
            현대카드 신고 방법 현재 접수처에 문의. 필요 서류 사업자등록증 사본. 수수료 확인 필요. 출처: 현대카드 가맹점 메인
            """).orElseThrow();
        assertThat(guide.rows()).extracting(CardMerchantNameGuide.Row::issuer).containsExactlyInAnyOrder("하나카드", "현대카드");
        var hana = guide.rows().stream().filter(r -> r.issuer().equals("하나카드")).findFirst().orElseThrow();
        assertThat(hana.inquiry()).contains("영업점");
        assertThat(hana.documents()).contains("신분증");
        var hyundai = guide.rows().stream().filter(r -> r.issuer().equals("현대카드")).findFirst().orElseThrow();
        assertThat(hyundai.documents()).contains("사업자등록증").doesNotContain("신분증");
        assertThat(hyundai.inquiry()).doesNotContain("팩스", "하나은행");
        assertThat(guide.rows()).allSatisfy(row -> assertThat(row.status()).contains("문의 전", "접수 및 반영 여부 미확인").doesNotContain("계약"));
    }

    @Test void givenCallWorksheet_whenRead_thenSeparatesCommonPreparationFromRequirementsAndOmitsNumbers() {
        var guide = CardMerchantNameGuide.create("""
            단말기 기사님 전화용. 카드사 상호명 변경 전화 준비본. 가맹점 문의. 통화 기록표. 출처: 금융위원회.
            옆에 두고 전화할 준비물 새 사업자등록증 사본, 최근 매출전표. 사업자등록번호 자체가 바뀌면 확인.
            카드사별 문의 창구. NH 농협카드 0000-0000 승인과 가맹점 전용. BC카드 1111-1111 가맹점 전용 창구.
            번호가 안 맞을 때는 현재 창구를 확인. 전화하거나 문자 보내세요.
            """).orElseThrow();
        assertThat(guide.rows()).hasSize(2).allSatisfy(row -> assertThat(row.documents()).contains("공통 준비물", "매출전표").doesNotContain("인감증명서"));
        assertThat(guide.note()).contains("서로 다르게", "공식 홈페이지", "대표자", "사업자등록번호", "시험 영수증");
        assertThat(guide.rows().getFirst().inquiry()).contains("독자망", "BC망");
        assertThat(guide.toString()).doesNotContain("0000", "1111", "전화하거나", "문자 보내세요");
    }

    @Test void givenSingleIssuerWithoutCitedDetails_whenRead_thenDoesNotAttributeAnotherDocumentsFacts() {
        var guide = CardMerchantNameGuide.create("카드사 상호명 변경 전화 준비본. BC카드 가맹점 상호 변경 문의 초안").orElseThrow();
        assertThat(guide.rows()).hasSize(1);
        assertThat(guide.rows().getFirst().documents()).doesNotContain("신분증");
        assertThat(guide.note()).contains("공식 홈페이지").doesNotContain("서로 다르게");
    }
}
