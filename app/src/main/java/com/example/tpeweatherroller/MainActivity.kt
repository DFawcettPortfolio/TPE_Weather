package com.example.tpeweatherroller

// --- Android & Jetpack Compose imports ---
import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tpeweatherroller.ui.theme.TPEWeatherRollerTheme
import kotlin.random.Random

// --- Weather Constants ---
// These describe various weather conditions for the world
const val BlecksShade = "Shadows stretch unnaturally. The first short rest the party takes each day may be completed in 5 minutes."
const val AzrahsWinds = "A gentle tailwind urges travelers onward. All creatures gain +5 feet to base movement speed."
const val KalisTears = "Steady rainfall blesses the earth. The land becomes dotted with clean water sources, sufficient for safe drinking."
const val MaeoriasClouds = "The sky is overcast with peaceful gray. On the first Wisdom saving throw each day, each creature gains advantage."
const val HushsEmptiness = "A cloudless silence. Bright sun counts as bright light; spotting illusions is at disadvantage due to the intense glare and stillness."
const val IAsFog = "A thin mist obscures all. Vision beyond 30 feet is lightly obscured. Perception checks relying on sight are at disadvantage past that."
const val SuthisScorcher = "A relentless desert sun. Unshaded creatures take 1d6 fire damage per hour of travel (enhanced: 1d12 in desert regions)."
const val UnisRot = "Decay clings to wounds. If a creature is bloodied, it takes 1d4 necrotic damage at the start of its turn."
const val DaunthurgesLongDay = "Time stretches painfully. Gain 1 level of exhaustion per day, max 1 from this effect."


// --- MainActivity ---
class MainActivity : ComponentActivity() {

    //  @RequiresApi(Build.VERSION_CODES.Q) -> annotation declares that this method should only run on Android 10 (API 29) or higher.
    // Reason: PDF export uses `MediaStore.Downloads`, which requires API 29+. Without this annotation, trying to use those features on older devices would cause crashes.
    @RequiresApi(Build.VERSION_CODES.Q)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enables drawing behind the system bars (status and navigation bar).
        enableEdgeToEdge()

        setContent {
            TPEWeatherRollerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), // Makes the Surface take up the full screen
                    color = Color(0xFFA56BC0) // Light purple background color for the entire screen
                ) {
                   Greeting()
                }
            }
        }
    }
}


