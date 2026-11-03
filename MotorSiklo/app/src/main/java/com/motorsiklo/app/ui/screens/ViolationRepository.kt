package com.motorsiklo.app.ui.screens

import androidx.compose.runtime.mutableStateListOf

/**
 * Repository to manage traffic violations.
 * Uses mutableStateListOf to ensure Jetpack Compose observes additions/deletions.
 *
 * EXPIRY LOGIC:
 * - Violations in "Detected" status (i.e. not yet ticketed by an enforcer) expire from
 * the active UI after [EXPIRY_DURATION_MS] (default: 30 minutes).
 * - Expiry is UI-only — expired entries are NOT deleted from Firebase or this list.
 * They are simply filtered out of the active display by [isExpiredFromUi].
 * - Once an enforcer creates a ticket, status moves to "Pending"/"Awaiting_Approval" and
 * expiry no longer applies — it's already been processed.
 * - Admins see expired-but-unprocessed violations via [allNonVoidedViolations] in the
 * Admin Dashboard's "Unapprehended" panel (which uses its own filter logic).
 */
object ViolationRepository {

    /** How long a freshly-Detected violation stays ticketable in the active UI feed (30 minutes). */
    const val EXPIRY_DURATION_MS: Long = 30 * 60 * 1000L

    /** Shared state list for all screens — contains every violation ever synced. */
    val violations = mutableStateListOf<ViolationEntry>()
    val newViolationIds = mutableStateListOf<String>()

    /**
     * Returns true when a Detected (not-yet-ticketed) violation has been sitting
     * unprocessed longer than [EXPIRY_DURATION_MS]. Violations in any other status
     * (Pending, Awaiting_Approval, Completed, Voided) never expire from the UI.
     */
    fun isExpiredFromUi(entry: ViolationEntry): Boolean {
        if (entry.status != "Detected") return false
        return (System.currentTimeMillis() - entry.timestamp) > EXPIRY_DURATION_MS
    }

    /**
     * Returns the remaining milliseconds before a Detected violation disappears
     * from the active UI. Returns 0 if already expired or not Detected.
     */
    fun getRemainingMs(entry: ViolationEntry): Long {
        if (entry.status != "Detected") return 0L
        val elapsed = System.currentTimeMillis() - entry.timestamp
        return (EXPIRY_DURATION_MS - elapsed).coerceAtLeast(0L)
    }

    fun markAsCompleted(id: String) {
        updateStatus(id, "Completed")
    }

    fun updateStatus(id: String, newStatus: String) {
        val index = violations.indexOfFirst { it.id == id }
        if (index != -1) {
            // Update status and trigger recomposition instantly
            violations[index] = violations[index].copy(status = newStatus)
        }
    }

    fun addViolation(entry: ViolationEntry) {
        // Adds to the top of the list (newest first)
        violations.add(0, entry)
    }

    fun markNewViolations(ids: Collection<String>) {
        ids.filter { it.isNotBlank() && !newViolationIds.contains(it) }.forEach { newViolationIds.add(it) }
    }

    fun clearNewViolation(id: String) {
        newViolationIds.remove(id)
    }
}
