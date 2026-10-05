import assert from "node:assert/strict";
import { describe, it } from "node:test";

import {
  iconSize,
  polar,
  restingAngle,
  samePosition,
  segmentCenter,
  segmentPath,
  segmentTone,
  spinTarget,
  SPIN_TURNS,
} from "../src/lib/wheel.ts";

/** Which segment sits under the pointer when the wheel is rotated by `angle`. */
function underPointer(angle: number, count: number): number {
  const sweep = 360 / count;
  const atPointer = (((-angle % 360) + 360) % 360) / sweep;
  return Math.floor(atPointer);
}

/** How far the pointer is from the middle of the segment under it, in degrees. */
function offCenter(angle: number, landing: number, count: number): number {
  const apart = (((angle + segmentCenter(landing, count)) % 360) + 360) % 360;
  return Math.min(apart, 360 - apart);
}

describe("restingAngle", () => {
  it("puts the landed segment's middle under the pointer", () => {
    for (const count of [2, 3, 7, 12, 60]) {
      for (let landing = 0; landing < count; landing++) {
        const angle = restingAngle(landing, count);
        assert.ok(angle >= 0 && angle < 360, `${angle} in [0, 360)`);
        assert.equal(underPointer(angle, count), landing, `${count} segments, landing ${landing}`);
        assert.ok(offCenter(angle, landing, count) < 1e-9);
      }
    }
  });

  it("turns segment 0 of four back by half a segment", () => {
    assert.equal(restingAngle(0, 4), 315);
    assert.equal(restingAngle(1, 4), 225);
  });
});

describe("spinTarget", () => {
  it("lands on the segment it was given, from any starting angle", () => {
    for (const current of [0, 17.5, 359, 1800, 2245.3]) {
      for (const [landing, count] of [
        [0, 2],
        [1, 2],
        [4, 7],
        [59, 60],
        [33, 60],
      ]) {
        const target = spinTarget(current, landing, count);
        assert.equal(underPointer(target, count), landing, `from ${current}, ${landing} of ${count}`);
        assert.ok(offCenter(target, landing, count) < 1e-6);
      }
    }
  });

  it("always turns forward, by at least the full turns and less than one more", () => {
    for (const current of [0, 90, 315, 1800, 3779.25]) {
      for (let landing = 0; landing < 9; landing++) {
        const travel = spinTarget(current, landing, 9) - current;
        assert.ok(travel >= SPIN_TURNS * 360, `travel ${travel} from ${current}`);
        assert.ok(travel < (SPIN_TURNS + 1) * 360, `travel ${travel} from ${current}`);
      }
    }
  });

  it("spins the full turns again when it lands where it already is", () => {
    const current = restingAngle(2, 5);
    assert.equal(spinTarget(current, 2, 5), current + SPIN_TURNS * 360);
  });

  it("takes the number of turns", () => {
    assert.equal(spinTarget(0, 0, 4, 2), 720 + 315);
  });

  it("chains: a second spin starts where the first stopped and still lands", () => {
    const first = spinTarget(0, 3, 12);
    const second = spinTarget(first, 8, 12);
    assert.ok(second > first);
    assert.equal(underPointer(second, 12), 8);
  });
});

describe("samePosition", () => {
  it("compares angles a whole number of turns apart as equal", () => {
    assert.ok(samePosition(spinTarget(0, 1, 6), restingAngle(1, 6)));
    assert.ok(samePosition(0, 720));
    assert.ok(!samePosition(10, 20));
  });
});

describe("segmentTone", () => {
  it("alternates two fills, so neighbors never match", () => {
    for (const count of [2, 3, 4, 5, 11, 60]) {
      for (let index = 0; index < count; index++) {
        const next = (index + 1) % count;
        assert.notEqual(segmentTone(index, count), segmentTone(next, count), `${index} and ${next} of ${count}`);
      }
    }
  });

  it("uses the third fill only for the last segment of an odd count", () => {
    assert.deepEqual(
      [0, 1, 2, 3, 4].map((index) => segmentTone(index, 5)),
      [0, 1, 0, 1, 2],
    );
    assert.deepEqual(
      [0, 1, 2, 3].map((index) => segmentTone(index, 4)),
      [0, 1, 0, 1],
    );
  });
});

describe("segment geometry", () => {
  it("measures angles clockwise from the top", () => {
    const top = polar(0, 10);
    const right = polar(90, 10);
    assert.ok(Math.abs(top.x) < 1e-9 && Math.abs(top.y + 10) < 1e-9);
    assert.ok(Math.abs(right.x - 10) < 1e-9 && Math.abs(right.y) < 1e-9);
  });

  it("rounds points to thousandths, so server and browser markup match", () => {
    assert.deepEqual(polar(63, 91.07), { x: 81.144, y: -41.345 });
    assert.deepEqual(polar(180, 50), { x: 0, y: 50 });
  });

  it("draws the first of four segments from the top to the right", () => {
    assert.equal(segmentPath(0, 4, 100), "M0 0L0 -100A100 100 0 0 1 100 0Z");
  });

  it("shrinks icons as segments narrow", () => {
    assert.equal(iconSize(2, 80, 22), 22);
    assert.ok(iconSize(60, 80, 22) < 6);
  });
});
