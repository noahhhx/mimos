# ADR-0031: Mimos depends on no cloud provider

- Status: Accepted
- Date: 2026-11-16
- Settles: the AGENTS.md open decisions "AWS compute" and "IaC tool"
- Changes: prime directives 1 and 2 in AGENTS.md, which named AWS as
  the scale target, and ROADMAP step 15 ("AWS deployment")

## Context

AGENTS.md named AWS as the scale target and planned infrastructure as
code in `deploy/aws`, leaving two choices open: what runs the
containers, and which tool describes them. Prime directive 2 allowed
AWS-native services behind a portable interface.

The owner rules this out. Mimos must always work for people who host it
themselves, and no part of it may rely on AWS or any other provider.
Every image is already published, configured only through its
environment, and run by `deploy/selfhost` on any machine with Docker
(ADR-0012).

## Decision

- **No provider-specific deployment.** There is no `deploy/aws` and no
  infrastructure code for any cloud. A hosted instance, ours included,
  runs the published images through `deploy/selfhost`, as a family's
  home server does. With nothing to describe, the compute and IaC
  questions do not need an answer.
- **Only standard, self-hostable pieces.** Containers, PostgreSQL, an
  S3-compatible API (ADR-0032), and standard OIDC through Keycloak. An
  operator may point Mimos at a managed service that speaks one of
  those interfaces, such as a hosted Postgres. Nothing in the repo may
  require one, assume one, or call a provider's own API.
- **Scaling stays inside the images.** When one host is not enough,
  the answer is something every self-hoster can run as well: more
  replicas of the same images behind their proxy, or Postgres
  replication. It is never a provider's service. Prime directive 4
  still decides when a service splits off.

Alternatives considered:

- **AWS as the scale target, behind portable interfaces.** This was the
  old prime directive 2. Each AWS-only piece is a path that self-hosters
  never run and CI never checks, and over time the project would come to
  rely on it.
- **Kubernetes manifests or a Helm chart.** They are provider-neutral,
  but a family has no use for them, and no concrete need asks for them
  yet. They can come later as another way to run the same images.

## Consequences

- Prime directives 1 and 2 no longer name AWS as a target; directive 2
  is "No cloud provider dependency". The AGENTS.md layout and
  `.dockerignore` drop `deploy/aws`.
- ROADMAP step 15 becomes running a public instance from
  `deploy/selfhost`, so the hosted path and the self-hosted path are
  one path.
- ADRs that mention AWS as a future target (ADR-0010, ADR-0023) are
  superseded on that point by this one.
