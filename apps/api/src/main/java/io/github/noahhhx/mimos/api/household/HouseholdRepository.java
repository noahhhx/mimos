package io.github.noahhhx.mimos.api.household;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC persistence for households, their members, and their invites. */
@Repository
class HouseholdRepository {

    private final JdbcTemplate jdbc;

    HouseholdRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<Member> members(UUID householdId) {
        return jdbc.query(
                "select id, display_name from user_profile where household_id = ? order by lower(display_name), id",
                (rs, i) -> new Member(rs.getObject("id", UUID.class), rs.getString("display_name")),
                householdId);
    }

    int memberCount(UUID householdId) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "select count(*) from user_profile where household_id = ?", Integer.class, householdId));
    }

    /**
     * The profile's household, with the profile's row locked until the
     * transaction ends, so one person's joins and leaves run one at a time.
     * {@code no key update} leaves rows that only reference the profile
     * (logs, diners) free to be written meanwhile.
     */
    UUID lockHouseholdOf(UUID profileId) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "select household_id from user_profile where id = ? for no key update", UUID.class, profileId));
    }

    UUID createHousehold() {
        return Objects.requireNonNull(
                jdbc.queryForObject("insert into household default values returning id", UUID.class));
    }

    void moveProfile(UUID profileId, UUID householdId) {
        jdbc.update(
                "update user_profile set household_id = ?, updated_at = now() where id = ?", householdId, profileId);
    }

    /** Deletes a household nothing refers to any more; its invites go with it. */
    void deleteHousehold(UUID householdId) {
        jdbc.update("delete from household where id = ?", householdId);
    }

    void insertInvite(String tokenHash, UUID householdId, UUID createdBy, Instant createdAt, Instant expiresAt) {
        jdbc.update("""
                insert into household_invite (token_hash, household_id, created_by_profile_id, created_at, expires_at)
                values (?, ?, ?, ?, ?)
                """, tokenHash, householdId, createdBy, Timestamp.from(createdAt), Timestamp.from(expiresAt));
    }

    Optional<Invite> findInvite(String tokenHash) {
        return jdbc
                .query(
                        "select household_id, expires_at, used_at from household_invite where token_hash = ?",
                        (rs, i) -> {
                            Timestamp usedAt = rs.getTimestamp("used_at");
                            return new Invite(
                                    rs.getObject("household_id", UUID.class),
                                    rs.getTimestamp("expires_at").toInstant(),
                                    usedAt == null ? null : usedAt.toInstant());
                        },
                        tokenHash)
                .stream()
                .findFirst();
    }

    /** Marks the invite used; false when it already was, or is gone. */
    boolean markUsed(String tokenHash, Instant usedAt) {
        return jdbc.update(
                        "update household_invite set used_at = ? where token_hash = ? and used_at is null",
                        Timestamp.from(usedAt),
                        tokenHash)
                > 0;
    }

    record Member(UUID profileId, String displayName) {}

    record Invite(
            UUID householdId, Instant expiresAt, @Nullable Instant usedAt) {}
}
