package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EasyCollectionSchedulerReadinessTest {

  @Test
  void schedulerDoesNotEnterIngestForUnverifiedProvider() {
    EasyBusinessRepository businesses = mock(EasyBusinessRepository.class);
    EasyCollectionSourceRepository sources = mock(EasyCollectionSourceRepository.class);
    EasyCollectionConnectionRepository connections =
        mock(EasyCollectionConnectionRepository.class);
    EasyCollectionRunRepository runs = mock(EasyCollectionRunRepository.class);
    EasyCollectionIngestService ingest = mock(EasyCollectionIngestService.class);
    EasyCollectionRunExecutionGate gate = mock(EasyCollectionRunExecutionGate.class);

    EasyCollectionSource source = EasyCollectionSource.builder()
        .code("CARDSALES").name("CREFIA").adapterType("OPENAPI").active(true).build();
    ReflectionTestUtils.setField(source, "sourceId", 3L);
    EasyBusiness business = mock(EasyBusiness.class);
    EasyCollectionConnection connection = EasyCollectionConnection.builder()
        .business(business).source(source).label("crefia").connectionRef("opaque-ref").build();

    when(sources.findAllByActiveTrue()).thenReturn(List.of(source));
    when(connections.findBySource_SourceIdAndStatus(
        3L, EasyCollectionConnectionStatus.ACTIVE)).thenReturn(List.of(connection));
    when(gate.readiness(connection)).thenReturn(EasyCollectionProviderReadiness.UNVERIFIED);

    EasyCollectionSchedulerService scheduler = new EasyCollectionSchedulerService(
        businesses, sources, connections, runs, ingest);
    ReflectionTestUtils.setField(scheduler, "executionGate", gate);

    scheduler.runScheduledCollections();

    verifyNoInteractions(ingest);
  }
}
