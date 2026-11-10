package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CODEF 수시입출 거래내역 — 공식 문서의 필드명으로 만든 합성 응답만 사용한다(실제 금융자료 아님). */
class CodefBankTransactionAdapterTest {

  private static final String CONFIG =
      "{\"organization\":\"0004\",\"clientType\":\"B\",\"account\":\"123-456-789012\"}";
  private static final String OFFICIAL_SAMPLE = """
      {"result":{"code":"CF-00000","extraMessage":"","message":"성공","transactionId":"t1"},
       "data":{"resAccountBalance":"19615","commStartDate":"20261101","commEndDate":"20261110",
        "resTrHistoryList":[
         {"resAccountTrDate":"20261105","resAccountTrTime":"095900","resAccountOut":"0",
          "resAccountIn":"250000","resAccountDesc1":"김학부","resAccountDesc2":"타행이체",
          "resAccountDesc3":"11월수강료","resAccountDesc4":"","resAfterTranBalance":"1250000"},
         {"resAccountTrDate":"20261105","resAccountTrTime":"095900","resAccountOut":"0",
          "resAccountIn":"250000","resAccountDesc1":"김학부","resAccountDesc2":"타행이체",
          "resAccountDesc3":"11월수강료","resAccountDesc4":"","resAfterTranBalance":"1250000"},
         {"resAccountTrDate":"20261106","resAccountTrTime":"101010","resAccountOut":"1,200,000",
          "resAccountIn":"0","resAccountDesc1":"신한카드","resAccountDesc2":"자동이체",
          "resAccountDesc3":"카드대금","resAccountDesc4":"","resAfterTranBalance":"50000"},
         {"resAccountTrDate":"2026110X","resAccountTrTime":"000000","resAccountOut":"0",
          "resAccountIn":"1000","resAccountDesc1":"","resAccountDesc2":"","resAccountDesc3":"",
          "resAccountDesc4":"","resAfterTranBalance":"0"}]}}
      """;

