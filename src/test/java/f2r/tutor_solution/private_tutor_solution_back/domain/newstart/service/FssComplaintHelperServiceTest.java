package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.FssComplaintHelperService.ComposeResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.FssComplaintHelperService.ComplaintSample;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiAiRouterClient;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

/**
 * 금감원 민원 도우미 service 계약 — 전자책 3종 resolve, 예시별 prompt 격리(C/D),
 * 사실 창작용 금지 계약 문구(E–H), 입력 가드(I/J), 프라이버시(L/M).
 */
@DisplayName("FSS complaint helper service — ebook sources, prompt isolation, privacy")
class FssComplaintHelperServiceTest {

    private WikiAiRouterClient router;
    private FssComplaintHelperService service;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger rootLogger;

    @BeforeEach
    void setUp() {
        router = mock(WikiAiRouterClient.class);
        service = new FssComplaintHelperService(router);
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    private void stubOk() {
        when(router.complete(any(), any(), anyInt()))
            .thenReturn(new WikiAiRouterClient.Completion(true, "정리된 민원문", null, "offline", "test-model"));
    }

    @Test
    @DisplayName("A/B: 전자책 EBK-6-3 실제 예시 정확히 3종 resolve + 원문 verbatim 마커")
    void resolvesExactlyThreeEbookSamples() {
        assertThat(service.samples()).hasSize(3);
        assertThat(service.samples()).extracting(ComplaintSample::id)
            .containsExactly("FS-1", "FS-2", "FS-3");
        assertThat(service.samples()).allSatisfy(s -> {
            assertThat(s.ebookRef()).startsWith("EBK-6-3");
            assertThat(s.sourceLines()).matches("L\\d+-\\d+");
            assertThat(s.title()).isNotBlank();
            assertThat(s.situation()).isNotBlank();
        });
        // 원문 verbatim 확인: 샘플 고유의 실제 문구 (직접 타이핑한 재현문이면 실패하도록 원문에서 채취)
        assertThat(sample("FS-1").body())
            .contains("새출발기금 부실차주 신청을 고려하고 있으며")
            .contains("00 카드사에서 지속적으로 채권추심 및 법적 절차를 진행하고 있습니다");
        assertThat(sample("FS-2").body())
            .contains("상계처리된 금액: ------- 원 (가맹점 대금)")
            .contains("부실차주 지원 취지에 맞지 않으며");
        assertThat(sample("FS-3").body())
            .contains("저는 [본인 이름]입니다")
            .contains("채권번호 [ ****** ]에 대한 채권 추심");
        // O: 매뉴얼 단계 resolve (전자책 EBK-6-1/6-2 실전 루프 + 카페 2961 사이트 단계)
        assertThat(service.manual().steps()).hasSize(5);
        assertThat(service.manual().intro()).contains("추심 문자");
        assertThat(service.manual().siteGuide().steps()).hasSize(14);
        assertThat(service.manual().siteGuide().steps().get(0).text()).contains("민원신청");
        assertThat(service.manual().siteGuide().steps().get(13).text())
            .contains("추심원 연락처").contains("채권사");
        assertThat(service.manual().siteGuide().tips()).isNotEmpty();
        assertThat(service.manual().siteGuide().source()).contains("2961");
    }

    @Test
    @DisplayName("C/D: 선택한 예시만 prompt에 실리고 다른 예시 문구는 섞이지 않음")
    void embedsSelectedSampleOnly() {
        stubOk();
        service.compose("FS-2", "장기연체 직후 카드가 가맹점 대금을 상계했어요.");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(router).complete(any(), prompt.capture(), anyInt());
        String sent = prompt.getValue();
        assertThat(sent).contains("상계처리된 금액").contains("00카드와의 중재를 간곡히 요청합니다");
        assertThat(sent).doesNotContain("죽을 것 같은 절박함").doesNotContain("법적 절차를 진행하고 있습니다");
    }

    @Test
    @DisplayName("E–H: system prompt에 §5 계약 — 날짜·금액·통신·규정·결과 창작용 금지 + 확인 필요 마커 + 단정 금지")
    void systemPromptCarriesNoFabricationContract() {
        stubOk();
        service.compose("FS-1", "문자가 계속 옵니다.");
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(router).complete(system.capture(), any(), anyInt());
        String s = system.getValue();
        assertThat(s)
            .contains("Never invent facts")
            .contains("dates, amounts")
            .contains("official rules, legal conclusions")
            .contains("[날짜 확인 필요]")
            .contains("CLASSIFY FIRST, INFORM SECOND, JUDGE ONLY WHEN ASKED");
        // 예시 사실의 사용자 쪽 이식을 막는 지시는 user prompt에도 존재
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(router).complete(any(), user.capture(), anyInt());
        assertThat(user.getValue())
            .contains("사용자에게 옮겨 쓰지 말 것")
            .contains("민원문에 사용할 사실은 오직 이 내용뿐");
    }

    @Test
    @DisplayName("I: 빈 입력·모르는 예시 ID는 router 호출 없이 가드")
    void guardsBlankAndUnknownSampleWithoutRouterCall() {
        assertThat(service.compose("FS-1", "   ").ok()).isFalse();
        assertThat(service.compose(null, "내용").errorMessage()).contains("예시를 찾을 수 없습니다");
        verify(router, never()).complete(any(), any(), anyInt());
    }

    @Test
    @DisplayName("J: 매우 짧은 입력도 정상 통과(가드 사유로 차단하지 않음)")
    void shortInputReachesRouter() {
        stubOk();
        ComposeResult result = service.compose("FS-3", "독촉 문자와요");
        assertThat(result.ok()).isTrue();
        assertThat(result.composedText()).isEqualTo("정리된 민원문");
    }

    @Test
    @DisplayName("길이 상한 초과 시 내용 미반환 안내 + router 미호출")
    void tooLongSituationRejected() {
        ComposeResult result = service.compose("FS-1", "가".repeat(4001));
        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).contains("4000");
        verify(router, never()).complete(any(), any(), anyInt());
    }

