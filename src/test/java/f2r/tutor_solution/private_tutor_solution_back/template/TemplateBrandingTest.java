package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TemplateBrandingTest {

    private static final Path TEMPLATE_ROOT = Path.of("src/main/resources/templates");
    private static final String F2R_LOGO_PATH = "/img/F2R_logo_pure.svg";

    @Test
    void standaloneLoginPageIsRemovedAndSharedModalHasSignupLink() throws IOException {
        // given
        Path loginTemplate = TEMPLATE_ROOT.resolve("auth/login.html");
        String accountMenuFragment = Files.readString(TEMPLATE_ROOT.resolve("fragments/_account_menu.html"));

        // when & then
        assertThat(loginTemplate).doesNotExist();
        assertThat(accountMenuFragment).contains("data-login-modal");
        assertThat(accountMenuFragment).contains("data-auth-signup");
        assertThat(accountMenuFragment).doesNotContain("role=tutor");
        assertThat(accountMenuFragment).doesNotContain("/tutor/register");
    }

    @Test
    void internalHeadersUseF2rLogoImageInsteadOfServiceText() throws IOException {
        // given
        List<String> templates = List.of(
                "student/stdt_dashboard.html",
                "tutor/tutor_dashboard.html",
                "admin/students.html",
                "admin/dashboard.html"
        );

        // when
        List<String> htmlFiles = templates.stream()
                .map(template -> TEMPLATE_ROOT.resolve(template))
                .map(TemplateBrandingTest::readString)
                .toList();
        String adminTopnavFragment = Files.readString(TEMPLATE_ROOT.resolve("fragments/_admin_topnav.html"));

        // then
        assertThat(htmlFiles).allSatisfy(html -> {
            boolean usesAdminTopnav = html.contains("fragments/_admin_topnav :: topnav");
            assertThat(html.contains(F2R_LOGO_PATH) || usesAdminTopnav).isTrue();
            assertThat(html).doesNotContain("header-title\">과외 솔루션");
            assertThat(html).doesNotContain("<h1>과외 솔루션");
        });
        assertThat(adminTopnavFragment).contains(F2R_LOGO_PATH);
    }


    @Test
    void authenticatedShellsExposeProfileAndLogoutActions() throws IOException {
        // given
        List<String> templates = List.of(
                "fragments/_admin_topnav.html",
                "fragments/_sidebar.html",
                "student/stdt_dashboard.html",
                "tutor/tutor_dashboard.html",
                "tutor/tutor_academy_posts.html",
                "tutor/tutor_academy_post_editor.html"
        );

        // when
        List<String> htmlFiles = templates.stream()
                .map(template -> TEMPLATE_ROOT.resolve(template))
                .map(TemplateBrandingTest::readString)
                .toList();
        String accountMenuFragment = Files.readString(TEMPLATE_ROOT.resolve("fragments/_account_menu.html"));
        String headFragment = Files.readString(TEMPLATE_ROOT.resolve("fragments/_head.html"));

        // then
        assertThat(htmlFiles).allSatisfy(html -> {
            boolean usesAccountMenu = html.contains("fragments/_account_menu :: accountMenu");
            assertThat(usesAccountMenu || html.contains("/my/profile")).isTrue();
            assertThat(usesAccountMenu || html.contains("로그아웃")).isTrue();
        });
        assertThat(accountMenuFragment).contains("/my/profile");
        assertThat(accountMenuFragment).contains("로그아웃");
        assertThat(headFragment).contains("/css/account_menu.css");
        assertThat(headFragment).contains("/js/return_url.js");
        assertThat(headFragment).contains("/js/account_menu.js");
    }

    @Test
    void publicHubPagesUseSharedFooter() throws IOException {
        // given
        List<String> templates = List.of(
                "public/landing.html",
                "public/home.html"
        );

        // when
        List<String> htmlFiles = templates.stream()
                .map(template -> TEMPLATE_ROOT.resolve(template))
                .map(TemplateBrandingTest::readString)
                .toList();
        String footerFragment = Files.readString(TEMPLATE_ROOT.resolve("fragments/_public_footer.html"));

        // then
        // 공용 footer는 사업자 정보를 담으므로 공개 허브 화면이 각자 footer를 만들면 안 된다.
        assertThat(htmlFiles).allSatisfy(html ->
                assertThat(html).contains("fragments/_public_footer :: publicFooter"));
        assertThat(footerFragment).contains("class=\"public-footer\"");
    }

    @Test
    void publicHubPagesUseTheirOwnBrandMark() throws IOException {
        // given
        String landing = readString(TEMPLATE_ROOT.resolve("public/landing.html"));
        String home = readString(TEMPLATE_ROOT.resolve("public/home.html"));
        String footerFragment = Files.readString(TEMPLATE_ROOT.resolve("fragments/_public_footer.html"));

        // then
        // 랜딩은 Feel2Real | EDUNOVA 화면이다. F2R 학생 플랫폼 로고 이미지를 헤더에 쓰지 않는다.
        assertThat(landing)
                .doesNotContain(F2R_LOGO_PATH)
                .contains("fragments/_public_footer :: publicFooterBranded(false, false, false)")
                .contains("Feel2Real")
                .contains("EDUNOVA");
        // /home은 F2R 학생용 플랫폼이라 리브랜딩 대상이 아니다.
        assertThat(home)
                .contains(F2R_LOGO_PATH)
                .contains("fragments/_public_footer :: publicFooter}");
        // 두 브랜드가 같은 fragment 본문(=같은 사업자 정보)을 공유해야 한다.
        assertThat(footerFragment)
                .contains("th:fragment=\"publicFooterBranded(\"")
                .contains("class=\"footer-name footer-name-bi\"")
                .contains(F2R_LOGO_PATH);
    }

    @Test
    void pageTemplatesIncludeSharedFaviconHeadFragment() throws IOException {
        // given
        List<Path> pageTemplates;
        try (Stream<Path> stream = Files.walk(TEMPLATE_ROOT)) {
            pageTemplates = stream
                    .filter(path -> path.toString().endsWith(".html"))
                    .filter(path -> !TEMPLATE_ROOT.relativize(path).startsWith("fragments"))
                    .toList();
        }

        // when
        List<String> htmlFiles = pageTemplates.stream()
                .map(TemplateBrandingTest::readString)
                .toList();
        String headFragment = Files.readString(TEMPLATE_ROOT.resolve("fragments/_head.html"));
        String adminTopnavFragment = Files.readString(TEMPLATE_ROOT.resolve("fragments/_admin_topnav.html"));

        // then
        assertThat(pageTemplates).isNotEmpty();
        assertThat(htmlFiles).allSatisfy(html ->
                assertThat(html.contains("fragments/_head :: favicon")
                        || html.contains("fragments/_admin_topnav :: head")
                        || html.contains("layout:decorate=\"~{layouts/public")).isTrue());
        assertThat(adminTopnavFragment).contains("fragments/_head :: favicon");
        assertThat(headFragment).contains("href=\"/favicon.ico?v=20260613\"");
        assertThat(headFragment).contains("type=\"image/svg+xml\"");
        assertThat(headFragment).contains("href=\"/favicon.svg?v=20260613\"");
    }

    @Test
    void browserTabFaviconUsesF2rLogoAssets() throws IOException {
        // given
        Path icoPath = Path.of("src/main/resources/static/favicon.ico");
        Path svgPath = Path.of("src/main/resources/static/favicon.svg");

        // when
        byte[] icoBytes = Files.readAllBytes(icoPath);
        String svg = Files.readString(svgPath);

        // then
        assertThat(Files.exists(icoPath)).isTrue();
        assertThat(Files.exists(svgPath)).isTrue();
        assertThat(icoBytes).hasSizeGreaterThan(0);
        assertThat(icoBytes[0]).isZero();
        assertThat(icoBytes[1]).isZero();
        assertThat(icoBytes[2]).isEqualTo((byte) 1);
        assertThat(icoBytes[3]).isZero();
        assertThat(svg).contains("<svg");
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read template: " + path, e);
        }
    }
}
