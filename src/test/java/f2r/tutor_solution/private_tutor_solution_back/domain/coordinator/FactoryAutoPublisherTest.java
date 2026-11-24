package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FactoryAutoPublisher — 1분마다 올리지 않은 시험 묶음 자동 게시")
class FactoryAutoPublisherTest {

  @Test
  @DisplayName("given 묶음 둘 중 하나가 실패 when 두 번 훑음 then 다른 건은 계속 올리고 실패한 건은 다시 부르지 않는다")
  void sweepContinuesAfterFailureAndSkipsFailedJob() {
    // given
    FactoryExamPackageRepository packages = mock(FactoryExamPackageRepository.class);
    CoordFactoryService factory = mock(CoordFactoryService.class);
    FactoryExamPackage bad = mock(FactoryExamPackage.class);
    FactoryExamPackage good = mock(FactoryExamPackage.class);
    when(bad.getJobId()).thenReturn("job-bad");
    when(good.getJobId()).thenReturn("job-good");
    when(packages.findAllByExamIdIsNull()).thenReturn(List.of(bad, good));
    when(factory.autoPublish("job-bad")).thenThrow(new IllegalStateException("이미 쓰이는 시험 코드입니다"));
    when(factory.autoPublish("job-good")).thenReturn("");
    FactoryAutoPublisher sweeper = new FactoryAutoPublisher(packages, factory);

    // when
    sweeper.sweep();
    sweeper.sweep();

    // then
    verify(factory, times(1)).autoPublish("job-bad");
    verify(factory, times(2)).autoPublish("job-good");
  }
}
