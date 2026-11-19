import { spawn } from "node:child_process";
import { closeSync, existsSync, mkdirSync, mkdtempSync, openSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, relative } from "node:path";
import { stripVTControlCharacters } from "node:util";

import { adb, parseDevices, parseReverseList, probe, probeOk, probeTargets, type Adb, type ProbeResult } from "../adb.ts";
import {
  APP_APK,
  APP_ID,
  AVD_NAME,
  AVD_SERIAL,
  avdEnv,
  avdPaths,
  BROWSER_COMMAND_LINE,
  BROWSER_COMMAND_LINE_FILE,
  BROWSER_ID,
  emulatorArgs,
  GRADLE_TASK,
  renderAvdConfig,
  renderAvdIni,
} from "../avd.ts";
import { createUser, joinHousehold } from "../accounts.ts";
import { HarnessError, parseCommandArgs, UsageError } from "../args.ts";
import { finishRun, RUN_OPTION, RUN_USAGE, startRun, type Command } from "../command.ts";
import { endpoints, FRESH_PASSWORD, REALM, REPO_ROOT } from "../config.ts";
import { copyRedacted } from "../evidence.ts";
import { fetchToken } from "./token.ts";
import { exec } from "../exec.ts";
import { flowFailure, flowNames, FLOWS_DIR, maestroArgs, flowTags, HOUSEHOLD_TAG, INVITEE_TAG, parseAppRequests, REQUEST_TAG, type AppRequest, type FlowEnv, type FlowFailure } from "../maestro.ts";
import {
  renderFlowSection,
  renderLogcatSection,
  renderShotSection,
  renderUpSection,
  summarizeLogcat,
  type StepRecord,
} from "../mobile-report.ts";
import { redactLiterals, redactText } from "../redact.ts";
import { appendSummary, writeRunFile, type Run } from "../run.ts";

/**
 * `harness mobile`: an Android emulator on this machine for agents. The device
 * reaches the compose stack through `adb reverse`, so `localhost:8080`, `:8081`
 * and `:3000` mean the same there as in a desktop browser and tokens carry the
 * issuer the API accepts (docs/harness/step-8-emulator.md).
 */

const ACTIONS = ["up", "shot", "flow", "logs", "down"] as const;
type Action = (typeof ACTIONS)[number];

export interface MobileArgs {
  action: Action;
  noBuild: boolean;
  name: string | undefined;
  run: string | undefined;
}

export function parseMobileArgs(argv: string[]): MobileArgs {
  const { values, positionals } = parseCommandArgs({
    args: argv,
    allowPositionals: true,
    options: { "no-build": { type: "boolean" }, ...RUN_OPTION },
  });
  const [action, name, ...extra] = positionals;
  if (action === undefined || !ACTIONS.includes(action as Action)) {
    throw new UsageError(`expected an action: ${ACTIONS.join(" | ")}`);
  }
  if (action === "flow" && name === undefined) throw new UsageError("flow needs a flow name");
  if (extra.length > 0 || (name !== undefined && action !== "shot" && action !== "flow")) {
    throw new UsageError(action === "shot" ? "expected at most one screenshot name" : action === "flow" ? "expected one flow name" : `${action} takes no arguments`);
  }
  if (name !== undefined && !/^[\w.-]+$/.test(name)) {
    throw new UsageError(`${action === "flow" ? "flow" : "screenshot"} name ${name} must be letters, digits, dots, dashes, and underscores`);
  }
  if (values["no-build"] && action !== "up") throw new UsageError("--no-build only applies to up");
  if (values.run !== undefined && action === "down") throw new UsageError("down records no run, so it takes no --run");
  return { action: action as Action, noBuild: values["no-build"] ?? false, name, run: values.run };
}

const BOOT_TIMEOUT_MS = 240_000;
const sleep = (ms: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms));

const paths = avdPaths(join(REPO_ROOT, ".harness"));
const toolEnv = avdEnv(paths);
const device = (): Adb => adb(AVD_SERIAL, toolEnv);

async function deviceState(): Promise<string | undefined> {
  const result = await exec("adb", ["devices"], { env: toolEnv });
  return parseDevices(result.stdout).get(AVD_SERIAL);
}

