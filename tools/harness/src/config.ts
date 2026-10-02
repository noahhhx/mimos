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

/** Log levels `harness loglevel` changed, with their originals, so `--reset` can restore them. */
export const LOGLEVEL_STATE = ".harness/loglevels.json";

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

/** Debug ports the overlay publishes on 127.0.0.1 (compose.debug.yml reads the same variables). */
export function debugPorts(env: Env = process.env): { jdwp: number; inspector: number } {
  return { jdwp: Number(env.API_DEBUG_PORT ?? "5005"), inspector: Number(env.WEB_INSPECT_PORT ?? "9229") };
}

/** The stack's database, as compose.yml names it (`psql` runs inside the postgres container). */
export function database(env: Env = process.env): { user: string; name: string } {
  return { user: env.POSTGRES_USER ?? "mimos", name: env.POSTGRES_DB ?? "mimos" };
}

/** Flyway migrations (the API owns the schema); `harness diag` compares them with what is applied. */
export const MIGRATIONS_DIR = "apps/api/src/main/resources/db/migration";

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
    paths: ["pom.xml", ".mvn", "contracts", "core", "integrations", "apps/api", "deploy/keycloak/mimos-realm.json"],
  },
  keycloak: {
    dockerfile: "deploy/keycloak/Dockerfile",
    paths: ["deploy/keycloak/themes", "apps/web/src/fonts"],
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
