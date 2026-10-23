package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WemadeKisWebSocketApprovalTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private WemadeKisClient kisClient;
    private WemadeKisWebSocket socket;

    @BeforeEach
    void setUp() {
        kisClient = mock(WemadeKisClient.class);
        socket = new WemadeKisWebSocket(kisClient, new ObjectMapper(), now::get);
    }

    @Test
    @DisplayName("승인키는 유효 시간 동안 한 번만 발급하고 재연결에도 재사용한다")
    void reusesApprovalKeyWithinTtl() {
        // given
        when(kisClient.websocketApprovalKey()).thenReturn(Optional.of("approval-1"));

        // when
        String first = socket.approvalKeyForConnection();
        now.addAndGet(30 * 60_000L);
        String second = socket.approvalKeyForConnection();

        // then
        assertThat(first).isEqualTo("approval-1");
        assertThat(second).isEqualTo("approval-1");
        verify(kisClient, times(1)).websocketApprovalKey();
    }

    @Test
    @DisplayName("재사용 기간이 지나면 새로 발급한다")
    void reissuesAfterTtl() {
        // given
        when(kisClient.websocketApprovalKey()).thenReturn(Optional.of("approval-1"), Optional.of("approval-2"));
        socket.approvalKeyForConnection();

        // when
        now.addAndGet(WemadeKisWebSocket.APPROVAL_KEY_TTL_MS);
        String renewed = socket.approvalKeyForConnection();

        // then
        assertThat(renewed).isEqualTo("approval-2");
        verify(kisClient, times(2)).websocketApprovalKey();
    }

    @Test
    @DisplayName("발급 실패 후 최소 간격 안에서는 다시 발급하지 않는다")
    void doesNotRetryIssuanceWithinMinInterval() {
        // given
        when(kisClient.websocketApprovalKey()).thenReturn(Optional.empty(), Optional.of("approval-1"));

        // when
        String failed = socket.approvalKeyForConnection();
        now.addAndGet(WemadeKisWebSocket.APPROVAL_MIN_INTERVAL_MS - 1);
        String blocked = socket.approvalKeyForConnection();
        now.addAndGet(1);
        String retried = socket.approvalKeyForConnection();

        // then
        assertThat(failed).isNull();
        assertThat(blocked).isNull();
        assertThat(retried).isEqualTo("approval-1");
        verify(kisClient, times(2)).websocketApprovalKey();
    }

    @Test
    @DisplayName("곧바로 끊긴 연결 뒤에는 키를 버리고, 최소 간격 이후에만 새로 발급한다")
    void invalidatedKeyIsReissuedOnlyAfterMinInterval() {
        // given
        when(kisClient.websocketApprovalKey()).thenReturn(Optional.of("approval-1"), Optional.of("approval-2"));
        socket.approvalKeyForConnection();

        // when
        socket.invalidateApprovalKey();
        now.addAndGet(5_000);
        String tooSoon = socket.approvalKeyForConnection();
        now.addAndGet(WemadeKisWebSocket.APPROVAL_MIN_INTERVAL_MS);
        String renewed = socket.approvalKeyForConnection();

        // then
        assertThat(tooSoon).isNull();
        assertThat(renewed).isEqualTo("approval-2");
        verify(kisClient, times(2)).websocketApprovalKey();
    }

    @Test
    @DisplayName("백오프는 실패가 이어지면 두 배씩 늘어 5분에서 멈추고, 정상 연결 뒤에는 5초로 돌아간다")
    void backoffGrowsAndCapsAndResetsAfterStableSession() {
        // given
        long backoff = WemadeKisWebSocket.BACKOFF_BASE_MS;

        // when
        long[] seen = new long[8];
        for (int i = 0; i < seen.length; i++) {
            backoff = WemadeKisWebSocket.nextBackoffMs(backoff, false);
            seen[i] = backoff;
        }
        long afterStable = WemadeKisWebSocket.nextBackoffMs(backoff, true);

        // then
        assertThat(seen).containsExactly(10_000, 20_000, 40_000, 80_000, 160_000, 300_000, 300_000, 300_000);
        assertThat(afterStable).isEqualTo(WemadeKisWebSocket.BACKOFF_BASE_MS);
    }

    @Test
    @DisplayName("발급이 계속 실패하는 30분 동안 발급 호출은 소수로 제한된다(이전 구현은 약 300회)")
    void failingIssuanceOverThirtyMinutesIsBounded() {
        // given: 승인키 발급이 계속 실패하는 KIS
        when(kisClient.websocketApprovalKey()).thenReturn(Optional.empty());
        long backoff = WemadeKisWebSocket.BACKOFF_BASE_MS;
        long end = now.get() + 30 * 60_000L;

        // when: 연결 루프와 같은 순서로 키 요청 → (실패) → 백오프 대기를 반복
        int attempts = 0;
        while (now.get() < end) {
            attempts++;
            String key = socket.approvalKeyForConnection();
            assertThat(key).isNull();
            backoff = WemadeKisWebSocket.nextBackoffMs(backoff, false);
            now.addAndGet(backoff);
        }

        // then: 30분간 발급 시도는 최대 30회(분당 1회)보다 훨씬 적다
        verify(kisClient, org.mockito.Mockito.atMost(12)).websocketApprovalKey();
        assertThat(attempts).isLessThan(20);
    }

    @Test
    @DisplayName("구독 9종은 기존 체결·호가·회원사 KRX/NXT/통합을 그대로 유지한다")
    void subscriptionsUnchanged() {
        assertThat(WemadeKisWebSocket.SUBSCRIPTIONS).containsExactly(
                "H0STCNT0", "H0NXCNT0", "H0UNCNT0",
                "H0STASP0", "H0NXASP0", "H0UNASP0",
                "H0STMBC0", "H0NXMBC0", "H0UNMBC0");
        verify(kisClient, never()).websocketApprovalKey();
    }
}
