package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

class StudioServiceTest {
  @TempDir Path dir;

  private StudioService.Revision revision(Integer episode, Integer cut, String origin) {
    return new StudioService.Revision(
        "ep01-script",
        null,
        "r14",
        "SCRIPT",
        episode,
        cut,
        origin,
        "제목",
        "본문",
        "대사",
        "연출",
        "WAITING",
        null);
  }

  @Test
  void givenProposal_whenValidated_thenRemainsProposal() {
    var r = revision(1, null, "AI_PROPOSAL");
    assertDoesNotThrow(() -> StudioService.validate(r));
    assertEquals("AI_PROPOSAL", r.origin());
  }

  @Test
  void givenOutOfRangeEpisodeOrCut_whenValidated_thenRejected() {
    assertThrows(
        ResponseStatusException.class,
        () -> StudioService.validate(revision(15, null, "ORIGINAL")));
    assertThrows(
        ResponseStatusException.class, () -> StudioService.validate(revision(1, 69, "ORIGINAL")));
    assertThrows(
        ResponseStatusException.class, () -> StudioService.validate(revision(null, 1, "ORIGINAL")));
  }

  @Test
  void givenSpoofedPng_whenValidated_thenRejected() throws Exception {
    Path p = dir.resolve("fake.png");
    Files.writeString(p, "<script>bad</script>");
    assertThrows(ResponseStatusException.class, () -> StudioService.validateType(p, "png"));
  }

  @Test
  void givenMediaSignatures_whenValidated_thenOnlyMatchingFormatsAccepted() throws Exception {
    var ascii = java.nio.charset.StandardCharsets.ISO_8859_1;
    var samples =
        java.util.Map.of(
            "mov", "\0\0\0\u0014ftypqt  \0\0\0\0".getBytes(ascii),
            "wav", "RIFF$\0\0\0WAVEfmt ".getBytes(ascii),
            "webp", "RIFF$\0\0\0WEBPVP8 ".getBytes(ascii),
            "flac", "fLaC\0\0\0\u0010".getBytes(ascii),
            "mp3", "ID3\u0004\0\0\0\0\0\0".getBytes(ascii),
            "jpg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 16});
    for (var sample : samples.entrySet()) {
      Path ok = dir.resolve("ok." + sample.getKey());
      Files.write(ok, sample.getValue());
      assertDoesNotThrow(() -> StudioService.validateType(ok, sample.getKey()), sample.getKey());
      Path bad = dir.resolve("bad." + sample.getKey());
      Files.writeString(bad, "<html>not media</html>");
      assertThrows(
          ResponseStatusException.class,
          () -> StudioService.validateType(bad, sample.getKey()),
          sample.getKey());
    }
    // WAV 헤더를 WEBP라고 속이면 거부한다.
    Path wav = dir.resolve("wav.webp");
    Files.write(wav, samples.get("wav"));
    assertThrows(ResponseStatusException.class, () -> StudioService.validateType(wav, "webp"));
    assertTrue(
        StudioService.EXTENSIONS.containsAll(
            java.util.Set.of("mov", "wav", "mp3", "flac", "jpg", "webp")));
  }

  @Test
  void givenNonUtf8_whenValidated_thenRejected() throws Exception {
    Path p = dir.resolve("bad.md");
    Files.write(p, new byte[] {(byte) 0xff, 0});
    assertThrows(Exception.class, () -> StudioService.validateType(p, "md"));
  }

  @Test
  void givenHtml_whenValidated_thenOriginalUnchanged() throws Exception {
    Path p = dir.resolve("original.html");
    byte[] b = "<script>alert('원문')</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Files.write(p, b);
    StudioService.validateType(p, "html");
    assertArrayEquals(b, Files.readAllBytes(p));
  }

  @Test
  void givenValidJson_whenValidated_thenAcceptedAndBytesUnchanged() throws Exception {
    // given
    Path p = dir.resolve("manifest.json");
    byte[] b = "{\"episode\":1,\"cuts\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Files.write(p, b);
    // when
    StudioService.validateType(p, "json");
    // then
    assertArrayEquals(b, Files.readAllBytes(p));
  }

  @Test
  void givenBrokenJson_whenValidated_thenRejected() throws Exception {
    // given
    Path p = dir.resolve("broken.json");
    for (String invalid : java.util.List.of("{\"episode\":1,", "  \n  ", "{} trailing", "{} {}")) {
      Files.writeString(p, invalid);
      var e =
          assertThrows(ResponseStatusException.class, () -> StudioService.validateType(p, "json"));
      assertTrue(e.getReason().contains("JSON"));
    }
  }

  @Test
  void givenVersionLabelsR14ToR8_whenValidated_thenAllAccepted() {
    // given
    for (String label :
        java.util.List.of("r14", "r13", "r12", "r11", "r10", "r9", "r8", "r7", "r7.final-2")) {
      var r =
          new StudioService.Revision(
              "ep01-script",
              null,
              label,
              "SCRIPT",
              1,
              null,
              "ORIGINAL",
              "각본",
              "본문",
              "",
              "",
              "REVIEW",
              null);
      // when / then
      assertDoesNotThrow(() -> StudioService.validate(r), label);
    }
  }
}