    @Test
    @DisplayName("M: AI 실패 응답에 사용자 원문·내부 상세가 없다")
    void failureMessageDoesNotEchoUserData() {
        when(router.complete(any(), any(), anyInt()))
            .thenReturn(WikiAiRouterClient.Completion.failed("AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요."));
        ComposeResult result = service.compose("FS-2", "개인금융정보비밀번호12345 상계 당했어요");
        assertThat(result.ok()).isFalse();
        assertThat(result.composedText()).isEmpty();
        assertThat(result.errorMessage())
            .doesNotContain("개인금융정보비밀번호12345")
            .doesNotContain("상계");
    }

    @Test
    @DisplayName("L: 성공·실패 경로 모두 사용자 원문이 application log에 남지 않는다")
    void situationTextNeverLogged() {
        String secret = "민원원문비밀마커-XKV7Q";
        stubOk();
        service.compose("FS-1", secret + " 추심 문자가 계속 옵니다");
        when(router.complete(any(), any(), anyInt()))
            .thenReturn(WikiAiRouterClient.Completion.failed("AI 서비스 연결에 실패했습니다."));
        service.compose("FS-1", secret + " 두 번째 시도");
        String allLogs = logAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(java.util.stream.Collectors.joining("\n"));
        assertThat(allLogs).doesNotContain(secret);
    }

