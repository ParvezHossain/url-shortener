#!/usr/bin/env python3
"""Operator-only provisioning using PostgreSQL's standard PG* connection variables."""
import argparse
import hashlib
import secrets
import subprocess
import uuid


def main():
    """Create an owner or issue a recovery key for an explicitly supplied owner."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--owner', type=uuid.UUID, help='Existing owner UUID for recovery')
    args = parser.parse_args()
    owner = args.owner or uuid.uuid4()
    prefix = secrets.token_hex(12)
    raw = f'usk_{prefix}_{secrets.token_hex(32)}'
    digest = hashlib.sha256(raw.encode('ascii')).hexdigest()
    create_owner = '' if args.owner else f"INSERT INTO api_owner(id) VALUES ('{owner}');"
    sql = f"""BEGIN;
{create_owner}
INSERT INTO api_key(prefix, owner_id, key_hash) VALUES ('{prefix}', '{owner}', '{digest}');
INSERT INTO api_audit(owner_id, event_type, target_prefix)
VALUES ('{owner}', 'KEY_CREATED', '{prefix}');
COMMIT;
"""
    subprocess.run(['psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1'], input=sql, text=True, check=True)
    print(f'Owner: {owner}\nAPI key (shown once): {raw}')


if __name__ == '__main__':
    main()
