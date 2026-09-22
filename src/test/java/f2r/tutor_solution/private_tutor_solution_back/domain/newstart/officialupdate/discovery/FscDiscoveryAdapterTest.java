package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery.OfficialUpdateDiscoveryAdapter.DiscoveredCandidate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FscDiscoveryAdapterTest {

  private final FscDiscoveryAdapter adapter = new FscDiscoveryAdapter();

  @Test
  void parsesRealNoticeIdRowsWithAbsoluteDetailUrl() {
    List<DiscoveredCandidate> result =
        adapter.parseListPage(NewStartFundDiscoveryAdapterTest.fixture("fsc-list.html"));

    assertThat(result).hasSize(2);
    DiscoveredCandidate first = result.get(0);
    assertThat(first.officialDocumentId()).isEqualTo("FSC-4175");
    assertThat(first.title()).startsWith("「자본시장과 금융투자업에 관한 법률 시행규칙」");
    assertThat(first.originalUrl())
        .startsWith("https://www.fsc.go.kr/po040301/view?noticeId=4175");
    assertThat(first.publishedAt()).isEqualTo(LocalDateTime.of(2026, 9, 21, 0, 0));
  }

  @Test
  void rowsWithoutNoticeIdLinksFailInsteadOfReportingSuccess() {
    String broken = NewStartFundDiscoveryAdapterTest.fixture("fsc-list.html")
        .replace("noticeId=4175", "movedKey=4175")
        .replace("noticeId=4174", "movedKey=4174");

    assertThatThrownBy(() -> adapter.parseListPage(broken))
        .isInstanceOf(DiscoverySourceException.class)
        .extracting(e -> ((DiscoverySourceException) e).getReason())
        .isEqualTo(DiscoverySourceException.Reason.PARSE_EMPTY_WITH_ITEMS);
  }
}
