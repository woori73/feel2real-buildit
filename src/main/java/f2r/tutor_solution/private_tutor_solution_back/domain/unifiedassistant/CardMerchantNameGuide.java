package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** A read-only preparation sheet. No uploaded text or contact details are copied into a row. */
public final class CardMerchantNameGuide {
    public record Guide(List<Row> rows, String note) {}
    public record Row(String issuer, String documents, String inquiry, String status) {}

    private record Issuer(String name, String detailEvidence, String detailQuestion,
                          String contactEvidence, String contactQuestion) {}

    private static final List<Issuer> ISSUERS = List.of(
        new Issuer("신한카드", "제신고", "포털의 제신고 양식과 함께 제출할 서류를 알려 주세요.",
            "정보조회및변경", "가맹점 정보 조회·변경 창구에서 제 상호 변경도 접수할 수 있나요?"),
        new Issuer("삼성카드", "간판명", "간판명 수정과 별도로 등록 상호를 변경할 접수 경로를 알려 주세요.",
            "거래승인전용", "거래승인 창구와 구분되는 가맹점 상호 변경 담당 창구를 알려 주세요."),
        new Issuer("현대카드", "팩스", "정보변경 신청서와 등록증을 제출할 최신 공식 경로를 알려 주세요.",
            "가맹점번호조회", "가맹점번호 조회와 상호 변경 접수를 같은 창구에서 할 수 있나요?"),
        new Issuer("KB국민카드", "개인단독대표", "개인 단독대표의 우편 접수 기준이 제 경우에도 적용되나요? 인감 서류 생략 조건도 알려 주세요.",
            "가맹점번호조회", "가맹점번호를 확인한 뒤 상호 변경을 어디에서 신청해야 하나요?"),
        new Issuer("롯데카드", "신규가맹", "신규 가맹 때 안내된 학원 허가증이 기존 가맹점의 상호 변경에도 필요한가요?",
            "가맹점전용번호", "대표 창구에서 상호 변경 서류를 받는 담당 부서와 접수 방법을 알려 주세요."),
        new Issuer("하나카드", "하나은행영업점", "하나카드 지점 또는 하나은행 영업점 방문이 필요한가요? 학원 관련 증빙도 확인해 주세요.",
            "가맹점관련메뉴", "대표 창구에서 상호 변경까지 접수할 수 있나요? 필요하면 방문할 곳을 알려 주세요."),
        new Issuer("우리카드", "독자가맹점", "독자가맹점인지 BC망인지 먼저 확인해 주시고, 해당 계약의 접수 창구를 알려 주세요.",
            "독자가맹점", "독자가맹점과 BC망 중 어느 쪽에 등록되어 있고 어디로 신청해야 하나요?"),
        new Issuer("NH농협카드", "정보변경신청안내", "기업 가맹점 포털에서 상호 변경이 가능한가요? 구비서류와 방문 필요 여부를 확인해 주세요.",
            "가맹점전용", "승인·가맹점 담당 창구에서 상호 변경을 접수하는 방법을 알려 주세요."),
        new Issuer("BC카드", "간판명", "정보변경 신청서에서 사업자상호명과 간판명 중 어느 항목을 기재하며 어디로 제출하나요?",
            "가맹점전용", "가맹점 전용 창구에서 상호 변경을 접수하는지와 공식 신청 양식을 알려 주세요.")
    );
    private static final Pattern TOPIC = Pattern.compile("상호(?:명|이름)?(?:을|를|은|는|이|가|만)?(?:변경|정정|바꾸|바뀌|바꿨)");
    private static final Pattern SPACE = Pattern.compile("[\\p{Z}\\s]+");
    private static final String STATUS = "문의 전 · 접수 및 반영 여부 미확인";

    private CardMerchantNameGuide() {}

