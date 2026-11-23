import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

import { load } from "js-yaml";

type Json = unknown;

const METHODS = ["get", "put", "post", "delete", "options", "head", "patch", "trace"];

function isObject(value: Json): value is Record<string, Json> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

const COMPOSITIONS = ["oneOf", "anyOf", "allOf"];
const NOT_CONTRACT = ["example", "examples"];

function refOf(item: Json): string | undefined {
  return isObject(item) && typeof item.$ref === "string" ? item.$ref : undefined;
}

function parameterKey(item: Json, index: number): string {
  const ref = refOf(item);
  if (ref) return ref;
  return isObject(item) ? `${String(item.name)} in ${String(item.in)}` : `#${index}`;
}

function compositionKeys(items: Json[]): string[] {
  let inline = 0;
  return items.map((item) => refOf(item) ?? `inline #${inline++}`);
}

function keyed(role: string, items: Json[]): string[] {
  return role === "parameters" ? items.map(parameterKey) : compositionKeys(items);
}

function removedMembers(role: string, previous: Json[], next: Json, path: string): string[] {
  const nextItems = Array.isArray(next) ? next : [];
  const nextByKey = new Map(keyed(role, nextItems).map((key, i) => [key, nextItems[i]]));
  const found: string[] = [];
  keyed(role, previous).forEach((key, i) => {
    if (nextByKey.has(key)) {
      found.push(...removals(previous[i], nextByKey.get(key), `${path}/${key}`));
    } else if (role === "parameters") {
      found.push(`removed parameter ${key} at ${path}`);
    } else {
      found.push(`removed ${role} member ${key} at ${path}`);
    }
  });
  return found;
}

/**
 * What a released client could no longer read or call: a removed operation,
 * parameter, response status, schema property, enum value, or oneOf/anyOf/allOf
 * member, a property no longer required, or a changed type. Anything the new
 * spec only adds is fine (ADR-0029). It does not catch a $ref swapped to a
 * different schema or a new required request field.
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
    if (NOT_CONTRACT.includes(key)) {
      continue;
    }
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
    } else if (key === "required" && Array.isArray(value)) {
      const kept = Array.isArray(current.required) ? current.required : [];
      const properties = isObject(current.properties) ? current.properties : {};
      for (const name of value) {
        if (typeof name === "string" && name in properties && !kept.includes(name)) {
          found.push(`removed ${name} from required at ${path}`);
        }
      }
    } else if (key === "type") {
      if (JSON.stringify(value) !== JSON.stringify(current.type)) {
        found.push(`changed type from ${JSON.stringify(value)} to ${JSON.stringify(current.type)} at ${path}`);
      }
    } else if (key === "responses" && isObject(value)) {
      const kept = isObject(current.responses) ? current.responses : {};
      for (const status of Object.keys(value)) {
        if (!(status in kept)) {
          found.push(`removed response ${status} at ${path}`);
        }
      }
      found.push(...removals(value, current.responses, here));
    } else if ((key === "parameters" || COMPOSITIONS.includes(key)) && Array.isArray(value)) {
      found.push(...removedMembers(key, value, current[key], here));
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
