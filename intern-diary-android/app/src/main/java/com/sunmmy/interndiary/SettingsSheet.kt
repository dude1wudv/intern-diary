package com.sunmmy.interndiary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sunmmy.interndiary.databinding.SheetSettingsBinding
import kotlinx.coroutines.launch

/**
 * Collapsible config panel reached from the top app bar gear icon.
 *
 * Holds the server URL, API token, a save / test-connection pair, and the theme toggle.
 */
class SettingsSheet : BottomSheetDialogFragment() {

    private var _binding: SheetSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsStore: SettingsStore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsStore = SettingsStore(requireContext())

        binding.editServerUrl.setText(settingsStore.serverUrl)
        binding.editToken.setText(settingsStore.token)

        selectThemeButton(settingsStore.themeMode)

        binding.btnSave.setOnClickListener { saveToken() }
        binding.btnTestConnection.setOnClickListener { testConnection() }

        binding.toggleTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.btnThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.btnThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (mode != settingsStore.themeMode) {
                settingsStore.themeMode = mode
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
    }

    private fun selectThemeButton(mode: Int) {
        val id = when (mode) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.id.btnThemeLight
            AppCompatDelegate.MODE_NIGHT_YES -> R.id.btnThemeDark
            else -> R.id.btnThemeSystem
        }
        binding.toggleTheme.check(id)
    }

    private fun saveToken() {
        settingsStore.serverUrl = binding.editServerUrl.text?.toString().orEmpty()
        settingsStore.token = binding.editToken.text?.toString().orEmpty()
        showResult("已保存")
    }

    private fun testConnection() {
        showResult("正在测试连接…")
        val serverUrl = binding.editServerUrl.text?.toString().orEmpty().trim().trimEnd('/')
        val token = binding.editToken.text?.toString().orEmpty().trim()
        if (serverUrl.isEmpty()) {
            showResult("请先填写服务器地址")
            return
        }
        lifecycleScope.launch {
            val client = ApiClient(serverUrl, token)
            val result = client.checkHealth()
            showResult(
                result.fold(
                    onSuccess = { it },
                    onFailure = { "连接失败: ${it.message}" }
                )
            )
        }
    }

    private fun showResult(text: String) {
        binding.textResult.visibility = View.VISIBLE
        binding.textResult.text = text
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingsSheet"
    }
}
