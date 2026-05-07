package com.jabauth.diagnostic.ui.dashboard

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.io.File

data class SyntheticTestResult(
    val colorMode: Int,
    val status: TestStatus,
    val message: String,
    val decodeTimeMs: Long = 0
)

enum class TestStatus {
    PENDING, RUNNING, PASS, FAIL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyntheticTestDialog(
    onDismiss: () -> Unit,
    onDecodeImage: (File) -> String?,
    modifier: Modifier = Modifier
) {
    val syntheticPath = "/sdcard/Download/jabcode-synthetic-tests/synthetic-tests"
    val colorModes = listOf(4, 8, 16, 32, 64, 128)
    val expectedMessage = "The quick brown fox jumps over the lazy dog 1234567890"
    
    var testResults by remember { mutableStateOf<List<SyntheticTestResult>>(emptyList()) }
    var isRunning by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        // Initialize results
        testResults = colorModes.map { colorMode ->
            SyntheticTestResult(
                colorMode = colorMode,
                status = TestStatus.PENDING,
                message = "Waiting..."
            )
        }
    }
    
    fun runTests() {
        isRunning = true
        
        colorModes.forEachIndexed { index, colorMode ->
            val path = "$syntheticPath/test_${colorMode}color.png"
            val imageFile = File(path)
            
            if (!imageFile.exists()) {
                testResults = testResults.toMutableList().apply {
                    this[index] = SyntheticTestResult(
                        colorMode = colorMode,
                        status = TestStatus.FAIL,
                        message = "File not found: $path"
                    )
                }
                return@forEachIndexed
            }
            
            // Update to running
            testResults = testResults.toMutableList().apply {
                this[index] = SyntheticTestResult(
                    colorMode = colorMode,
                    status = TestStatus.RUNNING,
                    message = "Decoding..."
                )
            }
            
            // Decode
            val startTime = System.currentTimeMillis()
            val result = try {
                onDecodeImage(imageFile)
            } catch (e: Exception) {
                null
            }
            val elapsed = System.currentTimeMillis() - startTime
            
            // Update result
            testResults = testResults.toMutableList().apply {
                this[index] = if (result == expectedMessage) {
                    SyntheticTestResult(
                        colorMode = colorMode,
                        status = TestStatus.PASS,
                        message = "✅ PASS (${elapsed}ms)",
                        decodeTimeMs = elapsed
                    )
                } else {
                    SyntheticTestResult(
                        colorMode = colorMode,
                        status = TestStatus.FAIL,
                        message = "❌ FAIL: Got \"$result\"",
                        decodeTimeMs = elapsed
                    )
                }
            }
        }
        
        isRunning = false
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Synthetic Image Tests") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Click 'Run Tests' below to automatically test all 6 synthetic images (4, 8, 16, 32, 64, 128-color modes)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                items(testResults) { result ->
                    SyntheticTestResultCard(result)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { runTests() },
                enabled = !isRunning
            ) {
                Text(if (isRunning) "Testing..." else "Run Tests")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        modifier = modifier
    )
}

@Composable
private fun SyntheticTestResultCard(result: SyntheticTestResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (result.status) {
                TestStatus.PASS -> Color(0xFF1B5E20).copy(alpha = 0.1f)
                TestStatus.FAIL -> Color(0xFFB71C1C).copy(alpha = 0.1f)
                TestStatus.RUNNING -> Color(0xFFE65100).copy(alpha = 0.1f)
                TestStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (result.status) {
                        TestStatus.PASS -> Icons.Default.CheckCircle
                        TestStatus.FAIL -> Icons.Default.Close
                        TestStatus.RUNNING -> Icons.Default.Info
                        TestStatus.PENDING -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = when (result.status) {
                        TestStatus.PASS -> Color(0xFF2E7D32)
                        TestStatus.FAIL -> Color(0xFFC62828)
                        TestStatus.RUNNING -> Color(0xFFEF6C00)
                        TestStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Column {
                    Text(
                        text = "${result.colorMode}-color",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
