package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.PublicSiteProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.dto.AcademyBlogPostResponse;
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
    // 공개 글은 '이전-주소' 학원에만 있다 — '학원&소개'는 글이 없어 글 목록 페이지가 빈 화면(Soft 404)이다.
    var post = mock(AcademyBlogPostResponse.class);
    when(post.academySlug()).thenReturn("이전-주소");
    when(post.publicUrl()).thenReturn("/academies/이전-주소/posts/첫-글");
    when(post.updatedAt()).thenReturn(java.time.LocalDateTime.of(2026, 11, 9, 10, 0));
    when(blogs.listPublishedForSitemap()).thenReturn(List.of(post));
    // when
    String xml = new SitemapController(repository, new PublicSiteProperties("feel2real.co.kr"), blogs).sitemap();
    var factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
    var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    var locations = document.getElementsByTagNameNS("http://www.sitemaps.org/schemas/sitemap/0.9", "loc");
    var urls = new java.util.ArrayList<String>();
    for (int i = 0; i < locations.getLength(); i++) urls.add(locations.item(i).getTextContent());
    // then: 8 fixed public pages (/home 제외 — / 로 301) + 1 non-redirecting legacy homepage
    //       + 글이 있는 학원의 글 목록 1개 + 공개 글 1개. 글이 없는 학원의 글 목록은 넣지 않는다.
    assertThat(urls).hasSize(11).doesNotHaveDuplicates();
    assertThat(urls).doesNotContain("https://feel2real.co.kr/academies/학원&소개/posts");
    assertThat(urls).contains("https://feel2real.co.kr/academies/이전-주소/posts/첫-글");
    assertThat(urls).doesNotContain("https://feel2real.co.kr/home");
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
