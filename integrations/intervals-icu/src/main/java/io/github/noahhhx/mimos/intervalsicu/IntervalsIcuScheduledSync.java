package io.github.noahhhx.mimos.intervalsicu;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Syncs every connected person in turn, every
 * {@code mimos.intervals-icu.sync-interval} (ADR-0022), so training made in
 * intervals.icu reaches targets without anyone pressing Refresh. Broken
 * connections and people intervals.icu asked to wait are skipped, and one
 * person's failure never stops the others.
 */
@Component
@Conditional(IntervalsIcuConfiguration.Active.class)
public class IntervalsIcuScheduledSync {

    private static final Logger log = LoggerFactory.getLogger(IntervalsIcuScheduledSync.class);

    private final IntervalsIcuConnections connections;
    private final IntervalsIcuSync sync;
    private final Clock clock;

    IntervalsIcuScheduledSync(IntervalsIcuConnections connections, IntervalsIcuSync sync, Clock clock) {
        this.connections = connections;
        this.sync = sync;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${mimos.intervals-icu.sync-interval:30m}",
            fixedDelayString = "${mimos.intervals-icu.sync-interval:30m}")
    public void syncEveryone() {
        for (UUID profileId : connections.due(clock.instant())) {
            try {
                sync.sync(profileId);
            } catch (RuntimeException e) {
                log.warn("Scheduled intervals.icu sync failed for one person; moving on", e);
            }
        }
    }
}
