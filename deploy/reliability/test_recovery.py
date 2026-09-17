import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('recovery', Path(__file__).with_name('recovery.py'))
recovery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recovery)


class RecoveryPolicyTest(unittest.TestCase):
    def snapshot(self):
        return {name: {'generation': name + ':1', 'status': 'healthy'} for name in recovery.SERVICES}

    def test_normal_and_first_observation_never_restart(self):
        actions, state = recovery.plan(self.snapshot(), {}, 1000)
        self.assertEqual([], actions)
        self.assertFalse(state['redisPending'])

    def test_requires_three_failures_and_honors_cooldown(self):
        snapshot = self.snapshot()
        snapshot['mqtt']['status'] = 'unhealthy'
        actions, state = recovery.plan(snapshot, {}, 1000)
        self.assertEqual([], actions)
        actions, state = recovery.plan(snapshot, state, 1060)
        self.assertEqual([], actions)
        actions, state = recovery.plan(snapshot, state, 1120)
        self.assertEqual(['mqtt'], actions)
        state['lastRepair'] = 1120
        self.assertEqual([], recovery.plan(snapshot, state, 1180)[0])

    def test_redis_restart_survives_cooldown_as_pending_work(self):
        snapshot = self.snapshot()
        _, state = recovery.plan(snapshot, {}, 1000)
        state['lastRepair'] = 1000
        snapshot['redis']['generation'] = 'redis:2'
        actions, state = recovery.plan(snapshot, state, 1060)
        self.assertEqual([], actions)
        self.assertTrue(state['redisPending'])
        actions, state = recovery.plan(snapshot, state, 1360)
        self.assertEqual(['zlmediakit', 'wvp-backend'], actions)

    def test_redis_must_be_healthy_before_rebuilding_dependents(self):
        snapshot = self.snapshot()
        _, state = recovery.plan(snapshot, {}, 1000)
        snapshot['redis'].update(generation='redis:2', status='starting')
        self.assertEqual([], recovery.plan(snapshot, state, 1360)[0])

    def test_backend_not_restarted_while_database_is_starting(self):
        snapshot = self.snapshot()
        snapshot['mysql']['status'] = 'starting'
        snapshot['wvp-backend']['status'] = 'unhealthy'
        state = {}
        for tick in range(4):
            actions, state = recovery.plan(snapshot, state, 1000 + tick * 60)
        self.assertEqual([], actions)

    def test_recovered_health_resets_failure_streak(self):
        snapshot = self.snapshot()
        snapshot['mqtt']['status'] = 'unhealthy'
        _, state = recovery.plan(snapshot, {}, 1000)
        snapshot['mqtt']['status'] = 'healthy'
        _, state = recovery.plan(snapshot, state, 1060)
        self.assertEqual(0, state['services']['mqtt']['failures'])

    def test_exited_processes_are_left_to_docker(self):
        snapshot = self.snapshot()
        snapshot['mqtt']['status'] = 'exited'
        state = {}
        for tick in range(4):
            actions, state = recovery.plan(snapshot, state, 1000 + tick * 60)
        self.assertEqual([], actions)

    def test_atomic_ledger_write(self):
        test_root = Path(__file__).resolve().parents[2] / 'codex' / 'reliability'
        test_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=test_root) as directory:
            ledger = Path(directory) / 'state.json'
            recovery.save_state(ledger, {'lastRepair': 123})
            self.assertIn('123', ledger.read_text())
            self.assertFalse(ledger.with_suffix('.tmp').exists())


if __name__ == '__main__':
    unittest.main()
