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

  @Test
  void givenWordSignatures_whenValidated_thenOnlyRealDocAndDocxAccepted() throws Exception {
    // given
    Path docx = dir.resolve("ok.docx");
    Files.write(docx, new byte[] {80, 75, 3, 4, 20, 0, 6, 0});
    Path doc = dir.resolve("ok.doc");
    Files.write(
        doc,
        new byte[] {
          (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1, 0
        });
    Path renamedPdf = dir.resolve("fake.docx");
    Files.writeString(renamedPdf, "%PDF-1.7 renamed");
    Path renamedText = dir.resolve("fake.doc");
    Files.writeString(renamedText, "plain text renamed to doc");
    // when / then
    assertDoesNotThrow(() -> StudioService.validateType(docx, "docx"));
    assertDoesNotThrow(() -> StudioService.validateType(doc, "doc"));
    assertThrows(ResponseStatusException.class, () -> StudioService.validateType(renamedPdf, "docx"));
    assertThrows(ResponseStatusException.class, () -> StudioService.validateType(renamedText, "doc"));
    // OLE2(doc) 바이트를 docx라고 속이거나 그 반대도 거부한다.
    assertThrows(ResponseStatusException.class, () -> StudioService.validateType(doc, "docx"));
    assertThrows(ResponseStatusException.class, () -> StudioService.validateType(docx, "doc"));
  }

  @Test
  void givenOtherStudioProjects_whenWordExtension_thenStillRejected() {
    // given / when / then
    for (String project : java.util.List.of("ai-agent-part1", "academy-relocation", "Student-Roster")) {
      assertFalse(StudioService.allowedExtension("docx", project), project);
      assertFalse(StudioService.allowedExtension("doc", project), project);
      assertTrue(StudioService.allowedExtension("pdf", project), project);
    }
    assertTrue(StudioService.allowedExtension("docx", StudentRosterController.PROJECT));
    assertTrue(StudioService.allowedExtension("doc", StudentRosterController.PROJECT));
    assertFalse(StudioService.allowedExtension("exe", StudentRosterController.PROJECT));
  }
}