function ensureAvd(): string {
  if (existsSync(paths.configIni) && existsSync(paths.avdIni)) return `${AVD_NAME} already exists`;
  mkdirSync(paths.avdDir, { recursive: true });
  mkdirSync(paths.userHome, { recursive: true });
  writeFileSync(paths.configIni, renderAvdConfig());
  writeFileSync(paths.avdIni, renderAvdIni(paths.avdDir));
  return `created ${AVD_NAME} in ${relative(REPO_ROOT, paths.avdHome)}`;
}

function tail(path: string, lines = 20): string {
  return existsSync(path) ? readFileSync(path, "utf8").trimEnd().split("\n").slice(-lines).join("\n") : "(no log)";
}

async function bootEmulator(): Promise<string> {
  const state = await deviceState();
  if (state === "device") {
    const name = (await device().run(["emu", "avd", "name"])).stdout.split("\n")[0]?.trim();
    if (name !== AVD_NAME) {
      throw new HarnessError(`${AVD_SERIAL} is running AVD ${name ?? "?"}, not ${AVD_NAME}; stop it first`);
    }
  } else {
    if (!existsSync("/dev/kvm")) throw new HarnessError("/dev/kvm is missing: the emulator needs KVM");
    const log = openSync(paths.emulatorLog, "w");
    const child = spawn("emulator", emulatorArgs(), { detached: true, stdio: ["ignore", log, log], env: toolEnv });
    closeSync(log);
    let exited: number | null | undefined;
    child.on("exit", (code) => (exited = code));
    child.on("error", () => (exited = -1));
    child.unref();
    const deadline = Date.now() + BOOT_TIMEOUT_MS;
    while (Date.now() < deadline && exited === undefined && (await deviceState()) !== "device") await sleep(1000);
    if (exited !== undefined) {
      throw new HarnessError(`the emulator exited (${exited}) before it came up:\n${tail(paths.emulatorLog)}`);
    }
  }
  const startedAt = Date.now();
  const deadline = startedAt + BOOT_TIMEOUT_MS;
  while (Date.now() < deadline) {
    const booted = (await device().run(["shell", "getprop", "sys.boot_completed"])).stdout.trim();
    if (booted === "1") return state === "device" ? "already running" : `booted (${((Date.now() - startedAt) / 1000).toFixed(0)} s to boot_completed)`;
    await sleep(1000);
  }
  throw new HarnessError(`${AVD_SERIAL} did not finish booting in ${BOOT_TIMEOUT_MS / 1000} s:\n${tail(paths.emulatorLog)}`);
}

async function ensureBrowser(): Promise<string> {
  const installed = (await device().run(["shell", "pm", "path", BROWSER_ID])).stdout.trim() !== "";
  let detail = `${BROWSER_ID} already installed`;
  if (!installed) {
    const apk = process.env.MIMOS_ANDROID_BROWSER_APK;
    if (apk === undefined || !existsSync(apk)) {
      throw new HarnessError("MIMOS_ANDROID_BROWSER_APK is not set: run the harness as `devenv shell -- harness …`");
    }
    await device().ok(["install", "-r", apk]);
    detail = `installed ${BROWSER_ID} from the pinned APK`;
  }
  await device().shell(`echo '${BROWSER_COMMAND_LINE}' > ${BROWSER_COMMAND_LINE_FILE} && chmod 644 ${BROWSER_COMMAND_LINE_FILE}`);
  return `${detail}; first-run screens off, external intents allowed`;
}

/**
 * Reverse rules live in adbd and belong to the adb server that made them: any
 * server restart (a version-mismatched client, `adb kill-server`) drops them,
 * and the device then refuses connections to localhost. So every `up` clears
 * and re-adds them, and the probes prove they work.
 */
async function reversePorts(ports: readonly number[]): Promise<string> {
  await device().ok(["reverse", "--remove-all"]);
  for (const port of ports) await device().ok(["reverse", `tcp:${port}`, `tcp:${port}`]);
  const listed = parseReverseList(await device().ok(["reverse", "--list"]));
  const missing = ports.filter((port) => !listed.includes(port));
  if (missing.length > 0) throw new HarnessError(`adb reverse lists ${listed.join(", ") || "nothing"}; missing ${missing.join(", ")}`);
  return ports.map((port) => `tcp:${port}`).join(", ");
}

