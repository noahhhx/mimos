import { join } from "node:path";

/**
 * The virtual device `harness mobile` boots. Its files are written here, from a
 * fixed hardware profile, rather than made with avdmanager: that is
 * deterministic, needs no Java, and avdmanager still fails to read the SDK's
 * devices.xml under a relocated ANDROID_USER_HOME.
 */

export const AVD_NAME = "mimos-pixel";
export const AVD_SERIAL = "emulator-5554";
export const EMULATOR_PORT = 5554;

/** The only image devenv installs (androidenv, AOSP `default`, at targetSdk). */
export const SYSTEM_IMAGE = { api: 36, tag: "default", abi: "x86_64" } as const;

/** A Pixel 6: 1080x2400 at 420 dpi. */
export const HARDWARE = { width: 1080, height: 2400, density: 420, ramMb: 2048, cores: 4, heapMb: 228 } as const;

/** The release's debug build (`applicationIdSuffix = ".debug"` in androidApp/build.gradle.kts). */
export const APP_ID = "io.github.noahhhx.mimos.debug";
export const APP_APK = "apps/mobile/androidApp/build/outputs/apk/debug/androidApp-debug.apk";
export const GRADLE_TASK = ":androidApp:assembleDebug";

/** The browser sign-in runs in (Cromite, fixed by hash in devenv.nix). */
export const BROWSER_ID = "org.cromite.cromite";
export const BROWSER_COMMAND_LINE_FILE = "/data/local/tmp/chrome-command-line";
/** Skips Chromium's first-run screens and default-browser prompt; the first word is the (ignored) program name. */
export const BROWSER_COMMAND_LINE = "_ --disable-fre --no-default-browser-check --no-first-run";

export interface AvdPaths {
  /** ANDROID_USER_HOME: the emulator's and adb's own state, kept out of ~/.android. */
  userHome: string;
  /** ANDROID_AVD_HOME: holds `<name>.ini` and `<name>.avd/`. */
  avdHome: string;
  avdDir: string;
  avdIni: string;
  configIni: string;
  emulatorLog: string;
}

export function avdPaths(harnessDir: string, name = AVD_NAME): AvdPaths {
  const root = join(harnessDir, "android");
  const avdHome = join(root, "avd");
  const avdDir = join(avdHome, `${name}.avd`);
  return {
    userHome: join(root, "user"),
    avdHome,
    avdDir,
    avdIni: join(avdHome, `${name}.ini`),
    configIni: join(avdDir, "config.ini"),
    emulatorLog: join(root, "emulator.log"),
  };
}

function iniLines(entries: Readonly<Record<string, string | number>>): string {
  return `${Object.entries(entries)
    .map(([key, value]) => `${key}=${value}`)
    .join("\n")}\n`;
}

/** `<name>.ini`: where the emulator finds the AVD, and which platform it targets. */
export function renderAvdIni(avdDir: string): string {
  return iniLines({
    "avd.ini.encoding": "UTF-8",
    path: avdDir,
    target: `android-${SYSTEM_IMAGE.api}`,
  });
}

/** `<name>.avd/config.ini`: the hardware profile. Headless, so the GPU is software-rendered. */
export function renderAvdConfig(name = AVD_NAME): string {
  return iniLines({
    "AvdId": name,
    "avd.ini.displayname": name,
    "PlayStore.enabled": "false",
    "abi.type": SYSTEM_IMAGE.abi,
    "hw.cpu.arch": SYSTEM_IMAGE.abi,
    "hw.cpu.ncore": HARDWARE.cores,
    "hw.ramSize": HARDWARE.ramMb,
    "vm.heapSize": HARDWARE.heapMb,
    "hw.lcd.width": HARDWARE.width,
    "hw.lcd.height": HARDWARE.height,
    "hw.lcd.density": HARDWARE.density,
    "hw.device.name": "pixel_6",
    "hw.device.manufacturer": "Google",
    "hw.gpu.enabled": "yes",
    "hw.gpu.mode": "swiftshader_indirect",
    "hw.keyboard": "yes",
    "hw.camera.back": "emulated",
    "hw.camera.front": "none",
    "hw.audioInput": "no",
    "hw.audioOutput": "no",
    "hw.sdCard": "no",
    "disk.dataPartition.size": "8G",
    "image.sysdir.1": `system-images/android-${SYSTEM_IMAGE.api}/${SYSTEM_IMAGE.tag}/${SYSTEM_IMAGE.abi}/`,
    "tag.id": SYSTEM_IMAGE.tag,
    "tag.display": "Default Android System Image",
    target: `android-${SYSTEM_IMAGE.api}`,
  });
}

/** Headless, KVM, software GPU, and a clean boot every time (userdata still persists, so installs survive). */
export function emulatorArgs(name = AVD_NAME): string[] {
  return [
    "-avd",
    name,
    "-port",
    String(EMULATOR_PORT),
    "-no-window",
    "-no-audio",
    "-gpu",
    "swiftshader_indirect",
    "-no-snapshot",
    "-no-boot-anim",
  ];
}

/** The environment that points the SDK tools at the harness's AVD home. */
export function avdEnv(paths: AvdPaths, env: NodeJS.ProcessEnv = process.env): NodeJS.ProcessEnv {
  return { ...env, ANDROID_AVD_HOME: paths.avdHome, ANDROID_USER_HOME: paths.userHome };
}
