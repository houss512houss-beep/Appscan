package com.example

import android.Manifest
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Modern Slate Dark Palette with Neon Accent Color matching the app icon
val SlateDark = Color(0xFF0F1115)
val CardBackground = Color(0xFF1B1E24)
val AccentTeal = Color(0xFF00E5FF)
val AccentTealSecondary = Color(0xFF00B0FF)
val ActiveChipColor = Color(0xFF1E3A47)
val InactiveChipColor = Color(0xFF242933)
val BorderColor = Color(0xFF2E3544)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileScannerApp(viewModel: ScanViewModel) {
    val context = LocalContext.current
    
    // Enforce RTL Layout Direction explicitly for Arab support
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        
        // Check storage permission states reactively
        var hasStoragePermission by remember { mutableStateOf(checkPermissions(context)) }
        
        // Define settings request launcher for Android 11+ (Manage Files)
        val manageStorageLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            hasStoragePermission = checkPermissions(context)
        }
        
        // Define legacy runtime permission launcher for API <= 29
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            hasStoragePermission = permissions.values.all { it }
        }

        // Keep observing permission state whenever activity receives focus or is active
        DisposableEffect(Unit) {
            hasStoragePermission = checkPermissions(context)
            onDispose {}
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderZip,
                                contentDescription = null,
                                tint = AccentTeal,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "FileScanner",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = SlateDark
                    )
                )
            },
            containerColor = SlateDark
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!hasStoragePermission) {
                    PermissionBlockedScreen(
                        onRequestPermission = {
                            requestStorageAccess(
                                context = context,
                                legacyLauncher = requestPermissionLauncher,
                                manageLauncher = manageStorageLauncher
                            )
                        }
                    )
                } else {
                    MainScannerScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun PermissionBlockedScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    Brush.sweepGradient(listOf(AccentTeal, AccentTealSecondary, AccentTeal)),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SlateDark, RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = AccentTeal,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(28.dp))
        
        Text(
            text = stringResource(R.string.perm_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(14.dp))
        
        Text(
            text = stringResource(R.string.perm_desc),
            fontSize = 14.sp,
            color = Color.LightGray.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(36.dp))
        
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentTeal,
                contentColor = SlateDark
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp)
        ) {
            Text(
                text = stringResource(R.string.perm_button),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MainScannerScreen(viewModel: ScanViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedExtensions by viewModel.selectedExtensions.collectAsStateWithLifecycle()
    val selectedKeywords by viewModel.selectedKeywords.collectAsStateWithLifecycle()
    val exportStatus by viewModel.exportStatus.collectAsStateWithLifecycle()

    var showConfigSection by remember { mutableStateOf(false) }
    var customExtInput by remember { mutableStateOf("") }
    var customKwInput by remember { mutableStateOf("") }

    // Toast/Alert configuration whenever export changes
    LaunchedEffect(exportStatus) {
        exportStatus?.let { path ->
            Toast.makeText(
                context,
                context.getString(R.string.export_success, path),
                Toast.LENGTH_LONG
            ).show()
            viewModel.clearExportStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        
        // Scan Control Hub Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                
                // Head status section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = when (uiState) {
                                is ScanUiState.Idle -> "الحالة: جاهز"
                                is ScanUiState.Scanning -> "الحالة: جار المسح..."
                                is ScanUiState.Completed -> "الحالة: مكتمل"
                                is ScanUiState.Cancelled -> "الحالة: ملغى"
                                is ScanUiState.Error -> "الحالة: خطأ"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        
                        Text(
                            text = when (val state = uiState) {
                                is ScanUiState.Scanning -> "فحص: " + state.currentDirName
                                is ScanUiState.Completed -> stringResource(R.string.scan_completed)
                                is ScanUiState.Cancelled -> stringResource(R.string.scan_cancelled)
                                is ScanUiState.Error -> state.message
                                else -> stringResource(R.string.scan_idle)
                            },
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 220.dp)
                        )
                    }

                    // Configuration Toggle Button
                    IconButton(
                        onClick = { showConfigSection = !showConfigSection },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (showConfigSection) ActiveChipColor else InactiveChipColor
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "تعديل التصفية",
                            tint = if (showConfigSection) AccentTeal else Color.White
                        )
                    }
                }

                // Progress Bar layout
                AnimatedVisibility(
                    visible = uiState is ScanUiState.Scanning,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        LinearProgressIndicator(
                            color = AccentTeal,
                            trackColor = Color.DarkGray,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val state = uiState as? ScanUiState.Scanning
                            Text(
                                text = stringResource(R.string.scanned_files_count, state?.scannedCount ?: 0),
                                fontSize = 11.sp,
                                color = AccentTeal
                            )
                            Text(
                                text = "العثور على ${state?.matchedFiles?.size ?: 0} ملف",
                                fontSize = 11.sp,
                                color = Color.LightGray
                            )
                        }
                    }
                }

                // Expandable parameters/filters configurator
                AnimatedVisibility(
                    visible = showConfigSection,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Divider(color = BorderColor, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Extension custom addition row
                        Text(
                            text = stringResource(R.string.filter_ext_label) + " (${selectedExtensions.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = customExtInput,
                                onValueChange = { customExtInput = it },
                                placeholder = { Text("أضف امتداداً (مثال: .json)", fontSize = 12.sp) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = InactiveChipColor,
                                    unfocusedContainerColor = InactiveChipColor,
                                    focusedIndicatorColor = AccentTeal,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (customExtInput.isNotBlank()) {
                                        viewModel.addCustomExtension(customExtInput)
                                        customExtInput = ""
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = ActiveChipColor),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "أضف", tint = AccentTeal)
                            }
                        }

                        // Horizontal Extensions scroll
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(selectedExtensions.toList()) { ext ->
                                FilterChip(
                                    selected = true,
                                    onClick = { viewModel.toggleExtension(ext) },
                                    label = { Text(ext, fontSize = 11.sp, color = Color.White) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ActiveChipColor
                                    ),
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "إزالة",
                                            modifier = Modifier.size(12.dp),
                                            tint = AccentTeal
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Keyword Add Option
                        Text(
                            text = stringResource(R.string.filter_kw_label) + " (${selectedKeywords.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = customKwInput,
                                onValueChange = { customKwInput = it },
                                placeholder = { Text("أضف كلمة مفتاحية (مثال: dump)", fontSize = 12.sp) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = InactiveChipColor,
                                    unfocusedContainerColor = InactiveChipColor,
                                    focusedIndicatorColor = AccentTeal,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (customKwInput.isNotBlank()) {
                                        viewModel.addCustomKeyword(customKwInput)
                                        customKwInput = ""
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = ActiveChipColor),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "أضف", tint = AccentTeal)
                            }
                        }

                        // Horizontal Keywords scroll
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(selectedKeywords.toList()) { kw ->
                                FilterChip(
                                    selected = true,
                                    onClick = { viewModel.toggleKeyword(kw) },
                                    label = { Text(kw, fontSize = 11.sp, color = Color.White) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ActiveChipColor
                                    ),
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "إزالة",
                                            modifier = Modifier.size(12.dp),
                                            tint = AccentTeal
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState is ScanUiState.Scanning) {
                        Button(
                            onClick = { viewModel.cancelScan() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.cancel_scan), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startScan() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentTeal,
                                contentColor = SlateDark
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.start_scan), fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    // CSV export button (enable only if there are matched results)
                    val matchedList = when (val state = uiState) {
                        is ScanUiState.Scanning -> state.matchedFiles
                        is ScanUiState.Completed -> state.matchedFiles
                        is ScanUiState.Cancelled -> state.matchedFiles
                        else -> emptyList()
                    }

                    Button(
                        onClick = { viewModel.exportToCsv(context, matchedList) },
                        enabled = matchedList.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InactiveChipColor,
                            contentColor = Color.White,
                            disabledContainerColor = InactiveChipColor.copy(alpha = 0.4f),
                            disabledContentColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصدير CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Summary result header layout
        val scannedFilesList = when (val state = uiState) {
            is ScanUiState.Scanning -> state.matchedFiles
            is ScanUiState.Completed -> state.matchedFiles
            is ScanUiState.Cancelled -> state.matchedFiles
            else -> emptyList()
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (scannedFilesList.isNotEmpty()) {
                    stringResource(R.string.results_count, scannedFilesList.size)
                } else {
                    "النتائج"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray
            )
            
            if (scannedFilesList.isNotEmpty()) {
                Text(
                    text = "اضغط مطولاً لنسخ المسار",
                    fontSize = 11.sp,
                    color = AccentTeal.copy(alpha = 0.8f)
                )
            }
        }

        // List View of scanned matched files
        if (scannedFilesList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when (uiState) {
                            is ScanUiState.Scanning -> stringResource(R.string.scanning_status)
                            else -> stringResource(R.string.no_files_found)
                        },
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(scannedFilesList) { checkedFile ->
                    FileResultItem(
                        scannedFile = checkedFile,
                        onLongClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("File Path", checkedFile.path)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.path_copied), Toast.LENGTH_SHORT).show()
                        },
                        sizeFormatted = viewModel.formatFileSize(checkedFile.size)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileResultItem(
    scannedFile: ScannedFile,
    onLongClick: () -> Unit,
    sizeFormatted: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(InactiveChipColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (scannedFile.name.substringAfterLast(".", "").lowercase()) {
                        "pdf" -> Icons.Default.InsertDriveFile
                        "zip", "rar", "7z" -> Icons.Default.FolderZip
                        "key", "pem", "crt" -> Icons.Default.Key
                        "sql", "db" -> Icons.Default.Storage
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = AccentTeal,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = scannedFile.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = scannedFile.path,
                    fontSize = 11.sp,
                    color = Color.LightGray.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Left // Paths are in Latin characters, left align them naturally
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = sizeFormatted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentTeal
                )
            }
        }
    }
}

// Permissions check helper
fun checkPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        val readPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        val writePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        readPerm == PackageManager.PERMISSION_GRANTED && writePerm == PackageManager.PERMISSION_GRANTED
    }
}

// Request permission navigation helper
fun requestStorageAccess(
    context: Context,
    legacyLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    manageLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            manageLauncher.launch(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            manageLauncher.launch(intent)
        }
    } else {
        legacyLauncher.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }
}