async function diagnostics(): Promise<string> {
  const [devices, reverse, version] = await Promise.all([
    exec("adb", ["devices", "-l"], { env: toolEnv }),
    device().run(["reverse", "--list"]),
    exec("adb", ["version"], { env: toolEnv }),
  ]);
  return [`$ adb version\n${version.stdout.trim()}`, `$ adb devices -l\n${devices.stdout.trim()}`, `$ adb reverse --list\n${reverse.stdout.trim() || "(none)"}`].join("\n\n");
}

async function probeStack(run: Run, file: string): Promise<ProbeResult[]> {
  const results = await Promise.all(probeTargets(endpoints(), REALM).map((target) => probe(device(), target)));
  writeRunFile(
    run,
    file,
    `${results.map((result) => `${result.name} localhost:${result.port}${result.path} → ${result.status ?? "no HTTP response"}`).join("\n")}\n`,
  );
  return results;
}

async function unreachable(run: Run, failed: readonly ProbeResult[], adbFile: string): Promise<HarnessError> {
  const detail = await diagnostics();
  writeRunFile(run, adbFile, `${detail}\n`);
  return new HarnessError(
    `the device cannot reach ${failed.map((result) => `localhost:${result.port}`).join(", ")}. Is the stack up (harness status)?\n` +
      `Rules listed but no answer: nothing on the host answers that port. No rules listed: the adb server restarted and dropped them.\n${detail}`,
  );
}

async function buildApk(run: Run): Promise<string> {
  const apk = join(REPO_ROOT, APP_APK);
  const result = await exec("./gradlew", [GRADLE_TASK, "--console=plain"], { cwd: join(REPO_ROOT, "apps/mobile"), stream: true });
  writeRunFile(run, "mobile/build.log", redactText(result.stdout + result.stderr));
  if (result.code !== 0) {
    throw new HarnessError(`./gradlew ${GRADLE_TASK} failed (exit ${result.code}); see mobile/build.log\n${(result.stdout + result.stderr).trimEnd().split("\n").slice(-20).join("\n")}`);
  }
  if (!existsSync(apk)) throw new HarnessError(`the build left no APK at ${APP_APK}`);
  return `${APP_APK} (${(statSync(apk).size / 1024 / 1024).toFixed(1)} MB)`;
}

async function installAndLaunch(): Promise<string> {
  const apk = join(REPO_ROOT, APP_APK);
  if (!existsSync(apk)) throw new HarnessError(`no APK at ${APP_APK}; run without --no-build`);
  await device().ok(["install", "-r", "-t", apk]);
  await device().run(["shell", "am", "force-stop", APP_ID]);
  await device().ok(["shell", "monkey", "-p", APP_ID, "-c", "android.intent.category.LAUNCHER", "1"]);
  for (let attempt = 0; attempt < 10; attempt++) {
    await sleep(500);
    const pid = (await device().run(["shell", "pidof", APP_ID])).stdout.trim();
    if (pid !== "") return `${APP_ID} running as pid ${pid}`;
  }
  throw new HarnessError(`${APP_ID} installed but its process is not running after launch; see harness mobile logs`);
}

function nextFile(run: Run, stem: string, extension: string): string {
  let file = `${stem}${extension}`;
  for (let n = 2; existsSync(join(run.dir, file)); n++) file = `${stem}-${n}${extension}`;
  return file;
}

async function screenshot(run: Run, name: string | undefined): Promise<string> {
  const file = name === undefined ? nextFile(run, "mobile/shot", ".png") : `mobile/${name}.png`;
  mkdirSync(join(run.dir, "mobile"), { recursive: true });
  const remote = "/data/local/tmp/harness-shot.png";
  await device().ok(["shell", "screencap", "-p", remote]);
  await device().ok(["pull", remote, join(run.dir, file)]);
  await device().run(["shell", "rm", remote]);
  return file;
}

async function requireBooted(): Promise<void> {
  if ((await deviceState()) !== "device") throw new HarnessError(`no emulator is running: harness mobile up`);
}

function stepper(steps: StepRecord[]): (name: string, action: () => Promise<string> | string) => Promise<void> {
  return async (name, action) => {
    const began = Date.now();
    try {
      steps.push({ name, ok: true, detail: await action(), ms: Date.now() - began });
    } catch (error) {
      steps.push({ name, ok: false, detail: "", ms: Date.now() - began });
      throw error;
    }
  };
}

