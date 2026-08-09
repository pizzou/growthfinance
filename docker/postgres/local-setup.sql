-- ================================================================
-- Run this in PostgreSQL (psql, pgAdmin, or DBeaver) to create
-- the database and user matching application.properties defaults.
--
-- Usage (from psql as superuser, e.g. 'postgres'):
--   psql -U postgres -f local-setup.sql
-- ================================================================

-- Create the role/user
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'loansaas') THEN
        CREATE USER loansaas WITH ENCRYPTED PASSWORD 'loansaas_pass';
    ELSE
        ALTER USER loansaas WITH ENCRYPTED PASSWORD 'loansaas_pass';
    END IF;
END
$$;

-- Create the database (must run outside a transaction block in some clients)
SELECT 'CREATE DATABASE loansaas OWNER loansaas'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'loansaas')\gexec

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE loansaas TO loansaas;

\c loansaas
GRANT ALL ON SCHEMA public TO loansaas;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO loansaas;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO loansaas;
