package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Academy video upload DTO — opaque capability boundary")
class AcademyVideoUploadDtoSecurityTest {

  @Test
  @DisplayName("given initiate response type when inspecting fields then storage identities and credentials are absent")
  void no_structured_storage_secret_fields() {
    // given / when
    Set<String> fields = Arrays.stream(
            AcademyVideoUploadDtos.InitiateResponse.class.getRecordComponents())
        .map(component -> component.getName().toLowerCase())
        .collect(Collectors.toSet());

    // then
    assertThat(fields).doesNotContain(
        "bucket", "storagekey", "uploadid", "accesskey", "secretkey", "credential");
    assertThat(fields).contains("parts");
  }

  @Test
  @DisplayName("given completion request type when inspecting fields then client cannot supply storage identity")
  void completion_accepts_manifest_only() {
    // given / when
    Set<String> fields = Arrays.stream(
            AcademyVideoUploadDtos.CompleteRequest.class.getRecordComponents())
        .map(component -> component.getName().toLowerCase())
        .collect(Collectors.toSet());

    // then
    assertThat(fields).containsExactly("parts");
  }
}