async function up(args: MobileArgs, argv: string[]): Promise<number> {
  const startedAt = new Date();
  const run = startRun("mobile-up", startedAt, args.run);
  const steps: StepRecord[] = [];
  const files: string[] = [];
  let probes: ProbeResult[] = [];
  let failure: string | undefined;

  const step = stepper(steps);

  try {
    await step("AVD", ensureAvd);
    await step("Emulator", bootEmulator);
    await step("Browser", ensureBrowser);
    await step("Reverse ports", () => reversePorts(probeTargets(endpoints(), REALM).map((target) => target.port)));
    await step("Reach the stack from the device", async () => {
      probes = await probeStack(run, "mobile/probe.txt");
      files.push("mobile/probe.txt");
      const failed = probes.filter((result) => !probeOk(result));
      if (failed.length > 0) throw await unreachable(run, failed, "mobile/adb.txt");
      return `${probes.length} of ${probes.length} answered`;
    });
    if (args.noBuild) {
      steps.push({ name: "Build", ok: true, detail: "skipped (--no-build)", ms: 0 });
    } else {
      await step("Build", () => buildApk(run));
      files.push("mobile/build.log");
    }
    await step("Install and launch", installAndLaunch);
    await sleep(2000);
    await step("Screenshot", async () => {
      const file = await screenshot(run, "up");
      files.push(file);
      return file;
    });
  } catch (error) {
    failure = redactText(error instanceof Error ? error.message : String(error));
    if (existsSync(join(run.dir, "mobile/adb.txt"))) files.push("mobile/adb.txt");
  }

  files.push(...(existsSync(paths.emulatorLog) ? [copyEmulatorLog(run)] : []));
  const ms = Date.now() - startedAt.getTime();
  appendSummary(run, renderUpSection({ argv, steps, probes, files, failure, ms }));
  const exitCode = failure === undefined ? 0 : 1;
  await finishRun(run, ["mobile", ...argv], startedAt, exitCode);
  process.stdout.write(failure === undefined ? `up: ${AVD_SERIAL} running ${APP_ID}\n` : `up failed: ${failure.split("\n")[0]}\n`);
  process.stdout.write(`summary: ${relative(process.cwd(), join(run.dir, "summary.md"))}\n`);
  return exitCode;
}

const errorText = (error: unknown): string => (error instanceof Error ? error.message : String(error));

