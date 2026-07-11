package com.sunmmy.interndiary

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder

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
        holder.bind(photos[position], selected.contains(photos[position]))
    }

    override fun onBindViewHolder(holder: PhotoHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.setSelected(selected.contains(photos[position]))
        } else {
            onBindViewHolder(holder, position)
        }
    }

    inner class PhotoHolder(view: View) : RecyclerView.ViewHolder(view) {
        val photo: ImageView = view.findViewById(R.id.imagePhoto)
        private val overlay: View = view.findViewById(R.id.selectionOverlay)
        private val badge: ImageView = view.findViewById(R.id.checkBadge)
        fun bind(uri: Uri, isSelected: Boolean) {
            photo.tag = uri
            itemView.post {
                val size = itemView.width
                if (size > 0 && itemView.height != size) {
                    itemView.layoutParams = itemView.layoutParams.apply { height = size }
                    itemView.requestLayout()
                }
                if (photo.tag == uri) {
                    photo.load(uri) {
                        placeholder(R.drawable.ic_gallery)
                        error(R.drawable.ic_gallery)
                        crossfade(false)
                    }
                }
            }
            setSelected(isSelected)
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                val clicked = photo.tag as? Uri ?: return@setOnClickListener
                if (selected.contains(clicked)) selected.remove(clicked) else selected.add(clicked)
                notifyItemChanged(position, PAYLOAD_SELECTION)
                onSelectionChanged(selectedUris())
            }
        }
        fun setSelected(selected: Boolean) {
            overlay.visibility = if (selected) View.VISIBLE else View.GONE
            badge.visibility = if (selected) View.VISIBLE else View.GONE
        }
    }

    companion object {
        private const val PAYLOAD_SELECTION = "selection"
    }
}
