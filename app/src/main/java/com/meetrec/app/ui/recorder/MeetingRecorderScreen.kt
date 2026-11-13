package com.meetrec.app.ui.recorder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meetrec.app.data.repository.SettingsRepository
import com.meetrec.app.service.RecordingService
import com.meetrec.app.ui.components.ApiKeyDialog
import com.meetrec.app.ui.components.VuMeter
import com.meetrec.app.ui.theme.Rose600
import com.meetrec.app.ui.theme.VuRed

@Composable
fun MeetingRecorderScreen(
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository.getInstance(context) }
    val apiKey by settingsRepo.apiKeyFlow.collectAsState()
    val recordingState by RecordingService.recordingState.collectAsState()

    var meetingTitle by remember { mutableStateOf("Reunión de trabajo") }
    var participants by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            initialApiKey = apiKey,
            initialModel = settingsRepo.getSelectedModel(),
            onDismissRequest = { showApiKeyDialog = false },
            onSave = { newKey, newModel ->
                settingsRepo.setApiKey(newKey)
                settingsRepo.setSelectedModel(newModel)
                showApiKeyDialog = false
            }
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Banner informativo de API Key si no está configurada
        if (apiKey.isBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Clave de OpenRouter pendiente",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configura tu clave para transcribir y generar resúmenes con IA.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { showApiKeyDialog = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Configurar", fontSize = 11.sp)
                    }
                }
            }
        }

        // Formulario de datos de la reunión
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = meetingTitle,
                onValueChange = { if (!recordingState.isRecording) meetingTitle = it },
                label = { Text("Título de la reunión") },
                placeholder = { Text("Ej: Estrategia Q4, Sync de Producto...") },
                singleLine = true,
                enabled = !recordingState.isRecording,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = participants,
                onValueChange = { if (!recordingState.isRecording) participants = it },
                label = { Text("Participantes (opcional)") },
                placeholder = { Text("Ej: Carlos, Ana, Marcos...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp)) },
                enabled = !recordingState.isRecording,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Círculo central interactivo de grabación
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = if (recordingState.isRecording) 1.08f else 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        if (recordingState.isRecording) Rose600.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    )
                    .clickable {
                        if (!recordingState.isRecording) {
                            RecordingService.startRecording(
                                context = context,
                                title = meetingTitle.ifBlank { "Reunión de trabajo" },
                                participants = participants,
                                notes = notes
                            )
                        } else {
                            RecordingService.stopRecording(context)
                            onNavigateToHistory()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (recordingState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (recordingState.isRecording) "Detener" else "Iniciar",
                    modifier = Modifier.size(60.dp),
                    tint = if (recordingState.isRecording) Rose600 else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = recordingState.formattedDuration,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 38.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = if (recordingState.isRecording) Rose600 else MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            val statusText = if (recordingState.isRecording) {
                "Grabando reunión • Bloque #${recordingState.currentSegmentIndex}"
            } else {
                "Pulsa el micrófono para iniciar la reunión"
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        // Vúmetro y Botón de Acción Principal
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VuMeter(
                rmsDb = recordingState.rmsDb,
                normalizedLevel = recordingState.normalizedRms,
                isClipping = recordingState.isClipping,
                isRecording = recordingState.isRecording
            )

            AnimatedVisibility(visible = recordingState.errorMessage != null) {
                Text(
                    text = recordingState.errorMessage ?: "",
                    color = VuRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!recordingState.isRecording) {
                Button(
                    onClick = {
                        RecordingService.startRecording(
                            context = context,
                            title = meetingTitle.ifBlank { "Reunión de trabajo" },
                            participants = participants,
                            notes = notes
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Iniciar Grabación de Reunión",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = {
                        RecordingService.stopRecording(context)
                        onNavigateToHistory()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Finalizar y Guardar Reunión",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

