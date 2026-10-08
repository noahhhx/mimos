package io.github.noahhhx.mimos.fueling.targets;

import java.time.LocalDate;

public record DailyTarget(
        LocalDate date, double calories, double proteinG, double carbsG, double fatG, TargetBasis basis) {}
