#!/usr/bin/env python3
"""Opt-in, project-scoped container recovery. No device-online based restarts."""
import argparse
import datetime
import json
import os
from pathlib import Path
import subprocess
import time

SERVICES = ('mysql', 'redis', 'mqtt', 'zlmediakit', 'wvp-backend')


def command(args, cwd):
    result = subprocess.run(args, cwd=cwd, capture_output=True, text=True, timeout=240)
    if result.returncode:
        # Docker output can contain configuration; do not include it in the ledger/log.
        raise RuntimeError('Docker command failed; inspect the named service separately')
    return result.stdout


def plan(snapshot, previous, now, threshold=3, cooldown=300):
    """Pure policy: a persisted baseline, bounded retries, and prerequisite readiness."""
    state = json.loads(json.dumps(previous))
    state.setdefault('services', {})
    state.setdefault('lastRepair', 0)
    actions = []
    changed_redis = False
    for name in SERVICES:
        current = snapshot[name]
        old = state['services'].get(name, {})
        generation = current['generation']
        if name == 'redis' and old.get('generation') and old['generation'] != generation:
            changed_redis = True
        bad = current['status'] == 'unhealthy'
        state['services'][name] = {
            'generation': generation,
            'failures': old.get('failures', 0) + 1 if bad else 0,
        }
    state['redisPending'] = state.get('redisPending', False) or changed_redis
    if now - state['lastRepair'] < cooldown:
        return [], state
    # Docker itself handles exited/restarting processes. Only repair sustained unhealthy state.
    for name in SERVICES:
        if state['services'][name]['failures'] < threshold:
            continue
        if name == 'wvp-backend' and any(snapshot[n]['status'] != 'healthy' for n in SERVICES[:4]):
            continue
        actions = [name]
        break
    if not actions and state['redisPending'] and all(snapshot[n]['status'] == 'healthy' for n in SERVICES[:3]):
        # Restart media before the backend so its startup routines re-register media/GB state.
        actions = ['zlmediakit', 'wvp-backend']
    return actions, state


def save_state(path, state):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix('.tmp')
    with temp.open('w', encoding='utf-8') as stream:
        json.dump(state, stream)
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temp, path)
    if os.name == 'posix':
        fd = os.open(str(path.parent), os.O_RDONLY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project-directory', required=True, type=Path)
    parser.add_argument('--state', required=True, type=Path)
    parser.add_argument('--apply', action='store_true', help='Explicitly enable restart actions and ledger writes')
    args = parser.parse_args()
    root = args.project_directory.resolve(strict=True)
    compose = ['docker', 'compose', '--project-directory', str(root), '-f', str(root / 'compose.yaml')]
    # Do not print rendered Compose configuration, which includes secrets.
    names = set(command(compose + ['config', '--services'], root).split())
    if names != set(SERVICES):
        raise RuntimeError('Template requires the repository five-service topology; adapt and validate other deployments first')
    ids = command(compose + ['ps', '--all', '--quiet'], root).split()
    if len(ids) != len(SERVICES):
        raise RuntimeError('Missing or scaled containers; inspect stack startup before enabling repair')
    snapshot = {}
    for item in json.loads(command(['docker', 'inspect'] + ids, root)):
        name = item['Config']['Labels']['com.docker.compose.service']
        runtime = item['State']
        if name in snapshot or name not in SERVICES:
            raise RuntimeError('Unexpected Compose service topology')
        snapshot[name] = {
            'generation': item['Id'] + ':' + runtime['StartedAt'],
            'status': runtime.get('Health', {}).get('Status', 'no-healthcheck')
            if runtime['Status'] == 'running' else runtime['Status'],
        }
    state = {}
    if args.state.exists():
        try:
            state = json.loads(args.state.read_text(encoding='utf-8'))
            if not isinstance(state, dict) or not isinstance(state.get('services', {}), dict):
                raise ValueError('Invalid ledger')
            if not isinstance(state.get('lastRepair', 0), (int, float)):
                raise ValueError('Invalid repair timestamp')
            for entry in state.get('services', {}).values():
                if not isinstance(entry, dict) or not isinstance(entry.get('failures', 0), int):
                    raise ValueError('Invalid service ledger')
        except (ValueError, TypeError):
            if args.apply:
                suffix = datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
                os.replace(args.state, args.state.with_name(args.state.name + '.corrupt-' + suffix))
            state = {}
            print('Ledger invalid; preserve original and rebuild observation baseline')
    actions, next_state = plan(snapshot, state, time.time())
    print(json.dumps({'health': {n: s['status'] for n, s in snapshot.items()},
                      'plannedRestarts': actions, 'apply': args.apply}))
    if not args.apply:
        return
    if actions:
        next_state['lastRepair'] = time.time()
        # Persist cooldown before action, including failures, to prevent restart storms.
        save_state(args.state, next_state)
        for service in actions:
            command(compose + ['restart', '-t', '30', service], root)
        if actions == ['zlmediakit', 'wvp-backend']:
            next_state['redisPending'] = False
        for service in actions:
            next_state['services'][service]['failures'] = 0
    save_state(args.state, next_state)


if __name__ == '__main__':
    # systemd serializes this unit; a file lock also protects manual apply invocations.
    import sys
    if '--apply' in sys.argv:
        import fcntl
        lock_path = Path(sys.argv[sys.argv.index('--state') + 1]).with_suffix('.lock')
        lock_path.parent.mkdir(parents=True, exist_ok=True)
        with lock_path.open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            main()
    else:
        main()
