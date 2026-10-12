package io.github.noahhhx.mimos.fueling;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FuelService {

    static final int MAX_RANGE_DAYS = 62;

    private final FuelRepository repository;

    public FuelService(FuelRepository repository) {
        this.repository = repository;
    }

    public Optional<FuelProfile> profile(UUID profileId) {
        return repository.findProfile(profileId);
    }

    public FuelProfile saveProfile(FuelProfile profile) {
        repository.saveProfile(profile);
        return profile;
    }

    public List<TrainingSession> sessions(UUID profileId, LocalDate from, LocalDate to) {
        if (from.isBefore(TrainingSession.EARLIEST_DATE) || to.isAfter(TrainingSession.LATEST_DATE)) {
            throw new IllegalArgumentException("from and to must be " + TrainingSession.DATE_BOUNDS);
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
        if (from.plusDays(MAX_RANGE_DAYS - 1).isBefore(to)) {
            throw new IllegalArgumentException("range must be at most " + MAX_RANGE_DAYS + " days");
        }
        return repository.findSessions(profileId, from, to);
    }

    public TrainingSession session(UUID profileId, UUID sessionId) {
        return repository
                .findSession(profileId, sessionId)
                .orElseThrow(() -> new NoSuchElementException("training session not found: " + sessionId));
    }

    public List<TrainingSession> manualSessions(UUID profileId) {
        return repository.findManual(profileId);
    }

    public boolean hasData(UUID profileId) {
        return repository.hasData(profileId);
    }

    public TrainingSession addManual(UUID profileId, TrainingSessionDraft draft) {
        TrainingSession session = new TrainingSession(
                UUID.randomUUID(),
                profileId,
                draft.date(),
                Source.MANUAL,
                draft.kind(),
                draft.sport(),
                draft.durationMinutes(),
                draft.workKj(),
                draft.intensity(),
                null);
        repository.insertSession(session);
        return session;
    }

    /** A synced session would come back on its source's next sync, so only manual ones are deleted. */
    @Transactional
    public void deleteManual(UUID profileId, UUID sessionId) {
        if (session(profileId, sessionId).source() != Source.MANUAL) {
            throw new IllegalArgumentException(
                    "This session comes from intervals.icu. Change or delete it there, and it follows here.");
        }
        repository.deleteSession(profileId, sessionId);
    }

    /** Deletes every session this person's training source synced, for a source they disconnected. */
    public void deleteSynced(UUID profileId) {
        repository.deleteSynced(profileId);
    }
}
