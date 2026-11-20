import test from "node:test";
import assert from "node:assert/strict";
import { normalizePlayerSettings } from "./playerSettingsStore.js";

test("legacy web defaults migrate once to immediate in-browser playback", () => {
  const migrated = normalizePlayerSettings({
    streamAutoPlayMode: "MANUAL",
    browserExternalPlayer: "lenna"
  });

  assert.equal(migrated.streamAutoPlayMode, "FIRST_STREAM");
  assert.equal(migrated.browserExternalPlayer, "disabled");
  assert.equal(migrated.directPlayMigrationVersion, 1);
});

test("explicit choices are preserved after the direct-play migration", () => {
  const manual = normalizePlayerSettings({
    directPlayMigrationVersion: 1,
    streamAutoPlayMode: "MANUAL",
    browserExternalPlayer: "lenna"
  });

  assert.equal(manual.streamAutoPlayMode, "MANUAL");
  assert.equal(manual.browserExternalPlayer, "lenna");
});