    @Test
    @DisplayName("K/P/Q/R: 템플릿 — textContent 전용(K), 공식 fss.or.kr 링크+noopener(P), 자동 제출 없음(Q), AI 전송 고지(R)")
    void templateRendersOnlyViaTextContent() throws Exception {
        String html = new String(
            getClass().getResourceAsStream("/templates/newstart/fss-helper.html").readAllBytes(),
            StandardCharsets.UTF_8);
        // K: XSS — 동적 데이터는 textContent로만
        assertThat(html).doesNotContain(".innerHTML");
        assertThat(html).contains("textContent");
        // P: 검증된 공식 도메인 진입 링크 + 새 탭(noopener)
        assertThat(html).contains("https://www.fss.or.kr/fss/minwon/search.jsp");
        assertThat(html).contains("target=\"_blank\" rel=\"noopener noreferrer\"");
        // Q: 자동 제출/자동화 부재 — form 없음, script에서 submit/클릭 자동화 없음
        assertThat(html).doesNotContain("<form");
        assertThat(html).doesNotContain(".submit(");
        assertThat(html).doesNotContain("DOMContentLoaded");
        // R: 프라이버시 고지 — AI 처리 시스템 전송을 숨기지 않고 밝히고, DB 미저장만 강조한다.
        //    "서버로 전송되지 않는다"는 식의 거짓 주장(전송 은폐)을 금지한다.
        assertThat(html).contains("AI 처리 시스템으로 전송");
        assertThat(html).contains("NewStart DB에는 저장하지 않습니다");
        assertThat(html).doesNotContain("서버로 전송되지");
        assertThat(html).doesNotContain("브라우저를 떠나지");
    }

    @Test
    @DisplayName("Extension 브리지 — 배지 id·ping 계약 유지, 공식 Store URL 상수 1개, pending 문구 0")
    void extensionBridgeContract() throws Exception {
        String html = new String(
            getClass().getResourceAsStream("/templates/newstart/fss-helper.html").readAllBytes(),
            StandardCharsets.UTF_8);
        // newstartContent.js가 갱신하는 배지 요소와 페이지측 ping 브리지 계약
        assertThat(html).contains("id=\"ns-chrome-badge\"");
        assertThat(html).contains("newstart-page-ping");
        assertThat(html).contains("newstart-agent");
        // Store 심사 통과(Published) — 공식 URL이 유일한 상수, 구형/가짜 Store 링크 금지
        assertThat(html).contains(
            "var CHROME_WEB_STORE_URL = \"https://chromewebstore.google.com/detail/aickncaeacnplhgpipmnffillpmocfhf\";");
        assertThat(html).doesNotContain("chrome.google.com/webstore");
        // pending UX 0: 검토 중/출시 준비/비활성 설치 버튼 금지
        assertThat(html).doesNotContain("Chrome Web Store 검토 중");
        assertThat(html).doesNotContain("출시 준비 중");
        assertThat(html).doesNotContain("id=\"ns-ext-install\" disabled");
        // 설치 CTA 활성 — 버튼 교체 배선이 상수 href로 공식 anchor 생성
        assertThat(html).contains(">Chrome Agent 설치</button>");
        assertThat(html).contains("a.href = CHROME_WEB_STORE_URL;");
    }

    @Test
    @DisplayName("§7 officialGuide: 금감원 공식 페이지 3종 출처와 소관 분기 데이터가 리소스에서 로딩된다")
    void officialGuideLoadsFromResources() {
        var official = service.officialGuide();
        assertThat(official).isNotNull();
        assertThat(official.authority()).isEqualTo("OFFICIAL");
        assertThat(official.retrievedAt()).isEqualTo("2026-09-25");
        assertThat(official.sources()).hasSize(3)
            .allSatisfy(s -> assertThat(s.url()).startsWith("https://www.fss.or.kr/"));
        assertThat(official.byCategory()).containsKeys("COLLECTION_CONTACT", "CREDITOR_RESPONSE",
            "REPAYMENT_DEBT", "NO_EXPLANATION", "UNFAIR_TREATMENT", "OTHER");
        assertThat(official.otherAgencies()).isNotEmpty()
            .anySatisfy(a -> assertThat(a.matter()).contains("신용회복"));
        // 부실차주 핵심 오답 방지: 채무조정은 신용회복위원회 소관임을 공식 데이터가 포함한다.
        assertThat(official.byCategory().get("REPAYMENT_DEBT")).contains("신용회복위원회");
    }

