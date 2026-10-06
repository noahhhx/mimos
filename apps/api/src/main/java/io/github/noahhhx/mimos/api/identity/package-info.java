/**
 * User identity: the lightweight profile keyed by the OIDC subject and the
 * household it belongs to (ADR-0019), both created on first authenticated
 * request. Authentication itself is delegated entirely to Keycloak.
 */
@NullMarked
package io.github.noahhhx.mimos.api.identity;

import org.jspecify.annotations.NullMarked;
