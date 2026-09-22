package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery.OfficialUpdateDiscoveryAdapter.DiscoveredCandidate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewStartFundDiscoveryAdapterTest {

  private final NewStartFundDiscoveryAdapter adapter = new NewStartFundDiscoveryAdapter();

  static String fixture(String name) {
    try (InputStream in = NewStartFundDiscoveryAdapterTest.class.getClassLoader()
        .getResourceAsStream("newstart/discovery/" + name)) {
      if (in == null) {
        throw new IllegalStateException("fixture not found: " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Test
  void parsesRealBoardListRowsWithDocumentIdAndDate() {
    List<DiscoveredCandidate> result =
        adapter.parseListPage(fixture("newstartfund-board-list.html")).candidates();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).officialDocumentId()).isEqualTo("NSF-B-56");
    assertThat(result.get(0).title()).contains("개편사항 안내");
    assertThat(result.get(0).publishedAt()).isEqualTo(LocalDateTime.of(2026, 6, 25, 0, 0));
    assertThat(result.get(0).originalUrl()).isEqualTo("https://www.newstartfund.or.kr/Board/View.do?idx=56");
  }

  @Test
  void enrichFromViewExtractsAttachmentNamesAndAbsoluteUrls() {
    DiscoveredCandidate base = adapter.parseListPage(fixture("newstartfund-board-list.html")).candidates().get(1);

    DiscoveredCandidate enriched = adapter.enrichFromView(fixture("newstartfund-board-view.html"), base);

    assertThat(enriched.summary()).contains("[첨부:");
    assertThat(enriched.summary()).contains("붙임1. 특수감면율 소급안.pdf");
    assertThat(enriched.summary())
        .contains("https://www.newstartfund.or.kr/AttachFile/btbd/101/2025/2025111700001_101_2025_01.pdf");
    assertThat(enriched.detailHtml()).contains("특수감면율");
  }

  @Test
  void revisedDocumentKeepsSameDedupKeySoNoDuplicateRegistration() {
    DiscoveredCandidate before = adapter.parseListPage(fixture("newstartfund-board-list.html")).candidates().get(0);
    DiscoveredCandidate revised = new DiscoveredCandidate(
        before.title(), "내용이 대폭 수정된 개정본", before.originalUrl(), before.officialDocumentId(),
        before.publishedAt(), null, null, before.category(), "개정된 본문");

    assertThat(adapter.buildDedupKey(revised)).isEqualTo(adapter.buildDedupKey(before));
  }

  @Test
  void rowsPresentButNothingParsableFailsInsteadOfReportingSuccess() {
    String broken = fixture("newstartfund-board-list.html").replace("fn_view(", "fn_movedTo(");

    assertThatThrownBy(() -> adapter.parseListPage(broken))
        .isInstanceOf(DiscoverySourceException.class)
        .extracting(e -> ((DiscoverySourceException) e).getReason())
        .isEqualTo(DiscoverySourceException.Reason.PARSE_EMPTY_WITH_ITEMS);
  }

  @Test
  void totalCountShownButNoRowsFailsInsteadOfReportingSuccess() {
    String noRows = fixture("newstartfund-board-list.html")
        .replaceAll("(?s)<tbody>.*?</tbody>", "<tbody></tbody>");

    assertThatThrownBy(() -> adapter.parseListPage(noRows))
        .isInstanceOf(DiscoverySourceException.class)
        .hasMessageContaining("49");
  }
}
