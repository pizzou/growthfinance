-- ============================================================
-- V10 - CREATE WEBHOOK DELIVERY HISTORY
-- ============================================================
--
-- Stores every webhook delivery attempt.
--
-- Examples:
--
-- PAYMENT_MADE  -> SUCCESS -> HTTP 200
-- PAYMENT_MADE  -> FAILED  -> HTTP 500
-- LOAN_APPROVED -> SUCCESS -> HTTP 200
--
-- This table is used by the webhook dashboard to display
-- webhook delivery history.
-- ============================================================


CREATE TABLE IF NOT EXISTS webhook_deliveries (

    id BIGSERIAL PRIMARY KEY,


    -- =========================================================
    -- WEBHOOK ENDPOINT
    -- =========================================================

    webhook_endpoint_id BIGINT NOT NULL,


    -- =========================================================
    -- ORGANIZATION
    -- =========================================================

    organization_id BIGINT NOT NULL,


    -- =========================================================
    -- EVENT
    -- =========================================================

    event_type VARCHAR(100) NOT NULL,


    -- =========================================================
    -- REQUEST
    -- =========================================================

    payload TEXT,

    endpoint_url TEXT,


    -- =========================================================
    -- DELIVERY RESULT
    -- =========================================================

    status VARCHAR(30) NOT NULL,

    http_status INTEGER,

    response_body TEXT,

    error_message TEXT,


    -- =========================================================
    -- ATTEMPTS
    -- =========================================================

    attempt_count INTEGER NOT NULL DEFAULT 1,


    -- =========================================================
    -- TIMESTAMPS
    -- =========================================================

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    delivered_at TIMESTAMP,


    -- =========================================================
    -- FOREIGN KEYS
    -- =========================================================

    CONSTRAINT fk_webhook_delivery_endpoint
        FOREIGN KEY (webhook_endpoint_id)
        REFERENCES webhook_endpoints(id)
        ON DELETE CASCADE,


    CONSTRAINT fk_webhook_delivery_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE CASCADE
);


-- ============================================================
-- INDEXES
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_webhook_delivery_endpoint
    ON webhook_deliveries(webhook_endpoint_id);


CREATE INDEX IF NOT EXISTS idx_webhook_delivery_organization
    ON webhook_deliveries(organization_id);


CREATE INDEX IF NOT EXISTS idx_webhook_delivery_event
    ON webhook_deliveries(event_type);


CREATE INDEX IF NOT EXISTS idx_webhook_delivery_created
    ON webhook_deliveries(created_at);


CREATE INDEX IF NOT EXISTS idx_webhook_delivery_status
    ON webhook_deliveries(status);