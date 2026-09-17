package sk.fotostudio.app

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

private val Ink = Color(0xFF111318)
private val Gold = Color(0xFFE8C978)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { FotoStudioApp() } }
}

@Composable
private fun FotoStudioApp() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Ink, primary = Gold)) {
        if (granted) CameraScreen() else Box(Modifier.fillMaxSize().background(Ink), Alignment.Center) { Text("Povolenie kamery je potrebné", color = Color.White) }
    }
}

@Composable
private fun CameraScreen() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var lens by remember { mutableStateOf("1×") }
    var autoCompose by remember { mutableStateOf(true) }
    var grid by remember { mutableStateOf(true) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    Box(Modifier.fillMaxSize().background(Ink)) {
        AndroidView(factory = { PreviewView(it).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }, modifier = Modifier.fillMaxSize()) { view ->
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val capture = ImageCapture.Builder().build()
                imageCapture = capture
                provider.unbindAll()
                camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            }, ContextCompat.getMainExecutor(context))
        }
        if (grid) GoldenGrid(Modifier.fillMaxSize())
        TopBar(lens)
        if (autoCompose) SuggestionChip(Modifier.align(Alignment.TopCenter).padding(top = 84.dp), "Priblíženie upravené pre zlatý rez")
        BottomControls(Modifier.align(Alignment.BottomCenter), context, lens, { value -> lens = value; camera?.cameraControl?.setLinearZoom(zoomForLens(value)) }, autoCompose, { autoCompose = it }, grid, { grid = it }, imageCapture)
    }
}

@Composable private fun TopBar(lens: String) { Row(Modifier.fillMaxWidth().padding(18.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { CircleButton("⚙"); Surface(color = Color(0xAA111318), shape = RoundedCornerShape(50)) { Text("AUTO KOMPOZÍCIA · $lens", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, style = MaterialTheme.typography.labelSmall) }; CircleButton("ϟ") } }

@Composable private fun BottomControls(modifier: Modifier, context: android.content.Context, lens: String, select: (String) -> Unit, auto: Boolean, setAuto: (Boolean) -> Unit, grid: Boolean, setGrid: (Boolean) -> Unit, imageCapture: ImageCapture?) {
    Column(modifier.fillMaxWidth().background(Color(0xEE111318)).padding(horizontal = 18.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf("0,5×", "1×", "2×", "5×").forEach { value -> LensButton(value, lens == value, { select(value) }) } }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF667B7B))); Button(onClick = { imageCapture?.let { capturePhoto(context, it) } }, modifier = Modifier.size(76.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink), contentPadding = PaddingValues(0.dp)) { Text("", Modifier.size(56.dp).border(4.dp, Color(0xFF9CA3AA), CircleShape), color = Ink) }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircleButton("±"); CircleButton("↻") } }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), Arrangement.Center) { listOf("VIDEO", "FOTOGRAFIA", "PORTRÉT", "PRO").forEach { Text(it, Modifier.padding(horizontal = 8.dp), color = if (it == "FOTOGRAFIA") Gold else Color(0xFFADB5C0), style = MaterialTheme.typography.labelSmall) } }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("Automatická kompozícia", color = Color.White, style = MaterialTheme.typography.labelSmall); Switch(checked = auto, onCheckedChange = setAuto, colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Gold)); TextButton(onClick = { setGrid(!grid) }) { Text(if (grid) "Mriežka zapnutá" else "Mriežka vypnutá", color = Gold, style = MaterialTheme.typography.labelSmall) } }
    }
}

@Composable private fun LensButton(text: String, active: Boolean, click: () -> Unit) { Button(onClick = click, modifier = Modifier.size(45.dp), shape = CircleShape, contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = if (active) Gold else Color(0xFF292E35), contentColor = if (active) Ink else Color.White)) { Text(text, style = MaterialTheme.typography.labelSmall) } }
@Composable private fun CircleButton(label: String) { IconButton(onClick = {}, modifier = Modifier.size(40.dp).background(Color(0x99111318), CircleShape)) { Text(label, color = Color.White) } }
@Composable private fun SuggestionChip(modifier: Modifier, text: String) { Surface(modifier, color = Color(0xDD111318), shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x55FFFFFF))) { Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, style = MaterialTheme.typography.labelSmall) } }
@Composable private fun GoldenGrid(modifier: Modifier) { Box(modifier.padding(top = 110.dp, bottom = 250.dp, start = 30.dp, end = 30.dp)) { Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) { repeat(2) { Box(Modifier.fillMaxWidth().height(1.dp).background(Gold.copy(alpha = .75f))) } }; Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) { repeat(2) { Box(Modifier.fillMaxHeight().width(1.dp).background(Gold.copy(alpha = .75f))) } }; Box(Modifier.fillMaxSize().border(1.dp, Gold.copy(alpha = .8f), RoundedCornerShape(50))) } }

private fun zoomForLens(lens: String): Float = when (lens) { "0,5×" -> 0f; "2×" -> .25f; "5×" -> .7f; else -> .08f }

private fun capturePhoto(context: android.content.Context, imageCapture: ImageCapture) {
    val name = "FotoStudio_${System.currentTimeMillis()}.jpg"
    val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg") }
    val output = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
    imageCapture.takePicture(output, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(result: ImageCapture.OutputFileResults) = Toast.makeText(context, "Fotografia uložená", Toast.LENGTH_SHORT).show()
        override fun onError(exception: ImageCaptureException) = Toast.makeText(context, "Fotografiu sa nepodarilo uložiť", Toast.LENGTH_SHORT).show()
    })
}
