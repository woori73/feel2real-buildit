package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery.OfficialUpdateDiscoveryAdapter.DiscoveredCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewStartFundFaqDiscoveryAdapterTest {

  private static final String PAGE_URL =
      "https://www.newstartfund.or.kr/Faq/List.do?pageIndex=1&schPrcdRnk=001";
  private final NewStartFundFaqDiscoveryAdapter adapter = new NewStartFundFaqDiscoveryAdapter();

  @Test
  void parsesServerRenderedQuestionsWithAnswers() {
    List<DiscoveredCandidate> result =
        adapter.parseFaqPage(NewStartFundDiscoveryAdapterTest.fixture("newstartfund-faq.html"), PAGE_URL);

    assertThat(result).hasSize(2);
    DiscoveredCandidate first = result.get(0);
    assertThat(first.officialDocumentId()).isEqualTo("NSF-FAQ-30");
    assertThat(first.title()).isEqualTo("소상공인ㆍ자영업자 새출발기금은 어떤 제도인가요?");
    assertThat(first.summary()).contains("추심 중단").contains("채무조정 프로그램");
    assertThat(first.detailHtml()).contains("신규 대출을 지원하는 제도는 아닙니다");
    assertThat(first.originalUrl()).isEqualTo(PAGE_URL + "#30");
  }

  @Test
  void dedupKeyIsStableAcrossAnswerRevision() {
    List<DiscoveredCandidate> original =
        adapter.parseFaqPage(NewStartFundDiscoveryAdapterTest.fixture("newstartfund-faq.html"), PAGE_URL);
    DiscoveredCandidate revised = new DiscoveredCandidate(
        original.get(0).title(), "개정된 답변 요약", original.get(0).originalUrl(),
        original.get(0).officialDocumentId(), null, null, null, original.get(0).category(), "개정된 답변 본문");

    assertThat(adapter.buildDedupKey(revised))
        .isEqualTo(adapter.buildDedupKey(original.get(0)))
        .isEqualTo("DOC:NEWSTART_FUND:nsf-faq-30");
  }

  @Test
  void questionsPresentButNothingParsableFails() {
    String broken = NewStartFundDiscoveryAdapterTest.fixture("newstartfund-faq.html")
        .replaceAll("h3 id =\"(\\d+)\"", "h3 id=\"renamed-$1\"");

    assertThatThrownBy(() -> adapter.parseFaqPage(broken, PAGE_URL))
        .isInstanceOf(DiscoverySourceException.class)
        .extracting(e -> ((DiscoverySourceException) e).getReason())
        .isEqualTo(DiscoverySourceException.Reason.PARSE_EMPTY_WITH_ITEMS);
  }
}
