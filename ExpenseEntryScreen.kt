package com.example.budgettracker.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.wear.compose.material3.LocalTextStyle

// Define your custom colors
val SageGreen = Color(0xFF9DC183)
val DarkSage = Color(0xFF6E8B57)

// Replace with the URL you copied from Google Apps Script
const val SCRIPT_URL = "https://script.google.com/macros/s/AKfycbwWndzPPV8ljrSqB6XHiY8aW9tPMqvrXdIsrC1SjgB89Y8AIxKAhgATZNfruK3pTWYp/exec"

@Composable
fun ExpenseEntryScreen() {
    var amount by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val client = remember { okhttp3.OkHttpClient() }

    // Auto-hide the popup card after 2.5 seconds
    LaunchedEffect(statusMessage) {
        if (statusMessage == "Logged!" || statusMessage == "Failed") {
            delay(2500)
            statusMessage = null
        }
    }

    val addAmount: (Int) -> Unit = { value ->
        amount = (amount.toIntOrNull()?.plus(value) ?: value).toString()
    }

    // Outer Box with black background to hold the UI and the Pop-up Overlay
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // Main UI Column
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp)) // Space for the top curve of the watch

            // 1. Big, Bold Total Expense Text
            Text(
                text = "¥${if (amount.isEmpty()) "0" else amount}",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // 2. The Big, Squeezed Add Buttons (+100, +10, +1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // Takes up the most available vertical space
                horizontalArrangement = Arrangement.spacedBy(2.dp) // Squeezed tight
            ) {
                val btnShape = RoundedCornerShape(10.dp)
                val btnModifier = Modifier.weight(1f).fillMaxHeight()
                val btnColors = ButtonDefaults.buttonColors(SageGreen, contentColor = Color.Black)

                Button(onClick = { addAmount(100) }, modifier = btnModifier, shape = btnShape, colors = btnColors) {
                    Text("+100", fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Button(onClick = { addAmount(10) }, modifier = btnModifier, shape = btnShape, colors = btnColors) {
                    Text("+10", fontWeight = FontWeight.Bold, fontSize = 25.sp, textAlign = TextAlign.Center)
                }
                Button(onClick = { addAmount(1) }, modifier = btnModifier, shape = btnShape, colors = btnColors) {
                    Text("+1", fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 3. The Smaller, Squeezed Bottom Buttons (Clear & Log)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp) // Locked smaller height
                    .padding(horizontal = 16.dp), // More horizontal padding for the bottom curve
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Button(
                    onClick = { amount = "" },
                    modifier = Modifier.weight(0.8f).fillMaxHeight(), // Clear is slightly narrower
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(Color.DarkGray, contentColor = Color.White)
                ) {
                    Text("Clear", fontSize = 10.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                }

                Button(
                    onClick = {
                        if (amount.isNotEmpty()) {
                            statusMessage = "Sending..."
                            coroutineScope.launch {
                                val success = sendDataToSheet(client, amount)
                                statusMessage = if (success) "Logged!" else "Failed"
                                if (success) amount = ""
                            }
                        }
                    },
                    modifier = Modifier.weight(1.2f).fillMaxHeight(), // Log is wider
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(DarkSage, contentColor = Color.White)
                ) {
                    Text("Log", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(0.dp)) // Space for the bottom curve
        }

        // 4. The Pop-Out Status Card Overlay
        AnimatedVisibility(
            visible = statusMessage != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {

            Text(
                text = statusMessage ?: "",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                style = LocalTextStyle.current.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.8f), // 80% opaque black
                        offset = Offset(2f, 4f), // Shifted 2 pixels right, 4 pixels down
                        blurRadius = 4f // Softens the edges of the shadow
                    )
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

        }
    }
}

suspend fun sendDataToSheet(client: OkHttpClient, amount: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply { put("amount", amount) }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(SCRIPT_URL).post(body).build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
