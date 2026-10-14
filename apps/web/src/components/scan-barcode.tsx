"use client";

import { useEffect, useRef, useState } from "react";

import { lookUpFoodProduct, type FoodProduct } from "@mimos/api-client";

import { BarcodeCamera, cameraFoodFormats } from "@/components/barcode-camera";
import { apiClient } from "@/lib/api";
import { lookupFailure, parseBarcode } from "@/lib/barcode";

/**
 * Where the camera stands: still asking the browser, unable to scan food
 * (the typed field is all there is), looking, or stopped after a read so
 * its result can be looked up.
 */
type Camera = { state: "checking" } | { state: "none" } | { state: "scanning" | "stopped"; formats: string[] };

type Lookup = { state: "idle" } | { state: "looking" } | { state: "failed"; message: string };

/**
 * "Scan a barcode": a dialog that finds a packaged food in Open Food Facts
 * (ADR-0025) by its barcode, from the camera where the browser can read
 * one, or typed. A found product goes to `onFound` and closes the dialog;
 * an unknown one or no answer keeps it open with a message.
 */
export function ScanBarcode({ onFound }: { onFound: (product: FoodProduct) => void }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" className="button secondary" onClick={() => setOpen(true)}>
        Scan a barcode
      </button>
      {open && (
        <ScanDialog
          onClose={() => setOpen(false)}
          onFound={(product) => {
            setOpen(false);
            onFound(product);
          }}
        />
      )}
    </>
  );
}

function ScanDialog({ onClose, onFound }: { onClose: () => void; onFound: (product: FoodProduct) => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [typed, setTyped] = useState("");
  const [camera, setCamera] = useState<Camera>({ state: "checking" });
  const [lookup, setLookup] = useState<Lookup>({ state: "idle" });
  const barcode = parseBarcode(typed);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (dialog && !dialog.open) {
      dialog.showModal();
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    void cameraFoodFormats().then((formats) => {
      if (!cancelled) {
        setCamera(formats.length > 0 ? { state: "scanning", formats } : { state: "none" });
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const lookUp = async (code: string) => {
    setLookup({ state: "looking" });
    try {
      const result = await lookUpFoodProduct({ client: apiClient, path: { barcode: code } });
      if (result.data) {
        onFound(result.data);
        return;
      }
      setLookup({ state: "failed", message: lookupFailure(result.response?.status) });
    } catch {
      setLookup({ state: "failed", message: lookupFailure(undefined) });
    }
  };

  return (
    <dialog ref={dialogRef} className="scan-dialog" aria-labelledby="scan-dialog-title" onClose={onClose}>
      <div className="toolbar">
        <h2 id="scan-dialog-title">Scan a barcode</h2>
        <button type="button" className="button secondary" onClick={onClose}>
          Close
        </button>
      </div>

      {camera.state === "scanning" && (
        <>
          <BarcodeCamera
            formats={camera.formats}
            onDetect={(code) => {
              setCamera({ state: "stopped", formats: camera.formats });
              setTyped(code);
              void lookUp(code);
            }}
          />
          <p className="muted">Hold the barcode up to the camera, or type its number.</p>
        </>
      )}
      {camera.state === "stopped" && lookup.state === "failed" && (
        <p>
          <button
            type="button"
            className="button secondary"
            onClick={() => {
              setLookup({ state: "idle" });
              setCamera({ state: "scanning", formats: camera.formats });
            }}
          >
            Scan again
          </button>
        </p>
      )}

      <form
        className="field-row"
        onSubmit={(e) => {
          e.preventDefault();
          if (barcode !== undefined) {
            void lookUp(barcode);
          }
        }}
      >
        <label>
          Barcode number
          <input
            inputMode="numeric"
            autoComplete="off"
            value={typed}
            placeholder="The digits under the bars"
            onChange={(e) => {
              setTyped(e.target.value);
              setLookup({ state: "idle" });
            }}
          />
        </label>
        <button type="submit" className="button" disabled={barcode === undefined || lookup.state === "looking"}>
          {lookup.state === "looking" ? "Looking up…" : "Look up"}
        </button>
      </form>

      {typed.trim() !== "" && barcode === undefined && (
        <p className="muted">A barcode number is 8 to 14 digits.</p>
      )}
      {lookup.state === "failed" && (
        <p className="error-text" role="alert">
          {lookup.message}
        </p>
      )}
    </dialog>
  );
}
