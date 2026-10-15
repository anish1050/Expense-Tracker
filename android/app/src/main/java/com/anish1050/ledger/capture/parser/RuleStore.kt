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
// on disk so bank formats can be fixed without an app release. Whichever of the
// bundled and cached sets has the higher version wins, and a cache that fails
// to parse is discarded.
class RuleStore(private val context: Context, private val api: LedgerApi) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile get() = File(context.filesDir, "rules.json")

    private val _engine = MutableStateFlow<RuleEngine?>(null)
    val engine: StateFlow<RuleEngine?> = _engine

    @Volatile private var ruleSet: RuleSet? = null

    suspend fun load(): RuleEngine = withContext(Dispatchers.IO) {
        _engine.value ?: run {
            val bundled = parse(context.assets.open("rules.json").bufferedReader().use { it.readText() })
                ?: error("bundled rules.json is invalid")
            val cached = cacheFile.takeIf { it.exists() }?.let { file ->
                parse(file.readText()) ?: run { file.delete(); null }
            }
            val chosen = if (cached != null && cached.version > bundled.version) cached else bundled
            ruleSet = chosen
            RuleEngine(chosen).also { _engine.value = it }
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (ruleSet == null) load()
        val current = ruleSet ?: return@withContext
        val latest = api.fetchRules(sinceVersion = current.version) ?: return@withContext
        if (latest.version <= current.version) return@withContext
        // Validate every regex before trusting the download.
        val engine = runCatching { RuleEngine(latest) }.getOrNull() ?: return@withContext
        val tmp = File(cacheFile.path + ".tmp")
        tmp.writeText(json.encodeToString(RuleSet.serializer(), latest))
        if (!tmp.renameTo(cacheFile)) { tmp.delete(); return@withContext }
        ruleSet = latest
        _engine.value = engine
    }

    private fun parse(text: String): RuleSet? =
        runCatching { json.decodeFromString(RuleSet.serializer(), text) }.getOrNull()?.takeIf { set -> runCatching { RuleEngine(set) }.isSuccess }
}
