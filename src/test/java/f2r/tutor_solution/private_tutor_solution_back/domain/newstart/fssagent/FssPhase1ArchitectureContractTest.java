package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.fssagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Phase 1(C) 아키텍처 계약 — ARCHITECTURE CHECK 판정대로 서버 브라우저 자동화가 코드베이스에
 * 존재해서는 안 된다: ① build.gradle에 Playwright 의존성 금지, ② gateway/Playwright 클래스
 * 금지, ③ 프론트 템플릿에 cred 수집·cert 저장·자동 제출 금지, ④ honest UX 금지 문구 금지.
 * (Phase 2 확장 브리지를 이 경로로 몰래 되살리는 것을 막는 회귀 게이트.)
 */
@DisplayName("FSS Phase-1 architecture contract — no server Playwright, no credential paths")
class FssPhase1ArchitectureContractTest {

  private static Path repoRoot() {
    Path p = Path.of("").toAbsolutePath();
    while (p != null && !Files.exists(p.resolve("build.gradle"))) p = p.getParent();
    assertThat(p).as("repository root with build.gradle").isNotNull();
    return p;
  }

  @Test
  void buildGradleHasNoPlaywrightDependency() throws IOException {
    String gradle = Files.readString(repoRoot().resolve("build.gradle"), StandardCharsets.UTF_8);
    assertThat(gradle.toLowerCase()).doesNotContain("playwright");
  }

  @Test
  void noBrowserGatewayClassesExistInMainSources() throws IOException {
    Path main = repoRoot().resolve("src/main/java");
    List<String> offenders;
    try (Stream<Path> s = Files.walk(main)) {
      offenders = s.filter(p -> p.toString().endsWith(".java"))
          .filter(p -> {
            String n = p.getFileName().toString();
            return n.contains("Playwright") || n.contains("BrowserGateway") || n.contains("Selenium")
                || n.contains("Cdp") || n.contains("Puppeteer");
          })
          .map(Path::toString)
          .toList();
    }
    assertThat(offenders).isEmpty();
    try (Stream<Path> s = Files.walk(main)) {
      List<String> refs = s.filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".java")).filter(p -> {
        try {
          return Files.readString(p, StandardCharsets.UTF_8).contains("com.microsoft.playwright");
        } catch (IOException e) {
          return false;
        }
      }).map(Path::toString).toList();
      assertThat(refs).isEmpty();
    }
  }

  @Test
  @DisplayName("fss 관련 main 소스에는 인증서·비밀번호·쿠키·토큰 수집 경로가 없다")
  void fssSourcesNeverTouchCredentials() throws IOException {
    Path fss = repoRoot().resolve(
        "src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/newstart");
    try (Stream<Path> s = Files.walk(fss)) {
      List<Path> files = s.filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().toLowerCase().contains("fss")).toList();
      assertThat(files).isNotEmpty();
      for (Path p : files) {
        String src = Files.readString(p, StandardCharsets.UTF_8).toLowerCase();
        assertThat(src).as(p.getFileName().toString())
            .doesNotContain("getpassword")
            .doesNotContain("readcookie")
            .doesNotContain("storecert")
            .doesNotContain("collecttoken")
            .doesNotContain("saveotp")
            .doesNotContain("httpsession");
      }
    }
  }

  @Test
  @DisplayName("템플릿: PC Chrome 고지·두 경로 CTA·모바일 fallback·honest UX 금지 문구 회귀")
  void templateCarriesNoticesAndHonestUX() throws IOException {
    String html = Files.readString(
        repoRoot().resolve("src/main/resources/templates/newstart/fss-helper.html"),
        StandardCharsets.UTF_8);
    assertThat(html).contains("금감원 민원 Agent는 PC의 Chrome 브라우저에서 이용할 수 있습니다");
    assertThat(html).contains("Agent와 함께 신청하기");
    assertThat(html).contains("화면을 보며 직접 따라하기");
    assertThat(html).contains("ns-ag-mobilenote");
    // honest UX 금지 — 감지·자동 진행을 주장하는 문구(서버는 사용자 탭을 볼 수 없다)
    assertThat(html).doesNotContain("현재 화면을 확인했습니다")
        .doesNotContain("버튼을 찾았습니다")
        .doesNotContain("완료를 감지했습니다")
        .doesNotContain("자동으로 다음 단계");
    // 서버 자동화 흔적 금지 — 금감원 DOM 조작·스크립트 주입·form submit 없음
    assertThat(html).doesNotContain("querySelector")
        .doesNotContain("contentWindow")
        .doesNotContain(".submit(")
        .doesNotContain("window.open(");
  }
}
