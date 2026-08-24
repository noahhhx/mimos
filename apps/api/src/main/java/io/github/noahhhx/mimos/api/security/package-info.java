/**
 * Security configuration: the API is a stateless OIDC resource server. All
 * requests require a valid Keycloak-issued bearer token except the health
 * endpoint; failures are reported as RFC 9457 problem-details.
 */
@NullMarked
package io.github.noahhhx.mimos.api.security;

import org.jspecify.annotations.NullMarked;
