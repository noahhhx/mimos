import { resolve } from "node:path";

/**
 * Where things are and how to reach them. Ports and URLs come from the same
 * environment variables (and defaults) as deploy/docker/compose.yml, so an
 * override exported for compose also retargets the harness.
 */

/** The repository root, independent of the caller's working directory. */
export const REPO_ROOT = resolve(import.meta.dirname, "../../..");

export const COMPOSE_FILE = "deploy/docker/compose.yml";
/** Opt-in debug overlay (introduced in step 3); never part of the product stack. */
export const DEBUG_OVERLAY = "deploy/docker/compose.debug.yml";

/** Evidence lives here (gitignored, dockerignored). */
export const RUNS_DIR = ".harness/runs";

export const REALM = "mimos";

export interface Endpoints {
  api: string;
  keycloak: string;
  web: string;
  clientId: string;
}

type Env = Record<string, string | undefined>;

export function endpoints(env: Env = process.env): Endpoints {
  return {
    api: env.API_PUBLIC_URL ?? `http://localhost:${env.API_PORT ?? "8080"}`,
    keycloak: env.KEYCLOAK_PUBLIC_URL ?? `http://localhost:${env.KEYCLOAK_PORT ?? "8081"}`,
    web: env.WEB_ORIGIN ?? `http://localhost:${env.WEB_PORT ?? "3000"}`,
    clientId: env.KEYCLOAK_CLIENT_ID ?? "mimos-web",
  };
}

/** The compose project name (`name:` in compose.yml) — images are `<project>-<service>`. */
export function composeProject(env: Env = process.env): string {
  return env.COMPOSE_PROJECT_NAME ?? "mimos";
}

/** Test users shipped in deploy/keycloak/mimos-realm.json (local only — not secrets). */
export const USERS: Readonly<Record<string, string>> = {
  test: "mimos-test",
  test2: "mimos-test",
};

export const DEFAULT_USER = "test";

/**
 * Repo paths whose changes make a service's image stale. Narrower than the
 * Dockerfiles' COPY sources where those over-copy (the API image copies all
 * of apps/, but only apps/api affects it); test/config.test.ts checks every
 * path here is actually copied by the service's Dockerfile.
 */
export const BUILD_INPUTS: Readonly<Record<string, { dockerfile: string; paths: readonly string[] }>> = {
  api: {
    dockerfile: "apps/api/Dockerfile",
    paths: ["pom.xml", ".mvn", "contracts", "core", "integrations", "apps/api", "deploy/keycloak"],
  },
  web: {
    dockerfile: "apps/web/Dockerfile",
    paths: ["package.json", "package-lock.json", "contracts", "libraries", "apps/web"],
  },
  "country-week": {
    dockerfile: "plugins/country-week/Dockerfile",
    paths: ["package.json", "package-lock.json", "libraries/plugin-sdk", "plugins/country-week"],
  },
};
