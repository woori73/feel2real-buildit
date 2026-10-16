"""dc_gate_watchdog.evaluate 단위 테스트 (given / when / then)."""
import unittest

from dc_gate_watchdog import DEFAULTS, evaluate

CFG = dict(DEFAULTS)


def obs(now, state='OPEN', mem=450, swap=934, swapout=1000, oom=0, spring=True, wiki=True):
    return {'now': now, 'state_file': state, 'mem_avail_mib': mem, 'swap_used_mib': swap, 'swapout_pages': swapout,
            'oom_count': oom, 'spring_ok': spring, 'wiki_ok': wiki}


def opened_state(at=1000.0):
    st = {'started_at': 900.0}
    evaluate(obs(at), CFG, st)  # OPEN 최초 관측으로 기준값 기록
    return st


class WatchdogDecisionTest(unittest.TestCase):
    def test_waits_while_gate_is_closed_and_never_opened(self):
        # given: gate가 CLOSED이고 아직 OPEN이 관측되지 않았다
        st = {'started_at': 1000.0}
        # when: START_WAIT 이내에 관측한다
        result = evaluate(obs(1100.0, state='CLOSED'), CFG, st)
        # then: 계속 기다린다
        self.assertEqual(result, ('WAIT', 'waiting_for_open'))

    def test_exits_when_never_opened_within_start_wait(self):
        st = {'started_at': 1000.0}
        result = evaluate(obs(1000.0 + CFG['start_wait_s'] + 1, state='CLOSED'), CFG, st)
        self.assertEqual(result, ('DONE', 'never_opened'))

    def test_monitors_normally_after_open(self):
        st = {'started_at': 900.0}
        self.assertEqual(evaluate(obs(1000.0), CFG, st), ('WAIT', 'monitoring'))
        self.assertEqual(evaluate(obs(1100.0), CFG, st), ('WAIT', 'monitoring'))

    def test_done_when_gate_returns_to_closed(self):
        st = opened_state()
        result = evaluate(obs(1200.0, state='CLOSED'), CFG, st)
        self.assertEqual(result, ('DONE', 'closed_normally'))

    def test_triggers_when_ttl_expires_while_open(self):
        st = opened_state(1000.0)
        self.assertEqual(evaluate(obs(1000.0 + CFG['ttl_s'] - 1), CFG, st), ('WAIT', 'monitoring'))
        self.assertEqual(evaluate(obs(1000.0 + CFG['ttl_s'] + 1), CFG, st), ('TRIGGER', 'ttl_expired'))

    def test_triggers_when_mem_available_drops_below_300(self):
        st = opened_state()
        self.assertEqual(evaluate(obs(1010.0, mem=299), CFG, st), ('TRIGGER', 'mem_below_min'))

    def test_does_not_trigger_at_exactly_300(self):
        st = opened_state()
        self.assertEqual(evaluate(obs(1010.0, mem=300), CFG, st), ('WAIT', 'monitoring'))

    def test_triggers_on_swap_growth_over_limit(self):
        st = opened_state()
        self.assertEqual(evaluate(obs(1010.0, swap=934 + CFG['swap_growth_mib'] + 1), CFG, st), ('TRIGGER', 'swap_growth'))

    def test_small_swap_change_is_ignored(self):
        st = opened_state()
        self.assertEqual(evaluate(obs(1010.0, swap=934 + 5), CFG, st), ('WAIT', 'monitoring'))

    def test_triggers_on_swap_out_burst(self):
        st = opened_state()
        self.assertEqual(evaluate(obs(1010.0, swapout=1000 + CFG['swapout_pages'] + 1), CFG, st), ('TRIGGER', 'swap_out_burst'))

    def test_triggers_when_new_oom_event_appears(self):
        st = opened_state()
        self.assertEqual(evaluate(obs(1010.0, oom=1), CFG, st), ('TRIGGER', 'oom_detected'))

    def test_oom_present_before_open_is_baseline_not_trigger(self):
        st = {'started_at': 900.0}
        evaluate(obs(1000.0, oom=2), CFG, st)
        self.assertEqual(evaluate(obs(1010.0, oom=2), CFG, st), ('WAIT', 'monitoring'))

    def test_single_health_blip_does_not_trigger_but_two_in_a_row_do(self):
        st = opened_state()
        self.assertEqual(evaluate(obs(1010.0, spring=False), CFG, st), ('WAIT', 'monitoring'))
        self.assertEqual(evaluate(obs(1013.0, spring=True), CFG, st), ('WAIT', 'monitoring'))
        self.assertEqual(evaluate(obs(1016.0, spring=False), CFG, st), ('WAIT', 'monitoring'))
        self.assertEqual(evaluate(obs(1019.0, spring=False), CFG, st), ('TRIGGER', 'spring_unhealthy'))

    def test_wiki_unhealthy_twice_triggers(self):
        st = opened_state()
        evaluate(obs(1010.0, wiki=False), CFG, st)
        self.assertEqual(evaluate(obs(1013.0, wiki=False), CFG, st), ('TRIGGER', 'wiki_unhealthy'))

    def test_draining_is_given_grace_then_triggers_when_stuck(self):
        st = opened_state()
        self.assertEqual(evaluate(obs(1100.0, state='DRAINING'), CFG, st), ('WAIT', 'monitoring'))
        self.assertEqual(evaluate(obs(1100.0 + CFG['drain_grace_s'] - 1, state='DRAINING'), CFG, st), ('WAIT', 'monitoring'))
        self.assertEqual(evaluate(obs(1100.0 + CFG['drain_grace_s'] + 1, state='DRAINING'), CFG, st), ('TRIGGER', 'drain_stuck'))

    def test_ttl_expiry_does_not_restart_a_drain_that_is_progressing(self):
        st = opened_state(1000.0)
        # when: TTL 직후지만 gate가 DRAINING(정상 종료 진행 중)이다
        result = evaluate(obs(1000.0 + CFG['ttl_s'] + 5, state='DRAINING'), CFG, st)
        # then: 재시작하지 않고 drain 유예를 준다
        self.assertEqual(result, ('WAIT', 'monitoring'))

    def test_unreadable_state_after_open_is_fail_safe_trigger(self):
        st = opened_state()
        self.assertEqual(evaluate(obs(1010.0, state=None), CFG, st), ('TRIGGER', 'state_unreadable'))

    def test_unreadable_state_before_open_just_waits(self):
        st = {'started_at': 1000.0}
        self.assertEqual(evaluate(obs(1010.0, state=None), CFG, st), ('WAIT', 'state_unreadable_before_open'))

    def test_fault_state_means_already_blocked(self):
        st = opened_state()
        self.assertEqual(evaluate(obs(1010.0, state='FAULT'), CFG, st), ('DONE', 'fault_already_blocked'))

    def test_guards_are_not_applied_before_open(self):
        # given: 아직 열리지 않았으므로 메모리가 낮아도 재시작할 이유가 없다
        st = {'started_at': 1000.0}
        self.assertEqual(evaluate(obs(1010.0, state='CLOSED', mem=100), CFG, st), ('WAIT', 'waiting_for_open'))


if __name__ == '__main__':
    unittest.main()
