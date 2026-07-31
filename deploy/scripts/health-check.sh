#!/bin/bash
# ================================================================
# LoanSaaS Pro — Health Check Script
# Usage: ./deploy/scripts/health-check.sh
# ================================================================
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

ok()   { echo -e "${GREEN}[OK]${NC}   $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; FAILED=1; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }

FAILED=0

echo "================================================"
echo "  LoanSaaS Pro — System Health Check"
echo "  $(date)"
echo "================================================"

# Docker containers
echo ""
echo "--- Docker Containers ---"
for svc in loansaas-postgres loansaas-backend loansaas-frontend loansaas-nginx; do
    STATUS=$(docker inspect --format='{{.State.Health.Status}}' $svc 2>/dev/null || echo "not running")
    case $STATUS in
        healthy)     ok "$svc: healthy" ;;
        unhealthy)   fail "$svc: UNHEALTHY" ;;
        starting)    warn "$svc: starting..." ;;
        *)           fail "$svc: $STATUS" ;;
    esac
done

# PostgreSQL connectivity
echo ""
echo "--- Database ---"
if docker-compose exec -T postgres pg_isready -U loansaas -d loansaas &>/dev/null; then
    ok "PostgreSQL: accepting connections"
    SIZE=$(docker-compose exec -T postgres psql -U loansaas -d loansaas -tAc "SELECT pg_size_pretty(pg_database_size('loansaas'));" 2>/dev/null | tr -d '[:space:]')
    ok "Database size: $SIZE"
    TABLES=$(docker-compose exec -T postgres psql -U loansaas -d loansaas -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';" 2>/dev/null | tr -d '[:space:]')
    ok "Tables: $TABLES"
else
    fail "PostgreSQL: cannot connect"
fi

# Backend API
echo ""
echo "--- Backend API ---"
if curl -sf http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    ok "Spring Boot: UP"
    FLYWAY=$(curl -sf http://localhost:8080/actuator/health 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('components',{}).get('flyway',{}).get('status','unknown'))" 2>/dev/null || echo "unknown")
    ok "Flyway migrations: $FLYWAY"
else
    fail "Spring Boot: DOWN or unhealthy"
fi

# Frontend
echo ""
echo "--- Frontend ---"
if curl -sf http://localhost:3000 &>/dev/null; then
    ok "Next.js: responding"
else
    fail "Next.js: not responding"
fi

# Nginx
echo ""
echo "--- Nginx ---"
if curl -sk https://localhost -o /dev/null -w "%{http_code}" 2>/dev/null | grep -qE "200|301|302"; then
    ok "Nginx HTTPS: responding"
else
    warn "Nginx HTTPS: check SSL certificate"
fi

# Disk usage
echo ""
echo "--- System Resources ---"
DISK=$(df -h / | tail -1 | awk '{print $5}')
MEM=$(free -h | awk '/^Mem:/{print $3 "/" $2}')
ok "Disk usage: $DISK"
ok "Memory: $MEM"

echo ""
echo "================================================"
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}  All checks passed!${NC}"
else
    echo -e "${RED}  Some checks FAILED — review above${NC}"
fi
echo "================================================"
exit $FAILED