    @Test
    @DisplayName("§13/§14 interview 프롬프트: FACT/CLAIM 분리 마커 + 타임라인 + 위법 단정 금지 + 제목/본문 형식")
    void interviewPromptCarriesSeparationAndFormatContract() {
        when(router.complete(any(), any(), anyInt()))
            .thenReturn(new WikiAiRouterClient.Completion(true,
                "제목: 추심 연락 절차 확인 요청\n본문:\n사건 개요\n2026-09-01 전화 수신", null, "offline", "test-model"));
        ComposeResult result = service.composeInterview(newInterview());
        assertThat(result.ok()).isTrue();
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(router).complete(system.capture(), user.capture(), anyInt());
        assertThat(system.getValue())
            .contains("Never invent facts")
            .contains("FACT / CLAIM / CONCERN / REQUEST")
            .contains("확정 금지")
            .contains("확인을 요청");
        String sent = user.getValue();
        assertThat(sent)
            .contains("[FACT — 사용자 본인의 진술")
            .contains("[타임라인(FACT)]")
            .contains("2026-09-01 | OO채권관리 | 전화 수신 | 증거: 통화기록")
            .contains("[CONCERN/REQUEST")
            .contains("매일 전화가 와요");
    }

    @Test
    @DisplayName("§19 제목/본문 분리 파싱 — '제목:'/'본문:' 경계 추출과 실패 시 원문 전체 fallback")
    void parsesSubjectAndBodyWithFallback() {
        var parsed = FssComplaintHelperService.parseDraft(
            "제목: 채권 내역 확인 요청\n\n본문:\n사건 개요\nA\n시간순 사실관계\nB");
        assertThat(parsed.subject()).isEqualTo("채권 내역 확인 요청");
        assertThat(parsed.body()).startsWith("사건 개요").contains("B");

        var noTitle = FssComplaintHelperService.parseDraft("제목 없음의 평문 답변 전체");
        assertThat(noTitle.subject()).isEmpty();
        assertThat(noTitle.body()).isEqualTo("제목 없음의 평문 답변 전체");

        var titleOnly = FssComplaintHelperService.parseDraft("제목: OO 요청\n본문 없는 continued text");
        assertThat(titleOnly.subject()).isEqualTo("OO 요청");
        assertThat(titleOnly.body()).isEqualTo("본문 없는 continued text");
    }

    @Test
    @DisplayName("interview 가드: 빈 facts는 router 호출 없이 거부, 상한 초과 항목도 거부")
    void interviewGuardsSkipRouter() {
        assertThat(service.composeInterview(null).ok()).isFalse();
        var blank = new FssComplaintHelperService.Interview("OTHER", "   ", "OO", null,
            null, null, null, "원함");
        assertThat(service.composeInterview(blank).ok()).isFalse();
        var longFacts = new FssComplaintHelperService.Interview("OTHER", "가".repeat(1001), "OO", null,
            null, null, null, "원함");
        var r = service.composeInterview(longFacts);
        assertThat(r.ok()).isFalse();
        assertThat(r.errorMessage()).contains("1000");
        verify(router, never()).complete(any(), any(), anyInt());
    }

    @Test
    @DisplayName("interview 입력 원문도 application log에 남지 않는다")
    void interviewInputNeverLogged() {
        String secret = "인터뷰비밀마커-QM3Z";
        stubOk();
        var iv = new FssComplaintHelperService.Interview("COLLECTION_CONTACT",
            secret + " 매일 전화가 옵니다", "OO채권관리",
            java.util.List.of(new FssComplaintHelperService.EventRow("2026-09-01", "OO",
                secret + " 전화 수신", "통화기록")),
            "연락 내용", "없음", java.util.List.of("통화기록"), "연락 중단");
        service.composeInterview(iv);
        String allLogs = logAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(java.util.stream.Collectors.joining("\n"));
        assertThat(allLogs).doesNotContain(secret);
    }

    private FssComplaintHelperService.Interview newInterview() {
        return new FssComplaintHelperService.Interview("COLLECTION_CONTACT",
            "매일 전화가 와요", "OO채권관리",
            java.util.List.of(new FssComplaintHelperService.EventRow(
                "2026-09-01", "OO채권관리", "전화 수신", "통화기록")),
            "법적 조치한다고 했음", "아직 없음",
            java.util.List.of("통화기록"), "연락 빈도 제한");
    }

    private ComplaintSample sample(String id) {
        return service.samples().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }
}