  @Test
  @DisplayName("given 공식 필드의 응답 when 변환 then 입출금·거래처·적요·원본 키가 채워지고 해석 실패 행은 오류로 남는다")
  void mapsOfficialResponseFields() throws Exception {
    // given
    CodefBankTransactionAdapter adapter = new CodefBankTransactionAdapter(null,
        () -> new CodefCredentialProvider.Credentials("id", "secret"), 30);
    var data = new ObjectMapper().readTree(OFFICIAL_SAMPLE).path("data");

    // when
    List<CollectionParsedRow> rows = adapter.mapResponse(data,
        CodefBankTransactionAdapter.parseConfig(CONFIG));

    // then
    assertThat(rows).hasSize(4);
    CollectionParsedRow first = rows.get(0);
    assertThat(first.direction()).isEqualTo("INCOME");
    assertThat(first.amount()).isEqualTo(250_000L);
    assertThat(first.occurredOn()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(first.counterparty()).isEqualTo("김학부");
    assertThat(first.description()).isEqualTo("11월수강료");
    assertThat(first.rawJson()).contains("\"account_masked\":\"********9012\"")
        .doesNotContain("123456789012");
    assertThat(first.rawJson()).contains("현금영수증");
    // 같은 내용의 두 거래는 등장 순번으로 구분된다(합치지 않음)
    assertThat(rows.get(1).externalId()).isNotEqualTo(first.externalId());
    assertThat(rows.get(1).externalId()).endsWith("#2");
    CollectionParsedRow payment = rows.get(2);
    assertThat(payment.direction()).isEqualTo("EXPENSE");
    assertThat(payment.amount()).isEqualTo(1_200_000L);
    assertThat(payment.rawJson()).contains("\"primary_disposition\":\"CARD_PAYMENT\"");
    CollectionParsedRow broken = rows.get(3);
    assertThat(broken.isValid()).isFalse();
    assertThat(broken.occurredOn()).isNull();
    assertThat(broken.amount()).isNull();
  }

  @Test
  @DisplayName("given 단건 응답(객체) when 변환 then 1건으로 읽는다")
  void singleObjectHistoryIsAccepted() throws Exception {
    // given
    String single = "{\"resTrHistoryList\":{\"resAccountTrDate\":\"20261101\",\"resAccountTrTime\":\"080000\","
        + "\"resAccountOut\":\"5000\",\"resAccountIn\":\"0\",\"resAccountDesc1\":\"편의점\","
        + "\"resAccountDesc2\":\"체크\",\"resAccountDesc3\":\"간식\",\"resAfterTranBalance\":\"95000\"}}";
    CodefBankTransactionAdapter adapter = new CodefBankTransactionAdapter(null,
        () -> new CodefCredentialProvider.Credentials("id", "secret"), 30);

    // when
    var rows = adapter.mapResponse(new ObjectMapper().readTree(single),
        CodefBankTransactionAdapter.parseConfig(CONFIG));

    // then
    assertThat(rows).singleElement().satisfies(row -> {
      assertThat(row.direction()).isEqualTo("EXPENSE");
      assertThat(row.amount()).isEqualTo(5_000L);
    });
  }

  @Test
  @DisplayName("given 잘못된 설정 when 검증 then 기관코드·고객구분·계좌번호 오류를 알린다")
  void rejectsInvalidConfig() {
    assertThatThrownBy(() -> CodefBankTransactionAdapter.parseConfig(
        "{\"organization\":\"4\",\"clientType\":\"B\",\"account\":\"1234567\"}"))
        .isInstanceOf(EasyCollectionException.class);
    assertThatThrownBy(() -> CodefBankTransactionAdapter.parseConfig(
        "{\"organization\":\"0004\",\"clientType\":\"X\",\"account\":\"1234567\"}"))
        .isInstanceOf(EasyCollectionException.class);
    assertThatThrownBy(() -> CodefBankTransactionAdapter.parseConfig(
        "{\"organization\":\"0004\",\"clientType\":\"B\",\"account\":\"12\"}"))
        .isInstanceOf(EasyCollectionException.class);
    assertThat(CodefBankTransactionAdapter.parseConfig(CONFIG).toString())
        .doesNotContain("123456789012");
  }

  @Test
  @DisplayName("given 키 미설정 when 연결 확인 then 설정 오류로 막는다(가짜 성공 금지)")
  void missingCredentialsBlockCollection() {
    // given
    CodefBankTransactionAdapter adapter = new CodefBankTransactionAdapter(null,
        () -> new CodefCredentialProvider.Credentials(null, null), 30);

    // when / then
    assertThatThrownBy(() -> adapter.validateConnection("cid"))
        .isInstanceOf(EasyCollectionException.class)
        .extracting(e -> ((EasyCollectionException) e).errorCode())
        .isEqualTo(EasyCollectionErrorCode.CONFIGURATION_ERROR);
    assertThatThrownBy(() -> adapter.validateConnection(" "))
        .isInstanceOf(EasyCollectionException.class);
  }

  @Test
  @DisplayName("given CODEF HTTP 응답 when 호출 then 토큰 발급 → URL 인코딩 본문 전송 → 디코딩 결과를 행으로 바꾼다")
  void clientProtocolUsesTokenAndUrlEncodedBodies() throws Exception {
    // given
    HttpClient http = mock(HttpClient.class);
    List<HttpRequest> requests = new ArrayList<>();
    List<String> bodies = new ArrayList<>();
    when(http.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
      HttpRequest request = inv.getArgument(0);
      requests.add(request);
      bodies.add(bodyOf(request));
      if (request.uri().toString().endsWith("/oauth/token")) {
        return response(200, "{\"access_token\":\"tok\",\"expires_in\":604799}");
      }
      return response(200, URLEncoder.encode(OFFICIAL_SAMPLE, StandardCharsets.UTF_8));
    });
    CodefApiClient client = new CodefApiClient("https://development.codef.io",
        CodefApiClient.TOKEN_URL, http, new ObjectMapper(), Duration.ofSeconds(5));
    CodefBankTransactionAdapter adapter = new CodefBankTransactionAdapter(client,
        () -> new CodefCredentialProvider.Credentials("id", "secret"), 30);

    // when
    var rows = adapter.fetchPeriod("cid-123", CONFIG, LocalDate.of(2026, 11, 1),
        LocalDate.of(2026, 11, 10), 5000);

    // then
    assertThat(rows).hasSize(4);
    assertThat(requests.get(0).uri().toString()).isEqualTo("https://oauth.codef.io/oauth/token");
    assertThat(requests.get(0).headers().firstValue("Authorization").orElse(""))
        .startsWith("Basic ");
    assertThat(requests.get(1).uri().toString())
        .isEqualTo("https://development.codef.io/v1/kr/bank/b/account/transaction-list");
    assertThat(requests.get(1).headers().firstValue("Authorization").orElse(""))
        .isEqualTo("Bearer tok");
    String sent = URLDecoder.decode(bodies.get(1), StandardCharsets.UTF_8);
    assertThat(sent).contains("\"organization\":\"0004\"", "\"connectedId\":\"cid-123\"",
        "\"account\":\"123456789012\"", "\"startDate\":\"20261101\"", "\"endDate\":\"20261110\"",
        "\"orderBy\":\"1\"");
  }

