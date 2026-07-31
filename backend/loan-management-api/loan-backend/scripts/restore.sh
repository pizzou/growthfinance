#!/bin/sh
# Restore database from backup
# Usage: ./scripts/restore.sh backups/loansaas_2026-01-01_02-00-00.sql.gz

BACKUP_FILE="$1"

if [ -z "$BACKUP_FILE" ]; then
    echo "Usage: $0 <backup_file.sql.gz>"
    echo ""
    echo "Available backups:"
    ls -lh backups/loansaas_*.sql.gz 2>/dev/null
    exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
    echo "Error: File not found: $BACKUP_FILE"
    exit 1
fi

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-loan_saas_db}"
DB_USER="${DB_USERNAME:-postgres}"

echo "WARNING: This will overwrite the current database!"
echo "Restoring from: $BACKUP_FILE"
printf "Type 'yes' to confirm: "
read CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo "Cancelled."
    exit 0
fi

echo "Restoring..."
gunzip -c "$BACKUP_FILE" | PGPASSWORD="$DB_PASSWORD" psql \
    -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME"

if [ $? -eq 0 ]; then
    echo "Restore completed successfully."
else
    echo "Restore FAILED." >&2
    exit 1
fi
```

---

## Summary — what you are creating and where
```
loan-saas-platform/
│
├── .github/                     ← New Folder (note the dot)
│   └── workflows/               ← New Folder inside .github
│       └── ci.yml               ← New File (yaml file)
│
├── scripts/                     ← New Folder
│   ├── backup.sh                ← New File (shell script)
│   └── restore.sh               ← New File (shell script)
│
└── frontend/
    └── loan-management-ui/
        └── loan-saas-frontend/
            ├── app/
            ├── package.json
            └── Dockerfile       ← New File (no extension)