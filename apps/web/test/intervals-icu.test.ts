import assert from "node:assert/strict";
import { describe, it } from "node:test";

import type { IntervalsIcuConnection } from "@mimos/api-client";

import { bodyFatNote, connectionSummary, hintSuggestions, syncNote } from "../src/lib/intervals-icu.ts";

const time = (iso: string) => `<${iso}>`;
const connected: IntervalsIcuConnection = {
  status: "CONNECTED",
  athleteId: "i1",
  lastSyncAt: "2026-10-12T21:14:00Z",
  hints: {},
};

describe("connectionSummary", () => {
  it("names the athlete and the last sync", () => {
    assert.equal(connectionSummary(connected, time), "Connected as i1. Last synced <2026-10-12T21:14:00Z>.");
    assert.equal(connectionSummary({ ...connected, lastSyncAt: undefined }, time), "Connected as i1. Not synced yet.");
  });

  it("says a broken connection needs the key again", () => {
    assert.equal(
      connectionSummary({ status: "BROKEN", athleteId: "i1", lastError: "Enter it again." }, time),
      "Enter it again.",
    );
    assert.match(connectionSummary({ status: "BROKEN", athleteId: "i1" }, time), /Enter it again\.$/);
    assert.equal(connectionSummary({ status: "DISCONNECTED" }, time), "Not connected.");
  });
});

describe("syncNote", () => {
  it("says when intervals.icu lets Mimos sync again, ahead of the last error", () => {
    assert.equal(
      syncNote({ ...connected, lastError: "wait", retryAt: "2026-10-12T21:30:00Z" }, time),
      "intervals.icu asked Mimos to wait. Training syncs again after <2026-10-12T21:30:00Z>.",
    );
  });

  it("passes on the last error, and says nothing when all is well or the connection is broken", () => {
    assert.equal(syncNote({ ...connected, lastError: "could not be reached" }, time), "could not be reached");
    assert.equal(syncNote(connected, time), null);
    assert.equal(syncNote({ status: "BROKEN", athleteId: "i1", lastError: "x" }, time), null);
  });
});

describe("hintSuggestions", () => {
  const hints = { weightKg: 70.8, bodyFatPercent: 12, sex: "MALE" as const, ftpWatts: 282 };

  it("suggests each value that differs from the form", () => {
    assert.deepEqual(
      hintSuggestions({ weightKg: "72", sex: "UNSPECIFIED", ftpWatts: "" }, hints).map((s) => [s.field, s.value, s.text]),
      [
        ["weightKg", "70.8", "intervals.icu has 70.8 kg."],
        ["sex", "MALE", "intervals.icu has male."],
        ["ftpWatts", "282", "intervals.icu has 282 W."],
      ],
    );
  });

  it("suggests nothing the form already holds, or without hints", () => {
    assert.deepEqual(hintSuggestions({ weightKg: "70.8", sex: "MALE", ftpWatts: "282" }, hints), []);
    assert.deepEqual(hintSuggestions({ weightKg: "72", sex: "MALE", ftpWatts: "" }, undefined), []);
    assert.deepEqual(hintSuggestions({ weightKg: "72", sex: "MALE", ftpWatts: "" }, {}), []);
  });
});

describe("bodyFatNote", () => {
  it("says synced body fat counts while the field is empty", () => {
    assert.equal(bodyFatNote(" ", { bodyFatPercent: 12 }), "Optional. While this is empty, your targets use 12% from intervals.icu.");
    assert.equal(bodyFatNote("15", { bodyFatPercent: 12 }), null);
    assert.equal(bodyFatNote("", {}), null);
    assert.equal(bodyFatNote("", undefined), null);
  });
});
