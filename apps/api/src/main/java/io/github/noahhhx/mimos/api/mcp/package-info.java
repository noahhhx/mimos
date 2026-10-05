/**
 * Agent access over MCP (ADR-0014): the API's operations, derived from the
 * OpenAPI contract, served as MCP tools at {@code /mcp}. An adapter over the
 * HTTP API itself; it never calls the core modules directly.
 */
@NullMarked
package io.github.noahhhx.mimos.api.mcp;

import org.jspecify.annotations.NullMarked;
