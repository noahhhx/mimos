import { HarnessError } from "./args.ts";

/**
 * A camera for Chromium to fake: one still frame of an EAN-13 barcode as a
 * Y4M video, the format `--use-file-for-fake-video-capture` reads (and
 * loops). Drawn here, so no image tool and no binary fixture are needed.
 */

/** The L-code of each digit; R-codes are its complement and G-codes the R-code reversed. */
const L_CODES = ["0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011"];
/** Which of L or G encodes each of the six left digits, chosen by the first digit. */
const PARITY = ["LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL"];

const complement = (bits: string) => [...bits].map((bit) => (bit === "1" ? "0" : "1")).join("");

/** An EAN-13's 95 modules, `1` for a bar. Refuses anything but 13 digits with a correct check digit. */
export function ean13Modules(barcode: string): string {
  if (!/^\d{13}$/.test(barcode)) throw new HarnessError(`an EAN-13 is 13 digits, not ${JSON.stringify(barcode)}`);
  const digits = [...barcode].map(Number);
  const sum = digits.slice(0, 12).reduce((total, digit, i) => total + digit * (i % 2 === 0 ? 1 : 3), 0);
  if ((10 - (sum % 10)) % 10 !== digits[12]) throw new HarnessError(`${barcode} has the wrong check digit`);

  const parity = PARITY[digits[0]!]!;
  const left = digits.slice(1, 7).map((digit, i) => {
    const code = L_CODES[digit]!;
    return parity[i] === "L" ? code : [...complement(code)].reverse().join("");
  });
  const right = digits.slice(7).map((digit) => complement(L_CODES[digit]!));
  return `101${left.join("")}01010${right.join("")}101`;
}

/**
 * One 640x480 grey frame (4:2:0, chroma neutral) with the barcode centred
 * at 4 px a module, black on white, in a Y4M stream at 5 frames a second.
 */
export function barcodeVideo(barcode: string): Buffer {
  const width = 640;
  const height = 480;
  const moduleWidth = 4;
  const modules = ean13Modules(barcode);
  const luma = Buffer.alloc(width * height, 235);
  const left = Math.floor((width - modules.length * moduleWidth) / 2);
  for (let y = 90; y < height - 90; y++) {
    for (let m = 0; m < modules.length; m++) {
      if (modules[m] === "1") luma.fill(16, y * width + left + m * moduleWidth, y * width + left + (m + 1) * moduleWidth);
    }
  }
  const chroma = Buffer.alloc((width / 2) * (height / 2) * 2, 128);
  return Buffer.concat([Buffer.from(`YUV4MPEG2 W${width} H${height} F5:1 Ip A1:1 C420jpeg\nFRAME\n`), luma, chroma]);
}
