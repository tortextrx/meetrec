package com.meetrec.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meetrec.app.data.repository.SettingsRepository

@Composable
fun ApiKeyDialog(
    initialApiKey: String,
    initialModel: String,
    onDismissRequest: () -> Unit,
    onSave: (apiKey: String, model: String) -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(initialApiKey) }
    var selectedModel by remember { mutableStateOf(initialModel) }
    var showPassword by remember { mutableStateOf(false) }

    val models = listOf(
        Pair(SettingsRepository.DEFAULT_MODEL, "GPT-4o mini (Rápido y económico)"),
        Pair(SettingsRepository.MODEL_CLAUDE, "Claude 3.5 Haiku (Excelente síntesis)"),
        Pair(SettingsRepository.MODEL_LLAMA, "Llama 3.3 70B (Open Source)")
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clave API de OpenRouter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "MeetRec utiliza OpenRouter para transcribir el audio con Whisper y redactar el resumen ejecutivo con el modelo que elijas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API Key (sk-or-v1-...)") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Modelo de IA para el Resumen:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                models.forEach { (modelId, label) ->
                    FilterChip(
                        selected = selectedModel == modelId,
                        onClick = { selectedModel = modelId },
                        label = { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Tu clave se almacena exclusivamente en tu teléfono y nunca se comparte con ningún servidor intermediario.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(apiKeyInput.trim(), selectedModel) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Guardar Configuración")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancelar")
            }
        }
    )
}

