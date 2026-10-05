# ADR-0014: Agent access over MCP

- Status: Accepted
- Date: 2026-10-05

## Context

People want to use Mimos from an AI agent: "create my weekly meal plan",
"what recipes with beef do I have", "what is still left on my shopping
list", "create a new recipe for me". The Model Context Protocol (MCP) is
how agents such as Claude Code and claude.ai connectors reach outside
services, and its authorization spec builds on OAuth 2.1 and Protected
Resource Metadata (RFC 9728).

The owner's requirement is that this works for every existing feature and
every future one. A hand-maintained tool list fails that the first time
someone adds an endpoint and forgets the tool. The API already has one
description of every operation that the build enforces: the OpenAPI
contract (ADR-0003).

## Decision

### The MCP server lives in mimos-api, at `/mcp`

Spring AI's MCP server (`spring-ai-starter-mcp-server-webmvc`, 2.0.x)
serves Streamable HTTP at `/mcp` with the stateless protocol: every call
is a self-contained, authenticated HTTP request, so there is no session
to lose on restart or to pin to one replica. Only the MCP server
auto-configuration and the MCP Java SDK are used. Spring AI's model layer
(`spring-ai-model`) and `@McpTool` scanning (`spring-ai-mcp-annotations`)
are excluded.

### Tools are derived from the OpenAPI contract at startup

The api jar carries `contracts/api/openapi.yaml`. At startup
`OpenApiToolParser` turns every operation under `/api/v1/` into a
`ToolOperation`, and `McpServerConfig` registers one tool for each:

- The name is the `operationId`. The description is the summary and
  description, so the spec's prose is what the agent reads.
- The input schema is one JSON Schema object. Path and query parameters
  become properties of the same name, and the request body becomes a
  `body` property. Component schemas are copied into `$defs`, which works
  because OpenAPI 3.1 schemas are JSON Schema 2020-12. Unknown arguments
  are rejected, so a misplaced field comes back as an error.
- Hints follow the HTTP method: `GET` is read-only, `PUT` and `DELETE`
  are destructive (a `PUT` replaces what was there), and `PUT` and
  `DELETE` are idempotent.

A new endpoint is a tool without further work. An operation opts out with
the vendor extension `x-mcp: false`. The public library duplicates
(`listPublicRecipes`, `getPublicRecipe`) and account export and import
(whole-account documents meant for files) opt out. A test compares the
registered tools with the spec's operation ids, so a tool cannot go
missing silently, and pins the opt-out list, so opting out is deliberate.

### A tool call is a request to the API itself

`ToolDispatcher` sends each call to the API's own port on `127.0.0.1`,
with the caller's bearer token and the `/mcp` request's `X-Request-Id`.
Validation, ownership checks, problem-details, and access logging are
the HTTP API's own, and the two access lines share one request ID. A 4xx
or 5xx comes back as a tool error that carries the problem detail, so the
agent can read what went wrong and try again.

### Agents sign in with Keycloak as the `mimos-agent` client

`mimos-agent` is a public client: Authorization Code with PKCE (S256
required), no password or device grants. Its redirect URIs are
`http://localhost/callback`, `http://127.0.0.1/callback`,
`http://[::1]/callback`, and `https://claude.ai/api/mcp/auth_callback`.
Keycloak 26.5 ignores the port when it matches a loopback redirect URI
registered without one (RFC 8252, section 7.3), so a CLI agent can listen
on any port. `AgentSignInTests` checks this against the real realm.

MCP clients discover Keycloak through Spring Security's Protected
Resource Metadata endpoint, `/.well-known/oauth-protected-resource`,
customized to name the realm as the authorization server. A 401 carries
`WWW-Authenticate: Bearer resource_metadata="..."`, pointing at the
metadata for the path that was requested. The API reads
`X-Forwarded-Proto` and `X-Forwarded-Host` from private-network proxies
(`server.forward-headers-strategy: native`), so behind a reverse proxy
the metadata names the public URL.

## Alternatives considered

- **A separate TypeScript MCP service.** It would be one more deployable
  and image, and a second HTTP client of the API to keep in step. The
  loopback dispatch already gives the API's own behavior from inside the
  monolith, and AGENTS.md asks for a concrete need before a new service.
- **An MCP route in the web app.** Next.js would hold agents' bearer
  tokens and forward them, the browser-facing app would become an API
  gateway, and self-hosters who run only the API would lose agent access.
- **Hand-written tools.** Better descriptions per tool, but every new
  endpoint needs a second change that is easy to forget. Improving the
  spec's descriptions gets the same text into the tools, and the HTTP
  docs gain it too.
- **Dynamic client registration.** MCP clients may register themselves.
  Keycloak limits anonymous registration to trusted hosts, and opening it
  to every MCP client would let anyone create clients in the realm. One
  pre-registered public client is simpler to reason about and needs no
  admin action per agent.

## Consequences

- MCP clients ask for `offline_access` (Claude Code does), so their
  refresh tokens outlive the user's browser session and signing out does
  not end them; users revoke them from the account console's
  Applications page. A user needs the realm's default roles for this,
  which Keycloak grants on registration; the dev export's `test` and
  `test2` list `default-roles-mimos` explicitly because an imported user
  gets only the roles it names.
- Spec descriptions are now agent prompts. Write them for a reader with
  nothing else to go on: say what a parameter must be (a plan's
  `startDate` is a Monday), and how operations relate (generating a
  shopping list versus reading one).
- The server's `instructions` (in `application.yml`) name tools and
  parameters. A test fails if one of those names stops existing.
- A tool call holds one request thread while its inner request takes
  another. Under heavy agent traffic, Tomcat's thread pool is shared
  between the two.
- Tokens are not bound to an audience. The API accepts any token from the
  realm, from `mimos-web` and `mimos-agent` alike, so the MCP resource
  indicator (RFC 8707) is advisory. Keycloak's `resource-indicators`
  feature is experimental; revisit audience checks when it is stable.
- Realm import skips an existing realm. Instances set up before this
  change must add the `mimos-agent` client by hand; `docs/self-hosting.md`
  has the steps.
- claude.ai connectors call the API and Keycloak from Anthropic's
  servers, so they need both on public HTTPS URLs. Claude Code runs on
  the user's machine and works on a home network.
