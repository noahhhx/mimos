import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join, relative } from "node:path";
import { pathToFileURL } from "node:url";

import { load } from "js-yaml";

export const WEB_ONLY_BY_DESIGN: Readonly<Record<string, string>> = {
  getPublicRecipe: "the signed-out library pages, which serve search engines",
  listPublicRecipes: "the signed-out library pages, which serve search engines",
};

const HTTP_METHODS = ["get", "put", "post", "delete", "options", "head", "patch", "trace"];
const WEB_CLIENT = "@mimos/api-client";
const APP_CLIENT = "io.github.noahhhx.mimos.api.apis";

export interface Parity {
  operations: string[];
  web: string[];
  app: string[];
  both: string[];
  webOnly: string[];
  appOnly: string[];
  neither: string[];
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function operationIds(openapiYaml: string): string[] {
  const spec = load(openapiYaml);
  const paths = isObject(spec) && isObject(spec.paths) ? spec.paths : {};
  const ids = new Set<string>();
  for (const item of Object.values(paths)) {
    if (!isObject(item)) continue;
    for (const method of HTTP_METHODS) {
      const operation = item[method];
      if (isObject(operation) && typeof operation.operationId === "string") ids.add(operation.operationId);
    }
  }
  return [...ids].sort();
}

export function stripNonCode(source: string): string {
  let out = "";
  let i = 0;
  while (i < source.length) {
    const rest = source.slice(i, i + 3);
    const char = source[i]!;
    if (rest.startsWith("//")) {
      while (i < source.length && source[i] !== "\n") i++;
    } else if (rest.startsWith("/*")) {
      const end = source.indexOf("*/", i + 2);
      const stop = end === -1 ? source.length : end + 2;
      out += source.slice(i, stop).replace(/[^\n]/g, " ");
      i = stop;
    } else if (rest === '"""') {
      const end = source.indexOf('"""', i + 3);
      const stop = end === -1 ? source.length : end + 3;
      out += source.slice(i, stop).replace(/[^\n]/g, " ");
      i = stop;
    } else if (char === '"' || char === "'" || char === "`") {
      let j = i + 1;
      while (j < source.length && source[j] !== char && (char === "`" || source[j] !== "\n")) j += source[j] === "\\" ? 2 : 1;
      const closed = source[j] === char;
      out += char + source.slice(i + 1, j).replace(/[^\n]/g, " ") + (closed ? char : "");
      i = closed ? j + 1 : j;
    } else {
      out += char;
      i++;
    }
  }
  return out;
}

const NOT_PART_OF_A_LONGER_NAME = "(?<![\\w$])";
const NOT_A_DECLARATION = "(?<!function\\s)";
const OPTIONAL_TYPE_ARGUMENTS = "(?:<[^>()]*>\\s*)?";
const NOT_ON_A_CLASS_NAME = "(?<!\\b[A-Z]\\w*\\s*)";

function webCallPattern(operation: string): RegExp {
  return new RegExp(`${NOT_PART_OF_A_LONGER_NAME}${NOT_A_DECLARATION}${operation}\\s*${OPTIONAL_TYPE_ARGUMENTS}\\(`);
}

function appCallPattern(operation: string): RegExp {
  const memberCall = `${NOT_ON_A_CLASS_NAME}\\.\\s*${operation}\\s*[({<]`;
  const memberReference = `::\\s*${operation}\\b`;
  return new RegExp(`(?:${memberCall}|${memberReference})`);
}

export function webCalls(source: string, operations: readonly string[]): string[] {
  if (!source.includes(WEB_CLIENT)) return [];
  const code = stripNonCode(source);
  return operations.filter((operation) => webCallPattern(operation).test(code));
}

export function appCalls(source: string, operations: readonly string[]): string[] {
  if (!source.includes(APP_CLIENT)) return [];
  const code = stripNonCode(source);
  return operations.filter((operation) => appCallPattern(operation).test(code));
}

export function classify(operations: readonly string[], web: Iterable<string>, app: Iterable<string>): Parity {
  const webSet = new Set(web);
  const appSet = new Set(app);
  const sorted = [...operations].sort();
  return {
    operations: sorted,
    web: sorted.filter((operation) => webSet.has(operation)),
    app: sorted.filter((operation) => appSet.has(operation)),
    both: sorted.filter((operation) => webSet.has(operation) && appSet.has(operation)),
    webOnly: sorted.filter((operation) => webSet.has(operation) && !appSet.has(operation)),
    appOnly: sorted.filter((operation) => !webSet.has(operation) && appSet.has(operation)),
    neither: sorted.filter((operation) => !webSet.has(operation) && !appSet.has(operation)),
  };
}

const EXCLUDED = /(^|\/)(node_modules|build|generated|test|tests|commonTest|androidTest|androidUnitTest|\.next|\.gradle)(\/|$)|\.(test|spec)\.tsx?$|Test\.kt$/;

function sources(root: string, directory: string, extensions: readonly string[]): string[] {
  const base = join(root, directory);
  if (!existsSync(base)) return [];
  return readdirSync(base, { recursive: true, withFileTypes: true })
    .filter((entry) => entry.isFile() && extensions.some((extension) => entry.name.endsWith(extension)))
    .map((entry) => join(entry.parentPath, entry.name))
    .filter((file) => !EXCLUDED.test(relative(root, file)))
    .sort();
}

const WEB_SOURCES = ["apps/web/src"];
const APP_SOURCES = ["apps/mobile/shared/src/commonMain", "apps/mobile/shared/src/androidMain", "apps/mobile/androidApp/src/main"];

export function scan(root: string): Parity {
  const operations = operationIds(readFileSync(join(root, "contracts/api/openapi.yaml"), "utf8"));
  const web = new Set<string>();
  const app = new Set<string>();
  for (const directory of WEB_SOURCES) {
    for (const file of sources(root, directory, [".ts", ".tsx"])) {
      for (const operation of webCalls(readFileSync(file, "utf8"), operations)) web.add(operation);
    }
  }
  for (const directory of APP_SOURCES) {
    for (const file of sources(root, directory, [".kt"])) {
      for (const operation of appCalls(readFileSync(file, "utf8"), operations)) app.add(operation);
    }
  }
  return classify(operations, web, app);
}

export function parityProblems(parity: Parity, webOnlyByDesign: Readonly<Record<string, string>>): string[] {
  const unexplained = parity.webOnly.filter((operation) => !(operation in webOnlyByDesign));
  const stale = Object.keys(webOnlyByDesign)
    .filter((operation) => !parity.webOnly.includes(operation))
    .sort();
  return [
    ...unexplained.map((operation) => `${operation}: the web calls it and the app does not`),
    ...stale.map((operation) => `${operation}: listed as web only by design, but it is not web only`),
  ];
}

export function renderParity(parity: Parity): string {
  const group = (title: string, operations: readonly string[]): string[] => [
    "",
    `${title} (${operations.length})`,
    ...operations.map((operation) => `  ${operation}`),
  ];
  return [
    `API operations: ${parity.operations.length}. Web calls ${parity.web.length}, the app calls ${parity.app.length}.`,
    "",
    `Web only (${parity.webOnly.length})`,
    ...parity.webOnly.map((operation) =>
      operation in WEB_ONLY_BY_DESIGN ? `  ${operation}: ${WEB_ONLY_BY_DESIGN[operation]}` : `  ${operation}`,
    ),
    ...group("App only", parity.appOnly),
    ...group("Both", parity.both),
    ...group("Neither", parity.neither),
    "",
  ].join("\n");
}

if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const root = join(import.meta.dirname, "../../..");
  const parity = scan(root);
  process.stdout.write(renderParity(parity));
  if (process.argv.includes("--check")) {
    const problems = parityProblems(parity, WEB_ONLY_BY_DESIGN);
    for (const problem of problems) process.stderr.write(`${problem}\n`);
    if (problems.length > 0) {
      process.stderr.write("Build it in the app, or add it to WEB_ONLY_BY_DESIGN with the reason.\n");
      process.exit(1);
    }
  }
}
