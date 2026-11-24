package app.valenced.xposed.manager

import android.os.Bundle
import android.os.SystemClock
import java.util.UUID
import org.json.JSONObject

/** Single in-memory, user-initiated job. Only the manager can enqueue it;
 * only the authenticated Discord UID can claim and finish it via the provider. */
internal object ManagerPluginMailbox {
    data class Job(val id: String, val operation: String, val payload: String,
        val created: Long = SystemClock.elapsedRealtime(), var claimed: Boolean = false,
        var result: String? = null)
    private var current: Job? = null
    private val allowed = setOf("snapshot", "refresh", "add_repo", "toggle_repo", "remove_repo",
        "plan", "install", "enable", "disable", "uninstall")
    @Synchronized fun submit(operation: String, payload: JSONObject): String {
        require(operation in allowed)
        require(payload.toString().length <= 4096)
        val old = current
        check(old == null || old.result != null || SystemClock.elapsedRealtime() - old.created > 120_000L) {
            "Eine Plugin-Aktion läuft bereits."
        }
        return UUID.randomUUID().toString().also { current = Job(it, operation, payload.toString()) }
    }
    @Synchronized fun claim(): Bundle {
        val job = current ?: return Bundle.EMPTY
        if (job.claimed || job.result != null || SystemClock.elapsedRealtime() - job.created > 120_000L)
            return Bundle.EMPTY
        job.claimed = true
        return Bundle().apply {
            putString("id", job.id)
            putString("operation", job.operation)
            putString("payload", job.payload)
        }
    }
    @Synchronized fun finish(id: String, result: String) {
        val job = current ?: return
        if (job.id == id && job.claimed && job.result == null) {
            job.result = if (result.length <= 350_000) result else
                JSONObject().put("error", "Katalog zu groß. Entferne eine Quelle.").toString()
        }
    }
    @Synchronized fun cancel(id: String): Boolean {
        val job = current ?: return false
        if (job.id != id || job.claimed || job.result != null) return false
        job.result = JSONObject().put("error", "Aktion abgebrochen.").toString()
        return true
    }
    @Synchronized fun result(id: String): String? {
        val job = current ?: return JSONObject().put("error", "Aktion nicht mehr verfügbar.").toString()
        if (job.id != id) return JSONObject().put("error", "Aktion ersetzt.").toString()
        if (job.result == null && SystemClock.elapsedRealtime() - job.created > 120_000L)
            job.result = JSONObject().put("error", "Discord antwortet nicht. Öffne Discord erneut und aktualisiere.").toString()
        return job.result
    }
}
