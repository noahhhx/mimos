package io.github.noahhhx.mimos.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Text

const val MAX_STARS = 5

private val STARS = 1..MAX_STARS

/** The rating a tap on star [tapped] leads to: tapping the current star clears it (ADR-0035). */
fun nextRating(current: Int?, tapped: Int): Int? = if (current == tapped) null else tapped

/** A household's rating as five stars to tap, the web's `StarRating` with `onChange`. */
@Composable
fun StarRating(rating: Int?, onRate: (Int) -> Unit, enabled: Boolean = true) {
    val colors = MimosTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xs)) {
        for (star in STARS) {
            Text(
                "★",
                Modifier.semantics {
                        contentDescription = "Rate $star of $MAX_STARS stars"
                        selected = star == rating
                    }
                    .clickable(enabled = enabled, role = Role.Button) { onRate(star) }
                    .padding(MimosTheme.spacing.xs),
                style = MimosTheme.typography.body.copy(fontSize = 24.sp),
                color = if (star <= (rating ?: 0)) colors.accent else colors.line,
            )
        }
    }
}

/** A rating to read, as on the web's recipe list; nothing when unrated. */
@Composable
fun RatedStars(rating: Int?) {
    if (rating == null) return
    val colors = MimosTheme.colors
    Row(
        Modifier.clearAndSetSemantics { contentDescription = "Rated $rating of $MAX_STARS stars" }
    ) {
        for (star in STARS) {
            Text(
                "★",
                style = MimosTheme.typography.small,
                color = if (star <= rating) colors.accent else colors.muted,
            )
        }
    }
}
