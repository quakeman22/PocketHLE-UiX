package com.pockethle.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FavoritesAdapter(
    private val libraryRoot: String,
    private val onRun: (GameEntry) -> Unit,
) : RecyclerView.Adapter<FavoritesAdapter.ViewHolder>() {

    private var items: List<GameEntry> = emptyList()

    fun submit(newItems: List<GameEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.fav_icon)
        private val title: TextView = view.findViewById(R.id.fav_title)

        fun bind(entry: GameEntry) {
            title.text = entry.displayName
            val bitmap = GameArt.load(itemView.context, entry, libraryRoot)
            if (bitmap != null) {
                icon.setImageBitmap(bitmap)
                icon.imageTintList = null
            } else {
                icon.setImageResource(R.drawable.ic_game)
            }
            itemView.setOnClickListener { onRun(entry) }
        }
    }
}
