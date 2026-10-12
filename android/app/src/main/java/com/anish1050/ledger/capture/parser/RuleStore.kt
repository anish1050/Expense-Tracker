package com.anish1050.ledger.capture.parser

import android.content.Context
import com.anish1050.ledger.data.api.LedgerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

// Bundled rules.json is the floor; a newer version from the server replaces it
// on disk so bank formats can be fixed without an app release.
class RuleStore(private val context: Context, private val api: LedgerApi) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile get() = File(context.filesDir, "rules.json")

    private val _engine = MutableStateFlow<RuleEngine?>(null)
    val engine: StateFlow<RuleEngine?> = _engine

    @Volatile private var ruleSet: RuleSet? = null

    suspend fun load(): RuleEngine = withContext(Dispatchers.IO) {
        _engine.value ?: run {
            val text = cacheFile.takeIf { it.exists() }?.readText() ?: context.assets.open("rules.json").bufferedReader().use { it.readText() }
            val parsed = json.decodeFromString(RuleSet.serializer(), text)
            ruleSet = parsed
            RuleEngine(parsed).also { _engine.value = it }
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val current = ruleSet ?: run { load(); ruleSet!! }
        val latest = api.fetchRules(sinceVersion = current.version) ?: return@withContext
        if (latest.version <= current.version) return@withContext
        // Validate every regex before trusting the download.
        val engine = runCatching { RuleEngine(latest) }.getOrNull() ?: return@withContext
        cacheFile.writeText(json.encodeToString(RuleSet.serializer(), latest))
        ruleSet = latest
        _engine.value = engine
    }
}
