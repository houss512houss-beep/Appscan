package com.example

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

sealed interface ScanUiState {
    object Idle : ScanUiState
    
    data class Scanning(
        val scannedCount: Int,
        val currentDirName: String,
        val matchedFiles: List<ScannedFile>
    ) : ScanUiState
    
    data class Completed(
        val totalScanned: Int,
        val matchedFiles: List<ScannedFile>
    ) : ScanUiState
    
    data class Cancelled(
        val totalScanned: Int,
        val matchedFiles: List<ScannedFile>
    ) : ScanUiState
    
    data class Error(val message: String) : ScanUiState
}

class ScanViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    // Default and toggleable extension filter state
    private val _selectedExtensions = MutableStateFlow(
        setOf(".txt", ".log", ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".zip", ".rar", ".7z", ".bak", ".key", ".pem", ".crt")
    )
    val selectedExtensions: StateFlow<Set<String>> = _selectedExtensions.asStateFlow()

    // Default and toggleable keyword filter state
    private val _selectedKeywords = MutableStateFlow(
        setOf("password", "key", "secret", "confidential", "backup", "database", "sql", "config", "token", "credential")
    )
    val selectedKeywords: StateFlow<Set<String>> = _selectedKeywords.asStateFlow()

    // Toast/Alert Notifications flow
    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    private var scanJob: Job? = null

    fun toggleExtension(ext: String) {
        val current = _selectedExtensions.value.toMutableSet()
        if (current.contains(ext)) {
            current.remove(ext)
        } else {
            current.add(ext)
        }
        _selectedExtensions.value = current
    }

    fun addCustomExtension(ext: String) {
        val formatted = if (ext.startsWith(".")) ext.lowercase().trim() else ".${ext.lowercase().trim()}"
        if (formatted.length > 1) {
            val current = _selectedExtensions.value.toMutableSet()
            current.add(formatted)
            _selectedExtensions.value = current
        }
    }

    fun removeExtension(ext: String) {
        val current = _selectedExtensions.value.toMutableSet()
        current.remove(ext)
        _selectedExtensions.value = current
    }

    fun toggleKeyword(kw: String) {
        val current = _selectedKeywords.value.toMutableSet()
        if (current.contains(kw)) {
            current.remove(kw)
        } else {
            current.add(kw)
        }
        _selectedKeywords.value = current
    }

    fun addCustomKeyword(kw: String) {
        val formatted = kw.lowercase().trim()
        if (formatted.isNotEmpty()) {
            val current = _selectedKeywords.value.toMutableSet()
            current.add(formatted)
            _selectedKeywords.value = current
        }
    }

    fun removeKeyword(kw: String) {
        val current = _selectedKeywords.value.toMutableSet()
        current.remove(kw)
        _selectedKeywords.value = current
    }

    fun clearExportStatus() {
        _exportStatus.value = null
    }

    // Main scanning function
    fun startScan() {
        // Cancel any existing scan
        scanJob?.cancel()

        scanJob = viewModelScope.launch {
            _uiState.value = ScanUiState.Scanning(0, "", emptyList())
            
            val extensions = _selectedExtensions.value
            val keywords = _selectedKeywords.value
            val rootDir = Environment.getExternalStorageDirectory()

            try {
                scanStorage(rootDir, extensions, keywords)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = ScanUiState.Error("خطأ أثناء الفحص: ${e.localizedMessage}")
            }
        }
    }

    fun cancelScan() {
        val current = _uiState.value
        scanJob?.cancel()
        if (current is ScanUiState.Scanning) {
            _uiState.value = ScanUiState.Cancelled(current.scannedCount, current.matchedFiles)
        }
    }

    private suspend fun scanStorage(
        baseDir: File,
        allowedExtensions: Set<String>,
        keywords: Set<String>
    ) = withContext(Dispatchers.IO) {
        val matchedList = mutableListOf<ScannedFile>()
        val stack = ArrayDeque<File>()
        stack.add(baseDir)
        var scannedCount = 0

        while (stack.isNotEmpty()) {
            // Check for coroutine cancellation (cooperative)
            if (!scanJob!!.isActive) {
                _uiState.value = ScanUiState.Cancelled(scannedCount, matchedList.toList())
                return@withContext
            }

            val currentDir = stack.removeLast()
            val files = try {
                currentDir.listFiles()
            } catch (e: Exception) {
                null
            }

            if (files != null) {
                for (file in files) {
                    if (!scanJob!!.isActive) {
                        _uiState.value = ScanUiState.Cancelled(scannedCount, matchedList.toList())
                        return@withContext
                    }

                    if (file.isDirectory) {
                        // Optimise by skipping common hidden, system, and media asset directories that cause deep traversals
                        val dirName = file.name
                        if (!dirName.startsWith(".") && 
                            dirName != "Android" && 
                            dirName != "com.android.providers.media" &&
                            dirName != "obb" &&
                            dirName != "data") {
                            stack.add(file)
                        }
                    } else {
                        scannedCount++
                        val name = file.name.lowercase()
                        val extension = "." + file.extension.lowercase()

                        val matchesExt = allowedExtensions.contains(extension)
                        val matchesKeyword = keywords.any { name.contains(it) }

                        if (matchesExt || matchesKeyword) {
                            val scannedFile = ScannedFile(
                                name = file.name,
                                path = file.absolutePath,
                                size = file.length()
                            )
                            matchedList.add(scannedFile)
                        }

                        // Throttle UI flow state updates to maintain pristine 60FPS
                        if (scannedCount % 120 == 0) {
                            val parentName = currentDir.name.ifEmpty { "وحدة التخزين" }
                            _uiState.value = ScanUiState.Scanning(
                                scannedCount = scannedCount,
                                currentDirName = parentName,
                                matchedFiles = matchedList.toList()
                            )
                        }
                    }
                }
            }
        }

        // Final completion state
        _uiState.value = ScanUiState.Completed(scannedCount, matchedList.toList())
    }

    // Exporting CSV File in Arabic format (UTF-8 with BOM)
    fun exportToCsv(context: Context, files: List<ScannedFile>) {
        if (files.isEmpty()) return

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val fileName = "FileScanner_Export_${System.currentTimeMillis()}.csv"
                    // \uFEFF is the UTF-8 Byte Order Mark (BOM) to force Excel/Windows to read Arabic correctly
                    val csvHeader = "\uFEFFالمسار كامل,اسم الملف,الحجم\n"
                    val csvBody = files.joinToString("\n") { file ->
                        val cleanPath = file.path.replace("\"", "\"\"")
                        val cleanName = file.name.replace("\"", "\"\"")
                        val formattedSize = formatFileSize(file.size)
                        "\"$cleanPath\",\"$cleanName\",\"$formattedSize\""
                    }
                    val csvContent = csvHeader + csvBody

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        val resolver = context.contentResolver
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(csvContent.toByteArray(Charsets.UTF_8))
                            }
                            "Downloads/$fileName"
                        } else {
                            null
                        }
                    } else {
                        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        if (!downloadDir.exists()) {
                            downloadDir.mkdirs()
                        }
                        val csvFile = File(downloadDir, fileName)
                        csvFile.writeText(csvContent, Charsets.UTF_8)
                        csvFile.absolutePath
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            _exportStatus.value = result
        }
    }

    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 بايت"
        val units = arrayOf("بايت", "كيلوبايت", "ميغابايت", "جيجابايت")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val divisor = Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.US, "%.1f %s", size / divisor, units[digitGroups])
    }
}
