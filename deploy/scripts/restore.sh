#!/bin/bash
# ================================================================
# PostgreSQL Restore Script
# Usage: ./deploy/scripts/restore.sh ./backups/loansaas_20240101_020000.sql.gz
#     or ./deploy/scripts/restore.sh ./backups/loansaas_20240101_020000.sql.gz.enc
# ================================================================
set -euo pipefail

BACKUP_FILE=${1:?Usage: restore.sh <backup_file.sql.gz[.enc]>}
[ -f "$BACKUP_FILE" ] || { echo "File not found: $BACKUP_FILE"; exit 1; }

echo "[RESTORE] WARNING: This will OVERWRITE the current database!"
read -rp "Type 'yes' to continue: " confirm
[ "$confirm" = "yes" ] || { echo "Cancelled."; exit 0; }

INPUT_FILE="$BACKUP_FILE"
if [[ "$BACKUP_FILE" == *.enc ]]; then
    [ -n "${BACKUP_ENCRYPTION_KEY:-}" ] || { echo "BACKUP_ENCRYPTION_KEY must be set to restore an encrypted backup"; exit 1; }
    DECRYPTED="${BACKUP_FILE%.enc}"
    echo "[RESTORE] Decrypting..."
    openssl enc -d -aes-256-cbc -pbkdf2 -pass env:BACKUP_ENCRYPTION_KEY -in "$BACKUP_FILE" -out "$DECRYPTED"
    INPUT_FILE="$DECRYPTED"
fi

echo "[RESTORE] Stopping backend..."
docker-compose stop backend

echo "[RESTORE] Restoring from: $INPUT_FILE"
gunzip -c "$INPUT_FILE" | docker-compose exec -T postgres psql -U loansaas loansaas

if [[ "$BACKUP_FILE" == *.enc ]]; then
    rm -f "$INPUT_FILE"   # don't leave a decrypted PII dump sitting on disk after restore
fi

echo "[RESTORE] Restarting backend..."
docker-compose start backend
echo "[RESTORE] Restore complete!"
