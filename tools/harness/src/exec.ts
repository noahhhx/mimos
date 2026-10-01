import { spawn } from "node:child_process";

import { HarnessError } from "./args.ts";

export interface ExecResult {
  code: number;
  stdout: string;
  stderr: string;
}

export interface ExecOptions {
  cwd?: string;
  /** Also stream the child's output to our stderr (long-running builds a human may watch). */
  stream?: boolean;
  env?: NodeJS.ProcessEnv;
}

/** Runs a command to completion, capturing output; never throws on a non-zero exit. */
export function exec(command: string, args: readonly string[], options: ExecOptions = {}): Promise<ExecResult> {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd: options.cwd, env: options.env, stdio: ["ignore", "pipe", "pipe"] });
    const stdout: Buffer[] = [];
    const stderr: Buffer[] = [];
    child.stdout.on("data", (chunk: Buffer) => {
      stdout.push(chunk);
      if (options.stream) process.stderr.write(chunk);
    });
    child.stderr.on("data", (chunk: Buffer) => {
      stderr.push(chunk);
      if (options.stream) process.stderr.write(chunk);
    });
    child.on("error", (error: NodeJS.ErrnoException) => {
      reject(
        error.code === "ENOENT"
          ? new HarnessError(`${command} not found on PATH — run the harness as \`devenv shell -- harness …\``)
          : error,
      );
    });
    child.on("close", (code) => {
      resolve({
        code: code ?? 1,
        stdout: Buffer.concat(stdout).toString("utf8"),
        stderr: Buffer.concat(stderr).toString("utf8"),
      });
    });
  });
}

/** Like exec, but a non-zero exit is an error carrying the command's stderr. */
export async function execOk(command: string, args: readonly string[], options: ExecOptions = {}): Promise<string> {
  const result = await exec(command, args, options);
  if (result.code !== 0) {
    throw new HarnessError(`${command} ${args.join(" ")} failed (exit ${result.code}):\n${result.stderr.trim()}`);
  }
  return result.stdout;
}
