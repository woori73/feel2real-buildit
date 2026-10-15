package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import f2r.tutor_solution.private_tutor_solution_back.config.AlbAccessStatisticsProperties;
import f2r.tutor_solution.private_tutor_solution_back.config.PublicSiteProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AlbAccessStatisticsWorkerConfigTest {

  @Test
  void givenApplicationAssetPrefix_whenAlbImportStarts_thenItFailsBeforeListingS3() {
    // given
    AlbAccessStatisticsProperties properties = new AlbAccessStatisticsProperties();
    properties.setBucket("shared-bucket");
    properties.setKeyPrefix("prd/academy-video/");
    AlbAccessStatisticsWorker worker = new AlbAccessStatisticsWorker(
        properties,
        new PublicSiteProperties("feel2real.co.kr"),
        mock(AcademyHandleHistoryRepository.class),
        mock(AcademyAccessStatisticsStore.class),
        mock(AcademyAccessStatisticsImportService.class));

    // when / then
    assertThatThrownBy(worker::init)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AWSLogs/");
  }
}
