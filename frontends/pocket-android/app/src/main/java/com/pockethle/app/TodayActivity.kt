package com.pockethle.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * App launcher entry point — a Pocket PC "Today screen" home: favorite
 * games pinned like Outlook appointments, a "Games" softkey that opens
 * the full poster grid ([MainActivity]), and a "Start" softkey with a
 * quick menu (Settings / About / Exit).
 */
class TodayActivity : AppCompatActivity() {

    private lateinit var rootDir: String
    private lateinit var adapter: FavoritesAdapter
    private lateinit var favoritesList: RecyclerView
    private lateinit var favoritesEmpty: TextView
    private lateinit var clock: TextView
    private lateinit var statusLine: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_today)

        rootDir = LibraryPaths.root(this)
        clock = findViewById(R.id.clock)
        statusLine = findViewById(R.id.status_line)
        favoritesList = findViewById(R.id.favorites_list)
        favoritesEmpty = findViewById(R.id.favorites_empty)

        adapter = FavoritesAdapter(libraryRoot = rootDir, onRun = { entry -> launchGame(entry) })
        favoritesList.layoutManager = LinearLayoutManager(this)
        favoritesList.adapter = adapter

        findViewById<View>(R.id.softkey_games).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
            )
        }
        findViewById<View>(R.id.softkey_start).setOnClickListener { showStartMenu(it) }
        findViewById<View>(R.id.btn_add_favorite).setOnClickListener { showAddFavoritesDialog() }
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

        adapter.submit(favorites)
        favoritesList.visibility = if (favorites.isEmpty()) View.GONE else View.VISIBLE
        favoritesEmpty.visibility = if (favorites.isEmpty()) View.VISIBLE else View.GONE

        val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(java.util.Date())
        statusLine.text = "$dateStr\n${getString(R.string.today_owner_line, games.size)}"
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

    private fun showStartMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, R.string.today_menu_settings)
        popup.menu.add(0, 1, 1, R.string.today_menu_about)
        popup.menu.add(0, 2, 2, R.string.today_menu_exit)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> startActivity(Intent(this, SettingsActivity::class.java))
                1 -> AlertDialog.Builder(this)
                    .setTitle(R.string.about_title)
                    .setMessage(NativeBridge.banner())
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                2 -> finishAffinity()
            }
            true
        }
        popup.show()
    }

    private fun launchGame(entry: GameEntry) {
        startActivity(
            Intent(this, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_GAME_ID, entry.id)
                .putExtra(GameActivity.EXTRA_GAME_NAME, entry.displayName),
        )
    }
}
