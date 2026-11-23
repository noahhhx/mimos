# ADR-0030: The Android app connects over HTTPS only

- Status: Accepted
- Date: 2026-11-16
- Settles: whether release builds of the app allow plain HTTP (ADR-0029)

## Context

Release builds of the app refused `http://`, pending a decision: a family
instance on a home network without TLS could not be used from a release
install. The alternative was to allow cleartext everywhere and warn on the
connect screen.

Such an instance does not work in a browser either. The web app signs in
with PKCE, which needs the Web Crypto API, and browsers offer it only on
`https://` pages and `http://localhost`. The self-hosting guide already
requires HTTPS for that reason ("Why HTTPS"), and `deploy/selfhost`
starts Keycloak in production mode behind the operator's TLS proxy. An
app that allowed cleartext would serve an instance nobody can use from
the web.

A home instance with TLS often uses a certificate from a private CA (a
router's, Caddy's internal CA, step-ca). Browsers accept it once the CA
is installed on the device. Android apps that target API 24 or later
trust only the system's CAs by default, so the same phone would open the
web app in its browser and fail to connect from the app.

## Decision

- **No cleartext in release builds.** The release network security
  config keeps `cleartextTrafficPermitted="false"`. Debug builds keep
  their `localhost` exception for the harness (item 02).
- **User-installed CAs are trusted.** The release config's base trust
  anchors are the system's and the user's. A CA the person installed on
  their phone works in the app as it does in their browser.
- **The connect screen names the reason.** An `http://` address other
  than `localhost` gets its own message: Mimos needs an `https://`
  address, with a link to the self-hosting guide's "Why HTTPS". It is
  never a generic network error.

Alternatives considered:

- **Cleartext everywhere, with a warning.** Bearer and refresh tokens
  would cross the network in the clear, and it would serve a setup the
  web does not support.
- **Cleartext to private addresses only.** The network security config
  cannot express address ranges, so the app would enforce it itself
  against resolved addresses, and the same instance would still not
  work in a browser.
- **System CAs only.** This is Android's default, but it breaks the
  common home setup that the web supports.

## Consequences

- Self-hosting needs HTTPS, as it already did. The app adds no new
  requirement.
- An app the person installed a CA for can be intercepted by whoever
  controls that CA. On a phone, only its owner can install one, and their
  browser trusts it already.
- Android lint's `AcceptsUserCertificates` check flags the trust anchor.
  It is suppressed in the config, citing this ADR.
- Item 03's connect screen gains the `http://` message.
