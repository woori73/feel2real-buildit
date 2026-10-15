package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AlbAccessLogParserTest {

  private final AlbAccessLogParser parser = new AlbAccessLogParser("feel2real.co.kr");

  @Test
  void givenSuccessfulHandleRootRequest_whenParsed_thenReturnsRequestFact() {
    // given
    String line = "https 2026-08-03T01:02:03.456789Z app/f2r/abc 1.2.3.4:1234 "
        + "10.0.0.1:8080 0.001 0.002 0.003 200 200 100 200 "
        + "\"GET https://math-one.feel2real.co.kr:443/?utm_source=test HTTP/1.1\" "
        + "\"Mozilla/5.0 (Macintosh; Intel Mac OS X)\" - - arn \"Root=trace\" "
        + "\"math-one.feel2real.co.kr\" \"arn:cert\" 1 2026-08-03T01:02:03Z \"forward\" "
        + "\"-\" \"-\" \"10.0.0.1:8080\" \"200\" \"-\" \"-\" TID_x \"-\" \"-\" \"-\"";

    // when
    var result = parser.parseCountableRequest(line);

    // then
    assertThat(result).contains(new AlbAccessLogParser.RequestFact(
        "math-one", Instant.parse("2026-08-03T01:02:03.456789Z")));
  }

  @Test
  void givenAssetOrFailedOrApexRequest_whenParsed_thenItIsExcluded() {
    // given / when / then
    assertThat(parser.parseCountableRequest(line("GET", "https://math-one.feel2real.co.kr/app.css", 200)))
        .isEmpty();
    assertThat(parser.parseCountableRequest(line("GET", "https://math-one.feel2real.co.kr/", 404)))
        .isEmpty();
    assertThat(parser.parseCountableRequest(line("POST", "https://math-one.feel2real.co.kr/", 200)))
        .isEmpty();
    assertThat(parser.parseCountableRequest(line("GET", "https://feel2real.co.kr/", 200)))
        .isEmpty();
  }

  @Test
  void givenMalformedLine_whenParsed_thenItIsIgnored() {
    // given / when / then
    assertThat(parser.parseCountableRequest("not an alb log line")).isEmpty();
  }

  private static String line(String method, String url, int status) {
    return "https 2026-08-03T01:02:03Z app/f2r/abc 1.2.3.4:1234 10.0.0.1:8080 "
        + "0 0 0 " + status + " " + status + " 0 0 \"" + method + " " + url
        + " HTTP/1.1\" \"agent with spaces\" - - arn \"trace\" \"domain\"";
  }
}
