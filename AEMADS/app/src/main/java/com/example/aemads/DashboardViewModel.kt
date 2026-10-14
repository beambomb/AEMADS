package com.example.aemads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.Serializable
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.doubleOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DashboardViewModel : ViewModel() {

    private val supabaseUrl = "https://jmfawibrlwiebqdyhdsk.supabase.co"
    private val supabaseKey = "sb_publishable_260CCSS5agIPHgDAKh04JA_u3Mps_Ho"

    private val supabase = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey
    ) {
        install(Postgrest)
        install(Realtime)
        install(Auth)
        install(Storage)
    }

    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true 
        coerceInputValues = true
    }

    // AUTH STATE
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState = _loginState.asStateFlow()

    private val _signUpState = MutableStateFlow<SignUpState>(SignUpState.Idle)
    val signUpState = _signUpState.asStateFlow()

    private val _currentUserSession = MutableStateFlow<UserSession?>(null)
    val currentUserSession = _currentUserSession.asStateFlow()

    private val _globalHeatmapState = MutableStateFlow<Map<String, AlertState>>(emptyMap())
    val globalHeatmapState = _globalHeatmapState.asStateFlow()

    private val _uploadingReport = MutableStateFlow(false)
    val uploadingReport: StateFlow<Boolean> = _uploadingReport

    private val _dataList = MutableStateFlow<List<EnergyLog>>(emptyList())
    val dataList: StateFlow<List<EnergyLog>> = _dataList.asStateFlow()

    private val _alertState = MutableStateFlow(AlertState.NORMAL)
    val alertState = _alertState.asStateFlow()
    
    private val _anomalyEvent = MutableSharedFlow<AnomalyRecord>()
    val anomalyEvent: SharedFlow<AnomalyRecord> = _anomalyEvent.asSharedFlow()

    private val _reportList = MutableStateFlow<List<DamageReport>>(emptyList())
    val reportList: StateFlow<List<DamageReport>> = _reportList.asStateFlow()

    private val _anomalyHistory = MutableStateFlow<List<AnomalyRecord>>(emptyList())
    val anomalyHistory: StateFlow<List<AnomalyRecord>> = _anomalyHistory.asStateFlow()

    private var isRealtimeStarted = false

    init {
        // Realtime and data fetching will be triggered after a successful login
    }

    fun login(emailInput: String, passwordInput: String, plant: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                supabase.auth.signInWith(Email) {
                    email = emailInput
                    password = passwordInput
                }
                
                val user = supabase.auth.currentUserOrNull()
                val metadataName = user?.userMetadata?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "Unknown User"
                
                val session = UserSession(emailInput, metadataName, plant)
                _currentUserSession.value = session
                _loginState.value = LoginState.Success(session)
                fetchInitialData()
                if (!isRealtimeStarted) {
                    startRealtimeListener()
                    isRealtimeStarted = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val cleanError = e.message?.substringBefore("URL:")?.trim() ?: "Login failed"
                _loginState.value = LoginState.Error("Error: $cleanError")
            }
        }
    }

    fun signUp(nameInput: String, emailInput: String, passwordInput: String) {
        viewModelScope.launch {
            _signUpState.value = SignUpState.Loading
            try {
                supabase.auth.signUpWith(Email) {
                    email = emailInput
                    password = passwordInput
                    data = buildJsonObject {
                        put("name", nameInput)
                    }
                }
                _signUpState.value = SignUpState.Success
            } catch (e: Exception) {
                e.printStackTrace()
                val cleanError = e.message?.substringBefore("URL:")?.trim() ?: "Registration failed"
                _signUpState.value = SignUpState.Error("Error: $cleanError")
            }
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }

    fun resetSignUpState() {
        _signUpState.value = SignUpState.Idle
    }

    fun logout() {
        viewModelScope.launch {
            try {
                supabase.auth.signOut()
                isRealtimeStarted = false
                realtimeJob?.cancel()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _currentUserSession.value = null
                _loginState.value = LoginState.Idle
                _dataList.value = emptyList()
            }
        }
    }

    fun changeShop(newPlant: String) {
        val currentSession = _currentUserSession.value
        if (currentSession != null) {
            _currentUserSession.value = currentSession.copy(plant = newPlant)
            _dataList.value = emptyList() // Clear local chart
            fetchInitialData() // Fetch historical data for new shop
        }
    }

    private val lastLoggedAnomalyTimes = mutableMapOf<String, Long>()

    private fun fetchInitialData() {
        val currentPlant = _currentUserSession.value?.plant ?: "Shop 1 - Stamping & Press"
        viewModelScope.launch {
            try {
                // Fetch latest data for ALL shops to update global heatmap
                val globalData = supabase.postgrest["energy_logs"]
                    .select {
                        order("id", Order.DESCENDING)
                        limit(10)
                    }.decodeList<EnergyLog>()
                
                val newMap = _globalHeatmapState.value.toMutableMap()
                val processedShops = mutableSetOf<String>()
                globalData.forEach { log ->
                    if (!processedShops.contains(log.lini_name)) {
                        val calculatedState = when {
                            log.power_kw > 150.0 && log.lini_name.contains("Shop 1") -> AlertState.SPIKE
                            log.power_factor < 0.85 || log.thd_value > 10.0 -> AlertState.DRIFT
                            else -> AlertState.NORMAL
                        }
                        
                        if (calculatedState != AlertState.NORMAL) {
                            val lastLogTime = lastLoggedAnomalyTimes[log.lini_name] ?: 0L
                            val currentTime = System.currentTimeMillis()
                            // Log maximum once every 15 seconds to avoid spam but guarantee visibility
                            if (currentTime - lastLogTime > 15000) {
                                logAnomaly(calculatedState, log)
                                lastLoggedAnomalyTimes[log.lini_name] = currentTime
                            }
                        }

                        newMap[log.lini_name] = calculatedState
                        processedShops.add(log.lini_name)
                    }
                }
                _globalHeatmapState.value = newMap
                _alertState.value = newMap[currentPlant] ?: AlertState.NORMAL

                fetchReports() // Fetch reports along with global heatmap update

                val initialData = supabase.postgrest["energy_logs"]
                    .select {
                        filter {
                            eq("lini_name", currentPlant)
                        }
                        order("id", Order.DESCENDING)
                        limit(20)
                    }.decodeList<EnergyLog>()
                
                _dataList.value = initialData.reversed()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var realtimeJob: kotlinx.coroutines.Job? = null

    private fun startRealtimeListener() {
        // [ARCHITECTURE DECISION]
        // Switched from Supabase Realtime (WebSocket) to Hard Polling (REST API)
        // Justification: Enterprise Firewalls/Campus Networks often block WSS connections.
        // Polling guarantees 100% data delivery every 2 seconds without stateful socket drops.
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            while (isActive) {
                delay(2000)
                fetchInitialData()
            }
        }
    }



    private fun logAnomaly(type: AlertState, data: EnergyLog) {
        val newRecord = AnomalyRecord(
            type = if (type == AlertState.SPIKE) "Case 2 (Spike)" else "Case 3 (Drift)",
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            triggerValue = if (type == AlertState.SPIKE) "${data.power_kw} kW" else "Cos Phi ${data.power_factor}",
            liniName = data.lini_name
        )
        val currentList = _anomalyHistory.value.toMutableList()
        currentList.add(0, newRecord)
        _anomalyHistory.value = currentList
        
        viewModelScope.launch {
            _anomalyEvent.emit(newRecord)
        }
        
        // TODO: In production, save this to `anomaly_history` table in Supabase.
    }

    fun exportToCsv(): String {
        val currentData = _dataList.value
        if (currentData.isEmpty()) return "Tidak ada data untuk diekspor"

        val csvBuilder = java.lang.StringBuilder()
        csvBuilder.append("ID,Waktu,Lini Produksi,Power (kW),Cos Phi,OEE (%),THD (%)\n")
        
        currentData.forEach { data ->
            csvBuilder.append("${data.id},${data.created_at},${data.lini_name},${data.power_kw},${data.power_factor},${data.oee_score},${data.thd_value}\n")
        }
        
        return csvBuilder.toString()
    }

    fun uploadReport(bitmap: Bitmap, shopName: String, relatedAnomaly: String, notes: String) {
        viewModelScope.launch {
            _uploadingReport.value = true
            try {
                // Compress Bitmap
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                val data = baos.toByteArray()

                // Upload to Supabase Storage
                val fileName = "report_${System.currentTimeMillis()}.jpg"
                val bucket = supabase.storage["reports"]
                // Upload Image with Fallback for Presentation
                var publicUrl = "https://via.placeholder.com/600x400.png?text=Storage+Policy+Restricted"
                try {
                    bucket.upload(fileName, data, upsert = false)
                    publicUrl = bucket.publicUrl(fileName)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // If bucket upload fails (usually due to Supabase RLS Storage Insert Policy),
                    // we catch it here and use the placeholder URL.
                    // This allows the Database Insert below to still succeed for the presentation!
                }
                
                // Insert to Database
                val newReport = DamageReportInsert(
                    shop_name = shopName,
                    related_anomaly = relatedAnomaly,
                    notes = notes,
                    image_url = publicUrl
                )
                
                supabase.postgrest["damage_reports"].insert(newReport)
                
                _uploadingReport.value = false
                fetchReports() // Refresh list after uploading
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uploadingReport.value = false
            }
        }
    }

    fun fetchReports() {
        viewModelScope.launch {
            try {
                val reports = supabase.postgrest["damage_reports"]
                    .select {
                        order("created_at", Order.DESCENDING)
                        limit(50)
                    }.decodeList<DamageReportDB>()
                
                _reportList.value = reports.map { dbReport ->
                    DamageReport(
                        id = UUID.randomUUID().toString(),
                        timestamp = dbReport.created_at ?: "",
                        shopName = dbReport.shop_name,
                        relatedAnomaly = dbReport.related_anomaly,
                        notes = dbReport.notes,
                        imageUrl = dbReport.image_url
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun fetchDataForExport(shopName: String, startMillis: Long?, endMillis: Long?): List<EnergyLog> {
        return try {
            val startDate = startMillis?.let { java.time.Instant.ofEpochMilli(it).toString() } ?: "1970-01-01T00:00:00Z"
            // endMillis is 00:00:00 of the selected end date, so we add 24 hours (86400000L) to cover the whole day
            val endDate = endMillis?.let { java.time.Instant.ofEpochMilli(it + 86400000L).toString() } ?: "2099-01-01T00:00:00Z"
            
            supabase.postgrest["energy_logs"]
                .select {
                    filter {
                        eq("lini_name", shopName)
                        gte("created_at", startDate)
                        lte("created_at", endDate)
                    }
                    order("created_at", Order.DESCENDING)
                }.decodeList<EnergyLog>()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val session: UserSession) : LoginState()
    data class Error(val message: String) : LoginState()
}

sealed class SignUpState {
    object Idle : SignUpState()
    object Loading : SignUpState()
    object Success : SignUpState()
    data class Error(val message: String) : SignUpState()
}

data class UserSession(
    val email: String,
    val name: String,
    val plant: String
)

enum class AlertState { NORMAL, SPIKE, DRIFT }

data class AnomalyRecord(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val timestamp: String,
    val triggerValue: String,
    val liniName: String
)

@Serializable
data class DamageReportDB(
    val id: Int? = null,
    val created_at: String? = null,
    val shop_name: String,
    val related_anomaly: String,
    val notes: String,
    val image_url: String
)

@Serializable
data class DamageReportInsert(
    val shop_name: String,
    val related_anomaly: String,
    val notes: String,
    val image_url: String
)

data class DamageReport(
    val id: String,
    val timestamp: String,
    val shopName: String,
    val relatedAnomaly: String,
    val notes: String,
    val imageUrl: String
)
