package com.pockethle.app

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App launcher entry point — a Today screen with quick status rows,
 * favorite games, and a Pocket PC-style start panel that slides in
 * from the left when the bottom Start button is tapped.
 */
class TodayActivity : AppCompatActivity() {

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    private lateinit var rootDir: String
    private lateinit var favoritesAdapter: FavoritesAdapter
    private lateinit var gamesAdapter: FavoritesAdapter
    private lateinit var favoritesList: RecyclerView
    private lateinit var favoritesEmpty: TextView
    private lateinit var clock: TextView
    private lateinit var statusLine: TextView
    private lateinit var ownerText: TextView
    private lateinit var programsText: TextView
    private lateinit var startPanel: View
    private lateinit var startScrim: View
    private var startPanelOpen = false

    private val importGame = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) handleImport(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_today)

        rootDir = LibraryPaths.root(this)
        clock = findViewById(R.id.clock)
        statusLine = findViewById(R.id.status_line)
        ownerText = findViewById(R.id.owner_text)
        programsText = findViewById(R.id.programs_text)
        favoritesList = findViewById(R.id.favorites_list)
        favoritesEmpty = findViewById(R.id.favorites_empty)
        startPanel = findViewById(R.id.start_panel)
        startScrim = findViewById(R.id.start_scrim)

        favoritesAdapter = FavoritesAdapter(libraryRoot = rootDir, onRun = { entry -> launchGame(entry) })
        favoritesList.layoutManager = LinearLayoutManager(this)
        favoritesList.adapter = favoritesAdapter

        gamesAdapter = FavoritesAdapter(libraryRoot = rootDir, onRun = { entry ->
            closeStartPanel()
            launchGame(entry)
        })
        findViewById<RecyclerView>(R.id.start_games_list).apply {
            layoutManager = LinearLayoutManager(this@TodayActivity)
            adapter = gamesAdapter
        }

        findViewById<View>(R.id.softkey_start).setOnClickListener {
            toggleStartPanel()
        }
        startScrim.setOnClickListener { closeStartPanel() }
        findViewById<View>(R.id.btn_install_cab).setOnClickListener { openImportPicker() }
        findViewById<View>(R.id.owner_row).setOnClickListener { editOwnerInfo() }
        findViewById<View>(R.id.programs_row).setOnClickListener { launchLibrary() }
        findViewById<View>(R.id.settings_row).setOnClickListener { openSettings() }
        findViewById<View>(R.id.about_row).setOnClickListener { showAboutDialog() }
        findViewById<View>(R.id.btn_add_favorite).setOnClickListener { showAddFavoritesDialog() }

        onBackPressedDispatcher.addCallback(this) {
            if (startPanelOpen) {
                closeStartPanel()
            } else {
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateClock()
        refresh()
    }

    private fun updateClock() {
        clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
    }

    private fun allGames(): List<GameEntry> {
        return try {
            GameEntry.listFromJson(NativeBridge.listGames(rootDir))
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun refresh() {
        val games = allGames()
        FavoritesStore.pruneMissing(this, games.map { it.id }.toSet())

        val favIds = FavoritesStore.ids(this)
        val byId = games.associateBy { it.id }
        val favorites = favIds.mapNotNull { byId[it] }

        favoritesAdapter.submit(favorites)
        gamesAdapter.submit(games)
        favoritesList.visibility = if (favorites.isEmpty()) View.GONE else View.VISIBLE
        favoritesEmpty.visibility = if (favorites.isEmpty()) View.VISIBLE else View.GONE

        val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(java.util.Date())
        statusLine.text = dateStr
        ownerText.text = prefs.getString(KEY_OWNER_TEXT, null)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.today_owner_hint)
        programsText.text = getString(R.string.today_programs_line, games.size)
    }

    private fun showAddFavoritesDialog() {
        val games = allGames()
        if (games.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.empty_library)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val currentFavorites = FavoritesStore.ids(this).toMutableSet()
        val labels = games.map { it.displayName }.toTypedArray()
        val checked = games.map { it.id in currentFavorites }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.today_add_favorites_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                if (isChecked) currentFavorites.add(games[which].id) else currentFavorites.remove(games[which].id)
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // Keep existing favorite order, then append newly-checked ones.
                val ordered = FavoritesStore.ids(this).filter { it in currentFavorites } +
                    games.map { it.id }.filter { it in currentFavorites && it !in FavoritesStore.ids(this) }
                FavoritesStore.setAll(this, ordered)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun handleImport(uri: Uri) {
        val name = queryDisplayName(uri) ?: "import.cab"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val cabFile = LibraryPaths.copyUriToCache(this@TodayActivity, uri, name)
                    importArchiveOrExe(cabFile)
                }
            }
            result.fold(
                onSuccess = {
                    if (handleImportResult(it)) {
                        refresh()
                    }
                },
                onFailure = { err ->
                    AlertDialog.Builder(this@TodayActivity)
                        .setTitle(R.string.import_failed)
                        .setMessage(err.message ?: "unknown")
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                },
            )
        }
    }

    private fun openImportPicker() {
        importGame.launch(
            arrayOf(
                "application/vnd.ms-cab-compressed",
                "application/x-rar-compressed",
                "application/zip",
                "application/octet-stream",
                "*/*",
            ),
        )
    }

    private fun editOwnerInfo() {
        val input = EditText(this).apply {
            setText(prefs.getString(KEY_OWNER_TEXT, "") ?: "")
            hint = getString(R.string.today_owner_dialog_hint)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setLines(2)
            minLines = 1
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.today_owner_dialog_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.edit().putString(KEY_OWNER_TEXT, input.text?.toString()?.trim().orEmpty()).apply()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(NativeBridge.banner())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun launchLibrary() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
    }

    private fun importArchiveOrExe(file: java.io.File): String {
        return when (file.extension.lowercase()) {
            "exe" -> NativeBridge.importExe(rootDir, file.absolutePath)
            "rar" -> error("RAR is not supported on-device yet; extract it first and import the CAB or EXE")
            else -> NativeBridge.importCab(rootDir, file.absolutePath)
        }
    }

    private fun handleImportResult(raw: String): Boolean {
        try {
            val obj = org.json.JSONObject(raw)
            if (!obj.optBoolean("ok", true)) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.import_failed_title)
                    .setMessage(obj.optString("error"))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return false
            }
            return true
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle(R.string.import_failed_title)
                .setMessage(raw)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return false
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { c ->
            val ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (ix >= 0 && c.moveToFirst()) c.getString(ix) else null
        }
    }

    private fun toggleStartPanel() {
        if (startPanelOpen) {
            closeStartPanel()
        } else {
            openStartPanel()
        }
    }

    private fun openStartPanel() {
        if (startPanelOpen) return
        startPanelOpen = true
        startScrim.visibility = View.VISIBLE
        startPanel.visibility = View.VISIBLE
        startPanel.alpha = 1f
        startPanel.doOnLayout {
            startPanel.translationX = -startPanel.width.toFloat()
            startPanel.animate()
                .translationX(0f)
                .setDuration(160L)
                .start()
        }
    }

    private fun closeStartPanel() {
        if (!startPanelOpen) return
        startPanelOpen = false
        startPanel.animate()
            .translationX(-startPanel.width.toFloat())
            .setDuration(140L)
            .withEndAction {
                startPanel.visibility = View.GONE
                startPanel.translationX = 0f
            }
            .start()
        startScrim.visibility = View.GONE
    }

    private fun launchGame(entry: GameEntry) {
        startActivity(
            Intent(this, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_GAME_ID, entry.id)
                .putExtra(GameActivity.EXTRA_GAME_NAME, entry.displayName),
        )
    }

    companion object {
        private const val PREFS_NAME = "today_screen"
        private const val KEY_OWNER_TEXT = "owner_text"
    }
}
