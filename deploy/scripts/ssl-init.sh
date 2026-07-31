#!/bin/bash
# ================================================================
# Let's Encrypt SSL Certificate Setup
#
# Single-tenant deployment — one certificate for this bank's domain
# (and its www. alias):
#   ./deploy/scripts/ssl-init.sh admin@growthfinance.rw growthfinance.rw www.growthfinance.rw
# ================================================================
set -euo pipefail

if [[ "$1" == *"@"* ]]; then
    # New multi-domain form: first arg is the email, rest are domains
    EMAIL=$1; shift
    DOMAINS=("$@")
    [ ${#DOMAINS[@]} -eq 0 ] && { echo "Usage: ssl-init.sh <email> <domain1> [domain2 ...]"; exit 1; }
else
    # Legacy single-domain form: ssl-init.sh <domain> <email>
    DOMAINS=("$1" "www.$1")
    EMAIL=$2
fi

DOMAIN_ARGS=()
for d in "${DOMAINS[@]}"; do DOMAIN_ARGS+=("-d" "$d"); done

echo "[SSL] Requesting Let's Encrypt certificate for: ${DOMAINS[*]}"

# Start nginx with HTTP only first
docker-compose up -d nginx

# Request cert (all domains on ONE certificate, since nginx points every
# server block at the same /etc/nginx/ssl/cert.pem)
docker-compose run --rm certbot certonly \
    --webroot -w /var/www/certbot \
    --email "$EMAIL" \
    --agree-tos --no-eff-email \
    "${DOMAIN_ARGS[@]}"

PRIMARY_DOMAIN="${DOMAINS[0]}"

# Copy cert to nginx ssl directory
docker-compose run --rm certbot sh -c "
    cp /etc/letsencrypt/live/$PRIMARY_DOMAIN/fullchain.pem /etc/nginx/ssl/cert.pem &&
    cp /etc/letsencrypt/live/$PRIMARY_DOMAIN/privkey.pem /etc/nginx/ssl/key.pem
"

# Reload nginx
docker-compose exec nginx nginx -s reload

echo "[SSL] Certificate installed covering: ${DOMAINS[*]}"
echo "[SSL] Auto-renewal: add to cron: 0 0 * * 0 docker-compose run --rm certbot renew && docker-compose exec nginx nginx -s reload"
