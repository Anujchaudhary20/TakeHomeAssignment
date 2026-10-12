#!/usr/bin/env bash
# Runs the full Postman collection against http://localhost:8080
set -e

echo "Running Accounting Ledger Service API Postman Collection..."
npx --yes newman run postman_collection.json