async function flow(args: MobileArgs, argv: string[]): Promise<number> {
  const name = args.name ?? "";
  if (!flowNames().includes(name)) {
    throw new UsageError(`no flow ${name}; apps/mobile/flows/ has: ${flowNames().join(" | ") || "none"}`);
  }
  const startedAt = new Date();
  const run = startRun(`mobile-flow-${name}`, startedAt, args.run);
  const dir = nextFile(run, `mobile/flows/${name}`, "");
  const target = join(run.dir, dir);
  const steps: StepRecord[] = [];
  const files: string[] = [];
  const step = stepper(steps);
  const secrets = [FRESH_PASSWORD];
  const clean = (text: string): string => redactLiterals(redactText(text), secrets);
  let account = "";
  let requests: AppRequest[] = [];
  let failure: FlowFailure | undefined;
  let maestro: { code: number; output: string } | undefined;
  const staging = mkdtempSync(join(tmpdir(), "harness-flow-"));

  try {
    try {
      await step("Emulator", async () => {
        await requireBooted();
        return AVD_SERIAL;
      });
      await step("App installed", async () => {
        if ((await device().run(["shell", "pm", "path", APP_ID])).stdout.trim() === "") {
          throw new HarnessError(`${APP_ID} is not installed: harness mobile up`);
        }
        return APP_ID;
      });
      await step("Reverse ports", () => reversePorts(probeTargets(endpoints(), REALM).map((each) => each.port)));
      await step("Reach the stack from the device", async () => {
        const probes = await probeStack(run, `${dir}/probe.txt`);
        files.push(`${dir}/probe.txt`);
        const failed = probes.filter((result) => !probeOk(result));
        if (failed.length > 0) throw await unreachable(run, failed, `${dir}/adb.txt`);
        return `${probes.length} of ${probes.length} answered`;
      });
      await step("Clear app and browser data", async () => {
        for (const id of [APP_ID, BROWSER_ID]) await device().ok(["shell", "pm", "clear", id]);
        return `${APP_ID}, ${BROWSER_ID}; ${await ensureBrowser()}`;
      });
      const flowFile = join(FLOWS_DIR, `${name}.yaml`);
      const tags = flowTags(readFileSync(flowFile, "utf8"));
      await step("Account", async () => {
        account = await createUser();
        return `${account} (fresh)`;
      });
      let partner: string | undefined;
      if (tags.has(HOUSEHOLD_TAG)) {
        await step("Household", async () => {
          partner = await createUser();
          await joinHousehold(await fetchToken(partner), await fetchToken(account));
          return `${account} joined ${partner}'s household`;
        });
      }
      let invitee: string | undefined;
      if (tags.has(INVITEE_TAG)) {
        await step("Invitee", async () => {
          invitee = await createUser();
          return `${invitee} (fresh, on its own)`;
        });
      }
      await device().ok(["logcat", "-c"]);

      const env: FlowEnv = {
        MIMOS_USER: account,
        MIMOS_PASSWORD: FRESH_PASSWORD,
        MIMOS_WEB_ADDRESS: endpoints().web,
        ...(partner === undefined ? {} : { MIMOS_PARTNER: partner }),
        ...(invitee === undefined ? {} : { MIMOS_INVITEE: invitee }),
      };
      const began = Date.now();
      const result = await exec("maestro", maestroArgs({ serial: AVD_SERIAL, flowFile, env, staging }), { env: toolEnv }).catch((error: unknown) => {
        steps.push({ name: "Maestro", ok: false, detail: "", ms: Date.now() - began });
        throw error;
      });
      maestro = { code: result.code, output: result.stdout + result.stderr };
      steps.push({ name: "Maestro", ok: result.code === 0, detail: `${relative(REPO_ROOT, flowFile)}, exit ${result.code}`, ms: Date.now() - began });
    } catch (error) {
      failure = { at: steps.findLast((each) => !each.ok)?.name ?? "Setup", message: clean(errorText(error)) };
      if (existsSync(join(run.dir, dir, "adb.txt"))) files.push(`${dir}/adb.txt`);
    }

    if (maestro !== undefined) {
      const rewrite = (text: string): string => clean(text.replaceAll(staging, relative(REPO_ROOT, target)));
      files.push(...copyRedacted(staging, target, rewrite).map((file) => `${dir}/${file}`));
      writeRunFile(run, `${dir}/output.log`, rewrite(stripVTControlCharacters(maestro.output)));
      files.push(`${dir}/output.log`);
      const reportPath = join(staging, "report.xml");
      const logcat = await device()
        .run(["logcat", "-d", "-v", "threadtime", "-s", `${REQUEST_TAG}:I`])
        .then((result) => result.stdout)
        .catch(() => "");
      writeRunFile(run, `${dir}/app-requests.log`, clean(logcat));
      files.push(`${dir}/app-requests.log`);
      const parsed = parseAppRequests(clean(logcat));
      requests = parsed.requests;
      failure = flowFailure({
        junit: existsSync(reportPath) ? clean(readFileSync(reportPath, "utf8")) : undefined,
        code: maestro.code,
        output: rewrite(stripVTControlCharacters(maestro.output)),
        requests: parsed.requests,
        unreadable: parsed.unreadable,
        requestLog: `${dir}/app-requests.log`,
      });
    }
  } finally {
    rmSync(staging, { recursive: true, force: true });
  }

  const ms = Date.now() - startedAt.getTime();
  appendSummary(run, clean(renderFlowSection({ argv, flow: name, account: account || undefined, steps, requests, files, failure, ms })));
  const exitCode = failure === undefined ? 0 : 1;
  await finishRun(run, ["mobile", ...argv], startedAt, exitCode);
  process.stdout.write(failure === undefined ? `flow ${name}: passed (${requests.length} requests)\n` : `flow ${name} failed at ${failure.at}\n`);
  process.stdout.write(`summary: ${relative(process.cwd(), join(run.dir, "summary.md"))}\n`);
  return exitCode;
}

function copyEmulatorLog(run: Run): string {
  writeRunFile(run, "mobile/emulator.log", redactText(readFileSync(paths.emulatorLog, "utf8")));
  return "mobile/emulator.log";
}

async function shot(args: MobileArgs, argv: string[]): Promise<number> {
  const startedAt = new Date();
  await requireBooted();
  const run = startRun("mobile-shot", startedAt, args.run);
  const file = await screenshot(run, args.name);
  appendSummary(run, renderShotSection(argv, file));
  await finishRun(run, ["mobile", ...argv], startedAt, 0);
  process.stdout.write(`${join(run.dir, file)}\n`);
  return 0;
}

