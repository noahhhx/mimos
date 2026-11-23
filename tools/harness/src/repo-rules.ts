import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

export type Violation = { rule: string; fix: string; path: string; line: number; text: string };

type Rule = {
  name: string;
  fix: string;
  applies: (path: string) => boolean;
  matches?: (line: string) => boolean;
};

const USER_FACING_COPY = [
  /^apps\/web\/src\/.*\.(tsx?|css)$/,
  /^apps\/mobile\/shared\/src\/commonMain\/.*\.kt$/,
  /^apps\/mobile\/androidApp\/src\/main\/res\/.*\.xml$/,
  /^(apps\/api|core\/[^/]+|integrations\/[^/]+)\/src\/main\/java\/.*\.java$/,
  /^core\/core-recipes\/src\/main\/resources\/library\/.*\.json$/,
  /^plugins\/[^/]+\/src\/(?!.*\.test\.ts$).*\.ts$/,
  /^deploy\/keycloak\/themes\/.*\.(css|js|ftl|properties)$/,
];

export const RULES: Rule[] = [
  {
    name: "no em-dash in user-facing copy",
    fix: "rewrite the sentence with a period, comma, colon, or semicolon (en dashes are fine for ranges)",
    applies: (path) => USER_FACING_COPY.some((pattern) => pattern.test(path)),
    matches: (line) => line.includes("\u2014"),
  },
  {
    name: "no NEXT_PUBLIC_ variables in the web app",
    fix: "read public config at runtime through src/lib/config.ts and /runtime-config.js (ADR-0012)",
    applies: (path) => path.startsWith("apps/web/"),
    matches: (line) => line.includes("NEXT_PUBLIC_"),
  },
  {
    name: "no literal colors in web components",
    fix: "use a token from src/app/globals.css (ADR-0008)",
    applies: (path) => /^apps\/web\/src\/.*\.(tsx?|css)$/.test(path) && path !== "apps/web/src/app/globals.css",
    matches: (line) => /#[0-9a-fA-F]{3,8}\b|\brgba?\(|\bhsla?\(/.test(line),
  },
  {
    name: "no source directory named target",
    fix: "rename the package: .gitignore and .dockerignore drop every target directory as build output",
    applies: (path) => /\/src\/(.+\/)?target\//.test(path),
  },
];

function blankComments(text: string): string {
  const blank = (comment: string) => comment.replace(/[^\n]/g, " ");
  return text
    .replace(/\/\*[\s\S]*?\*\//g, blank)
    .replace(/<!--[\s\S]*?-->/g, blank)
    .replace(/<#--[\s\S]*?-->/g, blank)
    .replace(/(^|[^:"'\\])\/\/.*$/gm, (_, before: string) => before);
}

export function check(path: string, text: string): Violation[] {
  const rules = RULES.filter((rule) => rule.applies(path));
  if (rules.length === 0) return [];
  const original = text.split("\n");
  const code = blankComments(text).split("\n");
  return rules.flatMap((rule) => {
    const { matches } = rule;
    if (!matches) return [{ rule: rule.name, fix: rule.fix, path, line: 1, text: path }];
    return code.flatMap((line, i) =>
      matches(line) ? [{ rule: rule.name, fix: rule.fix, path, line: i + 1, text: (original[i] ?? "").trim() }] : [],
    );
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const files = execFileSync("git", ["ls-files", "-z"], { encoding: "utf8" }).split("\0").filter(Boolean);
  const found = files.flatMap((path) => (RULES.some((rule) => rule.applies(path)) ? check(path, readFileSync(path, "utf8")) : []));
  for (const v of found) console.error(`${v.path}:${v.line}: ${v.rule}: ${v.fix}\n    ${v.text}`);
  if (found.length > 0) process.exit(1);
  console.log(`Repository rules hold across ${files.length} tracked files.`);
}
