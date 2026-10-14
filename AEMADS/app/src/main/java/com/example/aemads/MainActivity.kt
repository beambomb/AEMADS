package com.example.aemads

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import android.os.Environment
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.core.view.WindowCompat
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.component.shape.shader.verticalGradient
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePickerDefaults
import kotlinx.coroutines.launch
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import coil.compose.AsyncImage

// --- THEME COLORS ---
val DarkNavy = Color(0xFF0B0F19)
val SurfaceNavy = Color(0xFF151C2C)
val NeonAmber = Color(0xFFFFB800)
val CriticalRed = Color(0xFFFF4D4D)
val NeonCyan = Color(0xFF00E5FF)
val TextGray = Color(0xFF8A95A5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setupNotificationChannels()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = DarkNavy,
                    surface = SurfaceNavy,
                    primary = NeonCyan,
                    error = CriticalRed
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val viewModel: DashboardViewModel = viewModel()
                    AemadsApp(context = this, viewModel = viewModel)
                }
            }
        }
    }

    private fun setupNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val highChannel = NotificationChannel("spike_alert", "Spike Alerts", NotificationManager.IMPORTANCE_HIGH)
        highChannel.enableVibration(true)
        val silentChannel = NotificationChannel("drift_alert", "Drift Alerts", NotificationManager.IMPORTANCE_DEFAULT)
        silentChannel.setSound(null, null)
        silentChannel.enableVibration(false)
        manager.createNotificationChannel(highChannel)
        manager.createNotificationChannel(silentChannel)
    }
}

