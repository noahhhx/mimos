import assert from "node:assert/strict";
import { test } from "node:test";

import {
  parseDevices,
  parseProbeStatus,
  parseReverseList,
  probeCommand,
  probeOk,
  probeTargets,
} from "../src/adb.ts";
import { UsageError } from "../src/args.ts";
import {
  AVD_NAME,
  avdEnv,
  avdPaths,
  emulatorArgs,
  renderAvdConfig,
  renderAvdIni,
  SYSTEM_IMAGE,
} from "../src/avd.ts";
import { parseMobileArgs } from "../src/commands/mobile.ts";
import { renderLogcatSection, renderUpSection, summarizeLogcat } from "../src/mobile-report.ts";

function ini(text: string): Map<string, string> {
  return new Map(text.trim().split("\n").map((line) => [line.slice(0, line.indexOf("=")), line.slice(line.indexOf("=") + 1)]));
}

test("mobile: an action, with an optional screenshot name only for shot", () => {
  assert.deepEqual(parseMobileArgs(["up"]), { action: "up", noBuild: false, name: undefined, run: undefined });
  assert.deepEqual(parseMobileArgs(["up", "--no-build", "--run", "latest"]), {
    action: "up",
    noBuild: true,
    name: undefined,
    run: "latest",
  });
  assert.equal(parseMobileArgs(["shot", "after-sign-in"]).name, "after-sign-in");
  assert.equal(parseMobileArgs(["down"]).action, "down");
});

test("mobile: bad invocations are usage errors", () => {
  for (const argv of [[], ["reboot"], ["logs", "x"], ["shot", "a", "b"], ["shot", "../x"], ["logs", "--no-build"], ["down", "--run", "latest"]]) {
    assert.throws(() => parseMobileArgs(argv), UsageError, argv.join(" "));
  }
});

test("the AVD is the image devenv installs, on a Pixel 6 profile, with the GPU in software", () => {
  const config = ini(renderAvdConfig());
  assert.equal(config.get("image.sysdir.1"), `system-images/android-${SYSTEM_IMAGE.api}/default/x86_64/`);
  assert.equal(config.get("hw.lcd.width"), "1080");
  assert.equal(config.get("hw.lcd.height"), "2400");
  assert.equal(config.get("hw.lcd.density"), "420");
  assert.equal(config.get("hw.gpu.mode"), "swiftshader_indirect");
  assert.equal(config.get("PlayStore.enabled"), "false");
});

test("the AVD files are the same every time and stay under .harness/", () => {
  const paths = avdPaths("/repo/.harness");
  assert.equal(renderAvdConfig(), renderAvdConfig());
  assert.deepEqual(ini(renderAvdIni(paths.avdDir)).get("path"), `/repo/.harness/android/avd/${AVD_NAME}.avd`);
  assert.equal(paths.avdIni, `/repo/.harness/android/avd/${AVD_NAME}.ini`);
  const env = avdEnv(paths, { PATH: "/bin" });
  assert.equal(env.ANDROID_AVD_HOME, "/repo/.harness/android/avd");
  assert.equal(env.ANDROID_USER_HOME, "/repo/.harness/android/user");
  assert.equal(env.PATH, "/bin");
});

test("the emulator runs headless on a fixed console port with a software GPU and no snapshot", () => {
  const args = emulatorArgs();
  assert.deepEqual(args.slice(0, 4), ["-avd", AVD_NAME, "-port", "5554"]);
  for (const flag of ["-no-window", "-no-audio", "-no-snapshot"]) assert.ok(args.includes(flag), flag);
  assert.equal(args[args.indexOf("-gpu") + 1], "swiftshader_indirect");
});

test("adb output: devices by state, and reverse rules by device port", () => {
  const devices = parseDevices("List of devices attached\nemulator-5554\tdevice\nemulator-5556\toffline\n\n");
  assert.equal(devices.get("emulator-5554"), "device");
  assert.equal(devices.get("emulator-5556"), "offline");
  assert.equal(parseDevices("List of devices attached\n\n").size, 0);

  assert.deepEqual(parseReverseList("host-17 tcp:8080 tcp:8080\nhost-17 tcp:3000 tcp:3000\nhost-17 tcp:8081 tcp:8081\n"), [3000, 8080, 8081]);
  assert.deepEqual(parseReverseList("\n"), []);
});

