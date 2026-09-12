#!/bin/bash
set -euo pipefail
# Run only on a fresh volume. psql quotes values as SQL literals, including
# passwords containing quotes; existing databases are never reset.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  --set=account_password="$ACCOUNT_DB_PASSWORD" \
  --set=workspace_password="$WORKSPACE_DB_PASSWORD" \
  --set=intelligence_password="$INTELLIGENCE_DB_PASSWORD" <<'SQL'
CREATE USER account_user WITH PASSWORD :'account_password';
CREATE USER workspace_user WITH PASSWORD :'workspace_password';
CREATE USER intelligence_user WITH PASSWORD :'intelligence_password';
CREATE DATABASE account_db OWNER account_user;
CREATE DATABASE workspace_db OWNER workspace_user;
CREATE DATABASE intelligence_db OWNER intelligence_user;
SQL
