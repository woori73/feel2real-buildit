package f2r.tutor_solution.private_tutor_solution_back.infrastructure.cluster;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ClusterScheduleLeaderTest {
  @Test void followerAndQuarantineNeverInvokeWork() throws Throwable {
    var locks = mock(MysqlNamedLocks.class);
    var work = mock(ClusterScheduleLeader.Work.class);
    var leader = new ClusterScheduleLeader(locks);
    leader.tick(); leader.execute(work);
    var session = mock(MysqlNamedLocks.Session.class);
    when(locks.tryAcquire("scheduled-jobs")).thenReturn(session);
    when(session.valid()).thenReturn(true);
    leader.tick(); leader.execute(work);
    verifyNoInteractions(work);
    leader.stop();
  }
  @Test void sameLeaderOwnsSuccessiveInvocationsAndLossPreventsNewWork() throws Throwable {
    var locks = mock(MysqlNamedLocks.class);
    var session = mock(MysqlNamedLocks.Session.class);
    when(locks.tryAcquire("scheduled-jobs")).thenReturn(session);
    when(session.valid()).thenReturn(true);
    var leader = new ClusterScheduleLeader(locks);
    leader.tick();
    ReflectionTestUtils.setField(leader, "acquiredAt", 0L);
    var work = mock(ClusterScheduleLeader.Work.class);
    leader.execute(work); leader.execute(work);
    verify(locks, times(1)).tryAcquire("scheduled-jobs");
    verify(work, times(2)).run();
    when(session.valid()).thenReturn(false);
    leader.execute(work);
    verify(work, times(2)).run();
    leader.stop();
  }
  @Test void gracefulShutdownKeepsLockUntilActiveWorkFinishes() throws Throwable {
    var locks = mock(MysqlNamedLocks.class);
    var session = mock(MysqlNamedLocks.Session.class);
    when(locks.tryAcquire("scheduled-jobs")).thenReturn(session);
    when(session.valid()).thenReturn(true);
    var leader = new ClusterScheduleLeader(locks);
    leader.tick(); ReflectionTestUtils.setField(leader, "acquiredAt", 0L);
    leader.execute(() -> {
      leader.stop();
      verify(session, never()).close();
      return null;
    });
    verify(session).close();
  }
}
