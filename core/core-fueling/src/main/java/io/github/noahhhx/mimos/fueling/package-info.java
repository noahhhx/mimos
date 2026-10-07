/**
 * Fuel profiles, training sessions, and daily fuel targets (ADR-0022),
 * keyed by profile id.
 *
 * <p>Depends on no other Mimos module and knows nothing about households,
 * plans, or intervals.icu. Consumers reach this module through its public
 * API only.
 */
@NullMarked
package io.github.noahhhx.mimos.fueling;

import org.jspecify.annotations.NullMarked;
