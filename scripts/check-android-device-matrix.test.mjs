import assert from "node:assert/strict";
import test from "node:test";

import { auditAndroidDeviceMatrix } from "./check-android-device-matrix.mjs";

const complete = {
  gradle: "minSdk = 26\ntargetSdk = 35\n",
  ci: `jobs:
  android-device:
    name: Android Device (API \${{ matrix.api-level }})
    strategy:
      matrix:
        api-level: [26, 35]
    steps:
      - uses: example/action@sha
        with:
          api-level: \${{ matrix.api-level }}
  docs:
    name: Docs
`,
  release: 'for required in Android "Android Device (API 26)" "Android Device (API 35)"; do',
  repositorySettings:
    'checks: ["Android", "Android Device (API 26)", "Android Device (API 35)"]',
};

test("accepts minSdk and targetSdk device boundaries", () => {
  const result = auditAndroidDeviceMatrix(complete);
  assert.deepEqual(result.failures, []);
  assert.deepEqual(result.apiLevels, [26, 35]);
});

test("reports matrix and release-policy drift", () => {
  const result = auditAndroidDeviceMatrix({
    ...complete,
    ci: complete.ci.replace("[26, 35]", "[35]"),
    release: complete.release.replace(' "Android Device (API 26)"', ""),
  });
  assert.ok(result.failures.some((failure) => failure.includes("must contain exactly")));
  assert.ok(result.failures.includes("Release preflight must require successful job Android Device (API 26)"));
});
