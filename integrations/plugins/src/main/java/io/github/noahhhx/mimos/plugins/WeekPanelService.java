package io.github.noahhhx.mimos.plugins;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The week-panel capability (ADR-0017): asks the plugins a user turned on
 * for their panel of a week, and passes a pressed button to one of them.
 * Plugins hear only the user's subject for them and the week; what they
 * remember against the subject is theirs.
 */
@Service
public class WeekPanelService {

    private static final Logger log = LoggerFactory.getLogger(WeekPanelService.class);

    private final PluginOptInService optIns;
    private final PluginSubjectService subjects;

    public WeekPanelService(PluginOptInService optIns, PluginSubjectService subjects) {
        this.optIns = optIns;
        this.subjects = subjects;
    }

    /** Panels from the user's healthy plugins, in registration order. A failing plugin is left out. */
    public List<WeekPanel> panels(UUID householdId, LocalDate startDate) {
        requireMonday(startDate);
        List<WeekPanel> result = new ArrayList<>();
        for (PluginOptInService.EnabledPlugin enabled :
                optIns.enabledPlugins(householdId, PluginManifest.CAPABILITY_WEEK_PANEL)) {
            UUID subject = subjects.subjectFor(householdId, enabled.manifest().id());
            try {
                result.add(enabled.plugin().client().fetchWeekPanel(enabled.manifest(), subject, startDate, null));
            } catch (RuntimeException exception) {
                failed(enabled, exception);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Sends a button press to one plugin and returns its panel afterwards.
     * {@link NoSuchElementException} when the plugin is not available or the
     * user has not turned it on; {@link PluginActionFailedException} when it
     * fails to answer.
     */
    public WeekPanel act(UUID householdId, LocalDate startDate, String pluginId, PanelAction action) {
        requireMonday(startDate);
        PluginOptInService.EnabledPlugin enabled =
                optIns.enabledPlugins(householdId, PluginManifest.CAPABILITY_WEEK_PANEL).stream()
                        .filter(candidate -> candidate.manifest().id().equals(pluginId))
                        .findFirst()
                        .orElseThrow(() -> new NoSuchElementException(
                                "no plugin '" + pluginId + "' with a week panel is turned on for you"));
        UUID subject = subjects.subjectFor(householdId, pluginId);
        try {
            return enabled.plugin().client().fetchWeekPanel(enabled.manifest(), subject, startDate, action);
        } catch (RuntimeException exception) {
            failed(enabled, exception);
            throw new PluginActionFailedException(enabled.manifest().name(), exception);
        }
    }

    /**
     * Contains one plugin's failure. The manifest is invalidated because the
     * plugin may have restarted with a different one (ADR-0006).
     */
    private static void failed(PluginOptInService.EnabledPlugin enabled, RuntimeException exception) {
        enabled.plugin().invalidate();
        log.warn(
                "plugin {} failed to render a week panel: {}",
                enabled.manifest().id(),
                exception.getMessage());
    }

    private static void requireMonday(LocalDate startDate) {
        if (startDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("startDate must be the Monday of the week");
        }
    }
}
