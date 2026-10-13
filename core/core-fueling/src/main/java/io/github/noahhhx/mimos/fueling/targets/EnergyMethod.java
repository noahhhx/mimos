package io.github.noahhhx.mimos.fueling.targets;

/** How a session's energy was found, in ADR-0022's order of preference. */
public enum EnergyMethod {
    COMPLETED_WORK,
    PLANNED_WORK,
    FTP_INTENSITY,
    /** The calories intervals.icu reported for an activity with no work and no FTP to price it. */
    REPORTED_CALORIES,
    ESTIMATE
}
