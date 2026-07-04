package com.sunmmy.interndiary

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

/**
 * Grid adapter for the in-app album picker. Renders device photos (queried via
 * MediaStore) as square thumbnails with a multi-select check badge.
 *
 * Selection order is preserved so the caption/upload order matches what the user
 * tapped. Selection state lives here; the sheet observes it via [onSelectionChanged].
 */
class AlbumAdapter(
    private val onSelectionChanged: (List<Uri>) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.PhotoHolder>() {

    private val photos = mutableListOf<Uri>()
    private val selected = mutableListOf<Uri>()

    fun submit(uris: List<Uri>) {
        photos.clear()
        photos.addAll(uris)
        selected.clear()
        notifyDataSetChanged()
        onSelectionChanged(selectedUris())
    }

    fun selectedUris(): List<Uri> = selected.toList()

    override fun getItemCount(): Int = photos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_album_photo, parent, false)
        return PhotoHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoHolder, position: Int) {
        val uri = photos[position]
        holder.itemView.post {
            val size = holder.itemView.width
            if (size > 0 && holder.itemView.height != size) {
                holder.itemView.layoutParams = holder.itemView.layoutParams.apply {
                    height = size
                }
                holder.itemView.requestLayout()
            }
        }
        holder.photo.setImageURI(uri)
        val isSelected = selected.contains(uri)
        holder.setSelected(isSelected)
        holder.itemView.setOnClickListener {
            if (selected.contains(uri)) {
                selected.remove(uri)
            } else {
                selected.add(uri)
            }
            notifyItemChanged(position)
            onSelectionChanged(selectedUris())
        }
    }

    class PhotoHolder(view: View) : RecyclerView.ViewHolder(view) {
        val photo: ImageView = view.findViewById(R.id.imagePhoto)
        private val overlay: View = view.findViewById(R.id.selectionOverlay)
        private val badge: ImageView = view.findViewById(R.id.checkBadge)
        fun setSelected(selected: Boolean) {
            overlay.visibility = if (selected) View.VISIBLE else View.GONE
            badge.visibility = if (selected) View.VISIBLE else View.GONE
        }
    }
}
