import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

import {
  closeAppButton,
  notRespondingApp,
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
import { copyRedacted } from "../src/evidence.ts";
import { driverDied, flowFailure, flowNames, maestroArgs, maestroFailure, parseAppRequests, flowTags } from "../src/maestro.ts";
import { redactLiterals, redactText } from "../src/redact.ts";
import { renderFlowSection, renderLogcatSection, renderUpSection, summarizeLogcat } from "../src/mobile-report.ts";

function ini(text: string): Map<string, string> {
  return new Map(text.trim().split("\n").map((line) => [line.slice(0, line.indexOf("=")), line.slice(line.indexOf("=") + 1)]));
}

test("mobile: an action, with an optional screenshot name only for shot", () => {
  assert.deepEqual(parseMobileArgs(["up"]), { action: "up", noBuild: false, build: "debug", name: undefined, run: undefined });
  assert.deepEqual(parseMobileArgs(["up", "--no-build", "--run", "latest"]), {
    action: "up",
    noBuild: true,
    build: "debug",
    name: undefined,
    run: "latest",
  });
  assert.equal(parseMobileArgs(["up", "--minified"]).build, "minified");
  assert.equal(parseMobileArgs(["up", "--minified", "--no-build"]).noBuild, true);
  assert.equal(parseMobileArgs(["shot", "after-sign-in"]).name, "after-sign-in");
  assert.equal(parseMobileArgs(["down"]).action, "down");
});

test("mobile: bad invocations are usage errors", () => {
  for (const argv of [[], ["reboot"], ["logs", "x"], ["shot", "a", "b"], ["shot", "../x"], ["logs", "--no-build"], ["flow", "a", "--minified"], ["down", "--run", "latest"]]) {
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

test("flow: a name is required, and only shot and flow take one", () => {
  assert.deepEqual(parseMobileArgs(["flow", "stay-signed-in", "--run", "latest"]), {
    action: "flow",
    noBuild: false,
    build: "debug",
    name: "stay-signed-in",
    run: "latest",
  });
  for (const argv of [["flow"], ["flow", "a", "b"], ["flow", "../x"], ["flow", "a", "--no-build"]]) {
    assert.throws(() => parseMobileArgs(argv), UsageError, argv.join(" "));
  }
});

const JUNIT_PASS = `<?xml version='1.0' encoding='UTF-8'?>
<testsuites>
  <testsuite name="Test Suite" tests="1" failures="0">
    <testcase id="connect-and-sign-in" name="connect-and-sign-in" status="SUCCESS"/>
  </testsuite>
</testsuites>`;

const JUNIT_FAIL = `<?xml version='1.0' encoding='UTF-8'?>
<testsuites>
  <testsuite name="Test Suite" tests="1" failures="1">
    <testcase id="probe-failure" name="probe-failure" status="ERROR">
      <failure>Assertion is false: &quot;Coming to the app&quot; is visible &amp; more</failure>
    </testcase>
  </testsuite>
</testsuites>`;

test("maestro: a JUnit failure is the assertion Maestro reported, and a pass has none", () => {
  assert.equal(maestroFailure(JUNIT_PASS), undefined);
  assert.equal(maestroFailure(JUNIT_FAIL), 'Assertion is false: "Coming to the app" is visible & more');
  assert.equal(maestroFailure('<testcase status="ERROR"><failure message="Element not found: &apos;Sign in&apos;"/></testcase>'), "Element not found: 'Sign in'");
  assert.match(maestroFailure("<testcase><failure></failure></testcase>") ?? "", /no message/);
});

test("maestro: the command runs one flow on the emulator, with the flow's values and every output under staging", () => {
  const env = { MIMOS_USER: "user-1", MIMOS_PASSWORD: "pw", MIMOS_WEB_ADDRESS: "http://localhost:3000", MIMOS_REFRESH_TOKEN: "rt", MIMOS_ID_TOKEN: "it" };
  const args = maestroArgs({ serial: "emulator-5554", flowFile: "/repo/apps/mobile/flows/a.yaml", env, staging: "/tmp/s" });
  assert.deepEqual(args.slice(0, 3), ["test", "--udid", "emulator-5554"]);
  assert.equal(args.at(-1), "/repo/apps/mobile/flows/a.yaml");
  assert.equal(args[args.indexOf("--format") + 1], "JUNIT");
  assert.equal(args[args.indexOf("--output") + 1], "/tmp/s/report.xml");
  assert.equal(args[args.indexOf("--debug-output") + 1], "/tmp/s/debug");
  assert.ok(args.includes("--flatten-debug-output"));
  const passed = args.flatMap((arg, index) => (args[index - 1] === "-e" ? [arg] : []));
  assert.deepEqual(passed, ["MIMOS_USER=user-1", "MIMOS_PASSWORD=pw", "MIMOS_WEB_ADDRESS=http://localhost:3000", "MIMOS_REFRESH_TOKEN=rt", "MIMOS_ID_TOKEN=it"]);
});

test("maestro: a flow's tags are those of its config, not of its steps", () => {
  assert.deepEqual(flowTags("appId: a\ntags:\n  - household\n---\n- launchApp\n"), new Set(["household"]));
  assert.deepEqual(flowTags("appId: a\ntags: [smoke, invitee]\n---\n- launchApp\n"), new Set(["smoke", "invitee"]));
  assert.deepEqual(flowTags("appId: a\n---\n- tapOn: household\n"), new Set());
  assert.deepEqual(flowTags("appId: a\ntags: [smoke]\n---\n- runFlow:\n    tags: [household]\n"), new Set(["smoke"]));
  assert.deepEqual(flowTags("appId: a\ntags: [1, household]\n---\n"), new Set(["household"]));
});

test("maestro: flows are the yaml files of the directory, sorted by name", () => {
  const dir = mkdtempSync(join(tmpdir(), "flows-"));
  try {
    for (const file of ["stay-signed-in.yaml", "connect-and-sign-in.yaml", "notes.md"]) writeFileSync(join(dir, file), "");
    assert.deepEqual(flowNames(dir), ["connect-and-sign-in", "stay-signed-in"]);
    assert.deepEqual(flowNames(join(dir, "missing")), []);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
  assert.ok(flowNames().includes("connect-and-sign-in"));
  assert.ok(flowNames().includes("stay-signed-in"));
});

const REQUEST_LOGCAT = [
  "--------- beginning of main",
  "11-18 09:39:10.100  2518  2560 I MimosRequest: requestId=3f2a9c1e-0000-4000-8000-000000000001 method=GET path=/api/v1/public/instance",
  "11-18 09:39:10.200  2518  2560 D OkHttp  : --> GET http://localhost:8080/api/v1/public/instance",
  "11-18 09:39:20.300  2518  2561 I MimosRequest: requestId=3f2a9c1e-0000-4000-8000-000000000002 method=GET path=/api/v1/me",
  "11-18 09:39:20.301  2518  2561 I MimosRequest: requestId=3f2a9c1e-0000-4000-8000-000000000002 method=GET path=/api/v1/me",
  "11-18 09:39:21.000  2518  2561 I MimosRequest: something else entirely",
].join("\n");

test("app requests: each logged request once, in order, and the lines it could not read", () => {
  const { requests, unreadable } = parseAppRequests(REQUEST_LOGCAT);
  assert.deepEqual(requests, [
    { requestId: "3f2a9c1e-0000-4000-8000-000000000001", method: "GET", path: "/api/v1/public/instance" },
    { requestId: "3f2a9c1e-0000-4000-8000-000000000002", method: "GET", path: "/api/v1/me" },
  ]);
  assert.equal(unreadable.length, 1);
  assert.deepEqual(parseAppRequests("11-18 I Other: nothing"), { requests: [], unreadable: [] });
});

const FLOW_STEPS = [
  { name: "Emulator", ok: true, detail: "emulator-5554", ms: 100 },
  { name: "Maestro", ok: false, detail: "exit 1", ms: 18500 },
];
const FLOW_REQUESTS = [{ requestId: "id-1", method: "GET", path: "/api/v1/me" }];

test("flow summary: the failed assertion leads, then steps, request IDs, and evidence", () => {
  const text = renderFlowSection({
    argv: ["flow", "connect-and-sign-in"],
    flow: "connect-and-sign-in",
    account: "user-1a2b",
    steps: FLOW_STEPS,
    requests: FLOW_REQUESTS,
    files: ["mobile/flows/connect-and-sign-in/report.xml"],
    failure: { at: 'Assertion is false: "Coming to the app" is visible', message: 'Assertion is false: "Coming to the app" is visible' },
    ms: 20000,
  });
  const lines = text.split("\n");
  assert.match(lines[0] ?? "", /^## `harness mobile flow connect-and-sign-in` — failed \(20\.0 s\)/);
  assert.match(lines[2] ?? "", /^\*\*Failed at:\*\* Assertion is false: "Coming to the app" is visible/);
  assert.ok(text.indexOf("Failed at") < text.indexOf("Emulator"));
  assert.match(text, /as `user-1a2b`/);
  assert.match(text, /- ✗ \*\*Maestro\*\* \(18\.5 s\): exit 1/);
  assert.match(text, /- `id-1` GET \/api\/v1\/me/);
  assert.match(text, /harness logs --request-id/);
  assert.match(text, /- `mobile\/flows\/connect-and-sign-in\/report\.xml`/);
});

test("flow summary: a prerequisite failure names it with its message, and a pass has no failure block", () => {
  const failed = renderFlowSection({
    argv: ["flow", "a"],
    flow: "a",
    account: undefined,
    steps: [{ name: "Reach the stack from the device", ok: false, detail: "", ms: 3000 }],
    requests: [],
    files: [],
    failure: { at: "Reach the stack from the device", message: "the device cannot reach localhost:8080" },
    ms: 3000,
  });
  assert.match(failed, /\*\*Failed at:\*\* Reach the stack from the device\n\n```\nthe device cannot reach localhost:8080\n```/);
  assert.doesNotMatch(failed, /Requests the app sent/);

  const passed = renderFlowSection({ argv: ["flow", "a"], flow: "a", account: "u", steps: [FLOW_STEPS[0]!], requests: FLOW_REQUESTS, files: [], failure: undefined, ms: 1000 });
  assert.match(passed, /— passed/);
  assert.doesNotMatch(passed, /Failed at/);
});

const SENT = [{ requestId: "8c0ba77d-1d05-4184-a608-c0b95df648ae", method: "GET", path: "/api/v1/public/instance" }];
const outcome = (input: Partial<Parameters<typeof flowFailure>[0]>) =>
  flowFailure({ junit: JUNIT_PASS, code: 0, output: "", requests: SENT, unreadable: [], requestLog: "mobile/flows/x/app-requests.log", log: "", ...input });

test("flow outcome: a passing report with the app's requests passes", () => {
  assert.equal(outcome({}), undefined);
});

test("flow outcome: the report's assertion leads, whatever Maestro's exit code", () => {
  assert.deepEqual(outcome({ junit: JUNIT_FAIL, code: 1, output: "noise" }), {
    at: 'Assertion is false: "Coming to the app" is visible & more',
    message: 'Assertion is false: "Coming to the app" is visible & more',
  });
});

test("flow outcome: with no report, a failed Maestro run shows its last output lines", () => {
  const output = Array.from({ length: 40 }, (_, n) => `line ${n + 1}`).join("\n");
  const failure = outcome({ junit: undefined, code: 1, output });
  assert.equal(failure?.at, "Maestro");
  assert.equal(failure?.message.split("\n").length, 30);
  assert.match(failure?.message ?? "", /^line 11\n[\s\S]*line 40$/);
  assert.match(outcome({ junit: undefined, code: 2, output: "" })?.message ?? "", /exited 2 with no output/);
});

test("flow outcome: a passing flow with no request IDs fails as request evidence", () => {
  const failure = outcome({ requests: [], unreadable: ["I MimosRequest: id=1"] });
  assert.equal(failure?.at, "Request evidence");
  assert.match(failure?.message ?? "", /mobile\/flows\/x\/app-requests\.log/);
  assert.match(failure?.message ?? "", /id=1/);
});

test("flow outcome: only a dead Maestro driver earns a second attempt", () => {
  const died = JUNIT_FAIL.replace(
    /<failure>.*<\/failure>/,
    "<failure>maestro.android.DeviceServerDiedException: Device server died during 'takeScreenshot' on emulator-5554</failure>",
  );
  assert.equal(driverDied(outcome({ junit: died, code: 1 })), true);
  assert.equal(driverDied(outcome({ junit: JUNIT_FAIL, code: 1 })), false);
  assert.equal(driverDied(outcome({})), false);
});

const JUNIT_UNKNOWN = JUNIT_FAIL.replace(/<failure>.*<\/failure>/, "<failure>Unknown error</failure>");

const DEVICE_OFFLINE_LOG = [
  "15:26:54.939 [ INFO] maestro.Maestro.launchApp$lambda$5: Stopping io.github.noahhhx.mimos.debug app during launch",
  "15:26:54.943 [ERROR] maestro.orchestra.Orchestra.executeCommands: [Command execution] CommandFailed: Command failed (host:transport:emulator-5554): device offline",
  "15:26:54.971 [ WARN] maestro.orchestra.debug.ArtifactsGenerator.captureStepHierarchy: Failed to capture step hierarchy",
  "maestro.android.DeviceServerDiedException: Device server died during 'deviceInfo' on emulator-5554 (128ms since last byte, connection age 56774ms): StatusRuntimeException: UNAVAILABLE",
  "\tat maestro.android.AndroidDeviceConnection.onGrpcDeath(AndroidDeviceConnection.kt:309)",
].join("\n");

test("flow outcome: an unknown error names the command that failed, from Maestro's log, and adb losing the device is a dead driver", () => {
  const failure = outcome({ junit: JUNIT_UNKNOWN, code: 1, log: DEVICE_OFFLINE_LOG });
  assert.equal(failure?.at, "Command failed (host:transport:emulator-5554): device offline");
  assert.equal(driverDied(failure), true);
  assert.deepEqual(outcome({ junit: JUNIT_UNKNOWN, code: 1, log: "" }), { at: "Unknown error", message: "Unknown error" });
});

test("flow outcome: an unknown error that is not the driver dying runs once", () => {
  const thrown = [
    "15:26:54.943 [ERROR] maestro.orchestra.Orchestra.executeCommands: [Command execution] CommandFailed: Assertion is false: \"Not sent\" is visible",
    "maestro.MaestroException$AssertionFailure: Assertion is false: \"Not sent\" is visible",
    "15:26:55.000 [ WARN] maestro.orchestra.debug.ArtifactsGenerator.captureStepHierarchy: Failed to capture step hierarchy",
    "maestro.android.DeviceServerDiedException: Device server died during 'viewHierarchy' on emulator-5554",
  ].join("\n");
  const failure = outcome({ junit: JUNIT_UNKNOWN, code: 1, log: thrown });
  assert.equal(failure?.at, 'Assertion is false: "Not sent" is visible');
  assert.equal(driverDied(failure), false);
  assert.equal(driverDied(outcome({ junit: JUNIT_FAIL, code: 1, log: DEVICE_OFFLINE_LOG })), false);
});

test("flow evidence: the account's password is gone from every copied Maestro file", () => {
  const root = mkdtempSync(join(tmpdir(), "flow-evidence-"));
  try {
    const from = join(root, "staging");
    mkdirSync(join(from, "debug", "connect-and-sign-in", "logs"), { recursive: true });
    writeFileSync(join(from, "debug", "connect-and-sign-in", "commands.json"), '{"env":{"MIMOS_PASSWORD":"mimos-test"},"text":"mimos-test"}');
    writeFileSync(join(from, "debug", "connect-and-sign-in", "logs", "maestro.log"), "Inputting text: mimos-test\n");
    const to = join(root, "run");
    const copied = copyRedacted(from, to, (text) => redactLiterals(redactText(text), ["mimos-test"]));
    assert.equal(copied.length, 2);
    for (const file of copied) assert.doesNotMatch(readFileSync(join(to, file), "utf8"), /mimos-test/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("adb: a not-responding dialog with the focus names its app", () => {
  assert.equal(notRespondingApp("  mCurrentFocus=Window{300ffe2 u0 Application Not Responding: com.android.systemui}\n"), "com.android.systemui");
  assert.equal(notRespondingApp("  mCurrentFocus=Window{6c88baf u0 com.android.launcher3/com.android.launcher3.uioverrides.QuickstepLauncher}\n"), undefined);
});

test("adb: the dialog's Close app button is found by its id and tapped at its centre", () => {
  const close = `<node index="0" text="Close app" resource-id="android:id/aerr_close" class="android.widget.Button" package="android" clickable="true" bounds="[70,1206][1010,1332]" drawing-order="1" hint="" />`;
  const wait = `<node index="1" text="Wait" resource-id="android:id/aerr_wait" class="android.widget.Button" bounds="[70,1332][1010,1458]" />`;
  assert.deepEqual(closeAppButton(`<hierarchy>${wait}${close}</hierarchy>`), { x: 540, y: 1269 });
  assert.equal(closeAppButton(`<hierarchy>${wait}</hierarchy>`), undefined);
});
