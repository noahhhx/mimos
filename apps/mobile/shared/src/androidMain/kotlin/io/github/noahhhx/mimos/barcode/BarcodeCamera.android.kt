package io.github.noahhhx.mimos.barcode

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Text
import java.util.concurrent.Executors
import zxingcpp.BarcodeReader

private enum class CameraState {
    Asking,
    Allowed,
    Refused,
    Failed,
}

private val FOOD_FORMATS =
    setOf(
        BarcodeReader.Format.EAN_13,
        BarcodeReader.Format.EAN_8,
        BarcodeReader.Format.UPC_A,
        BarcodeReader.Format.UPC_E,
    )

/**
 * The rear camera through CameraX, each frame read by zxing-cpp. The permission is asked when the
 * camera is first shown, never before; refused, or on a phone with no camera, the typed number is
 * all there is.
 */
@Composable
actual fun BarcodeCamera(looking: Boolean, onDetect: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val hasCamera =
        remember(context) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        }
    if (!hasCamera) return
    var state by remember {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        mutableStateOf(if (granted) CameraState.Allowed else CameraState.Asking)
    }
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            state = if (granted) CameraState.Allowed else CameraState.Refused
        }
    LaunchedEffect(Unit) { if (state == CameraState.Asking) ask.launch(Manifest.permission.CAMERA) }
    when (state) {
        CameraState.Asking -> {}
        CameraState.Allowed ->
            CameraPreview(looking, onDetect, onFailed = { state = CameraState.Failed }, modifier)
        CameraState.Refused -> Hint("The camera isn't allowed. Type the number instead.")
        CameraState.Failed -> Hint("The camera didn't start. Type the number instead.")
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MimosTheme.typography.small, color = MimosTheme.colors.muted)
}

@Composable
private fun CameraPreview(
    looking: Boolean,
    onDetect: (String) -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val stillLooking by rememberUpdatedState(looking)
    val detect by rememberUpdatedState(onDetect)
    val fail by rememberUpdatedState(onFailed)
    // A TextureView: the default SurfaceView draws outside its bounds in the sheet's own window.
    val preview =
        remember(context) {
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }
    DisposableEffect(lifecycleOwner) {
        val frames = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val reader = BarcodeReader(BarcodeReader.Options(formats = FOOD_FORMATS))
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener(
            {
                try {
                    val cameras = future.get().also { provider = it }
                    val shown =
                        Preview.Builder().build().also {
                            it.surfaceProvider = preview.surfaceProvider
                        }
                    val analysis =
                        ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                    analysis.setAnalyzer(frames) { image ->
                        image.use {
                            if (!stillLooking) return@use
                            val code =
                                reader
                                    .read(it)
                                    .firstOrNull { result ->
                                        result.error == null && !result.text.isNullOrEmpty()
                                    }
                                    ?.text
                            if (code != null) main.execute { detect(code) }
                        }
                    }
                    cameras.unbindAll()
                    cameras.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        shown,
                        analysis,
                    )
                } catch (failure: Exception) {
                    fail()
                }
            },
            main,
        )
        onDispose {
            provider?.unbindAll()
            frames.shutdown()
        }
    }
    // Filling the box scales the picture past it, so it is cut to the box.
    AndroidView({ preview }, modifier.height(240.dp).clipToBounds())
}
