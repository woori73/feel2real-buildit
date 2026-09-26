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
    assertThat(html).contains("Agent와 함께 시작하기");
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

  @Test
  @DisplayName("온보딩: PC Chrome 상단 고지·4단계 사용법·자동 불대행 5개·/privacy 링크·과장 문구 금지")
  void templateCarriesChromeOnboarding() throws IOException {
    String html = Files.readString(
        repoRoot().resolve("src/main/resources/templates/newstart/fss-helper.html"),
        StandardCharsets.UTF_8);
    // 상단 사용자 안내 — PC Chrome 필수 + 확장 설치 시 단계별 작성 안내
    assertThat(html).contains("금감원 민원 도우미는 PC의 Google Chrome에서 사용하는 기능입니다.");
    assertThat(html).contains("현재 단계에 맞는 작성 안내를 받을 수 있습니다");
    // 모바일 fallback — 설치 유도 없이 PC 접속 안내
    assertThat(html).contains("이 기능은 PC Chrome에서 이용해주세요.");
    assertThat(html).contains("금감원 민원 도우미의 Chrome 연동 기능은 PC에서 이용할 수 있습니다.");
    // 4단계 사용법
    assertThat(html).contains("PC에서 Google Chrome을 실행합니다.");
    assertThat(html).contains("NewStart Chrome Agent를 설치합니다.");
    assertThat(html).contains("NewStart의 「금감원 민원 도우미」를 실행합니다.");
    assertThat(html).contains("금융감독원 사이트에서 화면 안내에 따라 민원을 작성합니다.");
    // 자동 불대행 5개 + 인증·제출 대행 금지 표현
    assertThat(html).contains("자동으로 하지 않는 것");
    assertThat(html).contains("공동인증서/금융인증서 인증을 대신하지 않습니다.");
    assertThat(html).contains("비밀번호를 입력하거나 저장하지 않습니다.");
    assertThat(html).contains("OTP/CAPTCHA를 대신 처리하지 않습니다.");
    assertThat(html).contains("민원을 자동 제출하지 않습니다.");
    assertThat(html).contains("최종 제출은 사용자가 직접 확인하고 진행합니다.");
    // 개인정보 — 기존 privacy 정책 링크
    assertThat(html).contains("비밀번호 등 민감한 입력값은 확장프로그램이 읽거나 저장하지 않습니다");
    assertThat(html).contains("href=\"/privacy\"");
    // 과장 금지 — 대리 접수·자동 제출 주장 문구
    assertThat(html).doesNotContain("AI가 대신 민원을 접수")
        .doesNotContain("자동으로 민원을 제출");
  }
}
