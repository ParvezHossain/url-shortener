"""Verify operator provisioning never sends a raw credential to PostgreSQL."""
import contextlib
import importlib.util
import io
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch
import uuid

spec = importlib.util.spec_from_file_location('provision', Path(__file__).parents[1] / 'provision-api-key.py')
provision = importlib.util.module_from_spec(spec)
spec.loader.exec_module(provision)


class ProvisionApiKeyTest(unittest.TestCase):
    def test_main_new_owner_prints_key_once_and_sends_only_hash(self):
        output = io.StringIO()
        with patch('sys.argv', ['provision']), patch.object(provision.subprocess, 'run') as run:
            with contextlib.redirect_stdout(output):
                provision.main()
        raw = output.getvalue().split('API key (shown once): ')[1].strip()
        self.assertRegex(raw, r'^usk_[0-9a-f]{24}_[0-9a-f]{64}$')
        sql = run.call_args.kwargs['input']
        self.assertNotIn(raw, sql)
        self.assertIn(provision.hashlib.sha256(raw.encode('ascii')).hexdigest(), sql)
        self.assertIn('INSERT INTO api_owner', sql)
        self.assertIn('KEY_CREATED', sql)
        self.assertEqual(output.getvalue().count(raw), 1)

    def test_main_existing_owner_preserves_owner_identity(self):
        owner = uuid.uuid4()
        with patch('sys.argv', ['provision', '--owner', str(owner)]), patch.object(provision.subprocess, 'run') as run:
            with contextlib.redirect_stdout(io.StringIO()):
                provision.main()
        self.assertNotIn('INSERT INTO api_owner', run.call_args.kwargs['input'])
        self.assertIn(str(owner), run.call_args.kwargs['input'])

    def test_main_database_failure_does_not_print_credential(self):
        output = io.StringIO()
        with patch('sys.argv', ['provision']), patch.object(provision.subprocess, 'run',
                side_effect=subprocess.CalledProcessError(1, 'psql')):
            with contextlib.redirect_stdout(output), self.assertRaises(subprocess.CalledProcessError):
                provision.main()
        self.assertEqual(output.getvalue(), '')


if __name__ == '__main__':
    unittest.main()
