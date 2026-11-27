package com.myai.smartscanpro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class ScannedDocument(
    val id: String,
    val name: String,
    val date: String,
    val pageCount: Int,
    val pdfUriString: String?,
    val thumbnailUriString: String?
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF13EC6C),
                    onPrimary = Color(0xFF003917),
                    primaryContainer = Color(0xFF005224),
                    onPrimaryContainer = Color(0xFF94FDB2),
                    background = Color(0xFF0F1512),
                    onBackground = Color(0xFFE2E3DE),
                    surface = Color(0xFF161D19),
                    onSurface = Color(0xFFE2E3DE),
                    surfaceVariant = Color(0xFF26332C),
                    onSurfaceVariant = Color(0xFFBFC9C2),
                    error = Color(0xFFFFB4AB)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartScanProApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartScanProApp() {
    val context = LocalContext.current
    var documents by remember { mutableStateOf(loadDocuments(context)) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Rename Dialog State
    var showRenameDialog by remember { mutableStateOf(false) }
    var documentToRename by remember { mutableStateOf<ScannedDocument?>(null) }
    var newDocName by remember { mutableStateOf("") }

    // ML Kit Scanner Launcher
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.let { res ->
                val pdfUri = res.pdf?.uri
                val pages = res.pages
                val firstPageUri = pages?.firstOrNull()?.imageUri

                var pdfPath: String? = null
                var thumbPath: String? = null

                pdfUri?.let { uri ->
                    val file = copyUriToFile(context, uri, "pdf")
                    pdfPath = file?.absolutePath
                }

                firstPageUri?.let { uri ->
                    val file = copyUriToFile(context, uri, "jpg")
                    thumbPath = file?.absolutePath
                }

                val pageCount = pages?.size ?: 1
                val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val currentDate = dateFormat.format(Date())
                val docName = "Scan_${System.currentTimeMillis().toString().takeLast(6)}"

                val newDoc = ScannedDocument(
                    id = System.currentTimeMillis().toString(),
                    name = docName,
                    date = currentDate,
                    pageCount = pageCount,
                    pdfUriString = pdfPath,
                    thumbnailUriString = thumbPath
                )

                val updatedList = listOf(newDoc) + documents
                documents = updatedList
                saveDocuments(context, updatedList)
            }
        }
    }

    val startScanner = {
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
            )
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(context as Activity)
            .addOnSuccessListener { intentSender ->
                try {
                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }

    val filteredDocuments = documents.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Smart Scan Pro", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            "${documents.size} Documents Scanned",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = startScanner) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Quick Scan", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = startScanner,
                icon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                text = { Text("Scan Document", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search documents...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            if (filteredDocuments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Tap Scan to digitize your first document",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredDocuments, key = { it.id }) { doc ->
                        DocumentCard(
                            document = doc,
                            onOpen = {
                                doc.pdfUriString?.let { path ->
                                    openPdf(context, path)
                                }
                            },
                            onShare = {
                                doc.pdfUriString?.let { path ->
                                    sharePdf(context, path)
                                }
                            },
                            onRename = {
                                documentToRename = doc
                                newDocName = doc.name
                                showRenameDialog = true
                            },
                            onDelete = {
                                val updated = documents.filter { it.id != doc.id }
                                documents = updated
                                saveDocuments(context, updated)
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog && documentToRename != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = newDocName,
                    onValueChange = { newDocName = it },
                    singleLine = true,
                    label = { Text("Document Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newDocName.isNotBlank()) {
                        val updated = documents.map {
                            if (it.id == documentToRename!!.id) it.copy(name = newDocName.trim()) else it
                        }
                        documents = updated
                        saveDocuments(context, updated)
                    }
                    showRenameDialog = false
                    documentToRename = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    documentToRename = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DocumentCard(
    document: ScannedDocument,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(60.dp, 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (document.thumbnailUriString != null) {
                        AsyncImage(
                            model = File(document.thumbnailUriString),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.Center),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = document.date,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${document.pageCount} Pages",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onOpen) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open", fontSize = 12.sp)
                }
                TextButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 12.sp)
                }
                TextButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rename", fontSize = 12.sp)
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// Utility functions for Data and Intents
private fun copyUriToFile(context: Context, uri: Uri, extension: String): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.filesDir, "doc_${System.currentTimeMillis()}.$extension")
        val outputStream = file.outputStream()
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()
        file
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun saveDocuments(context: Context, docs: List<ScannedDocument>) {
    val prefs = context.getSharedPreferences("SmartScanPrefs", Context.MODE_PRIVATE)
    val jsonArray = JSONArray()
    for (doc in docs) {
        val obj = JSONObject().apply {
            put("id", doc.id)
            put("name", doc.name)
            put("date", doc.date)
            put("pageCount", doc.pageCount)
            put("pdfUriString", doc.pdfUriString ?: "")
            put("thumbnailUriString", doc.thumbnailUriString ?: "")
        }
        jsonArray.put(obj)
    }
    prefs.edit().putString("documents_list", jsonArray.toString()).apply()
}

private fun loadDocuments(context: Context): List<ScannedDocument> {
    val prefs = context.getSharedPreferences("SmartScanPrefs", Context.MODE_PRIVATE)
    val jsonString = prefs.getString("documents_list", null) ?: return emptyList()
    val list = mutableListOf<ScannedDocument>()
    try {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                ScannedDocument(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    date = obj.getString("date"),
                    pageCount = obj.getInt("pageCount"),
                    pdfUriString = obj.getString("pdfUriString").takeIf { it.isNotEmpty() },
                    thumbnailUriString = obj.getString("thumbnailUriString").takeIf { it.isNotEmpty() }
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

private fun openPdf(context: Context, path: String) {
    try {
        val file = File(path)
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun sharePdf(context: Context, path: String) {
    try {
        val file = File(path)
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF Document"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}