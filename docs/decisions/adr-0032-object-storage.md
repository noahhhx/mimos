# ADR-0032: Object storage is any S3-compatible server, with Garage as the reference

- Status: Accepted
- Date: 2026-11-16
- Settles: the AGENTS.md open decision "Object storage"
- Builds on: [ADR-0023](adr-0023-recipe-pictures.md) (the `PictureStore`
  seam) and [ADR-0031](adr-0031-no-cloud-provider.md) (no cloud provider)

## Context

Recipe pictures keep their bytes in Postgres, behind `PictureStore`
(ADR-0023). Two things were left open: which store comes next, and what
stands in for it locally, since MinIO's community edition is archived.
ADR-0031 adds a constraint: whatever comes next, a self-hoster must be
able to run it, and it must not depend on a provider.

## Decision

- **Postgres stays the default.** `deploy/docker` and `deploy/selfhost`
  keep pictures in Postgres. A family instance runs no extra service, and
  its backup stays one database dump.
- **The next store speaks the S3 API to a server the operator names.**
  It is a second `PictureStore`, chosen by configuration, with a
  configurable endpoint, path-style addressing, and credentials. It uses
  only put, get, delete, and list objects. Adding it also means adding
  the migration that moves the bytes and the orphan sweep that ADR-0023
  names.
- **Garage is the reference server.** Garage (Deuxfleurs, AGPL-3.0,
  active in 2026) is a single binary built for small self-hosted
  clusters. The store's Testcontainers test runs against Garage, and
  `deploy/selfhost` gains it as an optional service when the store is
  built. Garage runs as a separate process, so its license places no
  requirement on Mimos. Other S3-compatible servers work if they
  implement those four operations, but Garage is the one CI proves.
- **The client is a free S3 library that takes any endpoint.** The pick
  is made when the store is built, judged by license and by whether it
  works against Garage with no provider defaults (region, endpoint
  discovery, credential chains).

Alternatives considered:

- **An object store in every deployment now.** It adds a service and a
  second backup for every self-hoster, with no concrete need (ADR-0023).
- **MinIO.** Its community edition is archived upstream.
- **SeaweedFS.** It is also S3-compatible, but it has more moving parts
  than Mimos needs to store put-and-get pictures.

## Consequences

- No code changes now. The S3 `PictureStore` arrives when a deployment
  outgrows pictures in Postgres.
- "S3-compatible" in Mimos means the API, never a provider. The local
  test against Garage is the only proof the store needs.
