package io.github.noahhhx.mimos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.noahhhx.mimos.resources.Res
import io.github.noahhhx.mimos.resources.newsreader
import io.github.noahhhx.mimos.resources.public_sans
import org.jetbrains.compose.resources.Font

/**
 * The web's type scale (`globals.css`), by role. Sizes are the web's px read as sp. Colour is not
 * part of a style: [Text] takes it, so one scale serves both themes.
 */
data class MimosTypography(
    /** Body text: Public Sans 17, line height 1.6. */
    val body: TextStyle,
    /** Notes under fields and rows: 13. */
    val small: TextStyle,
    /**
     * Eyebrows, section headings, and field labels: 13, 600, spaced out. Set the text in capitals.
     */
    val label: TextStyle,
    /** A page's h1: Newsreader 400. The web scales 40 to 68 with the viewport; a phone gets 40. */
    val title: TextStyle,
    /** h3, and the titles of dialogs and empty states: Newsreader 22, 500. */
    val heading: TextStyle,
    /** A page's lede: Newsreader 22, muted by the caller. */
    val lede: TextStyle,
    /** The brand beside the duck: Newsreader 26, 500. */
    val brand: TextStyle,
    /** A person's name in the profile panel: Newsreader 19. */
    val person: TextStyle,
    val button: TextStyle,
    val buttonSmall: TextStyle,
    /** What a person types: 16. */
    val input: TextStyle,
    /** A tab's label: 15. */
    val tab: TextStyle,
)

private val letterSpacedLabel = 0.12.em

/**
 * The variable fonts, one [FontFamily] per use: each font file is a variable font, so a weight is a
 * variation setting rather than another file. Newsreader's optical size follows the text size, as
 * the browser's `font-optical-sizing: auto` does.
 */
@Composable
private fun publicSans(): FontFamily {
    val weights = listOf(400, 500, 600)
    val fonts = weights.map {
        Font(
            Res.font.public_sans,
            FontWeight(it),
            variationSettings = FontVariation.Settings(FontVariation.weight(it)),
        )
    }
    return remember(fonts) { FontFamily(fonts) }
}

@Composable
private fun newsreader(size: TextUnit): FontFamily {
    val fonts =
        listOf(400, 500).map {
            Font(
                Res.font.newsreader,
                FontWeight(it),
                variationSettings =
                    FontVariation.Settings(
                        FontVariation.weight(it),
                        FontVariation.opticalSizing(size),
                    ),
            )
        }
    return remember(fonts) { FontFamily(fonts) }
}

@Composable
internal fun rememberMimosTypography(): MimosTypography {
    val sans = publicSans()
    val title = 40.sp
    val heading = 22.sp
    val brand = 26.sp
    val person = 19.sp
    val titleFont = newsreader(title)
    val headingFont = newsreader(heading)
    val brandFont = newsreader(brand)
    val personFont = newsreader(person)
    return remember(sans, titleFont, headingFont, brandFont, personFont) {
        val body = TextStyle(fontFamily = sans, fontSize = 17.sp, lineHeight = 1.6.em)
        MimosTypography(
            body = body,
            small = body.copy(fontSize = 13.sp),
            label =
                body.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = letterSpacedLabel,
                ),
            title =
                TextStyle(
                    fontFamily = titleFont,
                    fontSize = title,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 1.02.em,
                    letterSpacing = (-0.01).em,
                ),
            heading =
                TextStyle(
                    fontFamily = headingFont,
                    fontSize = heading,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 1.25.em,
                ),
            lede = TextStyle(fontFamily = headingFont, fontSize = heading, lineHeight = 1.45.em),
            brand =
                TextStyle(fontFamily = brandFont, fontSize = brand, fontWeight = FontWeight.Medium),
            person = TextStyle(fontFamily = personFont, fontSize = person),
            button =
                body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.2.em),
            buttonSmall =
                body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.2.em),
            input = body.copy(fontSize = 16.sp, lineHeight = 1.3.em),
            tab = body.copy(fontSize = 15.sp, lineHeight = 1.2.em),
        )
    }
}
