import assert from "node:assert/strict";
import test from "node:test";

import {
  assertSupportedNativeCoordinates,
  coordinatesForConfiguration,
  renderNativeNotices,
} from "./generate-native-third-party-notices.mjs";

test("selects only the requested packaged configuration", () => {
  const lockfile = [
    "org.jetbrains.kotlin:kotlin-stdlib:2.2.21=runtimeClasspath,testRuntimeClasspath",
    "org.jetbrains.kotlin:kotlin-test:2.2.21=testRuntimeClasspath",
    "org.jetbrains.skiko:skiko-iosarm64:0.8.18=iosArm64CompileKlibraries",
    "empty=",
  ].join("\n");

  assert.deepEqual(coordinatesForConfiguration(lockfile, "runtimeClasspath"), [
    "org.jetbrains.kotlin:kotlin-stdlib:2.2.21",
  ]);
  assert.deepEqual(coordinatesForConfiguration(lockfile, "iosArm64CompileKlibraries"), [
    "org.jetbrains.skiko:skiko-iosarm64:0.8.18",
  ]);
});

test("rejects an unreviewed native dependency family", () => {
  assert.throws(
    () => assertSupportedNativeCoordinates(["example:unknown:1.0"]),
    /Unsupported native notice license coordinate/,
  );
});

test("renders deterministic package inventory and source lockfile", () => {
  const notice = renderNativeNotices({
    name: "Desktop",
    lockfilePath: "apps/native/desktopApp/gradle.lockfile",
    scope: "desktop runtime",
    coordinates: ["net.java.dev.jna:jna:5.19.1"],
    licenseText: "Apache license text",
  });

  assert.match(notice, /^Sillage Desktop - Open-source software notices/);
  assert.match(notice, /apps\/native\/desktopApp\/gradle\.lockfile/);
  assert.match(notice, /Desktop runtime dependency count: 1/);
  assert.match(notice, /- net\.java\.dev\.jna:jna:5\.19\.1/);
  assert.match(notice, /Apache license text\n$/);
});
