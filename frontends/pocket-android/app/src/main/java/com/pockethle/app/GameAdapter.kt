package com.pockethle.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.BitmapFactory
import java.io.File
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for the library screen — launcher-style poster
 * grid. Tapping a poster runs the game; Settings/Remove/"Choose cover"
 * live behind the tile's overflow (⋮) button.
 *
 * Cover art precedence: user-picked custom cover (via [CoverStore]) >
 * icon extracted from the game's .CAB > generic placeholder.
 */
class GameAdapter(
    private val onRun: (GameEntry) -> Unit,
    private val onSettings: (GameEntry) -> Unit,
    private val onRemove: (GameEntry) -> Unit,
    private val onChooseCover: (GameEntry) -> Unit,
    private val onResetCover: (GameEntry) -> Unit,
    private val libraryRoot: String,
) : RecyclerView.Adapter<GameAdapter.ViewHolder>() {

    private var items: List<GameEntry> = emptyList()

    fun submit(newItems: List<GameEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    /** Re-binds just the cover art (e.g. after the user picks a new one), without a full rebind. */
    fun notifyCoverChanged(gameId: String) {
        val index = items.indexOfFirst { it.id == gameId }
        if (index >= 0) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val posterCard: View = view.findViewById(R.id.poster_card)
        private val icon = view.findViewById<android.widget.ImageView>(R.id.game_icon)
        private val title: TextView = view.findViewById(R.id.game_title)
        private val moreBtn: ImageButton = view.findViewById(R.id.btn_more)

        fun bind(entry: GameEntry) {
            title.text = entry.displayName

            val customCover = CoverStore.coverFile(itemView.context, entry.id)
            val iconFile = entry.icon?.let { File(libraryRoot, "games/${entry.id}/$it") }
            val artFile = customCover.takeIf { it.isFile } ?: iconFile?.takeIf { it.isFile }
            val bitmap = artFile?.let { BitmapFactory.decodeFile(it.absolutePath) }

            if (bitmap != null) {
                icon.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                icon.setImageBitmap(bitmap)
                icon.imageTintList = null
            } else {
                icon.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                icon.setImageResource(R.drawable.ic_game)
                icon.imageTintList = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(itemView.context, R.color.md_on_surface_variant),
                )
            }

            posterCard.setOnClickListener { onRun(entry) }
            moreBtn.setOnClickListener { showOverflowMenu(it, entry) }
        }

        private fun showOverflowMenu(anchor: View, entry: GameEntry) {
            val popup = PopupMenu(anchor.context, anchor)
            popup.menu.add(0, 0, 0, R.string.action_choose_cover)
            if (CoverStore.coverFile(anchor.context, entry.id).isFile) {
                popup.menu.add(0, 3, 1, R.string.action_reset_cover)
            }
            popup.menu.add(0, 1, 2, R.string.action_settings)
            popup.menu.add(0, 2, 3, R.string.action_remove)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> onChooseCover(entry)
                    1 -> onSettings(entry)
                    2 -> onRemove(entry)
                    3 -> onResetCover(entry)
                }
                true
            }
            popup.show()
        }
    }
}
