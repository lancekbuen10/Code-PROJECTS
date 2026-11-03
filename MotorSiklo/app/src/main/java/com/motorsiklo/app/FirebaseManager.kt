package com.motorsiklo.app

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.motorsiklo.app.ui.screens.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object FirebaseManager {

    private const val TAG = "FirebaseManager"
    private const val DATABASE_URL = "https://motorsiklo-c1594-default-rtdb.firebaseio.com/"

    private val db by lazy { Firebase.database(DATABASE_URL).reference }
    private val auth by lazy { Firebase.auth }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private const val NODE_VIOLATIONS = "violations"
    private const val NODE_TICKETS = "tickets"
    private const val NODE_REGISTRATION_REQUESTS = "registration_requests"
    private const val NODE_RESET_REQUESTS = "reset_requests"
    private const val NODE_CHANGE_PW_REQUESTS = "change_password_requests"
    private const val NODE_SYSTEM_HEALTH = "system_health"
    private const val NODE_ENFORCER_ACTIVITY = "enforcer_activity"
    private const val NODE_PRESENCE = "presence"
    private const val NODE_USERS = "users"
    private const val NODE_STATISTICS = "statistics"
    private const val NODE_DEVICE_ID = "deviceId"

    private var connectedListener: ValueEventListener? = null
    private var databaseConnectionListener: ValueEventListener? = null
    private var concurrentSessionListener: ValueEventListener? = null

    private var regListener: ValueEventListener? = null
    private var resetListener: ValueEventListener? = null
    private var changePwListener: ValueEventListener? = null
    private var violationsListener: ValueEventListener? = null
    private var presenceListener: ValueEventListener? = null

    private var serverTimeOffset: Long = 0L

    private var isListeningForViolations = false
    private var isListeningForSystemHealth = false
    private var isListeningForAIHeartbeat = false
    private var isListeningForRegistrationRequests = false
    private var isListeningForResetRequests = false
    private var isListeningForChangePasswordRequests = false
    private var isListeningForGlobalStats = false
    private var isSyncingServerOffset = false

    private var activityTimer: java.util.Timer? = null
    private var offlineTimer: java.util.Timer? = null
    private var reconnectTimer: java.util.Timer? = null
    private var isIdle = false
    private var currentSessionToken: String = ""
    private var hasCompletedInitialViolationSync = false
    private val mainHandler = Handler(Looper.getMainLooper())

    val currentIdleDevices = mutableStateOf(0)
    val isLocalDeviceIdle = mutableStateOf(false)
    val isDatabaseConnected = mutableStateOf(true)

    // ── THE FUZZY STRINGER MATCHER ──
    // Safely translates raw Python string labels into structured Kotlin Enums
    private fun mapRawTypeToEnum(raw: String): ViolationType {
        val clean = raw.uppercase().replace("_", " ").trim()
        return when {
            clean.contains("HELMET") -> ViolationType.NO_HELMET
            clean.contains("SPEED") -> ViolationType.OVERSPEEDING
            clean.contains("OVERLOAD") -> ViolationType.OVERLOADING
            clean.contains("PARK") -> ViolationType.ILLEGAL_PARKING
            else -> ViolationType.MANUAL
        }
    }

    @SuppressLint("HardwareIds")
    fun reportAppStatus(context: Context) {
        val currentUser = auth.currentUser ?: return

        if (currentSessionToken.isEmpty()) {
            currentSessionToken = java.util.UUID.randomUUID().toString()
        }

        val userRef = db.child(NODE_USERS).child(currentUser.uid)

        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val role = snapshot.child("role").value as? String ?: ""
                currentUserProfile.value = UserProfile(
                    uid = currentUser.uid,
                    username = snapshot.child("username").value as? String ?: "",
                    firstName = snapshot.child("firstName").value as? String ?: "",
                    lastName = snapshot.child("lastName").value as? String ?: "",
                    middleName = snapshot.child("middleName").value as? String ?: "",
                    badgeNumber = snapshot.child("badgeNumber").value as? String ?: "",
                    station = snapshot.child("station").value as? String ?: "",
                    role = role
                )

                if (role.lowercase() == "admin" || role == "Administrator") {
                    startAdminListeners()
                }
            }
        }

        val deviceIdRef = userRef.child(NODE_DEVICE_ID)
        deviceIdRef.setValue(currentSessionToken).addOnCompleteListener {
            concurrentSessionListener?.let { deviceIdRef.removeEventListener(it) }

            concurrentSessionListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val activeToken = snapshot.value as? String
                    if (activeToken != null && activeToken != currentSessionToken) {
                        forceLogoutEvent.value = true
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            deviceIdRef.addValueEventListener(concurrentSessionListener!!)
        }

        db.child(NODE_SYSTEM_HEALTH).child("firebaseSyncStatus").setValue("Active")
        syncServerTimeOffset()
        startPresenceTracking()
    }

    private fun startAdminListeners() {
        startListeningForRegistrationRequests()
        startListeningForResetRequests()
        startListeningForChangePasswordRequests()
    }

    private fun syncServerTimeOffset() {
        if (isSyncingServerOffset) return
        isSyncingServerOffset = true
        Firebase.database(DATABASE_URL).getReference(".info/serverTimeOffset")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    serverTimeOffset = snapshot.getValue(Long::class.java) ?: 0L
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun resetActivity() {
        val userId = auth.currentUser?.uid ?: return
        if (isIdle || offlineTimer != null) {
            isIdle = false
            isLocalDeviceIdle.value = false
            db.child(NODE_PRESENCE).child(userId).setValue("Online")
        }
        startIdleTimer()
    }

    private fun startIdleTimer() {
        activityTimer?.cancel()
        offlineTimer?.cancel()

        activityTimer = java.util.Timer()
        activityTimer?.schedule(object : java.util.TimerTask() {
            override fun run() {
                val userId = auth.currentUser?.uid ?: return
                isIdle = true
                isLocalDeviceIdle.value = true
                db.child(NODE_PRESENCE).child(userId).setValue("Idle")

                offlineTimer = java.util.Timer()
                offlineTimer?.schedule(object : java.util.TimerTask() {
                    override fun run() {
                        val uid = auth.currentUser?.uid ?: return
                        db.child(NODE_PRESENCE).child(uid).removeValue()
                    }
                }, 5 * 60 * 1000L)
            }
        }, 3 * 60 * 1000L)
    }

    fun startPresenceTracking() {
        val userId = auth.currentUser?.uid ?: return
        val connectedRef = Firebase.database(DATABASE_URL).getReference(".info/connected")
        val presenceRef = db.child(NODE_PRESENCE).child(userId)

        stopPresenceTracking()

        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    presenceRef.onDisconnect().removeValue()
                    presenceRef.setValue("Online")
                    isIdle = false
                    isLocalDeviceIdle.value = false
                    startIdleTimer()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        connectedRef.addValueEventListener(connectedListener!!)
    }

    fun monitorDatabaseConnection() {
        val connectedRef = Firebase.database(DATABASE_URL).getReference(".info/connected")
        databaseConnectionListener?.let { connectedRef.removeEventListener(it) }

        databaseConnectionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                isDatabaseConnected.value = connected
                currentSystemHealth.value = currentSystemHealth.value.copy(
                    firebaseSyncStatus = if (connected) "Active" else "Inactive"
                )

                if (connected) {
                    reconnectTimer?.cancel()
                    reconnectTimer = null
                    refreshViolations()
                } else {
                    scheduleDatabaseReconnect()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                isDatabaseConnected.value = false
                scheduleDatabaseReconnect()
            }
        }

        connectedRef.addValueEventListener(databaseConnectionListener!!)
    }

    private fun scheduleDatabaseReconnect() {
        if (reconnectTimer != null) return
        reconnectTimer = java.util.Timer()
        reconnectTimer?.schedule(object : java.util.TimerTask() {
            override fun run() {
                try {
                    Firebase.database(DATABASE_URL).goOffline()
                    Firebase.database(DATABASE_URL).goOnline()
                } catch (e: Exception) {
                    Log.w(TAG, "Database reconnect attempt failed: ${e.message}")
                }
                mainHandler.post {
                    reconnectTimer = null
                    refreshViolations()
                    if (!isDatabaseConnected.value) scheduleDatabaseReconnect()
                }
            }
        }, 5_000L)
    }

    private fun stopPresenceTracking() {
        connectedListener?.let { Firebase.database(DATABASE_URL).getReference(".info/connected").removeEventListener(it) }
        connectedListener = null
        activityTimer?.cancel()
        activityTimer = null
        offlineTimer?.cancel()
        offlineTimer = null
    }

    fun signOut() {
        val userId = auth.currentUser?.uid

        currentUserProfile.value = null
        currentSessionToken = ""
        forceLogoutEvent.value = false
        resetListeningFlags()

        if (userId != null) {
            val userRef = db.child(NODE_USERS).child(userId)
            concurrentSessionListener?.let { userRef.child(NODE_DEVICE_ID).removeEventListener(it) }
            concurrentSessionListener = null

            val presenceRef = db.child(NODE_PRESENCE).child(userId)
            stopPresenceTracking()
            presenceRef.onDisconnect().cancel()
            presenceRef.removeValue()

            java.util.Timer().schedule(object : java.util.TimerTask() {
                override fun run() {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        auth.signOut()
                    }
                }
            }, 600)
        } else {
            stopPresenceTracking()
            auth.signOut()
        }
    }

    private fun resetListeningFlags() {
        regListener?.let { db.child(NODE_REGISTRATION_REQUESTS).removeEventListener(it) }
        resetListener?.let { db.child(NODE_RESET_REQUESTS).removeEventListener(it) }
        changePwListener?.let { db.child(NODE_CHANGE_PW_REQUESTS).removeEventListener(it) }
        violationsListener?.let { db.child(NODE_VIOLATIONS).removeEventListener(it) }
        presenceListener?.let { db.child(NODE_PRESENCE).removeEventListener(it) }
        databaseConnectionListener?.let { Firebase.database(DATABASE_URL).getReference(".info/connected").removeEventListener(it) }
        databaseConnectionListener = null

        isListeningForViolations = false
        isListeningForSystemHealth = false
        isListeningForAIHeartbeat = false
        isListeningForRegistrationRequests = false
        isListeningForResetRequests = false
        isListeningForChangePasswordRequests = false
        isListeningForGlobalStats = false
        hasCompletedInitialViolationSync = false
        isDatabaseConnected.value = true

        registrationRequests.clear()
        resetRequests.clear()
        changePasswordRequests.clear()
        ViolationRepository.violations.clear()
    }

    fun signIn(username: String, password: String, loginTargetRole: String, onResult: (Boolean, String?) -> Unit) {
        val email = "$username@motorsiklo.app"
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user ?: return@addOnSuccessListener onResult(false, "Authentication failed.")
                db.child(NODE_USERS).child(user.uid).get().addOnSuccessListener { snapshot ->
                    val dbRole = (snapshot.child("role").value as? String)?.lowercase()?.trim() ?: ""
                    val status = (snapshot.child("status").value as? String)?.lowercase()?.trim() ?: ""

                    if (dbRole.isEmpty()) {
                        auth.signOut()
                        return@addOnSuccessListener onResult(false, "Account not found.")
                    }
                    if (status != "approved") {
                        auth.signOut()
                        return@addOnSuccessListener onResult(false, "Account status: $status")
                    }

                    val expectedDbRole = if (loginTargetRole.lowercase().contains("admin")) "admin" else "enforcer"
                    if (dbRole != expectedDbRole) {
                        auth.signOut()
                        return@addOnSuccessListener onResult(false, "Incorrect role hierarchy assignment.")
                    }

                    onResult(true, null)
                }.addOnFailureListener { onResult(false, "Verification protocol failure.") }
            }
            .addOnFailureListener { onResult(false, it.message ?: "Sign in attempt rejected.") }
    }

    fun submitRegistrationRequest(lastName: String, firstName: String, middleName: String, username: String, badgeNumber: String, password: String, station: String) {
        val key = db.child(NODE_REGISTRATION_REQUESTS).push().key ?: return
        val data = mapOf("id" to key, "lastName" to lastName, "firstName" to firstName, "middleName" to middleName, "username" to username, "badgeNumber" to badgeNumber, "password" to password, "station" to station, "timestamp" to ServerValue.TIMESTAMP, "status" to "Pending")
        db.child(NODE_REGISTRATION_REQUESTS).child(key).setValue(data)
    }

    fun submitResetRequest(username: String, badgeNumber: String, desiredPassword: String) {
        val key = db.child(NODE_RESET_REQUESTS).push().key ?: return
        val data = mapOf("id" to key, "username" to username, "badgeNumber" to badgeNumber, "desiredPassword" to desiredPassword, "timestamp" to ServerValue.TIMESTAMP, "status" to "Pending")
        db.child(NODE_RESET_REQUESTS).child(key).setValue(data)
    }

    fun submitChangePasswordRequest(username: String, badgeNumber: String, desiredPassword: String) {
        val key = db.child(NODE_CHANGE_PW_REQUESTS).push().key ?: return
        val data = mapOf("id" to key, "username" to username, "badgeNumber" to badgeNumber, "desiredPassword" to desiredPassword, "timestamp" to ServerValue.TIMESTAMP, "status" to "Pending")
        db.child(NODE_CHANGE_PW_REQUESTS).child(key).setValue(data)
    }

    fun approveRegistrationRequest(context: Context, request: RegistrationRequest, onResult: (Boolean, String) -> Unit) {
        registrationRequests.remove(request)
        val options = FirebaseApp.getInstance().options
        var secondaryApp: FirebaseApp?
        try { secondaryApp = FirebaseApp.getInstance("SecondaryApp") } catch (e: IllegalStateException) { secondaryApp = FirebaseApp.initializeApp(context, options, "SecondaryApp") }
        val secondaryAuth = FirebaseAuth.getInstance(secondaryApp!!)

        secondaryAuth.createUserWithEmailAndPassword("${request.username}@motorsiklo.app", request.password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    val userData = mapOf("username" to request.username, "firstName" to request.firstName, "lastName" to request.lastName, "middleName" to request.middleName, "badgeNumber" to request.badgeNumber, "station" to request.station, "role" to "enforcer", "status" to "approved")
                    db.child(NODE_USERS).child(user.uid).setValue(userData)
                        .addOnSuccessListener {
                            db.child(NODE_REGISTRATION_REQUESTS).child(request.id).removeValue()
                            secondaryAuth.signOut()
                            onResult(true, "Enforcer credentials written successfully.")
                        }.addOnFailureListener { e -> secondaryAuth.signOut(); onResult(false, "Failed to build user node: ${e.message}") }
                }
            }.addOnFailureListener { e -> onResult(false, "Staging platform failure: ${e.message}") }
    }

    fun rejectRegistrationRequest(request: RegistrationRequest) {
        registrationRequests.remove(request)
        db.child(NODE_REGISTRATION_REQUESTS).child(request.id).removeValue()
    }

    fun approveResetRequest(request: ResetRequest) {
        resetRequests.remove(request)
        stagePasswordForUser(request.username, request.desiredPassword, request.id, NODE_RESET_REQUESTS)
    }

    fun denyResetRequest(request: ResetRequest) {
        resetRequests.remove(request)
        db.child(NODE_RESET_REQUESTS).child(request.id).removeValue()
    }

    fun approveChangePasswordRequest(request: ChangePasswordRequest) {
        changePasswordRequests.remove(request)
        stagePasswordForUser(request.username, request.desiredPassword, request.id, NODE_CHANGE_PW_REQUESTS)
    }

    fun denyChangePasswordRequest(request: ChangePasswordRequest) {
        changePasswordRequests.remove(request)
        db.child(NODE_CHANGE_PW_REQUESTS).child(request.id).removeValue()
    }

    private fun stagePasswordForUser(username: String, newPassword: String, requestId: String, requestNode: String) {
        db.child(NODE_USERS).orderByChild("username").equalTo(username)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) child.ref.child("stagedPassword").setValue(newPassword)
                    db.child(requestNode).child(requestId).removeValue()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /**
     * Uploads a manually-captured evidence photo (e.g. enforcer's camera shot for a
     * manual entry ticket) to Firebase Storage, mirroring the same "evidences/" folder
     * convention the Python detection pipeline already uses for AI-detected violations.
     *
     * @param localUri  content:// Uri of the photo taken via the device camera.
     * @param violationId  the violation's id, used to build a unique storage filename.
     * @param onResult  called with the public download URL on success, or null on failure.
     */
    fun uploadManualEvidence(localUri: Uri, violationId: String, onResult: (String?) -> Unit) {
        val storageRef = Firebase.storage.reference.child("evidences/manual_$violationId.jpg")
        storageRef.putFile(localUri)
            .addOnSuccessListener {
                storageRef.downloadUrl
                    .addOnSuccessListener { uri -> onResult(uri.toString()) }
                    .addOnFailureListener { e ->
                        Log.e("MotorSiklo", "Failed to fetch download URL for manual evidence: ${e.message}")
                        onResult(null)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("MotorSiklo", "Failed to upload manual evidence: ${e.message}")
                onResult(null)
            }
    }

    fun addViolation(entry: ViolationEntry, onComplete: (String?) -> Unit = {}) {
        val key = entry.id.takeIf { it.isNotBlank() } ?: db.child(NODE_VIOLATIONS).push().key ?: return onComplete(null)
        val data = mapOf("id" to key, "types" to entry.types.map { it.name }, "timestamp" to ServerValue.TIMESTAMP, "location" to entry.location, "status" to entry.status, "badgeNumber" to entry.badgeNumber, "imageUrl" to entry.imageUrl)
        db.child(NODE_VIOLATIONS).child(key).setValue(data).addOnSuccessListener { onComplete(key) }
    }

    fun voidViolation(violationId: String) {
        db.child(NODE_VIOLATIONS).child(violationId).child("status").setValue("Voided")
    }

    fun saveTicketResult(result: TicketResult, isEnforcerSubmission: Boolean) {
        val violationId = result.violation.id
        if (violationId.isBlank()) return

        val finalStatus = if (isEnforcerSubmission) "Pending" else "Completed"
        val rootUpdates = mutableMapOf<String, Any>("status" to finalStatus, "location" to result.placeOfApprehension)
        if (result.officerId.isNotEmpty()) rootUpdates["badgeNumber"] = result.officerId
        db.child(NODE_VIOLATIONS).child(violationId).updateChildren(rootUpdates)

        val ticketData = mapOf(
            "violationId" to violationId, "violationTypes" to result.violation.types.map { it.name }, "violationTimestamp" to result.violation.timestamp, "violationLocation" to result.placeOfApprehension, "violationImageUrl" to result.violation.imageUrl, "violationVideoUrl" to result.violation.videoUrl, "violationStatus" to finalStatus, "violationBadge" to result.violation.badgeNumber, "topNumber" to result.topNumber, "violatorName" to result.violatorName, "violatorAddress" to result.violatorAddress, "licenseNumber" to result.licenseNumber, "licenseType" to result.licenseType, "plateNumber" to result.plateNumber, "makeAndType" to result.makeAndType, "placeOfApprehension" to result.placeOfApprehension, "apprehensionDate" to result.apprehensionDate, "apprehensionTime" to result.apprehensionTime, "confiscatedItems" to result.confiscatedItems, "offenseItems" to result.offenseItems, "remarks" to result.remarks, "officerLastName" to result.officerLastName, "officerFirstName" to result.officerFirstName, "officerMiddleInitial" to result.officerMiddleInitial, "officerId" to result.officerId, "unitStation" to result.unitStation
        )
        db.child(NODE_TICKETS).child(violationId).setValue(ticketData)
        firestore.collection("official_tickets").document(violationId).set(ticketData)

        if (!isEnforcerSubmission) {
            val statUpdates = mutableMapOf<String, Any>()
            if (result.violation.types.contains(ViolationType.MANUAL)) statUpdates["manualTicketCount"] = ServerValue.increment(1)
            result.offenseItems.forEach { offense ->
                val statKey = when {
                    offense.contains("Helmet", true) -> "helmetCount"
                    offense.contains("Overspeeding", true) -> "overspeedingCount"
                    offense.contains("Overloading", true) -> "overloadingCount"
                    offense.contains("Parking", true) -> "illegalParkingCount"
                    else -> null
                }
                if (statKey != null) statUpdates[statKey] = ServerValue.increment(1)
            }
            if (statUpdates.isNotEmpty()) db.child(NODE_STATISTICS).updateChildren(statUpdates)
            if (result.officerId.isNotEmpty()) db.child(NODE_ENFORCER_ACTIVITY).child(result.officerId).child("citationCount").setValue(ServerValue.increment(1))
        }
    }

    fun fetchTicketResult(violationId: String, onResult: (TicketResult?) -> Unit) {
        db.child(NODE_TICKETS).child(violationId).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) { onResult(null); return@addOnSuccessListener }
            try {
                fun str(k: String) = snapshot.child(k).value?.toString() ?: ""
                val confiscated = snapshot.child("confiscatedItems").children.mapNotNull { it.value?.toString() }
                val offenses = snapshot.child("offenseItems").children.mapNotNull { it.value?.toString() }

                // ── FUZZY MAPPER APPLIED HERE ──
                val rawTypes = snapshot.child("violationTypes").children.mapNotNull { it.value?.toString() }
                val parsedTypes = if (rawTypes.isNotEmpty()) {
                    rawTypes.map { mapRawTypeToEnum(it) }
                } else {
                    listOf(mapRawTypeToEnum(str("violationType")))
                }

                onResult(TicketResult(
                    violation = ViolationEntry(id = violationId, imageUrl = str("violationImageUrl"), videoUrl = str("violationVideoUrl"), location = str("violationLocation"), timestamp = snapshot.child("violationTimestamp").value as? Long ?: 0L, types = parsedTypes, badgeNumber = str("violationBadge"), status = str("violationStatus")),
                    topNumber = str("topNumber"), violatorName = str("violatorName"), violatorAddress = str("violatorAddress"), licenseNumber = str("licenseNumber"), licenseType = str("licenseType"), plateNumber = str("plateNumber"), makeAndType = str("makeAndType"), placeOfApprehension = str("placeOfApprehension"), apprehensionDate = str("apprehensionDate"), apprehensionTime = str("apprehensionTime"), confiscatedItems = confiscated, offenseItems = offenses, remarks = str("remarks"), officerLastName = str("officerLastName"), officerFirstName = str("officerFirstName"), officerMiddleInitial = str("officerMiddleInitial"), officerId = str("officerId"), unitStation = str("unitStation")
                ))
            } catch (e: Exception) { onResult(null) }
        }.addOnFailureListener { onResult(null) }
    }

    fun syncSystemHealth() {
        if (isListeningForSystemHealth) return
        isListeningForSystemHealth = true

        presenceListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                CoroutineScope(Dispatchers.IO).launch {
                    var onlineCount = 0
                    var idleCount = 0
                    for (child in snapshot.children) {
                        if (child.value?.toString() == "Idle") idleCount++ else onlineCount++
                    }
                    withContext(Dispatchers.Main) {
                        currentSystemHealth.value = currentSystemHealth.value.copy(activeDevices = onlineCount)
                        currentIdleDevices.value = idleCount
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child(NODE_PRESENCE).addValueEventListener(presenceListener!!)

        db.child(NODE_SYSTEM_HEALTH).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentSystemHealth.value = currentSystemHealth.value.copy(
                    aiCoreStatus = snapshot.child("aiCoreStatus").value as? String ?: "Not Connected",
                    firebaseSyncStatus = snapshot.child("firebaseSyncStatus").value as? String ?: "Inactive"
                )
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        startListeningForAICoreHeartbeat()
    }

    fun syncStatistics() {
        if (isListeningForGlobalStats) return
        isListeningForGlobalStats = true
        db.child(NODE_STATISTICS).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentGlobalStats.value = GlobalStats(
                    manualTicketCount = (snapshot.child("manualTicketCount").value as? Long)?.toInt() ?: 0,
                    helmetCount = (snapshot.child("helmetCount").value as? Long)?.toInt() ?: 0,
                    overspeedingCount = (snapshot.child("overspeedingCount").value as? Long)?.toInt() ?: 0,
                    overloadingCount = (snapshot.child("overloadingCount").value as? Long)?.toInt() ?: 0,
                    illegalParkingCount = (snapshot.child("illegalParkingCount").value as? Long)?.toInt() ?: 0
                )
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun startListeningForViolations() {
        if (isListeningForViolations) return
        isListeningForViolations = true

        violationsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                CoroutineScope(Dispatchers.IO).launch {
                    val list = snapshot.children.mapNotNull { child ->
                        try {
                            var status = child.child("status").value as? String ?: "Detected"
                            val badgeNumber = child.child("badgeNumber").value as? String ?: ""
                            val timestamp = child.child("timestamp").value as? Long ?: 0L

                            if (status.equals("Pending", ignoreCase = true) && (badgeNumber.isEmpty() || badgeNumber.startsWith("MOTO-", ignoreCase = true))) {
                                status = "Detected"
                            }
                            if (status.equals("Detected", ignoreCase = true) && System.currentTimeMillis() - timestamp > 24 * 60 * 60 * 1000L) {
                                status = "Expired"
                            }

                            // ── FUZZY MAPPER APPLIED HERE ──
                            val rawTypes = child.child("types").value as? List<*>
                            val safeTypes = if (rawTypes != null) {
                                rawTypes.mapNotNull { mapRawTypeToEnum(it.toString()) }
                            } else {
                                listOf(mapRawTypeToEnum(child.child("type").value?.toString() ?: "MANUAL"))
                            }

                            ViolationEntry(
                                id = child.child("id").value as? String ?: child.key ?: "",
                                types = if (safeTypes.isEmpty()) listOf(ViolationType.MANUAL) else safeTypes,
                                timestamp = timestamp, location = child.child("location").value as? String ?: "Unknown Location",
                                status = status, imageUrl = child.child("imageUrl").value as? String ?: "",
                                videoUrl = child.child("videoUrl").value as? String ?: "", badgeNumber = badgeNumber
                            )
                        } catch (e: Exception) { null }
                    }.sortedByDescending { it.timestamp }

                    withContext(Dispatchers.Main) {
                        isDataFullySynced.value = true
                        val existingIds = ViolationRepository.violations.map { it.id }.toSet()
                        val newIds = if (hasCompletedInitialViolationSync) {
                            list.map { it.id }.filter { it !in existingIds }
                        } else {
                            emptyList()
                        }
                        ViolationRepository.violations.clear()
                        ViolationRepository.violations.addAll(list)
                        ViolationRepository.markNewViolations(newIds)
                        hasCompletedInitialViolationSync = true
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        // ── LIMIT RAISED TO 4500 TO POWER THE GRAPH CHARTS ──
        db.child(NODE_VIOLATIONS).limitToLast(4500).addValueEventListener(violationsListener!!)
    }

    fun refreshViolations(onComplete: (() -> Unit)? = null) {
        db.child(NODE_VIOLATIONS).limitToLast(4500).get()
            .addOnSuccessListener { snapshot ->
                CoroutineScope(Dispatchers.IO).launch {
                    val list = snapshot.children.mapNotNull { child ->
                        try {
                            var status = child.child("status").value as? String ?: "Detected"
                            val badgeNumber = child.child("badgeNumber").value as? String ?: ""
                            val timestamp = child.child("timestamp").value as? Long ?: 0L

                            if (status.equals("Pending", ignoreCase = true) && (badgeNumber.isEmpty() || badgeNumber.startsWith("MOTO-", ignoreCase = true))) {
                                status = "Detected"
                            }
                            if (status.equals("Detected", ignoreCase = true) && System.currentTimeMillis() - timestamp > 24 * 60 * 60 * 1000L) {
                                status = "Expired"
                            }

                            val rawTypes = child.child("types").value as? List<*>
                            val safeTypes = if (rawTypes != null) {
                                rawTypes.mapNotNull { mapRawTypeToEnum(it.toString()) }
                            } else {
                                listOf(mapRawTypeToEnum(child.child("type").value?.toString() ?: "MANUAL"))
                            }

                            ViolationEntry(
                                id = child.child("id").value as? String ?: child.key ?: "",
                                types = if (safeTypes.isEmpty()) listOf(ViolationType.MANUAL) else safeTypes,
                                timestamp = timestamp, location = child.child("location").value as? String ?: "Unknown Location",
                                status = status, imageUrl = child.child("imageUrl").value as? String ?: "",
                                videoUrl = child.child("videoUrl").value as? String ?: "", badgeNumber = badgeNumber
                            )
                        } catch (e: Exception) { null }
                    }.sortedByDescending { it.timestamp }

                    withContext(Dispatchers.Main) {
                        val existingIds = ViolationRepository.violations.map { it.id }.toSet()
                        val newIds = if (hasCompletedInitialViolationSync) {
                            list.map { it.id }.filter { it !in existingIds }
                        } else {
                            emptyList()
                        }
                        isDataFullySynced.value = true
                        ViolationRepository.violations.clear()
                        ViolationRepository.violations.addAll(list)
                        ViolationRepository.markNewViolations(newIds)
                        hasCompletedInitialViolationSync = true
                        onComplete?.invoke()
                    }
                }
            }
            .addOnFailureListener {
                isDatabaseConnected.value = false
                scheduleDatabaseReconnect()
                onComplete?.invoke()
            }
    }

    fun startListeningForRegistrationRequests() {
        if (isListeningForRegistrationRequests) return
        isListeningForRegistrationRequests = true
        regListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                CoroutineScope(Dispatchers.IO).launch {
                    val list = snapshot.children.mapNotNull { child ->
                        try {
                            RegistrationRequest(id = child.child("id").value as? String ?: child.key ?: "", lastName = child.child("lastName").value as? String ?: "", firstName = child.child("firstName").value as? String ?: "", middleName = child.child("middleName").value as? String ?: "", username = child.child("username").value as? String ?: "", badgeNumber = child.child("badgeNumber").value as? String ?: "", password = child.child("password").value as? String ?: "", station = child.child("station").value as? String ?: "", timestamp = child.child("timestamp").value as? Long ?: 0L, status = child.child("status").value as? String ?: "Pending")
                        } catch (e: Exception) { null }
                    }.sortedByDescending { it.timestamp }
                    withContext(Dispatchers.Main) { registrationRequests.clear(); registrationRequests.addAll(list) }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child(NODE_REGISTRATION_REQUESTS).limitToLast(50).addValueEventListener(regListener!!)
    }

    fun startListeningForResetRequests() {
        if (isListeningForResetRequests) return
        isListeningForResetRequests = true
        resetListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                CoroutineScope(Dispatchers.IO).launch {
                    val list = snapshot.children.mapNotNull { child ->
                        try { ResetRequest(id = child.child("id").value as? String ?: child.key ?: "", username = child.child("username").value as? String ?: "", badgeNumber = child.child("badgeNumber").value as? String ?: "", desiredPassword = child.child("desiredPassword").value as? String ?: "", timestamp = child.child("timestamp").value as? Long ?: 0L, status = child.child("status").value as? String ?: "Pending") } catch (e: Exception) { null }
                    }.sortedByDescending { it.timestamp }
                    withContext(Dispatchers.Main) { resetRequests.clear(); resetRequests.addAll(list) }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child(NODE_RESET_REQUESTS).limitToLast(50).addValueEventListener(resetListener!!)
    }

    fun startListeningForChangePasswordRequests() {
        if (isListeningForChangePasswordRequests) return
        isListeningForChangePasswordRequests = true
        changePwListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                CoroutineScope(Dispatchers.IO).launch {
                    val list = snapshot.children.mapNotNull { child ->
                        try { ChangePasswordRequest(id = child.child("id").value as? String ?: child.key ?: "", username = child.child("username").value as? String ?: "", badgeNumber = child.child("badgeNumber").value as? String ?: "", desiredPassword = child.child("desiredPassword").value as? String ?: "", timestamp = child.child("timestamp").value as? Long ?: 0L, status = child.child("status").value as? String ?: "Pending") } catch (e: Exception) { null }
                    }.sortedByDescending { it.timestamp }
                    withContext(Dispatchers.Main) { changePasswordRequests.clear(); changePasswordRequests.addAll(list) }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child(NODE_CHANGE_PW_REQUESTS).limitToLast(50).addValueEventListener(changePwListener!!)
    }

    private fun startListeningForAICoreHeartbeat() {
        if (isListeningForAIHeartbeat) return
        isListeningForAIHeartbeat = true
        val heartbeatTimeoutMs = 60000L
        db.child(NODE_SYSTEM_HEALTH).child("aiLastHeartbeat").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lastHeartbeat = snapshot.value as? Long ?: 0L
                val now = System.currentTimeMillis() + serverTimeOffset
                val isAlive = lastHeartbeat > 0L && (now - lastHeartbeat) < heartbeatTimeoutMs
                val newStatus = if (isAlive) "Connected" else "Not Connected"
                db.child(NODE_SYSTEM_HEALTH).child("aiCoreStatus").setValue(newStatus)
                currentSystemHealth.value = currentSystemHealth.value.copy(aiCoreStatus = newStatus)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