async function logs(args: MobileArgs, argv: string[]): Promise<number> {
  const startedAt = new Date();
  await requireBooted();
  const run = startRun("mobile-logs", startedAt, args.run);
  const pid = (await device().run(["shell", "pidof", APP_ID])).stdout.trim().split(/\s+/)[0] || undefined;
  const dump = await device().ok(pid === undefined ? ["logcat", "-d", "-v", "threadtime"] : ["logcat", "-d", "-v", "threadtime", `--pid=${pid}`]);
  const text = redactText(pid === undefined ? dump.split("\n").filter((line) => line.includes(APP_ID)).join("\n") : dump);
  const file = nextFile(run, "mobile/logcat", ".txt");
  writeRunFile(run, file, text.endsWith("\n") || text === "" ? text : `${text}\n`);
  appendSummary(run, renderLogcatSection({ argv, file, pid, appId: APP_ID, summary: summarizeLogcat(text) }));
  await finishRun(run, ["mobile", ...argv], startedAt, 0);
  process.stdout.write(`${join(run.dir, file)}\n`);
  return 0;
}

async function down(): Promise<number> {
  if ((await deviceState()) === undefined) {
    process.stdout.write("no emulator running\n");
    return 0;
  }
  await device().run(["emu", "kill"]);
  const deadline = Date.now() + 60_000;
  while (Date.now() < deadline) {
    if ((await deviceState()) === undefined) {
      process.stdout.write(`stopped ${AVD_SERIAL}\n`);
      return 0;
    }
    await sleep(500);
  }
  throw new HarnessError(`${AVD_SERIAL} is still listed 60 s after \`adb emu kill\``);
}

export const mobileCommand: Command = {
  name: "mobile",
  summary: "Boot an Android emulator, install the app, and read its evidence",
  usage: `harness mobile up [--no-build] | shot [name] | flow <name> | logs | down

  up [--no-build]  create the AVD ${AVD_NAME} under .harness/android/ if missing, boot it headless (KVM,
                   software GPU), install the pinned browser (Cromite) once, forward 8080, 8081, and
                   3000 with \`adb reverse\`, prove from the device that each answers an HTTP request,
                   build the debug APK (./gradlew ${GRADLE_TASK} in apps/mobile; --no-build reuses
                   the last one), install it, launch ${APP_ID}, and take a screenshot.
                   Running it again converges: a booted device is kept, the reverse rules are
                   re-made, the app is reinstalled and relaunched.
  shot [name]      save a screenshot to mobile/<name>.png (default shot.png, then shot-2.png, …)
  flow <name>      run apps/mobile/flows/<name>.yaml with Maestro on the installed app:
                   recheck the reverse rules and what the device reaches, clear the app's and the
                   browser's data, create a fresh account (harness user create), and run the flow
                   with MIMOS_USER, MIMOS_PASSWORD, and MIMOS_WEB_ADDRESS. A flow whose config has
                   \`tags: [household]\` signs in as one of two fresh accounts in one household; the
                   other's name, as the app shows it, is MIMOS_PARTNER. \`tags: [invitee]\` adds
                   a second fresh account on its own, MIMOS_INVITEE, with the same password,
                   for a flow that invites someone. Builds and installs
                   nothing, so run \`up\` first. Maestro's output is captured, not streamed (it
                   echoes typed values); evidence is redacted, the password by value, into
                   mobile/flows/<name>/: report.xml, screenshots, Maestro's logs, and the
                   requests the app sent (app-requests.log). summary.md starts with the failing
                   assertion and lists each request ID for \`harness logs --request-id\`. A
                   passing flow with no request IDs fails.
  logs             write logcat for the app's process to mobile/logcat.txt, redacted; summary.md
                   lists the lines at error level. Without a running app, the lines naming it.
  down             stop the emulator; fine when none is running.
${RUN_USAGE}

Needs the stack up (harness up) and the devenv shell (emulator, adb, and the browser APK
come from it). Evidence: .harness/runs/<run>/mobile/, summary.md first. Exit code: 0 when
the command did what it says, 1 otherwise.`,
  async run(argv) {
    const args = parseMobileArgs(argv);
    switch (args.action) {
      case "up":
        return up(args, argv);
      case "shot":
        return shot(args, argv);
      case "flow":
        return flow(args, argv);
      case "logs":
        return logs(args, argv);
      case "down":
        return down();
    }
  },
};