@Composable
fun AemadsApp(context: MainActivity, viewModel: DashboardViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "login") {
        composable("login") { LoginScreen(navController, viewModel) }
        composable("signup") { SignUpScreen(navController, viewModel) }
        composable("main") { 
            MainScreen(context, viewModel, onNavigateToLogin = {
                navController.navigate("login") { 
                    popUpTo(0) // clear backstack
                }
            }) 
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavHostController, viewModel: DashboardViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var plant by remember { mutableStateOf("Shop 1 - Stamping & Press") }
    var expanded by remember { mutableStateOf(false) }

    val loginState by viewModel.loginState.collectAsState()

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            navController.navigate("main") { popUpTo("login") { inclusive = true } }
            viewModel.resetLoginState()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(DarkNavy)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Factory, contentDescription = "Logo", tint = NeonCyan, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("AEMADS", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Energy Anomaly Early Warning System", color = TextGray, fontSize = 14.sp)
        
        Spacer(modifier = Modifier.height(48.dp))
        
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, 
                unfocusedTextColor = Color.White,
                focusedBorderColor = NeonCyan
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, 
                unfocusedTextColor = Color.White,
                focusedBorderColor = NeonCyan
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = plant, onValueChange = {}, readOnly = true,
                label = { Text("Select Shop Location") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, 
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = NeonCyan
                ),
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Shop 1 - Stamping & Press") }, onClick = { plant = "Shop 1 - Stamping & Press"; expanded = false })
                DropdownMenuItem(text = { Text("Shop 2 - Body & Welding") }, onClick = { plant = "Shop 2 - Body & Welding"; expanded = false })
                DropdownMenuItem(text = { Text("Shop 3 - Paint Shop") }, onClick = { plant = "Shop 3 - Paint Shop"; expanded = false })
                DropdownMenuItem(text = { Text("Shop 4 - General Assembly") }, onClick = { plant = "Shop 4 - General Assembly"; expanded = false })
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        if (loginState is LoginState.Error) {
            Text((loginState as LoginState.Error).message, color = CriticalRed, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { viewModel.login(email, password, plant) },
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = loginState !is LoginState.Loading
        ) {
            if (loginState is LoginState.Loading) {
                CircularProgressIndicator(color = DarkNavy, modifier = Modifier.size(24.dp))
            } else {
                Text("Sign In to Control Room", color = DarkNavy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = { navController.navigate("signup") }) {
            Text("Don't have an account? Sign Up", color = NeonCyan)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(navController: NavHostController, viewModel: DashboardViewModel) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val signUpState by viewModel.signUpState.collectAsState()

    LaunchedEffect(signUpState) {
        if (signUpState is SignUpState.Success) {
            navController.popBackStack()
            viewModel.resetSignUpState()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(DarkNavy)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.PersonAdd, contentDescription = "Sign Up", tint = NeonCyan, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("CREATE ACCOUNT", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Join AEMADS Platform", color = TextGray, fontSize = 14.sp)
        
        Spacer(modifier = Modifier.height(48.dp))
        
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Full Name") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, 
                unfocusedTextColor = Color.White,
                focusedBorderColor = NeonCyan
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email Address") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, 
                unfocusedTextColor = Color.White,
                focusedBorderColor = NeonCyan
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, 
                unfocusedTextColor = Color.White,
                focusedBorderColor = NeonCyan
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        if (signUpState is SignUpState.Error) {
            Text((signUpState as SignUpState.Error).message, color = CriticalRed, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { viewModel.signUp(name, email, password) },
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = signUpState !is SignUpState.Loading
        ) {
            if (signUpState is SignUpState.Loading) {
                CircularProgressIndicator(color = DarkNavy, modifier = Modifier.size(24.dp))
            } else {
                Text("Register", color = DarkNavy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = { navController.popBackStack() }) {
            Text("Back to Login", color = TextGray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(context: MainActivity, viewModel: DashboardViewModel, onNavigateToLogin: () -> Unit = {}) {
    var selectedTab by remember { mutableStateOf(0) }
    val session by viewModel.currentUserSession.collectAsState()
    val uploadingReport by viewModel.uploadingReport.collectAsState()
    
    // Camera Logic
    var showReportDialog by remember { mutableStateOf(false) }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var reportNotes by remember { mutableStateOf("") }
    var reportShop by remember { mutableStateOf("") }
    var reportAnomaly by remember { mutableStateOf("") }
    var expandedShop by remember { mutableStateOf(false) }
    val shops = listOf("Shop 1 - Stamping & Press", "Shop 2 - Body & Welding", "Shop 3 - Paint Shop", "Shop 4 - General Assembly")
    
    val alertState by viewModel.alertState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.anomalyEvent.collect { record ->
            val channelId = if(record.type.contains("Spike")) "spike_alert" else "drift_alert"
            val title = if(record.type.contains("Spike")) "CRITICAL SPIKE DETECTED" else "SYSTEM DRIFT WARNING"
            val body = "${record.liniName} - ${record.triggerValue}"
            
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(if(record.type.contains("Spike")) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
            
            try {
                with(NotificationManagerCompat.from(context)) {
                    notify(System.currentTimeMillis().toInt(), builder.build())
                }
            } catch (e: SecurityException) {
                // Handle permission not granted
            }
        }
    }
    
    // Set default shop when session is available
    LaunchedEffect(session) {
        if (session != null && reportShop.isEmpty()) {
            reportShop = session?.plant ?: "Shop 1 - Stamping & Press"
        }
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            capturedImage = bitmap
            showReportDialog = true
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                // Convert hardware bitmap to software for compatibility
                val softwareBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                capturedImage = softwareBitmap
                showReportDialog = true
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showMediaActionDialog by remember { mutableStateOf(false) }

    if (showMediaActionDialog) {
        AlertDialog(
            onDismissRequest = { showMediaActionDialog = false },
            title = { Text("Add Damage Photo") },
            text = { Text("Choose a photo source for your damage report.") },
            confirmButton = {
                Button(onClick = {
                    showMediaActionDialog = false
                    cameraLauncher.launch()
                }, colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)) {
                    Text("Camera", color = DarkNavy)
                }
            },
            dismissButton = {
                Button(onClick = {
                    showMediaActionDialog = false
                    galleryLauncher.launch("image/*")
                }, colors = ButtonDefaults.buttonColors(containerColor = SurfaceNavy)) {
                    Text("Gallery", color = Color.White)
                }
            }
        )
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Submit Damage Report") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    capturedImage?.let { 
                        Image(bitmap = it.asImageBitmap(), contentDescription = "Captured", modifier = Modifier.height(150.dp).fillMaxWidth(), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.height(16.dp))
                    
                    ExposedDropdownMenuBox(expanded = expandedShop, onExpandedChange = { expandedShop = !expandedShop }) {
                        OutlinedTextField(
                            value = reportShop, 
                            onValueChange = {}, 
                            readOnly = true,
                            label = { Text("Shop Context") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedShop) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = expandedShop, onDismissRequest = { expandedShop = false }) {
                            shops.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        reportShop = selectionOption
                                        expandedShop = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reportAnomaly, 
                        onValueChange = { reportAnomaly = it },
                        label = { Text("Related Anomaly (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Spike Case 2") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reportNotes, 
                        onValueChange = { reportNotes = it },
                        label = { Text("Damage Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.uploadReport(capturedImage!!, reportShop, reportAnomaly, reportNotes)
                    showReportDialog = false
                    reportNotes = ""
                    reportAnomaly = ""
                    selectedTab = 3 // Go to history tab
                }, enabled = !uploadingReport) { 
                    if (uploadingReport) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = DarkNavy)
                    } else {
                        Text("Upload to Supabase") 
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
            }
        )
    }
    
    Scaffold(
        bottomBar = {
            BottomAppBar(
                containerColor = SurfaceNavy,
                contentColor = Color.White,
                tonalElevation = 8.dp
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedTab = 0 }, modifier = Modifier.weight(1f)) { Icon(if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home, "Home", tint = if (selectedTab == 0) NeonCyan else TextGray) }
                    IconButton(onClick = { selectedTab = 1 }, modifier = Modifier.weight(1f)) { Icon(if (selectedTab == 1) Icons.Filled.Warning else Icons.Outlined.Warning, "Anomalies", tint = if (selectedTab == 1) NeonCyan else TextGray) }
                    
                    // FAB is now inline with other buttons
                    FloatingActionButton(
                        onClick = { showMediaActionDialog = true },
                        containerColor = NeonAmber,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, "Report Damage", tint = DarkNavy, modifier = Modifier.size(28.dp))
                    }

                    IconButton(onClick = { selectedTab = 3 }, modifier = Modifier.weight(1f)) { Icon(if (selectedTab == 3) Icons.Filled.History else Icons.Outlined.History, "Reports", tint = if (selectedTab == 3) NeonCyan else TextGray) }
                    IconButton(onClick = { selectedTab = 4 }, modifier = Modifier.weight(1f)) { Icon(if (selectedTab == 4) Icons.Filled.Person else Icons.Outlined.Person, "Profile", tint = if (selectedTab == 4) NeonCyan else TextGray) }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(DarkNavy)) {
            when (selectedTab) {
                0 -> DashboardTab(viewModel, session)
                1 -> AnomalyHistoryTab(viewModel)
                3 -> ReportsTab(viewModel)
                4 -> ProfileTab(session, viewModel, onLogout = onNavigateToLogin)
            }
        }
    }
}

@Composable
fun DashboardTab(viewModel: DashboardViewModel, session: UserSession?) {
    val dataList by viewModel.dataList.collectAsState()
    val alertState by viewModel.alertState.collectAsState()
    val globalHeatmap by viewModel.globalHeatmapState.collectAsState()
    
    val chartEntryModelProducer = remember { ChartEntryModelProducer() }
    LaunchedEffect(dataList) {
        val entries = dataList.mapIndexed { index, data -> FloatEntry(index.toFloat(), data.power_kw.toFloat()) }
        if (entries.isNotEmpty()) {
            chartEntryModelProducer.setEntries(entries)
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // TOP HEADER
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AccountCircle, "Profile", tint = Color.White, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Technician Portal", color = Color.White, fontWeight = FontWeight.Bold)
                Text(session?.plant ?: "Global Overview", color = TextGray, fontSize = 12.sp)
            }
            
            // EXPORT BUTTON
            var showExportMenu by remember { mutableStateOf(false) }
            var showDateDialog by remember { mutableStateOf(false) }
            var exportFormat by remember { mutableStateOf("") }
            val context = androidx.compose.ui.platform.LocalContext.current
            val currentShop = session?.plant ?: "Global_Overview"
            val coroutineScope = rememberCoroutineScope()

            Box {
                IconButton(onClick = { showExportMenu = true }) {
                    Icon(Icons.Default.Share, "Export", tint = NeonCyan)
                }
                DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false },
                    modifier = Modifier.background(SurfaceNavy)
                ) {
                    DropdownMenuItem(
                        text = { Text("Export to CSV", color = Color.White) },
                        onClick = {
                            showExportMenu = false
                            exportFormat = "csv"
                            showDateDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export to PDF", color = Color.White) },
                        onClick = {
                            showExportMenu = false
                            exportFormat = "pdf"
                            showDateDialog = true
                        }
                    )
                }
            }
            
            if (showDateDialog) {
                ExportDateRangeDialog(
                    onDismiss = { showDateDialog = false },
                    onExport = { startMillis, endMillis ->
                        showDateDialog = false
                        coroutineScope.launch {
                            Toast.makeText(context, "Fetching data for export...", Toast.LENGTH_SHORT).show()
                            val data = viewModel.fetchDataForExport(currentShop, startMillis, endMillis)
                            if (data.isEmpty()) {
                                Toast.makeText(context, "No data found for this range.", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            if (exportFormat == "csv") {
                                exportToCsv(context, data, currentShop)
                            } else {
                                exportToPdf(context, data, currentShop)
                            }
                        }
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // CLEAN METRICS
        val latestData = dataList.lastOrNull()
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("POWER (kW)", latestData?.power_kw?.toString() ?: "0.0", NeonCyan, Modifier.weight(1f))
            MetricCard("OEE SCORE", latestData?.oee_score?.toString()?.plus("%") ?: "0%", Color.White, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("COS PHI", latestData?.power_factor?.toString() ?: "0.0", Color.White, Modifier.weight(1f))
            MetricCard("THD", latestData?.thd_value?.toString()?.plus("%") ?: "0%", if ((latestData?.thd_value ?: 0.0) > 10.0) CriticalRed else Color.White, Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // REALTIME CHART (Moved here from Analytics Tab)
        Text("REALTIME POWER TREND (kW)", color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        val bottomAxisValueFormatter = com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter<com.patrykandpatrick.vico.core.axis.AxisPosition.Horizontal.Bottom> { value, _ ->
            val index = value.toInt()
            if (index >= 0 && index < dataList.size) {
                formatUtcToLocal(dataList[index].created_at).takeLast(8)
            } else {
                ""
            }
        }

        Card(modifier = Modifier.fillMaxWidth().height(220.dp), colors = CardDefaults.cardColors(containerColor = SurfaceNavy)) {
            Box(modifier = Modifier.padding(16.dp)) {
                if (dataList.isNotEmpty()) {
                    Chart(
                        chart = lineChart(
                            lines = listOf(
                                lineSpec(
                                    lineColor = NeonCyan,
                                    lineBackgroundShader = verticalGradient(arrayOf(NeonCyan.copy(alpha = 0.5f), Color.Transparent))
                                )
                            )
                        ),
                        chartModelProducer = chartEntryModelProducer, 
                        startAxis = rememberStartAxis(), 
                        bottomAxis = rememberBottomAxis(valueFormatter = bottomAxisValueFormatter)
                    )
                } else {
                    Text("NO DATA", color = CriticalRed, modifier = Modifier.align(Alignment.Center))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("ACTIVE SHOP HEATMAP", color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ShopHeatmapCard("Stamping", globalHeatmap["Shop 1 - Stamping & Press"] ?: AlertState.NORMAL)
            ShopHeatmapCard("Body", globalHeatmap["Shop 2 - Body & Welding"] ?: AlertState.NORMAL)
            ShopHeatmapCard("Paint", globalHeatmap["Shop 3 - Paint Shop"] ?: AlertState.NORMAL)
            ShopHeatmapCard("Assembly", globalHeatmap["Shop 4 - General Assembly"] ?: AlertState.NORMAL)
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier.height(100.dp), colors = CardDefaults.cardColors(containerColor = SurfaceNavy)) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center) {
            Text(title, color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        }
    }
}

@Composable
fun ShopHeatmapCard(name: String, alertState: AlertState) {
    val isAnomaly = alertState != AlertState.NORMAL
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = if (isAnomaly) 0.3f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ), label = "blink"
    )

    val color = when (alertState) {
        AlertState.NORMAL -> Color(0xFF4CAF50)
        AlertState.SPIKE -> CriticalRed
        AlertState.DRIFT -> NeonAmber
    }

    Card(colors = CardDefaults.cardColors(containerColor = SurfaceNavy), shape = RoundedCornerShape(8.dp), modifier = Modifier.size(64.dp)) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.size(16.dp).background(color.copy(alpha = if (isAnomaly) alpha else 1f), CircleShape))
            Spacer(modifier = Modifier.height(4.dp))
            Text(name, color = Color.White, fontSize = 10.sp)
        }
    }
}

@Composable
fun AnomalyHistoryTab(viewModel: DashboardViewModel) {
    val anomalies by viewModel.anomalyHistory.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Anomaly Events History", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (anomalies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No anomalies detected yet.", color = TextGray)
            }
        } else {
            LazyColumn {
                items(anomalies) { anomaly ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = SurfaceNavy)) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning, 
                                contentDescription = "Warning", 
                                tint = if (anomaly.type.contains("Spike")) CriticalRed else NeonAmber,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(anomaly.type, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("${anomaly.liniName} | Trigger: ${anomaly.triggerValue}", color = TextGray, fontSize = 12.sp)
                                Text(anomaly.timestamp, color = NeonCyan, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportsTab(viewModel: DashboardViewModel) {
    val reports by viewModel.reportList.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Damage Report History", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (reports.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No reports yet. Use the Camera to report.", color = TextGray)
            }
        } else {
            LazyColumn {
                items(reports) { report ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = SurfaceNavy)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val localTime = try { formatUtcToLocal(report.timestamp) } catch (e: Exception) { report.timestamp }
                            Text(localTime, color = TextGray, fontSize = 12.sp)
                            Text("${report.shopName} | ${if (report.relatedAnomaly.isEmpty()) "No Anomaly Linked" else report.relatedAnomaly}", color = NeonCyan, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            if (report.imageUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = report.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.height(180.dp).fillMaxWidth(),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(report.notes, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTab(userSession: UserSession?, viewModel: DashboardViewModel, onLogout: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val shops = listOf("Shop 1 - Stamping & Press", "Shop 2 - Body & Welding", "Shop 3 - Paint Shop", "Shop 4 - General Assembly")
    var selectedShopText by remember(userSession) { mutableStateOf(userSession?.plant ?: shops[0]) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AccountCircle, "Profile", tint = Color.White, modifier = Modifier.size(100.dp))
        Spacer(Modifier.height(16.dp))
        Text(userSession?.name ?: "Technician", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text("Maintenance Technician", color = NeonCyan)
        Spacer(Modifier.height(32.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceNavy)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Email: ${userSession?.email ?: "N/A"}", color = Color.White)
                Divider(modifier = Modifier.padding(vertical = 12.dp), color = DarkNavy)
                
                Text("Current Active Plant:", color = TextGray, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedShopText,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        shops.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    selectedShopText = selectionOption
                                    expanded = false
                                    viewModel.changeShop(selectionOption)
                                }
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = {
                viewModel.logout()
                onLogout()
            },
            colors = ButtonDefaults.buttonColors(containerColor = CriticalRed),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Logout", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

fun formatUtcToLocal(utcTime: String): String {
    try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val cleanUtc = utcTime.substringBefore(".").substringBefore("+")
        val date = parser.parse(cleanUtc)
        
        val formatter = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
        formatter.timeZone = java.util.TimeZone.getDefault()
        return formatter.format(date!!)
    } catch (e: Exception) {
        return utcTime.take(19).replace("T", " ")
    }
}

// --- EXPORT FUNCTIONS ---
fun exportToCsv(context: Context, dataList: List<EnergyLog>, shopName: String) {
    try {
        val fileName = "AEMADS_Report_${shopName.replace(" ", "_")}_${System.currentTimeMillis()}.csv"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        
        val writer = FileOutputStream(file).bufferedWriter()
        writer.write("Timestamp,Shop Name,Power kW,Cos Phi,THD,OEE Score\n")
        dataList.forEach { log ->
            val localTime = formatUtcToLocal(log.created_at)
            writer.write("${localTime},${log.lini_name},${log.power_kw},${log.power_factor},${log.thd_value},${log.oee_score}\n")
        }
        writer.close()
        
        Toast.makeText(context, "CSV Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to export CSV", Toast.LENGTH_SHORT).show()
    }
}

fun exportToPdf(context: Context, dataList: List<EnergyLog>, shopName: String) {
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(600, 900, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 14f
        }
        val titlePaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 20f
            isFakeBoldText = true
        }
        
        canvas.drawText("AEMADS Energy Report", 40f, 50f, titlePaint)
        canvas.drawText("Shop: $shopName", 40f, 80f, paint)
        
        var yPosition = 120f
        canvas.drawText("Timestamp", 40f, yPosition, titlePaint)
        canvas.drawText("Power kW", 220f, yPosition, titlePaint)
        canvas.drawText("Cos Phi", 320f, yPosition, titlePaint)
        canvas.drawText("OEE", 420f, yPosition, titlePaint)
        
        yPosition += 30f
        
        dataList.take(20).forEach { log ->
            val localTime = formatUtcToLocal(log.created_at)
            canvas.drawText(localTime, 40f, yPosition, paint)
            canvas.drawText(log.power_kw.toString(), 220f, yPosition, paint)
            canvas.drawText(log.power_factor.toString(), 320f, yPosition, paint)
            canvas.drawText(log.oee_score.toString(), 420f, yPosition, paint)
            yPosition += 25f
        }
        
        pdfDocument.finishPage(page)
        
        val fileName = "AEMADS_Report_${shopName.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        
        Toast.makeText(context, "PDF Saved to Downloads: $fileName", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to export PDF", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDateRangeDialog(onDismiss: () -> Unit, onExport: (Long?, Long?) -> Unit) {
    val dateRangePickerState = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onExport(dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis) }
            ) {
                Text("Export", color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) }
        },
        colors = DatePickerDefaults.colors(containerColor = SurfaceNavy)
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            title = {
                Text(
                    text = "Select Date Range",
                    modifier = Modifier.padding(16.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = DatePickerDefaults.colors(
                containerColor = SurfaceNavy,
                titleContentColor = Color.White,
                headlineContentColor = NeonCyan,
                weekdayContentColor = TextGray,
                subheadContentColor = TextGray,
                yearContentColor = Color.White,
                currentYearContentColor = NeonCyan,
                selectedYearContentColor = Color.Black,
                selectedYearContainerColor = NeonCyan,
                dayContentColor = Color.White,
                disabledDayContentColor = TextGray,
                selectedDayContentColor = Color.Black,
                selectedDayContainerColor = NeonCyan,
                todayContentColor = NeonCyan,
                todayDateBorderColor = NeonCyan,
                dayInSelectionRangeContentColor = Color.White,
                dayInSelectionRangeContainerColor = NeonCyan.copy(alpha = 0.2f)
            ),
            modifier = Modifier.weight(1f)
        )
    }
}
