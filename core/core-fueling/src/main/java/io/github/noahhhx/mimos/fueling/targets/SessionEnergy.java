package io.github.noahhhx.mimos.fueling.targets;

import java.util.UUID;

public record SessionEnergy(UUID sessionId, double kcal, EnergyMethod method) {}
