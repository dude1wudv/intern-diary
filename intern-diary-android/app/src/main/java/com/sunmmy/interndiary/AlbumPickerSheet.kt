package com.sunmmy.interndiary

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sunmmy.interndiary.databinding.SheetAlbumPickerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-app album picker shown from the compose bar "+" button.
 *
 * Presents a multi-select grid of device photos (queried via MediaStore) plus a
 * shared caption field, so the user can send several images with one piece of
 * text (image + 配文). Also offers a "take photo" shortcut.
 *
 * Results are delivered to the host via [Listener]; the host performs the actual
 * uploads. This fixes the old flow, where picking a single image immediately and
 * silently uploaded it with no chance to add a caption or pick more.
 */
class AlbumPickerSheet : BottomSheetDialogFragment() {

    interface Listener {
        /** User confirmed [uris] (in tap order) with an optional shared [caption]. */
        fun onAlbumImagesChosen(uris: List<Uri>, caption: String)

        /** User tapped the camera shortcut. */
        fun onAlbumTakePhoto()
    }

    private var _binding: SheetAlbumPickerBinding? = null
    private val binding get() = _binding!!

    private lateinit var albumAdapter: AlbumAdapter

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadPhotos() else showPermissionNeeded()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetAlbumPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        albumAdapter = AlbumAdapter { selected -> onSelectionChanged(selected) }
        binding.recyclerAlbum.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerAlbum.adapter = albumAdapter

        binding.btnTakePhoto.setOnClickListener {
            (parentFragment as? Listener ?: activity as? Listener)?.onAlbumTakePhoto()
            dismiss()
        }

        binding.btnAlbumSend.setOnClickListener { confirmSelection() }

        onSelectionChanged(emptyList())
        ensurePermissionThenLoad()
    }

    override fun onStart() {
        super.onStart()
        // Expand to a tall sheet so the grid is usable immediately.
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    private fun readPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun ensurePermissionThenLoad() {
        val perm = readPermission()
        if (ContextCompat.checkSelfPermission(requireContext(), perm) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            loadPhotos()
        } else {
            requestPermission.launch(perm)
        }
    }

    private fun showPermissionNeeded() {
        binding.textPermission.visibility = View.VISIBLE
        binding.recyclerAlbum.visibility = View.GONE
        binding.textEmpty.visibility = View.GONE
    }

    private fun loadPhotos() {
        binding.textPermission.visibility = View.GONE
        lifecycleScope.launch {
            val uris = withContext(Dispatchers.IO) { queryImages() }
            if (_binding == null) return@launch
            albumAdapter.submit(uris)
            binding.textEmpty.visibility = if (uris.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerAlbum.visibility = if (uris.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun queryImages(): List<Uri> {
        val out = ArrayList<Uri>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        requireContext().contentResolver.query(
            collection, projection, null, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                out.add(Uri.withAppendedPath(collection, id.toString()))
            }
        }
        return out
    }

    private fun onSelectionChanged(selected: List<Uri>) {
        val count = selected.size
        binding.btnAlbumSend.isEnabled = count > 0
        binding.textAlbumTitle.text = if (count > 0) {
            getString(R.string.album_selected_count, count)
        } else {
            getString(R.string.album_title)
        }
    }

    private fun confirmSelection() {
        val selected = albumAdapter.selectedUris()
        if (selected.isEmpty()) return
        val caption = binding.editAlbumCaption.text?.toString()?.trim().orEmpty()
        (parentFragment as? Listener ?: activity as? Listener)
            ?.onAlbumImagesChosen(selected, caption)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AlbumPickerSheet"
    }
}
