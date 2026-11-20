package io.github.noahhhx.mimos.barcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.noahhhx.mimos.ui.BottomSheet
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.TextField

/**
 * The camera reading food barcodes (EAN-8, EAN-13, UPC-A, UPC-E) while [looking], or what to say
 * when it cannot. It asks for the camera only once it is shown.
 */
@Composable
expect fun BarcodeCamera(looking: Boolean, onDetect: (String) -> Unit, modifier: Modifier)

/** Opens the scan sheet. */
@Composable
fun ScanButton(model: ScanModel, modifier: Modifier = Modifier) {
    Button("Scan a barcode", model::open, modifier, kind = ButtonKind.Secondary)
}

/** The scan sheet, while [model] has it open: the camera, then the typed number, always offered. */
@Composable
fun ScanSheet(model: ScanModel) {
    val scan by model.scan.collectAsState()
    val current = scan ?: return
    val spacing = MimosTheme.spacing
    val looking = current.lookup == Lookup.Looking
    BottomSheet(onDismiss = model::close) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = spacing.page, vertical = spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Scan a barcode",
                    Modifier.weight(1f),
                    style = MimosTheme.typography.heading,
                )
                Button("Close", model::close, kind = ButtonKind.Secondary, small = true)
            }
            BarcodeCamera(current.cameraLooking, model::detected, Modifier.fillMaxWidth())
            if (!current.cameraLooking && current.lookup is Lookup.Failed) {
                Button("Scan again", model::scanAgain, kind = ButtonKind.Secondary)
            }
            TextField(
                current.typed,
                model::type,
                "Barcode number",
                placeholder = "The digits under the bars",
                error =
                    if (current.typed.isNotBlank() && current.barcode == null) {
                        "A barcode number is 8 to 14 digits."
                    } else null,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Search,
                    ),
                keyboardActions = KeyboardActions(onSearch = { model.lookUpTyped() }),
            )
            Button(
                if (looking) "Looking up…" else "Look up",
                model::lookUpTyped,
                enabled = current.barcode != null && !looking,
            )
            (current.lookup as? Lookup.Failed)?.let { Notice(it.message) }
        }
    }
}
