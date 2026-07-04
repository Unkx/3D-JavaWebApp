#!/bin/bash
# wait-for-it.sh - Wait for database to be ready

set -e

host="$1"
shift
cmd="$@"

>&2 echo "DEBUG: DB_USERNAME=[$DB_USERNAME] DB_PASSWORD_LEN=${#DB_PASSWORD} DB_PASSWORD=[$DB_PASSWORD]"

until PGPASSWORD=$DB_PASSWORD psql -h "$host" -U "$DB_USERNAME" -d "3D-JavaApp" -c '\q'; do
  >&2 echo "Postgres is unavailable - sleeping"
  sleep 2
done

>&2 echo "Postgres is up - executing command"
exec $cmd
