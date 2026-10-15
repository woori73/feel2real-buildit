package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermAdminDtos.CampaignUpsert;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermAdminDtos.MakingOfUpsert;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermAdminDtos.MaterialUpsert;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermPublicDtos.MaterialCard;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.UploadSizeGuard;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

/**
 * PR #220 병합 전 발견된 보안 blocker 회귀 테스트.
 *
 * <p>핵심: 클라이언트가 raw storage key 를 지정할 수 없고, 공개 image serve 는 key 가
 * 그 소유자 namespace 안인지 재확인한 뒤에만 파일을 읽는다({@link MidtermImageService}).
 */
class MidtermImageSecurityTest {

  private MidtermImageService imageService(String uploadDir) {
    return new MidtermImageService(mock(ImageSanitizer.class), mock(FileStorage.class),
        mock(UploadSizeGuard.class), uploadDir);
  }

  private static java.util.List<String> componentNames(Class<?> record) {
    return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
  }

  @Test
  void rawStorageKeyCannotBeInjected_upsertDtosHaveNoKeyFields() {
    assertThat(componentNames(CampaignUpsert.class)).doesNotContain("popupVisualKey");
    assertThat(componentNames(MaterialUpsert.class)).doesNotContain("thumbnailKey");
    assertThat(componentNames(MakingOfUpsert.class)).doesNotContain("imageKey");
  }

  @Test
  void privateWikiSourceTitleIsNotExposedPublicly_publicCardHasNoSourceLabel() {
    assertThat(componentNames(MaterialCard.class)).doesNotContain("sourceLabel", "sourceTitle");
  }

  @Test
  void arbitraryLocalPathCannotBeServed(@TempDir Path uploadDir) {
    MidtermImageService svc = imageService(uploadDir.toString());
    assertThatThrownBy(() -> svc.read("/etc/passwd", 42L))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> svc.read("C:\\Windows\\System32\\config\\SAM", 42L))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void traversalKeyCannotBeServed(@TempDir Path uploadDir) {
    MidtermImageService svc = imageService(uploadDir.toString());
    assertThatThrownBy(() -> svc.read(uploadDir + "/42/../99/x.jpg", 42L))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void otherAcademyLocalKeyCannotBeServed(@TempDir Path uploadDir) throws Exception {
    Path other = Files.createDirectories(uploadDir.resolve("99"));
    Path victim = Files.write(other.resolve("secret.jpg"), new byte[] {1, 2, 3});
    MidtermImageService svc = imageService(uploadDir.toString());
    assertThatThrownBy(() -> svc.read(victim.toString(), 42L))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void validOwnedLocalKeyPassesNamespaceCheck(@TempDir Path uploadDir) throws Exception {
    Path mine = Files.createDirectories(uploadDir.resolve("42"));
    Path file = Files.write(mine.resolve("pic.jpg"), new byte[] {9, 9});

    FileStorage fs = mock(FileStorage.class);
    when(fs.readSingleLimited(eq(file.toString()), anyString(), anyLong())).thenReturn(new byte[] {9, 9});
    MidtermImageService svc = new MidtermImageService(mock(ImageSanitizer.class), fs,
        mock(UploadSizeGuard.class), uploadDir.toString());

    MidtermImageService.ServedImage served = svc.read(file.toString(), 42L);
    assertThat(served.bytes()).hasSize(2);
  }

  @Test
  void s3StyleKeyIsScopedToOwner() throws Exception {
    FileStorage fs = mock(FileStorage.class);
    when(fs.readSingleLimited(anyString(), anyString(), anyLong())).thenReturn(new byte[] {1});
    MidtermImageService svc = new MidtermImageService(mock(ImageSanitizer.class), fs,
        mock(UploadSizeGuard.class), "/var/uploads");

    // 소유자 42 의 객체 키는 통과
    assertThat(svc.read("prd/users/42/abc_pic.jpg", 42L).bytes()).hasSize(1);
    // 다른 소유자(99) 객체 키는 거부
    assertThatThrownBy(() -> svc.read("prd/users/99/abc_pic.jpg", 42L))
        .isInstanceOf(ResponseStatusException.class);
    // users 아래 하위 경로(디렉터리 탈출 시도)도 거부
    assertThatThrownBy(() -> svc.read("prd/users/42/sub/../../99/x.jpg", 42L))
        .isInstanceOf(ResponseStatusException.class);
  }
}