test("the probes cover the web, the API, and Keycloak's realm at the ports compose publishes", () => {
  const targets = probeTargets(
    { web: "http://localhost:3000", api: "http://localhost:9090", keycloak: "http://localhost:8081" },
    "mimos",
  );
  assert.deepEqual(
    targets.map(({ name, port, path }) => [name, port, path]),
    [
      ["web", 3000, "/"],
      ["api", 9090, "/actuator/health"],
      ["keycloak", 8081, "/realms/mimos/.well-known/openid-configuration"],
    ],
  );
});

test("a probe sends a real request and keeps stdin open until the response arrives", () => {
  const command = probeCommand({ name: "api", port: 8080, path: "/actuator/health" });
  assert.match(command, /GET \/actuator\/health HTTP\/1\.0/);
  assert.match(command, /sleep \d+\) \| nc -w \d+ 127\.0\.0\.1 8080/);
});

test("a probe passes only on an HTTP status below 400: a refused or closed connection prints nothing", () => {
  assert.equal(parseProbeStatus("HTTP/1.1 200 OK\n"), 200);
  assert.equal(parseProbeStatus("HTTP/1.1 503 \n"), 503);
  assert.equal(parseProbeStatus(""), undefined);
  assert.equal(parseProbeStatus("nc: connect: Connection refused\n"), undefined);
  const target = { name: "web", port: 3000, path: "/" };
  assert.ok(probeOk({ ...target, status: 200 }));
  assert.ok(probeOk({ ...target, status: 302 }));
  assert.ok(!probeOk({ ...target, status: 503 }));
  assert.ok(!probeOk({ ...target, status: undefined }));
});

test("up's summary lists each step, what the device reached, and the failure when there was one", () => {
  const steps = [
    { name: "Emulator", ok: true, detail: "already running", ms: 100 },
    { name: "Reach the stack from the device", ok: false, detail: "", ms: 2500 },
  ];
  const probes = [
    { name: "web", port: 3000, path: "/", status: 200 },
    { name: "api", port: 8080, path: "/actuator/health", status: undefined },
  ];
  const text = renderUpSection({
    argv: ["up", "--no-build"],
    steps,
    probes,
    files: ["mobile/probe.txt"],
    failure: "the device cannot reach localhost:8080",
    ms: 4200,
  });
  assert.match(text, /^## `harness mobile up --no-build` — failed \(4\.2 s\)/);
  assert.match(text, /- ✓ \*\*Emulator\*\* \(0\.1 s\): already running/);
  assert.match(text, /- ✗ \*\*Reach the stack from the device\*\* \(2\.5 s\)/);
  assert.match(text, /- ✓ `localhost:3000\/` \(web\) → 200/);
  assert.match(text, /- ✗ `localhost:8080\/actuator\/health` \(api\) → no HTTP response/);
  assert.match(text, /the device cannot reach localhost:8080/);
  assert.match(text, /- `mobile\/probe\.txt`/);

  const passed = renderUpSection({ argv: ["up"], steps: [steps[0]!], probes: [], files: [], failure: undefined, ms: 100 });
  assert.match(passed, /— passed/);
  assert.doesNotMatch(passed, /Failure:/);
});

const LOGCAT = [
  "--------- beginning of main",
  "11-16 16:25:55.674  2638  2638 I Mimos   : started",
  "11-16 16:25:55.680  2638  2638 E ashmem  : Pinning is deprecated",
  "11-16 16:25:56.001  2638  2650 F libc    : Fatal signal 11",
  "11-16 16:25:56.100  2638  2650 W Mimos   : error: not a level",
  "",
].join("\n");

test("logcat: counts lines and picks out error and fatal levels only", () => {
  const summary = summarizeLogcat(LOGCAT);
  assert.equal(summary.lines, 4);
  assert.deepEqual(summary.errors, [
    "11-16 16:25:55.680  2638  2638 E ashmem  : Pinning is deprecated",
    "11-16 16:25:56.001  2638  2650 F libc    : Fatal signal 11",
  ]);
});

test("logcat summary says which process it covers", () => {
  const summary = summarizeLogcat(LOGCAT);
  const running = renderLogcatSection({ argv: ["logs"], file: "mobile/logcat.txt", pid: "2638", appId: "app.id", summary });
  assert.match(running, /`app\.id`, pid 2638/);
  assert.match(running, /4 lines, 2 at error or fatal/);
  const stopped = renderLogcatSection({ argv: ["logs"], file: "mobile/logcat.txt", pid: undefined, appId: "app.id", summary });
  assert.match(stopped, /`app\.id` is not running/);
});
