"use client";

import { useEffect, useRef, useState } from "react";

import { foodFormatsAmong, parseBarcode } from "@/lib/barcode";

/** The slice of the Barcode Detection API this reads; TypeScript's DOM types do not have it yet. */
type BarcodeDetectorInstance = { detect(source: HTMLVideoElement): Promise<{ rawValue: string }[]> };
type BarcodeDetectorClass = {
  new (options: { formats: string[] }): BarcodeDetectorInstance;
  getSupportedFormats(): Promise<string[]>;
};

const DETECT_EVERY_MS = 250;

/**
 * The food formats this browser's camera can read, or none: no Barcode
 * Detection API (Safari, Firefox), no food formats in it, or no camera access.
 */
export async function cameraFoodFormats(): Promise<string[]> {
  const Detector = detectorClass();
  if (Detector === undefined || typeof navigator.mediaDevices?.getUserMedia !== "function") {
    return [];
  }
  try {
    return foodFormatsAmong(await Detector.getSupportedFormats());
  } catch {
    return [];
  }
}

/**
 * The rear camera, looking for a food barcode about four times a second.
 * The first one it reads goes to `onDetect`, once. Every camera track
 * stops then, and when this unmounts, so the camera is on only while it
 * shows.
 */
export function BarcodeCamera({ formats, onDetect }: { formats: string[]; onDetect: (barcode: string) => void }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const onDetectRef = useRef(onDetect);
  const [problem, setProblem] = useState<string | null>(null);

  useEffect(() => {
    onDetectRef.current = onDetect;
  }, [onDetect]);

  useEffect(() => {
    const Detector = detectorClass();
    if (Detector === undefined) {
      return;
    }
    const detector = new Detector({ formats });
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
  }, [formats]);

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

function detectorClass(): BarcodeDetectorClass | undefined {
  return (window as unknown as { BarcodeDetector?: BarcodeDetectorClass }).BarcodeDetector;
}