// --- Main UI Composable ---
@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun Greeting(modifier: Modifier = Modifier) {
    // --- State Variables ---
    var rollResult by remember { mutableStateOf("") } // stores result of current weather roll
    var weatherDataRange by remember { mutableStateOf<String?>(null) } // for multi-day exports
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var showDurationDialog by remember { mutableStateOf(false) } // for showing dialog
    var inputDay by remember { mutableStateOf("") }
    var inputMonth by remember { mutableStateOf("") }
    var inputYear by remember { mutableStateOf("") }
    var inputDuration by remember { mutableStateOf("") }

    // --- Layout ---
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text("Weather", fontSize = 28.sp, modifier = Modifier.padding(bottom = 16.dp))

        // --- Display the result text with scroll ---
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            Text(rollResult, fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Single Day Weather Button ---
        Button(
            onClick = {
                rollResult = rollIt().first
                weatherDataRange = null // clear previous data if any
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Roll for weather", fontSize = 26.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Multi-Day Simulation Button ---
        Button(
            onClick = { showDurationDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Roll for custom duration", fontSize = 20.sp)
        }

        // --- Export Button: only shows when data is present ---
        if (weatherDataRange != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    weatherDataRange?.let { exportToPdf(context, it) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export to PDF", fontSize = 20.sp)
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }

    // --- Dialog: Custom Simulation Entry ---
    if (showDurationDialog) {
        AlertDialog(
            onDismissRequest = { showDurationDialog = false },
            title = { Text("Custom Weather Simulation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = inputMonth, onValueChange = { inputMonth = it }, label = { Text("Start Month (1–12)") }, singleLine = true)
                    OutlinedTextField(value = inputDay, onValueChange = { inputDay = it }, label = { Text("Start Day (1–30)") }, singleLine = true)
                    OutlinedTextField(value = inputYear, onValueChange = { inputYear = it }, label = { Text("Start Year") }, singleLine = true)
                    OutlinedTextField(value = inputDuration, onValueChange = { inputDuration = it }, label = { Text("Duration (in days)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val day = inputDay.toIntOrNull()
                    val month = inputMonth.toIntOrNull()
                    val year = inputYear.toIntOrNull()
                    val duration = inputDuration.toIntOrNull()

                    if (day != null && day in 1..30 &&
                        month != null && month in 1..12 &&
                        year != null && duration != null && duration > 0) {

                        val startDayOfYear = (month - 1) * 30 + day
                        val result = weatherDuration(startDayOfYear, duration, year)
                        rollResult = result
                        weatherDataRange = result
                        showDurationDialog = false
                    }
                }) { Text("Roll") }
            },
            dismissButton = {
                TextButton(onClick = { showDurationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


// --- Single Roll Logic ---
// Randomly selects weather from a 1-10 list with special case for roll == 10
fun rollIt(): Pair<String, Int> {
    val weatherRoll = Random.nextInt(1, 11)

    val weatherMap = mapOf(
        1 to Triple(BlecksShade, "Bleck's Shade", 4),
        2 to Triple(AzrahsWinds, "Azrah's Winds", 6),
        3 to Triple(KalisTears, "Kali's Tears", 8),
        4 to Triple(MaeoriasClouds, "Maeoria's Clouds", 10),
        5 to Triple(HushsEmptiness, "Hush's Emptiness", 12),
        6 to Triple(IAsFog, "IA's Fog", 12),
        7 to Triple(SuthisScorcher, "Suthis' Scorcher", 10),
        8 to Triple(UnisRot, "Uni's Rot", 8),
        9 to Triple(DaunthurgesLongDay, "Daunthurge's Long Day", 6)
    )

    // Special event: Nyx's Game (combines two effects)
    if (weatherRoll == 10) {
        val options = (1..9).shuffled()
        val (effect1, title1, _) = weatherMap[options[0]]!!
        val (effect2, title2, _) = weatherMap[options[1]]!!
        val duration = Random.nextInt(1, 5)

        return Pair("Nyx's Game: $title1 & $title2\n\n$effect1\n   ~and~ \n$effect2\n\nDuration: $duration days", duration)
    } else {
        val (effect, title, dieMax) = weatherMap[weatherRoll] ?: Triple("Unknown effect", "Unknown", 0)
        val duration = Random.nextInt(1, dieMax + 1)
        return Pair("$title:\n$effect\n\nDuration: $duration days", duration)
    }
}


// --- Multi-Day Simulation Logic ---
// Computes sequences of weather with accurate dates and durations
fun weatherDuration(startDayOfYear: Int, durationInDays: Int, startYear: Int): String {
    val log = StringBuilder()
    var totalDays = 0

    while (totalDays < durationInDays) {
        val (result, duration) = rollIt()
        val startDay = startDayOfYear + totalDays
        val endDay = (startDay + duration - 1).coerceAtMost(startDayOfYear + durationInDays - 1)

        // Convert day-of-year to MM/DD/YYYY
        val sYear = startYear + (startDay - 1) / 360
        val sDOY = (startDay - 1) % 360 + 1
        val sMonth = (sDOY - 1) / 30 + 1
        val sDay = (sDOY - 1) % 30 + 1

        val eYear = startYear + (endDay - 1) / 360
        val eDOY = (endDay - 1) % 360 + 1
        val eMonth = (eDOY - 1) / 30 + 1
        val eDay = (eDOY - 1) % 30 + 1

        val dateRange = if (startDay == endDay) {
            "$sMonth/$sDay/$sYear"
        } else {
            "$sMonth/$sDay/$sYear - $eMonth/$eDay/$eYear"
        }

        log.append("$dateRange\n$result")

        // Note the full (natural) ending date if this is the final entry
        if (totalDays + duration >= durationInDays) {
            val nEnd = startDay + duration - 1
            val nYear = startYear + (nEnd - 1) / 360
            val nDOY = (nEnd - 1) % 360 + 1
            val nMonth = (nDOY - 1) / 30 + 1
            val nDay = (nDOY - 1) % 30 + 1
            log.append("\n(Ends $nMonth/$nDay/$nYear)")
        }

        log.append("\n\n")
        totalDays += duration
    }

    return log.toString()
}


// --- PDF Export Utility ---
// Converts a string of weather data into a multi-page PDF saved to Downloads
@RequiresApi(Build.VERSION_CODES.Q)
fun exportToPdf(context: Context, content: String, filename: String = "weather.pdf") {
    try {
        val document = PdfDocument()
        val paint = Paint().apply {
            textSize = 12f
            isAntiAlias = true
        }

        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val lineHeight = 20f
        val usableWidth = pageWidth - 2 * margin
        val maxLinesPerPage = ((pageHeight - 2 * margin) / lineHeight).toInt()

        // Wrap content by word into lines
        val lines = content.lines().flatMap { line -> wrapLine(line, paint, usableWidth) }

        // Break into pages
        val pages = lines.chunked(maxLinesPerPage)
        for ((pageIndex, pageLines) in pages.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            var y = margin
            for (line in pageLines) {
                canvas.drawText(line, margin, y, paint)
                y += lineHeight
            }

            document.finishPage(page)
        }

        // Write file to Downloads
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri).use { outputStream ->
                document.writeTo(outputStream!!)
            }
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            Toast.makeText(context, "Export successful!", Toast.LENGTH_SHORT).show()
        }

        document.close()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Export failed!", Toast.LENGTH_SHORT).show()
    }
}

// --- Text wrapping utility for PDF generation ---
private fun wrapLine(line: String, paint: Paint, maxWidth: Float): List<String> {
    val words = line.split(" ")
    val wrapped = mutableListOf<String>()
    var currentLine = ""

    for (word in words) {
        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
        if (paint.measureText(testLine) <= maxWidth) {
            currentLine = testLine
        } else {
            wrapped.add(currentLine)
            currentLine = word
        }
    }

    if (currentLine.isNotEmpty()) wrapped.add(currentLine)
    return wrapped
}