    public static Optional<Guide> create(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) return Optional.empty();
        String text = SPACE.matcher(Normalizer.normalize(extractedText, Normalizer.Form.NFKC)).replaceAll("")
            .toUpperCase(Locale.ROOT).replace("비씨카드", "BC카드")
            .replaceAll("(?<!KB)국민카드", "KB국민카드").replaceAll("(?<!NH)농협카드", "NH농협카드");
        if (!text.contains("가맹점") || !TOPIC.matcher(text).find()) return Optional.empty();
        List<Row> rows = new ArrayList<>();
        String table = between(text, "카드사별문의창구", "번호가안맞을때");
        String common = between(text, "옆에두고전화할준비물", "사업자등록번호자체");
        for (Issuer issuer : ISSUERS) {
            if (!text.contains(issuer.name())) continue;
            // In the reference HTML each issuer's detail block ends with its own source attribution.
            int source = text.indexOf("출처:" + issuer.name());
            String details = "";
            if (source >= 0) {
                int section = text.lastIndexOf("신고방법", source);
                if (section >= 0 && section > text.lastIndexOf("출처:", source - 1)) details = text.substring(section, source);
            }
            String contactRow = contactRow(table, issuer.name());
            String question = "필요한 서류와 공식 접수 방법을 알려 주세요.";
            if (details.contains(issuer.detailEvidence())) question = issuer.detailQuestion();
            else if (contactRow.contains(issuer.contactEvidence())) question = issuer.contactQuestion();
            if ((issuer.name().equals("NH농협카드") || issuer.name().equals("우리카드")) && !question.contains("BC망")) {
                question += " 이 가맹점의 계약이 독자망인지 BC망인지도 확인해 주세요.";
            }
            String documents = documents(details, common);
            rows.add(new Row(issuer.name(), documents,
                issuer.name() + " 가맹점으로 계약되어 있는지 확인 부탁드립니다. 기존 상호를 새 상호로 변경하려고 합니다. " + question, STATUS));
        }
        if (rows.isEmpty()) return Optional.empty();
        String note = "먼저 사업자등록번호가 유지되는지, 대표자가 바뀌었는지, 새 상호로 정정한 사업자등록증이 발급됐는지 확인하세요. 영수증·정산 내역에서 실제 계약사를 확인한 뒤 문의하고 접수 여부와 반영 예정일을 기록하세요. 마지막에는 단말기에서 시험 영수증을 출력해 새 등록증의 상호와 대조하세요.";
        if (text.contains("BC카드")) {
            boolean reviewedGuide = text.contains("상호명을바꾸면,카드사9곳에각각알려야합니다") && text.contains("대행수수료3만원검증");
            boolean reviewedWorksheet = text.contains("카드사상호명변경전화준비본") && text.contains("단말기기사님전화용")
                && text.contains("통화기록표") && text.contains("금융위원회");
            if (reviewedGuide || reviewedWorksheet) {
                note += " 비교한 HTML 안내와 PDF 전화 준비본은 BC카드 문의 번호를 서로 다르게 기재했습니다.";
            }
            note += " BC카드 공식 홈페이지의 최신 가맹점 창구를 확인하세요.";
        }
        return Optional.of(new Guide(List.copyOf(rows), note));
    }

    /** The PDF contact table introduces each issuer row with its name and a contact column. */
    private static String contactRow(String table, String issuerName) {
        int start = phoneColumn(table, issuerName, 0);
        if (start < 0) return "";
        int end = table.length();
        for (Issuer issuer : ISSUERS) {
            int next = phoneColumn(table, issuer.name(), start + issuerName.length());
            if (next >= 0) end = Math.min(end, next);
        }
        return table.substring(start, end);
    }

    private static int phoneColumn(String text, String name, int from) {
        int index = text.indexOf(name, from);
        while (index >= 0) {
            int after = index + name.length();
            if (after < text.length() && Character.isDigit(text.charAt(after))) return index;
            index = text.indexOf(name, after);
        }
        return -1;
    }

    private static String documents(String details, String common) {
        String evidence = between(details, "필요서류", "수수료");
        boolean general = evidence.isEmpty();
        if (general) evidence = common;
        List<String> items = new ArrayList<>();
        if (evidence.contains("신청서")) items.add("정보변경 신청서");
        if (evidence.contains("사업자등록증")) items.add(evidence.contains("사업자등록증사본") ? "정정한 사업자등록증 사본" : "정정한 사업자등록증");
        if (evidence.contains("신분증")) items.add(evidence.contains("신분증사본") ? "대표자 신분증 사본" : "대표자 신분증");
        if (evidence.contains("결제계좌사본")) items.add("결제계좌 사본");
        if (evidence.contains("인감증명서")) items.add(evidence.contains("생략") ? "인감증명서(생략 조건 확인)" : "인감증명서");
        if (evidence.contains("허가(신고)증") || evidence.contains("영업허가증")) items.add("업종 허가·신고증(상호 변경 시 적용 여부 확인)");
        if (evidence.contains("매출전표")) items.add("최근 매출전표");
        if (items.isEmpty()) return "제출 서류는 카드사 공식 가맹점 창구에서 확인하세요.";
        String scope = general ? "문서의 공통 준비물" : "첨부의 카드사별 서류 안내";
        if (evidence.contains("개인단독대표")) scope += "(개인 단독대표 우편 접수)";
        return scope + "에 나온 " + String.join(", ", items) + ". 실제 제출 대상은 확인이 필요합니다.";
    }

    private static String between(String value, String start, String end) {
        int begin = value.indexOf(start);
        if (begin < 0) return "";
        int from = begin + start.length();
        int until = value.indexOf(end, from);
        return until < 0 ? "" : value.substring(from, until);
    }
}
