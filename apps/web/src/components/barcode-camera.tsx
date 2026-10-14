"use client";

import { useEffect, useRef, useState } from "react";

import { FOOD_BARCODE_FORMATS, chooseDetector, parseBarcode, type FoodBarcodeFormat } from "@/lib/barcode";

/** What the camera needs of a barcode detector, native or polyfilled. */
export type FoodBarcodeDetector = { detect(source: HTMLVideoElement): Promise<{ rawValue: string }[]> };

/** The slice of the native Barcode Detection API this reads; TypeScript's DOM types do not have it yet. */
type NativeBarcodeDetector = {
  new (options: { formats: FoodBarcodeFormat[] }): FoodBarcodeDetector;
  getSupportedFormats(): Promise<string[]>;
};

const DETECT_EVERY_MS = 250;

/**
 * A detector that reads food barcodes, or none when the browser has no
 * camera access. The browser's own `BarcodeDetector` comes first; without
 * one that reads food formats (Safari, Firefox, desktop Chrome on Linux),
 * the zxing polyfill loads now, so only an open scan dialog pays for it.
 */
export async function foodBarcodeDetector(): Promise<FoodBarcodeDetector | undefined> {
  if (typeof navigator.mediaDevices?.getUserMedia !== "function") {
    return undefined;
  }
  const Native = (window as unknown as { BarcodeDetector?: NativeBarcodeDetector }).BarcodeDetector;
  const choice = chooseDetector(await Native?.getSupportedFormats().catch(() => undefined));
  try {
    if (choice.use === "native" && Native !== undefined) {
      return new Native({ formats: choice.formats });
    }
    const { BarcodeDetector } = await import("@/lib/barcode-polyfill");
    return new BarcodeDetector({ formats: [...FOOD_BARCODE_FORMATS] });
  } catch {
    return undefined;
  }
}

/**
 * The rear camera, looking for a food barcode about four times a second.
 * The first one it reads goes to `onDetect`, once. Every camera track
 * stops then, and when this unmounts, so the camera is on only while it
 * shows.
 */
export function BarcodeCamera({
  detector,
  onDetect,
}: {
  detector: FoodBarcodeDetector;
  onDetect: (barcode: string) => void;
}) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const onDetectRef = useRef(onDetect);
  const [problem, setProblem] = useState<string | null>(null);

  useEffect(() => {
    onDetectRef.current = onDetect;
  }, [onDetect]);

  useEffect(() => {
    let stream: MediaStream | undefined;
    let timer: ReturnType<typeof setInterval> | undefined;
    let stopped = false;
    let detecting = false;

    const stop = () => {
      stopped = true;
      clearInterval(timer);
      stream?.getTracks().forEach((track) => track.stop());
    };

    const detectOnce = async (video: HTMLVideoElement) => {
      if (detecting || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) {
        return;
      }
      detecting = true;
      try {
        const found = (await detector.detect(video))
          .map((barcode) => parseBarcode(barcode.rawValue))
          .find((barcode) => barcode !== undefined);
        if (found !== undefined && !stopped) {
          stop();
          onDetectRef.current(found);
        }
      } catch {
        // A frame the detector cannot read yet; the next tick tries again.
      } finally {
        detecting = false;
      }
    };

    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: "environment" }, audio: false })
      .then(async (media) => {
        stream = media;
        const video = videoRef.current;
        if (stopped || video === null) {
          stop();
          return;
        }
        video.srcObject = media;
        await video.play();
        timer = setInterval(() => void detectOnce(video), DETECT_EVERY_MS);
      })
      .catch((error: unknown) => {
        if (!stopped) {
          setProblem(
            error instanceof DOMException && error.name === "NotAllowedError"
              ? "Mimos can't use the camera. Type the barcode instead."
              : "The camera didn't start. Type the barcode instead.",
          );
        }
      });

    return stop;
  }, [detector]);

  if (problem) {
    return (
      <p className="error-text" role="alert">
        {problem}
      </p>
    );
  }
  return (
    <video
      ref={videoRef}
      className="barcode-camera"
      aria-label="Camera, looking for a barcode"
      muted
      playsInline
    />
  );
}
