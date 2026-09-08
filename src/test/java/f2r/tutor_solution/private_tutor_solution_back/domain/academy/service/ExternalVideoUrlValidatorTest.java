package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyExternalVideoProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExternalVideoUrlValidator — scheme/host 판정, embed 는 YouTube 만")
class ExternalVideoUrlValidatorTest {

  private final ExternalVideoUrlValidator validator = new ExternalVideoUrlValidator();

  @Test
  @DisplayName("naver.me 단축 URL → NAVER, embed 없음 (리다이렉트 추적 안 함)")
  void naverShortUrl() {
    var r = validator.validate("https://naver.me/F4WnDovq");
    assertThat(r.provider()).isEqualTo(AcademyExternalVideoProvider.NAVER);
    assertThat(r.embedUrl()).isNull();
    assertThat(r.normalizedUrl()).isEqualTo("https://naver.me/F4WnDovq");
  }

  @Test
  @DisplayName("tv.naver.com/v/<clipNo> → NAVER + tv.naver.com/embed 주소")
  void naverTvClip() {
    var v = validator.validate("https://tv.naver.com/v/12345");
    assertThat(v.provider()).isEqualTo(AcademyExternalVideoProvider.NAVER);
    assertThat(v.embedUrl()).isEqualTo("https://tv.naver.com/embed/12345");
    assertThat(validator.validate("https://tv.naver.com/clip/98765?query=1").embedUrl())
        .isEqualTo("https://tv.naver.com/embed/98765");
    assertThat(validator.validate("https://tv.naver.com/embed/55555?autoPlay=false").embedUrl())
        .isEqualTo("https://tv.naver.com/embed/55555");
  }

  @Test
  @DisplayName("tv.naver.com 채널 등 clip 번호 없음 → NAVER, embed 없음")
  void naverTvNonClip() {
    var v = validator.validate("https://tv.naver.com/eduholic");
    assertThat(v.provider()).isEqualTo(AcademyExternalVideoProvider.NAVER);
    assertThat(v.embedUrl()).isNull();
  }

  @Test
  @DisplayName("naver.me 단축주소 / m.naver.com/shorts(GUID) → NAVER, embed 없음(공식 경로 없음)")
  void naverShareLinksHaveNoEmbed() {
    assertThat(validator.validate("https://naver.me/F4WnDovq").embedUrl()).isNull();
    assertThat(validator.validate(
        "https://m.naver.com/shorts?mediaId=3B84EA2409259B3619D13D9748298262888E&serviceType=NAVER_TV")
        .embedUrl()).isNull();
    assertThat(validator.validate("https://m.naver.com/shorts?mediaId=ABC").provider())
        .isEqualTo(AcademyExternalVideoProvider.NAVER);
  }

  @Test
  @DisplayName("clip-viewer.naver.com/oembed/ (공유>소스코드 iframe src) → NAVER + canonical embed URL")
  void naverClipViewerOembed() {
    String[] seeds = {
      "3B84EA2409259B3619D13D9748298262888E",
      "7EC5EB4A7C09B448379F094D0787416655BF",
      "139CB1F7A8E070D6B2119B4AE236E28DA6E2"
    };
    for (String seed : seeds) {
      var v = validator.validate(
          "https://clip-viewer.naver.com/oembed/?seedMediaId=" + seed
          + "&mediaType=SHORT_FORM&serviceType=NTV");
      assertThat(v.provider()).isEqualTo(AcademyExternalVideoProvider.NAVER);
      assertThat(v.embedUrl()).isEqualTo(
          "https://clip-viewer.naver.com/oembed/?seedMediaId=" + seed
          + "&mediaType=SHORT_FORM&serviceType=NTV");
    }
    // 파라미터 순서/여분 파라미터가 달라도 canonical 로 재구성한다.
    assertThat(validator.validate(
        "https://clip-viewer.naver.com/oembed/?serviceType=NTV&extra=1"
        + "&seedMediaId=139CB1F7A8E070D6B2119B4AE236E28DA6E2&mediaType=SHORT_FORM").embedUrl())
        .isEqualTo("https://clip-viewer.naver.com/oembed/?seedMediaId="
        + "139CB1F7A8E070D6B2119B4AE236E28DA6E2&mediaType=SHORT_FORM&serviceType=NTV");
  }

