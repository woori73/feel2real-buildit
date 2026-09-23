package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery.OfficialUpdateDiscoveryAdapter.DiscoveredCandidate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.Category;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DiscoveryUrlResolutionTest {

  private static final String BASE = "https://www.newstartfund.or.kr/Board/View.do?idx=56";

  @Test
  void absoluteHrefIsPreservedAndNeverPrefixedWithBase() {
    assertThat(AbstractOfficialUpdateDiscoveryAdapter.resolveUrl(
        BASE, "https://www.fss.or.kr/notice/1")).isEqualTo("https://www.fss.or.kr/notice/1");
    assertThat(AbstractOfficialUpdateDiscoveryAdapter.resolveUrl(
        BASE, "HTTPS://www.fss.or.kr/notice/1")).isEqualTo("HTTPS://www.fss.or.kr/notice/1");
  }

  @Test
  void relativeAndProtocolRelativeHrefResolveAgainstBase() {
    assertThat(AbstractOfficialUpdateDiscoveryAdapter.resolveUrl(BASE, "/AttachFile/a.pdf"))
        .isEqualTo("https://www.newstartfund.or.kr/AttachFile/a.pdf");
    assertThat(AbstractOfficialUpdateDiscoveryAdapter.resolveUrl(BASE, "//cdn.newstartfund.or.kr/x.js"))
        .isEqualTo("https://cdn.newstartfund.or.kr/x.js");
  }

  @Test
  void dedupKeyRepairsDoubleDomainUrlAndKeepsQueryAndFragment() {
    NewStartFundDiscoveryAdapter adapter = new NewStartFundDiscoveryAdapter();
    DiscoveredCandidate polluted = new DiscoveredCandidate(
        "제목", "요약",
        "https://www.newstartfund.or.kr/https://www.newstartfund.or.kr/Faq/List.do?pageIndex=1&schPrcdRnk=005#43",
        null, null, null, null, Category.NOTICE, null);

    assertThat(adapter.buildDedupKey(polluted))
        .isEqualTo("URL:https://www.newstartfund.or.kr/Faq/List.do?pageIndex=1&schPrcdRnk=005#43");
  }

  @Test
  void faqDoDiscoverNeverProducesDoubleDomainUrls() {
    NewStartFundFaqDiscoveryAdapter adapter = new NewStartFundFaqDiscoveryAdapter();
    DiscoveryHttpClient client = mock(DiscoveryHttpClient.class);
    when(client.fetchHtml(anyString()))
        .thenReturn(NewStartFundDiscoveryAdapterTest.fixture("newstartfund-faq.html"));
    adapter.httpClient = client;

    List<DiscoveredCandidate> result = adapter.discover(null);

    org.mockito.ArgumentCaptor<String> fetched = org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(client, org.mockito.Mockito.atLeastOnce()).fetchHtml(fetched.capture());
    for (String url : fetched.getAllValues()) {
      assertThat(countSchemeOccurrences(url)).isEqualTo(1);
    }
    assertThat(result).isNotEmpty();
    for (DiscoveredCandidate candidate : result) {
      assertThat(countSchemeOccurrences(candidate.originalUrl())).isEqualTo(1);
    }
    assertThat(result.get(0).originalUrl())
        .isEqualTo("https://www.newstartfund.or.kr/Faq/List.do?pageIndex=1&schPrcdRnk=001#30");
  }

  private static int countSchemeOccurrences(String url) {
    Matcher m = Pattern.compile("(?i)https?://").matcher(url);
    int count = 0;
    while (m.find()) {
      count++;
    }
    return count;
  }
}
