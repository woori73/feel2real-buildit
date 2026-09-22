package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery.OfficialUpdateDiscoveryAdapter.DiscoveredCandidate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CcrsDiscoveryAdapterTest {

  private final CcrsDiscoveryAdapter adapter = new CcrsDiscoveryAdapter(new ObjectMapper());

  private static String jsonFixture(String name) {
    try (InputStream in = CcrsDiscoveryAdapterTest.class.getClassLoader().getResourceAsStream("newstart/discovery/" + name)) {
      if (in == null) {
        throw new IllegalStateException("fixture not found: " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Test
  void parsesRealApiPayloadAndSkipsSecretPosts() {
    List<DiscoveredCandidate> result = adapter.parseListJson(jsonFixture("ccrs-list.json"));

    assertThat(result).hasSize(2);
    DiscoveredCandidate first = result.get(0);
    assertThat(first.officialDocumentId()).isEqualTo("CCRS-BBS-487");
    assertThat(first.title()).contains("반복 예약 부도");
    assertThat(first.originalUrl()).contains("boardView.do").contains("BBS_SEQ=487");
    assertThat(first.publishedAt().getYear()).isEqualTo(2026);

    DiscoveredCandidate withFiles = result.stream()
        .filter(c -> c.officialDocumentId().equals("CCRS-BBS-485"))
        .findFirst().orElseThrow();
    assertThat(withFiles.summary()).contains("[첨부:").contains("청년인생설계학교");
  }

  @Test
  void missingListFieldFailsInsteadOfReportingSuccess() {
    assertThatThrownBy(() -> adapter.parseListJson("{\"data\":\"\"}"))
        .isInstanceOf(DiscoverySourceException.class)
        .extracting(e -> ((DiscoverySourceException) e).getReason())
        .isEqualTo(DiscoverySourceException.Reason.PARSE_EMPTY_WITH_ITEMS);
  }

  @Test
  void malformedJsonFails() {
    assertThatThrownBy(() -> adapter.parseListJson("<html>blocked by waf</html>"))
        .isInstanceOf(DiscoverySourceException.class);
  }
}