  @Test
  @DisplayName("clip-viewer.naver.com — allowlist 조건 하나라도 어긋나면 embed 없음(CTA)")
  void naverClipViewerStrictAllowlist() {
    String seed = "3B84EA2409259B3619D13D9748298262888E";
    // seedMediaId 없음
    assertThat(validator.validate(
        "https://clip-viewer.naver.com/oembed/?mediaType=SHORT_FORM&serviceType=NTV").embedUrl()).isNull();
    // mediaType 불일치
    assertThat(validator.validate(
        "https://clip-viewer.naver.com/oembed/?seedMediaId=" + seed
        + "&mediaType=LONG_FORM&serviceType=NTV").embedUrl()).isNull();
    // serviceType 불일치
    assertThat(validator.validate(
        "https://clip-viewer.naver.com/oembed/?seedMediaId=" + seed
        + "&mediaType=SHORT_FORM&serviceType=NAVER_TV").embedUrl()).isNull();
    // path 불일치
    assertThat(validator.validate(
        "https://clip-viewer.naver.com/player/?seedMediaId=" + seed
        + "&mediaType=SHORT_FORM&serviceType=NTV").embedUrl()).isNull();
    // host 불일치(서브도메인 위장)
    assertThat(validator.validate(
        "https://evil.clip-viewer.naver.com/oembed/?seedMediaId=" + seed
        + "&mediaType=SHORT_FORM&serviceType=NTV").embedUrl()).isNull();
    // seedMediaId 형식 불량
    assertThat(validator.validate(
        "https://clip-viewer.naver.com/oembed/?seedMediaId=../../etc&mediaType=SHORT_FORM&serviceType=NTV")
        .embedUrl()).isNull();
    // http
    assertThatThrownBy(() -> validator.validate(
        "http://clip-viewer.naver.com/oembed/?seedMediaId=" + seed
        + "&mediaType=SHORT_FORM&serviceType=NTV"))
        .isInstanceOf(VideoUploadRejectedException.class);
  }

  @Test
  @DisplayName("youtube watch?v= → YOUTUBE + embed URL")
  void youtubeWatch() {
    var r = validator.validate("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10s");
    assertThat(r.provider()).isEqualTo(AcademyExternalVideoProvider.YOUTUBE);
    assertThat(r.embedUrl()).isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");
  }

  @Test
  @DisplayName("youtu.be 단축 → YOUTUBE + embed URL")
  void youtuBe() {
    var r = validator.validate("https://youtu.be/dQw4w9WgXcQ");
    assertThat(r.embedUrl()).isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");
  }

  @Test
  @DisplayName("youtube /shorts/ → YOUTUBE + embed URL")
  void youtubeShorts() {
    var r = validator.validate("https://www.youtube.com/shorts/abcDEF12345");
    assertThat(r.embedUrl()).isEqualTo("https://www.youtube.com/embed/abcDEF12345");
  }

  @Test
  @DisplayName("youtube 채널 URL(재생 id 없음) → GENERIC, embed 없음")
  void youtubeChannelNoEmbed() {
    var r = validator.validate("https://www.youtube.com/@somechannel");
    assertThat(r.provider()).isEqualTo(AcademyExternalVideoProvider.GENERIC);
    assertThat(r.embedUrl()).isNull();
  }

  @Test
  @DisplayName("알 수 없는 host → GENERIC, embed 없음")
  void genericHost() {
    var r = validator.validate("https://vimeo.com/76979871");
    assertThat(r.provider()).isEqualTo(AcademyExternalVideoProvider.GENERIC);
    assertThat(r.embedUrl()).isNull();
  }

  @Test
  @DisplayName("http:// 는 거부 (https 만)")
  void rejectsHttp() {
    assertThatThrownBy(() -> validator.validate("http://naver.me/x"))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("https");
  }

  @Test
  @DisplayName("javascript: 스킴 거부")
  void rejectsJavascriptScheme() {
    assertThatThrownBy(() -> validator.validate("javascript:alert(document.cookie)"))
        .isInstanceOf(VideoUploadRejectedException.class);
  }

  @Test
  @DisplayName("data: 스킴 거부")
  void rejectsDataScheme() {
    assertThatThrownBy(() -> validator.validate("data:text/html,<script>1</script>"))
        .isInstanceOf(VideoUploadRejectedException.class);
  }

  @Test
  @DisplayName("빈 값 거부")
  void rejectsBlank() {
    assertThatThrownBy(() -> validator.validate("   "))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("URL");
  }

  @Test
  @DisplayName("2048자 초과 거부")
  void rejectsTooLong() {
    String longUrl = "https://example.com/" + "a".repeat(2100);
    assertThatThrownBy(() -> validator.validate(longUrl))
        .isInstanceOf(VideoUploadRejectedException.class);
  }

  @Test
  @DisplayName("host 없는 URL 거부")
  void rejectsNoHost() {
    assertThatThrownBy(() -> validator.validate("https:///path"))
        .isInstanceOf(VideoUploadRejectedException.class);
  }
}
