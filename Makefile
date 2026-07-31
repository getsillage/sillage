# Unified verification entry for local development and CI.
# Prefer `make check` or `make check-affected`. Individual gates match CI jobs.

SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c
comma := ,
E2E_PROJECTS ?= chromium,firefox,webkit
PLAYWRIGHT_INSTALL_FLAGS ?=

.PHONY: help check check-fast check-affected check-go check-proto check-web check-android check-android-device check-scale \
	check-docs check-container check-supply-chain check-e2e check-restore check-upgrade check-commits \
	check-secrets check-actions check-repository-settings generate-third-party-notices generate-android-third-party-notices print-affected

help:
	@printf '%s\n' \
		'Verification targets (see docs/development/governance.md):' \
		'  make check            Run all CI-equivalent code, secret, artifact, and E2E gates' \
		'  make check-fast       Run go + proto + web + docs gates' \
		'  make check-affected   Run gates implied by git changes (BASE_SHA optional)' \
		'  make check-go         Go mod tidy, test, vet, build' \
		'  make check-proto      Buf lint/breaking/generate + gen drift' \
		'  make check-web        Web lint, typecheck, test, build, embed policy' \
		'  make check-android    Android unit tests, lint, APKs, dependency integrity/security' \
		'  make check-android-device  Android instrumentation journeys on a connected device/emulator' \
		'  make check-scale      10k-record long-term listing, search, sync, and integrity budgets' \
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
		'  make generate-android-third-party-notices  Regenerate APK license inventory' \
		'  make print-affected   Show gates for the current change set' \
		'' \
		'Environment:' \
		'  BASE_SHA / BASE_REF   Git base for breaking, doc-sync, whitespace, commits, affected'

check: check-go check-proto check-web check-android check-scale check-docs check-secrets check-container check-supply-chain check-e2e check-restore check-upgrade

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
	node scripts/check-web-bundle.mjs
	bash scripts/check-web-assets.sh

check-android:
	node scripts/check-android-device-matrix.mjs
	cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:processReleaseMainManifest
	grep -Fq 'android:usesCleartextTraffic="false"' android/app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml
	! grep -Rq 'http://10.0.2.2:5231' android/app/build/intermediates/packaged_res/release/packageReleaseResources
	grep -Rq 'http://10.0.2.2:5231' android/app/build/intermediates/packaged_res/debug/packageDebugResources
	@aapt2_bin="$$(find "$${ANDROID_HOME:-$${ANDROID_SDK_ROOT:-}}/build-tools" -type f -name aapt2 | sort | tail -1)"; \
		release_apk="android/app/build/outputs/apk/release/app-release.apk"; \
		if ! test -f "$$release_apk"; then release_apk="android/app/build/outputs/apk/release/app-release-unsigned.apk"; fi; \
		test -n "$$aapt2_bin"; \
		test -f "$$release_apk"; \
		"$$aapt2_bin" dump resources "$$release_apk" | grep -Fq 'raw/third_party_notices'
	bash scripts/check-android-supply-chain.sh

check-android-device:
	cd android && ./gradlew :app:connectedDebugAndroidTest

check-scale:
	go test -tags=scale_acceptance -count=1 -timeout=70s ./integration/scale

check-docs: check-actions
	node scripts/check-docker-context.mjs
	node --test scripts/check-android-device-matrix.test.mjs
	node --test scripts/check-doc-sync.test.mjs
	node --test scripts/check-release-notes.test.mjs
	node --test scripts/compose-release-notes.test.mjs
	node --test scripts/check-repository-settings.test.mjs
	node --test scripts/affected.test.mjs
	node scripts/check-release-notes.mjs
	node scripts/check-markdown-links.mjs
	node scripts/check-terminology.mjs
	bash scripts/check-whitespace.sh
	node scripts/check-doc-sync.mjs

check-actions:
	node scripts/check-actions-pinned.mjs
	node scripts/check-android-device-matrix.mjs

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

generate-android-third-party-notices:
	node scripts/generate-android-third-party-notices.mjs --write

check-e2e:
	pnpm --dir web exec playwright install $(PLAYWRIGHT_INSTALL_FLAGS) $(subst $(comma), ,$(E2E_PROJECTS))
	pnpm --dir web build
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
