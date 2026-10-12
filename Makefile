# Unified verification entry for local development and CI.
# Prefer `make check` or `make check-affected`. Individual gates match CI jobs.

SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c
comma := ,
E2E_PROJECTS ?= chromium,firefox,webkit
PLAYWRIGHT_INSTALL_FLAGS ?=

.PHONY: help check check-fast check-affected check-go check-proto check-web check-scale \
	check-docs check-container check-supply-chain check-e2e check-restore check-upgrade check-commits \
	check-secrets check-actions check-repository-settings generate-third-party-notices print-affected

help:
	@printf '%s\n' \
		'Verification targets (see docs/development/governance.md):' \
		'  make check            Run all CI-equivalent code, secret, artifact, and E2E gates' \
		'  make check-fast       Run go + proto + web + docs gates' \
		'  make check-affected   Run gates implied by git changes (BASE_SHA optional)' \
		'  make check-go         Go mod tidy, test, vet, build' \
		'  make check-proto      Buf lint/breaking/generate + gen drift' \
		'  make check-web        Web lint, typecheck, test, build, embed policy' \
		'  make check-scale      10k-record long-term listing, search, and integrity budgets' \
		'  make check-docs       Docker context, markdown links, terminology, whitespace, doc-sync' \
		'  make check-container  Docker image build + Compose config' \
		'  make check-supply-chain  Dependency audit, notices, SBOM, final-image vulnerability scan' \
		'  make check-e2e        Fresh-instance Playwright release journeys' \
		'  make check-restore    Isolated backup/restore recovery drill' \
		'  make check-upgrade    Latest-stable upgrade and backup-backed rollback drill' \
		'  make check-commits    Conventional Commits for BASE_SHA..HEAD' \
		'  make check-secrets    gitleaks scan (requires local gitleaks install)' \
		'  make check-actions    Verify GitHub Actions are pinned to commit SHAs' \
		'  make check-repository-settings  Audit required GitHub repository controls' \
		'  make generate-third-party-notices  Regenerate runtime license inventory' \
		'  make print-affected   Show gates for the current change set' \
		'' \
		'Environment:' \
		'  BASE_SHA / BASE_REF   Git base for breaking, doc-sync, whitespace, commits, affected'

check: check-go check-proto check-web check-scale check-docs check-secrets check-container check-supply-chain check-e2e check-restore check-upgrade

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
	pnpm --dir apps/web lint
	pnpm --dir apps/web typecheck
	pnpm --dir apps/web test
	pnpm --dir apps/web build
	node scripts/check-web-bundle.mjs
	bash scripts/check-web-assets.sh

check-scale:
	go test -tags=scale_acceptance -count=1 -timeout=70s ./integration/scale

check-docs: check-actions
	$(MAKE) --no-print-directory help >/dev/null
	node scripts/check-docker-context.mjs
	node --test scripts/check-content-copy.test.mjs
	node --test scripts/check-doc-sync.test.mjs
	node --test scripts/check-release-version.test.mjs
	node --test scripts/check-release-notes.test.mjs
	node --test scripts/compose-release-notes.test.mjs
	node --test scripts/check-repository-settings.test.mjs
	node --test scripts/affected.test.mjs
	node scripts/check-release-notes.mjs
	node scripts/check-markdown-links.mjs
	node scripts/check-content-copy.mjs
	node scripts/check-terminology.mjs
	bash scripts/check-whitespace.sh
	node scripts/check-doc-sync.mjs

check-actions:
	node scripts/check-actions-pinned.mjs

check-repository-settings:
	node scripts/check-repository-settings.mjs

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
	pnpm --dir apps/web exec playwright install $(PLAYWRIGHT_INSTALL_FLAGS) $(subst $(comma), ,$(E2E_PROJECTS))
	pnpm --dir apps/web build
	node scripts/run-e2e.mjs

check-restore:
	@report="$$(mktemp)"; \
	trap 'rm -f "$$report"' EXIT; \
	SILLAGE_RESTORE_DRILL_REPORT="$$report" go test -tags=restore_drill -count=1 ./integration/restore_drill; \
	cat "$$report"

check-upgrade:
	bash scripts/check-upgrade.sh

check-commits:
	node scripts/check-commit-msg.mjs --range

check-secrets:
	@command -v gitleaks >/dev/null 2>&1 || { echo "Install gitleaks first (brew install gitleaks)."; exit 1; }
	gitleaks detect --source . --config .gitleaks.toml --no-git --verbose --redact
