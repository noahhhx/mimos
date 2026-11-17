import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

import { load } from "js-yaml";

type Json = unknown;

const METHODS = ["get", "put", "post", "delete", "options", "head", "patch", "trace"];

function isObject(value: Json): value is Record<string, Json> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/**
 * What a released client could no longer read or call: a removed operation,
 * a removed schema property, or a removed enum value. Anything the new spec
 * only adds is fine (ADR-0029).
 */
export function removals(previous: Json, next: Json, path = "#"): string[] {
  if (Array.isArray(previous)) {
    const current = Array.isArray(next) ? next : [];
    return previous.flatMap((item, index) => removals(item, current[index], `${path}/${index}`));
  }
  if (!isObject(previous)) {
    return [];
  }
  const current = isObject(next) ? next : {};
  const found: string[] = [];
  for (const [key, value] of Object.entries(previous)) {
    const here = `${path}/${key}`;
    if (key === "enum" && Array.isArray(value)) {
      const kept = Array.isArray(current.enum) ? current.enum : [];
      for (const option of value) {
        if (!kept.includes(option)) {
          found.push(`removed enum value ${JSON.stringify(option)} at ${path}`);
        }
      }
    } else if (key === "properties" && isObject(value)) {
      const kept = isObject(current.properties) ? current.properties : {};
      for (const [name, schema] of Object.entries(value)) {
        if (name in kept) {
          found.push(...removals(schema, kept[name], `${here}/${name}`));
        } else {
          found.push(`removed property ${name} at ${path}`);
        }
      }
    } else if (key === "paths" && path === "#" && isObject(value)) {
      found.push(...removedOperations(value, isObject(current.paths) ? current.paths : {}));
      found.push(...removals(value, current.paths, here));
    } else {
      found.push(...removals(value, current[key], here));
    }
  }
  return found;
}

function removedOperations(previous: Record<string, Json>, next: Record<string, Json>): string[] {
  const found: string[] = [];
  for (const [route, item] of Object.entries(previous)) {
    if (!isObject(item)) continue;
    const kept = next[route];
    for (const method of METHODS) {
      if (method in item && !(isObject(kept) && method in kept)) {
        found.push(`removed operation ${method.toUpperCase()} ${route}`);
      }
    }
  }
  return found;
}

export function removalsBetween(previousYaml: string, nextYaml: string): string[] {
  return [...new Set(removals(load(previousYaml), load(nextYaml)))];
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [previousFile, nextFile] = process.argv.slice(2);
  if (!previousFile || !nextFile) {
    console.error("usage: contract-additive.ts <previous.yaml> <next.yaml>");
    process.exit(2);
  }
  const found = removalsBetween(readFileSync(previousFile, "utf8"), readFileSync(nextFile, "utf8"));
  if (found.length > 0) {
    console.error("The API contract is additive-only; these changes break released apps:");
    for (const line of found) console.error(`  ${line}`);
    process.exit(1);
  }
  console.log("The API contract only adds to the last release.");
}