  @Test
  @DisplayName("given 추가인증·인증서 만료·토큰·기간 오류 코드 when 분류 then 재인증·설정·데이터 오류로 나눈다")
  void classifiesCodefResultCodes() {
    assertThat(CodefApiClient.classify("CF-03002").errorCode())
        .isEqualTo(EasyCollectionErrorCode.AUTH_REQUIRED);
    assertThat(CodefApiClient.classify("CF-12806").errorCode())
        .isEqualTo(EasyCollectionErrorCode.AUTH_REQUIRED);
    assertThat(CodefApiClient.classify("CF-04019").errorCode())
        .isEqualTo(EasyCollectionErrorCode.AUTH_REQUIRED);
    assertThat(CodefApiClient.classify("CF-00013").errorCode())
        .isEqualTo(EasyCollectionErrorCode.CONFIGURATION_ERROR);
    assertThat(CodefApiClient.classify("CF-13001").errorCode())
        .isEqualTo(EasyCollectionErrorCode.INVALID_DATA);
    assertThat(CodefApiClient.classify("CF-99999").errorCode())
        .isEqualTo(EasyCollectionErrorCode.PROVIDER_TEMPORARY);
  }

  @Test
  @DisplayName("given 401 응답 when 호출 then 토큰을 다시 받아 한 번만 재시도한다")
  void refreshesTokenOnceOn401() throws Exception {
    // given
    HttpClient http = mock(HttpClient.class);
    List<String> uris = new ArrayList<>();
    int[] dataCalls = {0};
    when(http.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
      HttpRequest request = inv.getArgument(0);
      uris.add(request.uri().toString());
      if (request.uri().toString().endsWith("/oauth/token")) {
        return response(200, "{\"access_token\":\"tok" + uris.size() + "\",\"expires_in\":604799}");
      }
      dataCalls[0]++;
      return dataCalls[0] == 1 ? response(401, "{\"error\":\"invalid_token\"}")
          : response(200, URLEncoder.encode(OFFICIAL_SAMPLE, StandardCharsets.UTF_8));
    });
    CodefApiClient client = new CodefApiClient(null, null, http, new ObjectMapper(),
        Duration.ofSeconds(5));

    // when
    var data = client.call(new CodefCredentialProvider.Credentials("id", "s"),
        CodefBankTransactionAdapter.BUSINESS_ENDPOINT, java.util.Map.of("a", "b"));

    // then
    assertThat(data.path("resTrHistoryList").size()).isEqualTo(4);
    assertThat(uris.stream().filter(u -> u.endsWith("/oauth/token")).count()).isEqualTo(2);
    assertThat(dataCalls[0]).isEqualTo(2);
  }

  @SuppressWarnings("unchecked")
  private static HttpResponse<String> response(int status, String body) {
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
    return response;
  }

  private static String bodyOf(HttpRequest request) {
    if (request.bodyPublisher().isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    request.bodyPublisher().get().subscribe(new Flow.Subscriber<>() {
      @Override public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
      @Override public void onNext(java.nio.ByteBuffer item) {
        byte[] bytes = new byte[item.remaining()];
        item.get(bytes);
        sb.append(new String(bytes, StandardCharsets.UTF_8));
      }
      @Override public void onError(Throwable t) {}
      @Override public void onComplete() {}
    });
    return sb.toString();
  }
}
