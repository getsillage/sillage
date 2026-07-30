# Unified verification entry for local development and CI.
# Prefer `make check` or `make check-affected`. Individual gates match CI jobs.

SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c

.PHONY: help check check-fast check-affected check-go check-proto check-web check-android \
	check-docs check-container check-supply-chain check-e2e check-restore check-commits \
	check-secrets check-actions generate-third-party-notices print-affected

help:
	@printf '%s\n' \
		'Verification targets (see docs/development/governance.md):' \
		'  make check            Run all CI-equivalent code, secret, artifact, and E2E gates' \
		'  make check-fast       Run go + proto + web + docs gates' \
		'  make check-affected   Run gates implied by git changes (BASE_SHA optional)' \
		'  make check-go         Go mod tidy, test, vet, build' \
		'  make check-proto      Buf lint/breaking/generate + gen drift' \
		'  make check-web        Web lint, typecheck, test, build, embed policy' \
		'  make check-android    Android unit tests, lint, debug APK, release manifest policy' \
		'  make check-docs       Docker context, markdown links, terminology, whitespace, doc-sync' \
		'  make check-container  Docker image build + Compose config' \
		'  make check-supply-chain  Dependency audit, notices, SBOM, final-image vulnerability scan' \
		'  make check-e2e        Fresh-instance Playwright release journeys' \
		'  make check-restore    Isolated backup/restore recovery drill' \
		'  make check-commits    Conventional Commits for BASE_SHA..HEAD' \
		'  make check-secrets    gitleaks scan (requires local gitleaks install)' \
		'  make check-actions    Verify GitHub Actions are pinned to commit SHAs' \
		'  make generate-third-party-notices  Regenerate runtime license inventory' \
		'  make print-affected   Show gates for the current change set' \
		'' \
		'Environment:' \
		'  BASE_SHA / BASE_REF   Git base for breaking, doc-sync, whitespace, commits, affected'

check: check-go check-proto check-web check-android check-docs check-secrets check-container check-supply-chain check-e2e check-restore

check-fast: check-go check-proto check-web check-docs

check-affected:
	node scripts/affected.mjs --run

print-affected:
	node scripts/affected.mjs

check-go:
	go mod tidy -diff
	go test -count=1 ./...
	go vet ./...
	go run golang.org/x/vuln/cmd/govulncheck@v1.1.4 ./...
	go build ./cmd/sillage

check-proto:
	bash scripts/check-proto.sh

check-web:
	pnpm --dir web lint
	pnpm --dir web typecheck
	pnpm --dir web test
	pnpm --dir web build
	bash scripts/check-web-assets.sh

check-android:
	cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:processReleaseMainManifest
	grep -Fq 'android:usesCleartextTraffic="false"' android/app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml

check-docs: check-actions
	node scripts/check-docker-context.mjs
	node --test scripts/compose-release-notes.test.mjs
	node scripts/check-markdown-links.mjs
	node scripts/check-terminology.mjs
	bash scripts/check-whitespace.sh
	node scripts/check-doc-sync.mjs

check-actions:
	node scripts/check-actions-pinned.mjs

check-container:
	node scripts/check-docker-context.mjs
	docker build --build-arg VERSION=dev --build-arg REVISION="$$(git rev-parse HEAD)" -t sillage:dev -f scripts/Dockerfile .
	docker compose -f scripts/compose.yaml config >/dev/null
	docker compose -f scripts/compose.yaml -f scripts/compose.build.yaml config >/dev/null

check-supply-chain: check-container
	bash scripts/check-supply-chain.sh

generate-third-party-notices:
	node scripts/generate-third-party-notices.mjs --write

check-e2e:
	pnpm --dir web exec playwright install chromium
	pnpm --dir web build
	@e2e_data="$$(mktemp -d)"; \
	server_binary="$$(mktemp)"; \
	e2e_log="$$(mktemp)"; \
	e2e_ai_log="$$(mktemp)"; \
	e2e_port="$$(node -e 'const net=require("net"); const server=net.createServer(); server.listen(0,"127.0.0.1",()=>{console.log(server.address().port); server.close();});')"; \
	e2e_ai_port="$$(node -e 'const net=require("net"); const server=net.createServer(); server.listen(0,"127.0.0.1",()=>{console.log(server.address().port); server.close();});')"; \
	go build -o "$$server_binary" ./cmd/sillage; \
	E2E_MOCK_AI_PORT="$$e2e_ai_port" node scripts/e2e-mock-ai.mjs > "$$e2e_ai_log" 2>&1 & \
	e2e_ai_pid=$$!; \
	SILLAGE_DATA="$$e2e_data" SILLAGE_ADDR=127.0.0.1 SILLAGE_PORT="$$e2e_port" "$$server_binary" > "$$e2e_log" 2>&1 & \
	server_pid=$$!; \
	trap 'kill "$$server_pid" "$$e2e_ai_pid" 2>/dev/null || true; wait "$$server_pid" "$$e2e_ai_pid" 2>/dev/null || true; rm -f "$$server_binary" "$$e2e_log" "$$e2e_ai_log"; rm -rf "$$e2e_data"' EXIT; \
	sleep 0.1; \
	if ! kill -0 "$$server_pid" 2>/dev/null; then cat "$$e2e_log"; exit 1; fi; \
	if ! kill -0 "$$e2e_ai_pid" 2>/dev/null; then cat "$$e2e_ai_log"; exit 1; fi; \
	ready=0; \
	for attempt in $$(seq 1 30); do \
	  if ! kill -0 "$$server_pid" 2>/dev/null; then cat "$$e2e_log"; exit 1; fi; \
	  if ! kill -0 "$$e2e_ai_pid" 2>/dev/null; then cat "$$e2e_ai_log"; exit 1; fi; \
	  if grep -q "Access Sillage at: http://localhost:$$e2e_port" "$$e2e_log" && \
	     curl --fail --silent "http://127.0.0.1:$$e2e_port/readyz" >/dev/null && \
	     curl --fail --silent "http://127.0.0.1:$$e2e_ai_port/healthz" >/dev/null; then ready=1; break; fi; \
	  sleep 1; \
	done; \
	if [[ "$$ready" -ne 1 ]]; then cat "$$e2e_log"; cat "$$e2e_ai_log"; echo "E2E services did not become ready on their isolated ports" >&2; exit 1; fi; \
	PLAYWRIGHT_BASE_URL="http://127.0.0.1:$$e2e_port" E2E_FRESH_INSTANCE=1 E2E_MOCK_AI_BASE_URL="http://127.0.0.1:$$e2e_ai_port" pnpm --dir web test:e2e

check-restore:
	@report="$$(mktemp)"; \
	trap 'rm -f "$$report"' EXIT; \
	SILLAGE_RESTORE_DRILL_REPORT="$$report" go test -tags=restore_drill -count=1 ./integration/restore_drill; \
	cat "$$report"

check-commits:
	node scripts/check-commit-msg.mjs --range

check-secrets:
	@command -v gitleaks >/dev/null 2>&1 || { echo "Install gitleaks first (brew install gitleaks)."; exit 1; }
	gitleaks detect --source . --config .gitleaks.toml --no-git --verbose --redact
