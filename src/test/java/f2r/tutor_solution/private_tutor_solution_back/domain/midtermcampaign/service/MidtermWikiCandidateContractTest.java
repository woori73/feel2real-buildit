package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Python signed candidate endpoint와 Java gateway의 최소 보안 계약. */
class MidtermWikiCandidateContractTest {

  private static final Path ROOT = Path.of(System.getProperty("user.dir"));

  @Test
  void candidateEndpoint_isCampaignExactEligibleOnly_andReturnsNoSensitiveLocator()
      throws Exception {
    String python = Files.readString(
        ROOT.resolve("problem-generator/wiki-backend-src/backend/main.py"));
    int start = python.indexOf("@app.get('/api/live-material-candidates')");
    int end = python.indexOf("@app.get('/api/final-deliverables/{deliverable_id}/original')", start);
    String endpoint = python.substring(start, end);

    assertThat(endpoint).contains("def list_live_material_candidates(year: int, semester: str, exam: str)");
    assertThat(endpoint).doesNotContain("school: str", "grade: str");
    assertThat(endpoint).contains("year=? AND semester=? AND exam=?");
    assertThat(endpoint).contains("core_type='school_print'");
    assertThat(endpoint).contains("kind='final'");
    assertThat(endpoint).contains("TRIM(html_text) <> ''");
    assertThat(endpoint).contains("'hasHtml': True");
    assertThat(endpoint).doesNotContain(
        "student_id", "original_name", "original_blob", "storage", "download", "url");
  }

  @Test
  void gateway_signsTutorIdentity_andRechecksExactCampaignScope() throws Exception {
    String java = Files.readString(ROOT.resolve(
        "src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/"
            + "midtermcampaign/service/HttpMidtermWikiSourceGateway.java"));

    assertThat(java).contains("identitySigner.sign(builder, tutor)");
    assertThat(java).contains("/api/live-material-candidates?year=");
    assertThat(java).contains("year != scope.year()");
    assertThat(java).contains("!scope.semester().equals(semester)");
    assertThat(java).contains("!scope.exam().equals(exam)");
    assertThat(java).doesNotContain("listByContext");
  }
}
