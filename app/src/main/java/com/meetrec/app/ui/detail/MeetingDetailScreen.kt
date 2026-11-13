package com.meetrec.app.ui.detail

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.meetrec.app.data.model.MeetingSession
import com.meetrec.app.data.model.MeetingSummary
import com.meetrec.app.data.model.MeetingTranscript
import com.meetrec.app.data.remote.SummaryProgress
import com.meetrec.app.data.remote.SummaryService
import com.meetrec.app.data.remote.TranscriptionProgress
import com.meetrec.app.data.remote.TranscriptionService
import com.meetrec.app.data.repository.MeetingRepository
import com.meetrec.app.pdf.NativePdfGenerator
import com.meetrec.app.ui.theme.Blue600
import com.meetrec.app.ui.theme.Emerald600
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingDetailScreen(
    session: MeetingSession,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { MeetingRepository.getInstance(context) }
    val transcriptionService = remember { TranscriptionService.getInstance(context) }
    val summaryService = remember { SummaryService.getInstance(context) }

    val transcriptionProgress by transcriptionService.progress.collectAsState()
    val summaryProgress by summaryService.progress.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Resumen, 1: Transcripción, 2: PDF
    var transcript by remember { mutableStateOf<MeetingTranscript?>(null) }
    var summary by remember { mutableStateOf<MeetingSummary?>(null) }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var isGeneratingPdf by remember { mutableStateOf(false) }

    fun refreshData() {
        transcript = repo.getTranscript(session.id)
        summary = repo.getSummary(session.id)
        val f = repo.getPdfFile(session.id)
        if (f.exists() && f.length() > 0) {
            pdfFile = f
        }
    }

    LaunchedEffect(session.id) {
        refreshData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${session.formattedDate} • ${session.formattedDuration}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Resumen Ejecutivo", fontSize = 12.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Transcripción", fontSize = 12.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Exportar PDF", fontSize = 12.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> SummaryTab(
                        session = session,
                        summary = summary,
                        transcript = transcript,
                        summaryProgress = summaryProgress,
                        transcriptionProgress = transcriptionProgress,
                        onGenerateSummary = {
                            scope.launch {
                                // Si no hay transcripción, primero transcribir
                                if (transcript == null) {
                                    val tRes = transcriptionService.transcribeMeeting(session)
                                    if (tRes.isSuccess) {
                                        transcript = tRes.getOrNull()
                                        transcript?.let { t ->
                                            val sRes = summaryService.generateSummary(session, t)
                                            if (sRes.isSuccess) {
                                                summary = sRes.getOrNull()
                                                refreshData()
                                            }
                                        }
                                    }
                                } else {
                                    val sRes = summaryService.generateSummary(session, transcript!!)
                                    if (sRes.isSuccess) {
                                        summary = sRes.getOrNull()
                                        refreshData()
                                    }
                                }
                            }
                        }
                    )
                    1 -> TranscriptTab(
                        session = session,
                        transcript = transcript,
                        transcriptionProgress = transcriptionProgress,
                        onStartTranscription = {
                            scope.launch {
                                val res = transcriptionService.transcribeMeeting(session)
                                if (res.isSuccess) {
                                    transcript = res.getOrNull()
                                    refreshData()
                                }
                            }
                        }
                    )
                    2 -> PdfExportTab(
                        session = session,
                        summary = summary,
                        transcript = transcript,
                        pdfFile = pdfFile,
                        isGenerating = isGeneratingPdf,
                        onGeneratePdf = {
                            scope.launch {
                                isGeneratingPdf = true
                                val res = NativePdfGenerator.generateMeetingPdf(context, session, summary, transcript)
                                isGeneratingPdf = false
                                if (res.isSuccess) {
                                    pdfFile = res.getOrNull()
                                    repo.markPdfGenerated(session.id)
                                    Toast.makeText(context, "PDF generado con éxito", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Error al generar PDF: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onOpenPdf = { file ->
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Instala un lector de PDF para ver el archivo", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSharePdf = { file ->
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "Informe de Reunión: ${session.title}")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir Informe PDF"))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryTab(
    session: MeetingSession,
    summary: MeetingSummary?,
    transcript: MeetingTranscript?,
    summaryProgress: SummaryProgress,
    transcriptionProgress: TranscriptionProgress,
    onGenerateSummary: () -> Unit
) {
    if (summaryProgress is SummaryProgress.Processing || transcriptionProgress is TranscriptionProgress.Processing) {
        val msg = if (transcriptionProgress is TranscriptionProgress.Processing) {
            (transcriptionProgress as TranscriptionProgress.Processing).status
        } else {
            (summaryProgress as SummaryProgress.Processing).message
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        return
    }

    if (summary == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(14.dp))
                Text("Resumen Ejecutivo Pendiente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "La IA sintetizará la reunión extrayendo el objetivo, los temas tratados, los acuerdos y la lista de tareas asignadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onGenerateSummary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (transcript == null) "Transcribir y Generar Resumen" else "Generar Resumen Ejecutivo")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Objetivo
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("🎯 1. Objetivo de la Reunión", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(summary.objective, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Puntos Tratados
            if (summary.topicsDiscussed.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("📋 2. Puntos Tratados y Discusión", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            summary.topicsDiscussed.forEach { topic ->
                                Text("• ${topic.title}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(topic.summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }

            // Acuerdos Clave
            if (summary.keyAgreements.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("🤝 3. Acuerdos Clave", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            summary.keyAgreements.forEach { agr ->
                                Text("✓  $agr", fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            // To-Do List (Tareas y Compromisos)
            if (summary.actionItems.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("⚡ 4. Plan de Acción (To-Do List)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            summary.actionItems.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.task, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        Text("Responsable: ${item.assignee}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(item.deadline, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptTab(
    session: MeetingSession,
    transcript: MeetingTranscript?,
    transcriptionProgress: TranscriptionProgress,
    onStartTranscription: () -> Unit
) {
    if (transcriptionProgress is TranscriptionProgress.Processing) {
        val p = transcriptionProgress as TranscriptionProgress.Processing
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(p.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        return
    }

    if (transcript == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(14.dp))
                Text("Transcripción Pendiente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Usa Whisper a través de OpenRouter para convertir el audio grabado en texto íntegro con marcas de tiempo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onStartTranscription,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Iniciar Transcripción con Whisper")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (transcript.segments.isNotEmpty()) {
                items(transcript.segments.size) { i ->
                    val seg = transcript.segments[i]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(seg.formattedTimestamp, color = Blue600, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(seg.text, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                item {
                    Text(transcript.fullText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PdfExportTab(
    session: MeetingSession,
    summary: MeetingSummary?,
    transcript: MeetingTranscript?,
    pdfFile: File?,
    isGenerating: Boolean,
    onGeneratePdf: () -> Unit,
    onOpenPdf: (File) -> Unit,
    onSharePdf: (File) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Informe PDF 100% Nativo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Genera un documento A4 profesional en el propio teléfono (sin servidores externos ni VPS). La primera página maqueta el resumen ejecutivo y las tareas, seguido de la transcripción completa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isGenerating) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Generando documento PDF en local...", fontSize = 13.sp)
                }
            }
        } else {
            Button(
                onClick = onGeneratePdf,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (pdfFile != null) "Regenerar PDF de Reunión" else "Generar PDF de Reunión")
            }
        }

        if (pdfFile != null && pdfFile.exists()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Emerald600.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Emerald600.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Emerald600, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Documento PDF Listo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${pdfFile.name} • ${(pdfFile.length() / 1024)} KB", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onOpenPdf(pdfFile) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Abrir PDF")
                        }

                        OutlinedButton(
                            onClick = { onSharePdf(pdfFile) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartir")
                        }
                    }
                }
            }
        }
    }
}

