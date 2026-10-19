package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/** Opt-in: uses only the disposable local grammar_review_test schema on port 13316. */
@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
    "spring.datasource.url=jdbc:mysql://127.0.0.1:13316/grammar_review_test?allowPublicKeyRetrieval=true&useSSL=false",
    "spring.datasource.username=root", "spring.datasource.password=",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, GrammarBankService.class, GrammarBankImportService.class, GrammarBankAccessGuard.class})
@EnabledIfEnvironmentVariable(named = "GRAMMAR_MYSQL_TEST", matches = "true")
class GrammarReviewMySqlTest {
  @Autowired GrammarBankImportService importer;
  @Autowired GrammarBankService bank;
  @Autowired GrammarQuestionProfileRepository profiles;
  @Autowired GrammarTagRepository tags;
  @Autowired SubjectRepository subjects;
  @Autowired jakarta.persistence.EntityManager em;

  @Test void completeReviewedPayloadSurvivesStorageFilteringEditingAndReplay() throws Exception {
    var json = new ObjectMapper();
    Map<String, Object> payload = json.readValue(Files.readString(Path.of(System.getenv("GRAMMAR_REVIEW_IMPORT_JSON"))), new TypeReference<>() {});
    @SuppressWarnings("unchecked") var records = (List<Map<String, Object>>)payload.get("records");
    assertThat(records).hasSize(1016);
    assertThat(subjects.saveAndFlush(Subject.builder().nameEn("ENGLISH").nameKo("영어").build()).getSubjectId()).isEqualTo(1);
    var names = List.of("시제", "조동사", "수동태", "부정사", "동명사", "분사·분사구문", "관계대명사·관계부사", "접속사", "가정법", "비교구문", "일치·화법", "의문문·간접의문문", "문장 구조·형식", "기타 문법");
    for (int i = 0; i < names.size(); i++) tags.save(GrammarTag.builder().nameKo(names.get(i)).sortOrder(i).build());
    tags.flush();
    var admin = new AuthenticatedPrincipal(1L, "test-admin", "ADMIN");
    var request = new GrammarImportRequest(null, "UNKNOWN", records);
    var first = importer.importJson(admin, request);
    assertThat(first.failed()).withFailMessage(first.errors().toString()).isZero();
    assertThat(first.created()).isEqualTo(1016);
    em.flush(); em.clear();
    for (var record : records) {
      var profile = profiles.findFirstByExternalRef((String)record.get("external_ref")).orElseThrow();
      assertThat(GrammarReviewMetadata.snapshot(profile.getAutoClassification())).isEqualTo(record.get("review_metadata"));
      assertThat(profile.getQuestion().getQuestionText()).isEqualTo(record.get("stem"));
    }
    var h1 = new GrammarBankService.SearchFilters(null, null, null, null, null, null, null, null, null, SchoolLevel.H1);
    assertThat(bank.search(h1, PageRequest.of(0, 50)).getTotalElements()).isEqualTo(38);
    long passiveTag = tags.findAll().stream().filter(t -> t.getNameKo().equals("수동태")).findFirst().orElseThrow().getTagId();
    var combined = new GrammarBankService.SearchFilters(null, null, GrammarQuestionProfile.GrammarDifficulty.MEDIUM, null, null, null, null, null, List.of(passiveTag), SchoolLevel.M2);
    long expected = records.stream().filter(r -> r.get("difficulty").equals("MEDIUM") && ((Map<?, ?>)r.get("review_metadata")).get("recommended_grade").equals("중2") && ((List<?>)r.get("grammar_tags")).contains("수동태")).count();
    assertThat(expected).isPositive();
    assertThat(bank.search(combined, PageRequest.of(0, 50)).getTotalElements()).isEqualTo(expected);
    var replay = importer.importJson(admin, request);
    assertThat(replay.duplicatesSkipped()).isEqualTo(1016);
    assertThat(replay.created()).isZero();
    var independent = profiles.findFirstByExternalRef("WG-0459").orElseThrow();
    var before = GrammarReviewMetadata.snapshot(independent.getAutoClassification());
    var original = independent.getSchoolLevel();
    var edited = bank.updateProfile(admin, independent.getQuestion().getQuestionId(), new GrammarProfileUpdateRequest(null, null, null, null, null, null, null, SchoolLevel.UNKNOWN));
    assertThat(edited.schoolLevel()).isEqualTo(original);
    assertThat(edited.reviewMetadata()).isEqualTo(before);
    em.flush(); em.clear();
    var unknown = new GrammarBankService.SearchFilters(null, null, null, null, null, null, null, null, null, SchoolLevel.UNKNOWN);
    assertThat(bank.search(unknown, PageRequest.of(0, 50)).getTotalElements()).isEqualTo(1);
  }
}
