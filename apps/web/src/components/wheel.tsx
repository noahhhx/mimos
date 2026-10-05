"use client";

import { useEffect, useRef, useState } from "react";

import type { PanelWheelSegment } from "@mimos/api-client";

import { iconSize, polar, restingAngle, samePosition, segmentCenter, segmentPath, segmentTone, spinTarget } from "@/lib/wheel";

const RADIUS = 100;
const ICON_MAX = 22;

/**
 * A plugin's wheel (ADR-0017): segments in alternating fills, each icon
 * near the rim, and a fixed pointer at the top. When `spin` is set it turns
 * forward to `landing` (the one animation in the app), then calls `onStop`
 * and announces where it stopped. Otherwise it rests on `landing`, or where
 * it is when there is none.
 */
export function Wheel({
  segments,
  landing,
  spin,
  onStop,
}: {
  segments: PanelWheelSegment[];
  landing?: number;
  spin: boolean;
  onStop: () => void;
}) {
  const count = segments.length;
  const [angle, setAngle] = useState(() => (spin || landing === undefined ? 0 : restingAngle(landing, count)));
  const [turning, setTurning] = useState(false);
  const [announcement, setAnnouncement] = useState("");
  const disc = useRef<HTMLDivElement>(null);

  const landedLabel = landing === undefined ? undefined : segments[landing]?.label;

  const stop = () => {
    setTurning(false);
    setAnnouncement(landedLabel ? `The wheel stopped on ${landedLabel}.` : "");
    onStop();
  };

  useEffect(() => {
    if (landing === undefined) {
      return;
    }
    if (!spin) {
      const rest = restingAngle(landing, count);
      setAngle((current) => (samePosition(current, rest) ? current : rest));
      return;
    }
    // Resolve the current angle first: a wheel that just mounted has no
    // computed transform yet, and would jump instead of turning.
    disc.current?.getBoundingClientRect();
    setAngle((current) => spinTarget(current, landing, count));
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      stop();
    } else {
      setTurning(true);
    }
  }, [segments, landing, spin, count]);

  const size = iconSize(count, RADIUS - 20, ICON_MAX);
  const rim = RADIUS - 6 - size / 2;

  return (
    <div className="wheel">
      <div
        ref={disc}
        className={turning ? "wheel-disc turning" : "wheel-disc"}
        style={{ transform: `rotate(${angle}deg)` }}
        onTransitionEnd={(event) => {
          if (turning && event.target === event.currentTarget) {
            stop();
          }
        }}
      >
        <svg
          viewBox={`${-RADIUS} ${-RADIUS} ${RADIUS * 2} ${RADIUS * 2}`}
          role="img"
          aria-label={`A wheel of ${count}: ${segments.map((segment) => segment.label).join(", ")}`}
        >
          {segments.map((segment, index) => (
            <path
              key={index}
              className={`wheel-segment tone-${segmentTone(index, count)}`}
              d={segmentPath(index, count, RADIUS)}
            >
              <title>{segment.label}</title>
            </path>
          ))}
          {segments.map((segment, index) => {
            if (!segment.icon) {
              return null;
            }
            const center = segmentCenter(index, count);
            const at = polar(center, rim);
            return (
              <text
                key={index}
                className="wheel-icon"
                x={at.x}
                y={at.y}
                fontSize={size}
                textAnchor="middle"
                dominantBaseline="central"
                transform={`rotate(${center} ${at.x} ${at.y})`}
                aria-hidden="true"
              >
                {segment.icon}
              </text>
            );
          })}
          <circle className="wheel-rim" r={RADIUS - 0.75} />
          <circle className="wheel-hub" r={9} />
        </svg>
      </div>
      <svg className="wheel-pointer" viewBox="0 0 20 22" aria-hidden="true">
        <path d="M0 0H20L10 22Z" />
      </svg>
      <p className="visually-hidden" aria-live="polite">
        {announcement}
      </p>
    </div>
  );
}
