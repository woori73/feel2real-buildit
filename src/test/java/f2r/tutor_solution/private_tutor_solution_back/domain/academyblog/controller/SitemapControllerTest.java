package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.PublicSiteProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.service.AcademyBlogService;
import java.io.StringReader;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

class SitemapControllerTest {
  @Test void givenMigratedAndLegacyHomes_whenProductionSitemapBuilt_thenNoRedirectOrCrossHostEntries() throws Exception {
    // given: only the existing published + visible repository query may feed the sitemap.
    var repository = mock(AcademyRepository.class);
    var blogs = mock(AcademyBlogService.class);
    var migrated = mock(Academy.class);
    when(migrated.getSlug()).thenReturn("이전-주소"); when(migrated.getHandle()).thenReturn("new-home");
    var legacy = mock(Academy.class);
    when(legacy.getSlug()).thenReturn("학원&소개");
    when(repository.findPublicList(AcademyStatus.PUBLISHED, null, null, null, null))
        .thenReturn(List.of(migrated, legacy));
    when(blogs.listPublishedForSitemap()).thenReturn(List.of());
    // when
    String xml = new SitemapController(repository, new PublicSiteProperties("feel2real.co.kr"), blogs).sitemap();
    var factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
    var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    var locations = document.getElementsByTagNameNS("http://www.sitemaps.org/schemas/sitemap/0.9", "loc");
    var urls = new java.util.ArrayList<String>();
    for (int i = 0; i < locations.getLength(); i++) urls.add(locations.item(i).getTextContent());
    // then: 9 fixed public pages + 2 blog lists + 1 non-redirecting legacy homepage.
    assertThat(urls).hasSize(12).doesNotHaveDuplicates();
    assertThat(urls).contains("https://feel2real.co.kr/academies/학원&소개",
        "https://feel2real.co.kr/academies/이전-주소/posts");
    assertThat(urls).doesNotContain("https://feel2real.co.kr/academies/이전-주소");
    assertThat(urls).allMatch(url -> url.startsWith("https://feel2real.co.kr/")
        && !url.contains("studio") && !url.contains("/admin") && !url.contains("/api/"));
    verify(repository).findPublicList(AcademyStatus.PUBLISHED, null, null, null, null);
    verifyNoMoreInteractions(repository);
    // Local routing has no subdomain migration; keep the path URL in that configuration.
    assertThat(new SitemapController(repository, new PublicSiteProperties(null), blogs).sitemap())
        .contains("<loc>https://feel2real.co.kr/academies/이전-주소</loc>");
  }
}
