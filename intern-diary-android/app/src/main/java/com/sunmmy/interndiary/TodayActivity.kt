package com.sunmmy.interndiary

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.sunmmy.interndiary.databinding.ActivityTodayBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val MAX_UPLOAD_IMAGE_COUNT = 12
private const val MAX_UPLOAD_IMAGE_BYTES = 12L * 1024L * 1024L
private const val DIRECT_UPLOAD_IMAGE_BYTES = 2L * 1024L * 1024L
private const val MAX_PARALLEL_IMAGE_UPLOADS = 3
private const val UPLOAD_IMAGE_LONG_EDGE_PX = 1280
private const val UPLOAD_IMAGE_JPEG_QUALITY = 78

class TodayActivity : AppCompatActivity(), AlbumPickerSheet.Listener {

    enum class Mode { DIARY, ASSISTANT }

    private lateinit var binding: ActivityTodayBinding
    private lateinit var settingsStore: SettingsStore
    private lateinit var conversationStore: ConversationStore
    private lateinit var assistantHistoryStore: AssistantHistoryStore

    private val chatAdapter = ChatAdapter(onCopyDraft = { copyDraft(it) })
    private val assistantAdapter = AssistantAdapter(
        onConfirmPreview = { confirmPreview(it) },
        onCancelPreview = { cancelPreview(it) },
        onViewDraft = { viewDraftFromAssistant() },
        onGenerateFinal = { openDiaryAndGenerateFinal() },
        onBackDiary = { openDiaryTab() },
        onCopyText = { copyAssistantText(it) },
        onDeleteMessage = { deleteAssistantMessage(it) },
        onRegenerateMessage = { regenerateAssistantMessage(it) },
    )
    private val historyAdapter = HistoryAdapter(onDateClick = { switchToDate(it) })
    private val sessionAdapter = AssistantSessionAdapter(
        onSessionClick = { switchToSession(it) },
        onRenameSession = { renameSession(it) },
        onDeleteSession = { confirmDeleteSession(it) },
    )

    private var currentMode: Mode = Mode.DIARY
    private var currentDate: String = ""
    private var nextChatId: Long = 0L
    private var nextAssistantId: Long = 0L
    private var isDiaryEditMode = false
    private var isBusy = false
    private var pendingCameraPhotoFile: File? = null

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera() else snack("需要相机权限才能拍照")
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val file = pendingCameraPhotoFile
            if (file != null && file.exists()) {
                val uri = FileProvider.getUriForFile(this, "com.sunmmy.interndiary.fileprovider", file)
                uploadImages(listOf(uri), "")
            } else {
                snack("拍照失败")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = SettingsStore(this)
        conversationStore = ConversationStore(this)
        assistantHistoryStore = AssistantHistoryStore(this)
        AppCompatDelegate.setDefaultNightMode(settingsStore.themeMode)

        binding = ActivityTodayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentDate = todayDateString()

        binding.recyclerMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.recyclerMessages.adapter = chatAdapter

        setupBottomDeadZone()
        setupTabs()
        setupDrawer()
        setupToolbar()
        registerBackHandler()

        binding.btnSend.setOnClickListener { onSendClicked() }
        binding.btnAttach.setOnClickListener { openAlbumPicker() }
        binding.chipSortDay.setOnClickListener { sortDay() }
        binding.chipGenerateDraft.setOnClickListener { generateDraft() }
        binding.chipGenerateDiary.setOnClickListener { confirmGenerateDiary() }
        binding.chipDownloadDocx.setOnClickListener { downloadDocx() }
        binding.chipGenerateReport.setOnClickListener { confirmGenerateWeeklyReport() }
        binding.chipRefresh.setOnClickListener { refreshStatus() }
        binding.chipDiaryEditMode.setOnCheckedChangeListener { _, checked -> onAssistantModeToggled(checked) }

        loadConversation(currentDate)
    }

    override fun onResume() {
        super.onResume()
        refreshHistory()
        if (currentMode == Mode.DIARY && chatAdapter.isEmpty()) refreshStatus()
        if (currentMode == Mode.ASSISTANT) refreshSessionList()
    }

    override fun onPause() {
        super.onPause()
        persistDiary()
        persistAssistantSession()
    }

    // region setup

    private fun setupBottomDeadZone() {
        binding.bottomBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val barHeight = bottom - top
            if (binding.recyclerMessages.paddingBottom != barHeight) {
                val wasAtBottom = !binding.recyclerMessages.canScrollVertically(1)
                binding.recyclerMessages.updatePadding(bottom = barHeight)
                if (wasAtBottom && !chatAdapter.isEmpty()) {
                    binding.recyclerMessages.scrollToPosition(chatAdapter.lastIndex())
                }
            }
        }
    }

    private fun setupTabs() {
        binding.tabMode.addTab(binding.tabMode.newTab().setText(R.string.tab_diary))
        binding.tabMode.addTab(binding.tabMode.newTab().setText(R.string.tab_assistant))
        binding.tabMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                switchMode(if (tab.position == 0) Mode.DIARY else Mode.ASSISTANT)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupDrawer() {
        binding.drawerContent.sectionDiary.visibility = View.VISIBLE
        binding.drawerContent.sectionAssistant.visibility = View.GONE

        binding.drawerContent.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.drawerContent.recyclerHistory.adapter = historyAdapter
        binding.drawerContent.btnNewChat.setOnClickListener { startNewDiaryChat() }

        binding.drawerContent.recyclerSessions.layoutManager = LinearLayoutManager(this)
        binding.drawerContent.recyclerSessions.adapter = sessionAdapter
        binding.drawerContent.btnNewSession.setOnClickListener { startNewAssistantSession() }
        binding.drawerContent.editSessionSearch.addTextChangedListener { refreshSessionList() }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> { openSettings(); true }
                else -> false
            }
        }
        binding.btnDatePill.setOnClickListener { showDatePicker() }
        updateDateBadge()
    }

    // endregion

    // region mode switching

    private fun switchMode(mode: Mode) {
        if (isBusy) return
        if (currentMode == mode) return
        persistDiary()
        persistAssistantSession()
        currentMode = mode
        applyModeUi(mode)
    }

    private fun applyModeUi(mode: Mode) {
        when (mode) {
            Mode.DIARY -> {
                resetAssistantEditUi()
                binding.recyclerMessages.adapter = chatAdapter
                binding.scrollDiaryChips.visibility = View.VISIBLE
                binding.rowAssistantMode.visibility = View.GONE
                binding.btnAttach.visibility = View.VISIBLE
                binding.btnDatePill.visibility = View.VISIBLE
                binding.editContent.hint = getString(R.string.hint_message)
                binding.drawerContent.sectionDiary.visibility = View.VISIBLE
                binding.drawerContent.sectionAssistant.visibility = View.GONE
                binding.emptyTitle.setText(R.string.empty_title)
                binding.emptySubtitle.setText(R.string.empty_subtitle)
                updateEmptyVisibility(chatAdapter.isEmpty())
                if (!chatAdapter.isEmpty()) binding.recyclerMessages.scrollToPosition(chatAdapter.lastIndex())
            }
            Mode.ASSISTANT -> {
                binding.recyclerMessages.adapter = assistantAdapter
                binding.scrollDiaryChips.visibility = View.GONE
                binding.rowAssistantMode.visibility = View.VISIBLE
                binding.btnAttach.visibility = View.GONE
                binding.btnDatePill.visibility = if (isDiaryEditMode) View.VISIBLE else View.GONE
                binding.editContent.hint = getString(R.string.assistant_hint_message)
                binding.drawerContent.sectionDiary.visibility = View.GONE
                binding.drawerContent.sectionAssistant.visibility = View.VISIBLE
                binding.emptyTitle.setText(R.string.assistant_empty_title)
                binding.emptySubtitle.setText(R.string.assistant_empty_subtitle)
                loadCurrentAssistantSession()
            }
        }
    }

    private fun updateEmptyVisibility(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    // endregion

    // region diary conversation

    private fun persistDiary() {
        conversationStore.save(currentDate, chatAdapter.snapshot())
    }

    private fun loadConversation(date: String) {
        val saved = conversationStore.load(date)
        chatAdapter.submit(saved)
        nextChatId = (saved.maxOfOrNull { it.id } ?: -1L) + 1L
        updateEmptyVisibility(saved.isEmpty())
        if (saved.isNotEmpty()) binding.recyclerMessages.scrollToPosition(chatAdapter.lastIndex())
        updateDateBadge()
    }

    private fun switchToDate(date: String) {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        if (currentMode == Mode.ASSISTANT && isDiaryEditMode) {
            currentDate = date
            updateDateBadge()
            addAssistantMessage(
                AssistantMessage.SystemNote(
                    nextAssistantId(),
                    nowTimeString(),
                    getString(R.string.diary_edit_mode_enter_note, currentDate),
                )
            )
            return
        }
        if (currentMode != Mode.DIARY) {
            binding.tabMode.getTabAt(0)?.select()
            return
        }
        if (date == currentDate) return
        persistDiary()
        currentDate = date
        loadConversation(date)
        refreshHistory()
        if (chatAdapter.isEmpty()) refreshStatus()
    }

    private fun startNewDiaryChat() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        if (currentMode != Mode.DIARY) {
            binding.tabMode.getTabAt(0)?.select()
            return
        }
        persistDiary()
        currentDate = todayDateString()
        chatAdapter.submit(emptyList())
        conversationStore.save(currentDate, emptyList())
        nextChatId = 0L
        updateEmptyVisibility(true)
        updateDateBadge()
        refreshHistory()
        refreshStatus()
    }

    private fun refreshHistory() {
        val dates = conversationStore.archivedDates()
        historyAdapter.submit(dates, currentDate, todayDateString())
        binding.drawerContent.textNoHistory.visibility = if (dates.isEmpty()) View.VISIBLE else View.GONE
    }

    // endregion

    // region assistant session

    private fun persistAssistantSession() {
        assistantHistoryStore.saveCurrentSession(assistantAdapter.snapshot())
    }

    private fun loadCurrentAssistantSession() {
        val id = assistantHistoryStore.currentSessionId
        val saved = assistantHistoryStore.loadSession(id)
        assistantAdapter.submit(saved)
        nextAssistantId = (saved.maxOfOrNull { it.id } ?: -1L) + 1L
        updateEmptyVisibility(saved.isEmpty())
        if (saved.isNotEmpty()) binding.recyclerMessages.scrollToPosition(assistantAdapter.lastIndex())
        refreshSessionList()
    }

    private fun switchToSession(id: String) {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        if (assistantHistoryStore.currentSessionId == id) return
        persistAssistantSession()
        assistantHistoryStore.selectSession(id)
        val saved = assistantHistoryStore.loadSession(id)
        assistantAdapter.submit(saved)
        nextAssistantId = (saved.maxOfOrNull { it.id } ?: -1L) + 1L
        resetAssistantEditUi()
        updateEmptyVisibility(saved.isEmpty())
        if (saved.isNotEmpty()) binding.recyclerMessages.scrollToPosition(assistantAdapter.lastIndex())
        refreshSessionList()
    }

    private fun startNewAssistantSession() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        persistAssistantSession()
        assistantHistoryStore.createSession()
        resetAssistantEditUi()
        assistantAdapter.submit(emptyList())
        nextAssistantId = 0L
        updateEmptyVisibility(true)
        refreshSessionList()
    }

    private fun refreshSessionList() {
        val q = binding.drawerContent.editSessionSearch.text?.toString()?.trim().orEmpty()
        val sessions = assistantHistoryStore.listSessions()
            .filter { q.isEmpty() || it.preview.contains(q, ignoreCase = true) }
        sessionAdapter.submit(sessions, assistantHistoryStore.currentSessionId)
        binding.drawerContent.textNoSessions.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renameSession(id: String) {
        val input = android.widget.EditText(this)
        input.setText(assistantHistoryStore.listSessions().firstOrNull { it.id == id }?.preview.orEmpty())
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.assistant_rename_session_title)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                assistantHistoryStore.renameSession(id, input.text?.toString().orEmpty())
                refreshSessionList()
            }
            .show()
    }

    private fun confirmDeleteSession(id: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.assistant_delete_session_title)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                assistantHistoryStore.deleteSession(id)
                if (currentMode == Mode.ASSISTANT) loadCurrentAssistantSession()
            }
            .show()
    }

    // endregion
    // region assistant mode toggle + send

    private fun onAssistantModeToggled(entering: Boolean) {
        if (isBusy) {
            binding.chipDiaryEditMode.isChecked = isDiaryEditMode
            return
        }
        if (entering == isDiaryEditMode) return
        isDiaryEditMode = entering
        if (entering) {
            currentDate = todayDateString()
            updateDateBadge()
            binding.btnDatePill.visibility = View.VISIBLE
            setTargetChipsVisible(true)
            binding.chipDiaryEditMode.setText(R.string.action_exit_diary_edit)
            binding.editContent.hint = getString(R.string.assistant_hint_message_edit)
            addAssistantMessage(
                AssistantMessage.SystemNote(
                    nextAssistantId(),
                    nowTimeString(),
                    getString(R.string.diary_edit_mode_enter_note, todayDateString()),
                )
            )
        } else {
            binding.btnDatePill.visibility = View.GONE
            setTargetChipsVisible(false)
            binding.chipDiaryEditMode.setText(R.string.action_enter_diary_edit)
            binding.editContent.hint = getString(R.string.assistant_hint_message)
            addAssistantMessage(
                AssistantMessage.SystemNote(nextAssistantId(), nowTimeString(), getString(R.string.diary_edit_mode_exit_note))
            )
        }
    }

    private fun onSendClicked() {
        when (currentMode) {
            Mode.DIARY -> sendText()
            Mode.ASSISTANT -> sendAssistantMessage()
        }
    }

    private fun sendAssistantMessage() {
        if (isBusy) return
        val content = binding.editContent.text?.toString()?.trim().orEmpty()
        if (content.isEmpty()) return
        val client = requireClient() ?: return
        binding.editContent.setText("")
        addAssistantMessage(AssistantMessage.UserText(nextAssistantId(), nowTimeString(), content))
        if (isDiaryEditMode) {
            sendDiaryEditInstruction(client, content)
        } else {
            sendPlainChat(client, content)
        }
    }

    private fun sendPlainChat(client: ApiClient, content: String) {
        setBusy(true)
        val history = chatHistoryForApi()
        lifecycleScope.launch {
            val result = client.assistantChat(history)
            result.fold(
                onSuccess = { reply -> animateAssistantReply(reply) },
                onFailure = { e ->
                    setBusy(false)
                    snackWithRetry(getString(R.string.assistant_send_failed, e.message ?: "")) {
                        sendPlainChat(client, content)
                    }
                }
            )
        }
    }

    private fun sendDiaryEditInstruction(client: ApiClient, instruction: String) {
        setBusy(true)
        val history = diaryEditHistoryForApi()
        val date = currentDate
        val targets = selectedDiaryEditTargets()
        lifecycleScope.launch {
            val result = client.diaryEditPreview(date, instruction, history, targets)
            setBusy(false)
            result.fold(
                onSuccess = { preview ->
                    addAssistantMessage(
                        AssistantMessage.DiaryPreview(
                            nextAssistantId(),
                            nowTimeString(),
                            preview.reply,
                            preview.previewId,
                            preview.changes.map {
                                AssistantMessage.ChangeItem(
                                    it.target,
                                    it.beforeSummary,
                                    it.afterSummary,
                                    it.beforeContent,
                                    it.newContent,
                                )
                            },
                            AssistantMessage.PreviewStatus.PENDING,
                        )
                    )
                },
                onFailure = { e ->
                    snackWithRetry(getString(R.string.assistant_send_failed, e.message ?: "")) {
                        sendDiaryEditInstruction(client, instruction)
                    }
                }
            )
        }
    }

    private fun confirmPreview(preview: AssistantMessage.DiaryPreview) {
        if (isBusy) return
        if (preview.status != AssistantMessage.PreviewStatus.PENDING) return
        val client = requireClient() ?: return
        setBusy(true)
        lifecycleScope.launch {
            val result = client.diaryEditConfirm(preview.previewId)
            setBusy(false)
            result.fold(
                onSuccess = { updatePreviewStatus(preview, AssistantMessage.PreviewStatus.CONFIRMED) },
                onFailure = { e ->
                    val msg = e.message.orEmpty()
                    snack(
                        if ("404" in msg) getString(R.string.preview_expired)
                        else getString(R.string.preview_confirm_failed, msg)
                    )
                }
            )
        }
    }

    private fun cancelPreview(preview: AssistantMessage.DiaryPreview) {
        if (preview.status != AssistantMessage.PreviewStatus.PENDING) return
        updatePreviewStatus(preview, AssistantMessage.PreviewStatus.CANCELLED)
    }

    private fun updatePreviewStatus(preview: AssistantMessage.DiaryPreview, status: AssistantMessage.PreviewStatus) {
        assistantAdapter.replace(preview.copy(status = status))
        persistAssistantSession()
    }

    private fun chatHistoryForApi(): List<Map<String, String>> = textTurns().takeLast(20)

    private fun diaryEditHistoryForApi(): List<Map<String, String>> {
        val turns = textTurns()
        return turns.subList(0, maxOf(0, turns.size - 1)).takeLast(20)
    }

    private fun textTurns(): List<Map<String, String>> = assistantAdapter.snapshot().mapNotNull { m ->
        when (m) {
            is AssistantMessage.UserText -> mapOf("role" to "user", "content" to m.content)
            is AssistantMessage.Assistant -> mapOf("role" to "assistant", "content" to m.content)
            else -> null
        }
    }

    private fun nextAssistantId(): Long = nextAssistantId++

    private fun addAssistantMessage(message: AssistantMessage) {
        updateEmptyVisibility(false)
        assistantAdapter.add(message)
        binding.recyclerMessages.scrollToPosition(assistantAdapter.lastIndex())
        persistAssistantSession()
    }

    private suspend fun animateAssistantReply(reply: String) {
        val id = nextAssistantId()
        val msg = AssistantMessage.Assistant(id, nowTimeString(), "")
        addAssistantMessage(msg)
        val step = 12
        var i = 0
        while (i < reply.length) {
            i = minOf(reply.length, i + step)
            assistantAdapter.replace(msg.copy(content = reply.substring(0, i)))
            binding.recyclerMessages.scrollToPosition(assistantAdapter.lastIndex())
            delay(18)
        }
        persistAssistantSession()
        setBusy(false)
    }

    private fun setTargetChipsVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        binding.chipTargetDraft.visibility = v
        binding.chipTargetSorted.visibility = v
        binding.chipTargetImages.visibility = v
    }

    private fun resetAssistantEditUi() {
        isDiaryEditMode = false
        binding.chipDiaryEditMode.isChecked = false
        binding.chipDiaryEditMode.setText(R.string.action_enter_diary_edit)
        binding.editContent.hint = getString(R.string.assistant_hint_message)
        binding.btnDatePill.visibility = if (currentMode == Mode.ASSISTANT) View.GONE else View.VISIBLE
        setTargetChipsVisible(false)
    }

    private fun selectedDiaryEditTargets(): List<String> {
        val out = mutableListOf<String>()
        if (binding.chipTargetDraft.isChecked) out.add("diary_draft.md")
        if (binding.chipTargetSorted.isChecked) out.add("sorted_notes.md")
        if (binding.chipTargetImages.isChecked) out.add("image_descriptions")
        return out
    }

    private fun openDiaryTab() {
        binding.tabMode.getTabAt(0)?.select()
    }

    private fun viewDraftFromAssistant() {
        val client = requireClient() ?: return
        val date = currentDate
        openDiaryTab()
        setBusy(true)
        lifecycleScope.launch {
            val result = client.getDraft(date)
            setBusy(false)
            result.fold(
                onSuccess = { draft ->
                    addChatMessage(ChatMessage.DiaryDraft(nextChatId(), nowTimeString(), draft))
                },
                onFailure = { e -> snack(e.message ?: "读取草稿失败") },
            )
        }
    }

    private fun openDiaryAndGenerateFinal() {
        openDiaryTab()
        confirmGenerateDiary()
    }

    private fun copyAssistantText(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("assistant", text))
        snack(getString(R.string.assistant_copied))
    }

    private fun deleteAssistantMessage(message: AssistantMessage) {
        assistantAdapter.remove(message)
        persistAssistantSession()
        updateEmptyVisibility(assistantAdapter.isEmpty())
    }

    private fun regenerateAssistantMessage(message: AssistantMessage.Assistant) {
        if (isBusy) return
        val snapshot = assistantAdapter.snapshot()
        val index = snapshot.indexOfFirst { it.id == message.id }
        val previousUser = snapshot.take(index).lastOrNull { it is AssistantMessage.UserText } as? AssistantMessage.UserText
        if (previousUser != null) {
            assistantAdapter.remove(message)
            persistAssistantSession()
            val client = requireClient() ?: return
            sendPlainChat(client, previousUser.content)
        }
    }

    // endregion

    // region date helpers

    private fun todayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        return sdf.format(Date())
    }

    private fun nowTimeString(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.CHINA)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        return sdf.format(Date())
    }

    private fun updateDateBadge() {
        binding.btnDatePill.text = if (currentMode == Mode.ASSISTANT && isDiaryEditMode) {
            getString(R.string.assistant_editing_date, currentDate)
        } else {
            currentDate
        }
    }

    private fun showDatePicker() {
        val utcMillis = dateStringToUtcMillis(currentDate)
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.action_change_date)
            .setSelection(utcMillis)
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            val date = utcMillisToDateString(selection)
            switchToDate(date)
        }
        picker.show(supportFragmentManager, "date_picker")
    }

    private fun dateStringToUtcMillis(date: String): Long {
        val parts = date.split("-").mapNotNull { it.toIntOrNull() }
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        if (parts.size == 3) cal.set(parts[0], parts[1] - 1, parts[2])
        return cal.timeInMillis
    }

    private fun utcMillisToDateString(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    private fun weekRangeFor(date: String): Pair<String, String> {
        val tz = TimeZone.getTimeZone("Asia/Shanghai")
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
            timeZone = tz
            isLenient = false
        }
        val cal = Calendar.getInstance(tz, Locale.CHINA)
        cal.time = runCatching { sdf.parse(date) }.getOrNull() ?: Date()
        cal.firstDayOfWeek = Calendar.MONDAY
        val offset = if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) -6 else Calendar.MONDAY - cal.get(Calendar.DAY_OF_WEEK)
        cal.add(Calendar.DAY_OF_MONTH, offset)
        val start = sdf.format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 6)
        return start to sdf.format(cal.time)
    }

    // endregion

    private fun openSettings() {
        SettingsSheet().show(supportFragmentManager, SettingsSheet.TAG)
    }

    private fun requireClient(): ApiClient? {
        val serverUrl = settingsStore.serverUrl
        if (serverUrl.isEmpty()) {
            snack("请先在设置中填写服务器地址")
            openSettings()
            return null
        }
        val token = settingsStore.token
        if (token.isEmpty()) {
            snack("请先在设置中填写 API Token")
            openSettings()
            return null
        }
        return ApiClient(serverUrl, token)
    }

    private fun nextChatId(): Long = nextChatId++

    private fun addChatMessage(message: ChatMessage) {
        updateEmptyVisibility(false)
        chatAdapter.add(message)
        binding.recyclerMessages.scrollToPosition(chatAdapter.lastIndex())
        persistDiary()
    }

    private fun setBusy(busy: Boolean) {
        isBusy = busy
        binding.progressBar.visibility = if (busy) View.VISIBLE else View.GONE
        binding.textBusy.visibility = if (busy && currentMode == Mode.ASSISTANT) View.VISIBLE else View.GONE
        binding.btnSend.isEnabled = !busy
        binding.chipDiaryEditMode.isEnabled = !busy
        binding.chipTargetDraft.isEnabled = !busy
        binding.chipTargetSorted.isEnabled = !busy
        binding.chipTargetImages.isEnabled = !busy
        setTabInteractionEnabled(!busy)
        assistantAdapter.setActionsEnabled(!busy)
    }

    private fun setTabInteractionEnabled(enabled: Boolean) {
        binding.tabMode.isEnabled = enabled
        (binding.tabMode.getChildAt(0) as? android.view.ViewGroup)?.let { strip ->
            for (i in 0 until strip.childCount) {
                strip.getChildAt(i).isEnabled = enabled
            }
        }
    }

    private fun snack(text: String) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT).show()
    }

    private fun snackWithRetry(text: String, onRetry: () -> Unit) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG)
            .setAction(R.string.assistant_retry) { onRetry() }
            .show()
    }

    private fun copyDraft(markdown: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("diary_draft", markdown))
        snack(getString(R.string.draft_copied))
    }

    // region actions

    private fun sendText() {
        val content = binding.editContent.text?.toString()?.trim().orEmpty()
        if (content.isEmpty()) {
            snack("请输入文字内容")
            return
        }
        val client = requireClient() ?: return
        binding.editContent.setText("")
        addChatMessage(ChatMessage.UserText(nextChatId(), nowTimeString(), content))
        setBusy(true)
        lifecycleScope.launch {
            val result = client.sendText(currentDate, content)
            setBusy(false)
            result.fold(
                onSuccess = { refreshStatus() },
                onFailure = { snack("发送失败: ${it.message}") }
            )
        }
    }

    // region image flow

    private fun openAlbumPicker() {
        AlbumPickerSheet().show(supportFragmentManager, AlbumPickerSheet.TAG)
    }

    override fun onAlbumImagesChosen(uris: List<Uri>, caption: String) {
        if (uris.isEmpty()) return
        uploadImages(uris, caption)
    }

    override fun onAlbumTakePhoto() {
        onCameraClicked()
    }

    private fun onCameraClicked() {
        val granted = checkSelfPermission(android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else requestCameraPermission.launch(android.Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        try {
            val imagesDir = File(cacheDir, "images")
            imagesDir.mkdirs()
            val file = File(imagesDir, "camera_${System.currentTimeMillis()}.jpg")
            pendingCameraPhotoFile = file
            val uri = FileProvider.getUriForFile(this, "com.sunmmy.interndiary.fileprovider", file)
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            snack("无法启动相机: ${e.message}")
        }
    }

    private fun uploadImages(uris: List<Uri>, caption: String) {
        val client = requireClient() ?: return
        val cap = caption.trim().takeIf { it.isNotEmpty() }
        setBusy(true)
        lifecycleScope.launch {
            var failures = (uris.size - MAX_UPLOAD_IMAGE_COUNT).coerceAtLeast(0)
            for (batch in uris.take(MAX_UPLOAD_IMAGE_COUNT).withIndex().chunked(MAX_PARALLEL_IMAGE_UPLOADS)) {
                failures += batch.map { (index, uri) ->
                    async {
                        val filename = "img_${System.currentTimeMillis()}_$index.jpg"
                        val uploadFile = withContext(Dispatchers.IO) {
                            copyUriToTempUploadFile(uri, filename)
                        } ?: return@async 1
                        val localPath = withContext(Dispatchers.IO) {
                            conversationStore.persistImage(uri, filename)
                        }
                        addChatMessage(
                            ChatMessage.UserImage(nextChatId(), nowTimeString(), uri, localPath, if (index == 0) cap else null)
                        )
                        val result = try {
                            client.uploadImage(currentDate, uploadFile, filename, note = cap.orEmpty())
                        } finally {
                            withContext(Dispatchers.IO) { uploadFile.delete() }
                        }
                        if (result.isFailure) 1 else 0
                    }
                }.awaitAll().sum()
            }
            setBusy(false)
            if (failures > 0) snack("有 $failures 张图片上传失败")
            refreshStatus()
        }
    }

    private fun copyUriToTempUploadFile(uri: Uri, filename: String): File? {
        val file = File(cacheDir, "upload_$filename")
        return try {
            val sourceSize = uriSize(uri)
            if (sourceSize in 1..DIRECT_UPLOAD_IMAGE_BYTES) {
                return copyUriWithLimit(uri, file, DIRECT_UPLOAD_IMAGE_BYTES)
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsInput = contentResolver.openInputStream(uri) ?: return null
            boundsInput.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return copyUriWithLimit(uri, file, MAX_UPLOAD_IMAGE_BYTES)
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = imageSampleSize(bounds.outWidth, bounds.outHeight)
            }
            val decoded = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null
            val maxEdge = maxOf(decoded.width, decoded.height)
            val uploadBitmap = if (maxEdge > UPLOAD_IMAGE_LONG_EDGE_PX) {
                val scale = UPLOAD_IMAGE_LONG_EDGE_PX.toFloat() / maxEdge.toFloat()
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }
            FileOutputStream(file).use { out ->
                if (!uploadBitmap.compress(Bitmap.CompressFormat.JPEG, UPLOAD_IMAGE_JPEG_QUALITY, out)) {
                    file.delete()
                    return null
                }
            }
            if (uploadBitmap !== decoded) uploadBitmap.recycle()
            decoded.recycle()
            file.takeIf { it.length() <= MAX_UPLOAD_IMAGE_BYTES } ?: run {
                file.delete()
                null
            }
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    private fun imageSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > UPLOAD_IMAGE_LONG_EDGE_PX * 2 || height / sample > UPLOAD_IMAGE_LONG_EDGE_PX * 2) {
            sample *= 2
        }
        return sample
    }

    private fun uriSize(uri: Uri): Long {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getLong(index)
        }
        return contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
    }

    private fun copyUriWithLimit(uri: Uri, file: File, maxBytes: Long): File? {
        val input = contentResolver.openInputStream(uri) ?: return null
        input.use { source ->
            FileOutputStream(file).use { sink ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) {
                        file.delete()
                        return null
                    }
                    sink.write(buffer, 0, read)
                }
            }
        }
        return file
    }

    // endregion

    private fun refreshStatus() {
        val client = requireClient() ?: return
        setBusy(true)
        lifecycleScope.launch {
            val result = client.getDay(currentDate)
            setBusy(false)
            result.fold(
                onSuccess = { body -> addChatMessage(buildStatusCard(body)) },
                onFailure = { snack("状态加载失败: ${it.message}") }
            )
        }
    }

    private fun buildStatusCard(body: String): ChatMessage.SystemCard {
        val summary = try {
            val json = JSONObject(body)
            buildString {
                append("文字记录: ").append(if (json.optBoolean("raw_text_exists")) "已有" else "无").append('\n')
                append("图片数量: ").append(json.optInt("image_count")).append('\n')
                append("已识图数量: ").append(json.optInt("described_image_count")).append('\n')
                append("素材整理: ").append(if (json.optBoolean("sorted_notes_exists")) "已完成" else "未完成").append('\n')
                append("日记草稿: ").append(if (json.optBoolean("diary_draft_exists")) "已生成" else "未生成").append('\n')
                append("正式日记: ").append(if (json.optBoolean("diary_final_exists")) "已生成" else "未生成")
            }
        } catch (e: Exception) {
            body
        }
        return ChatMessage.SystemCard(nextChatId(), nowTimeString(), "今日状态 · $currentDate", summary)
    }

    private fun sortDay() {
        val client = requireClient() ?: return
        addChatMessage(ChatMessage.Assistant(nextChatId(), nowTimeString(), "正在整理今日素材…"))
        setBusy(true)
        lifecycleScope.launch {
            val result = client.sortDay(currentDate)
            setBusy(false)
            result.fold(
                onSuccess = {
                    addChatMessage(ChatMessage.Assistant(nextChatId(), nowTimeString(), "素材整理完成。"))
                    refreshStatus()
                },
                onFailure = { snack("整理失败: ${it.message}") }
            )
        }
    }

    private fun generateDraft() {
        val client = requireClient() ?: return
        addChatMessage(ChatMessage.Assistant(nextChatId(), nowTimeString(), "正在生成日记草稿…"))
        setBusy(true)
        lifecycleScope.launch {
            val result = client.generateDiary(currentDate)
            setBusy(false)
            result.fold(
                onSuccess = { body ->
                    addChatMessage(ChatMessage.DiaryDraft(nextChatId(), nowTimeString(), extractDraft(body)))
                    refreshStatus()
                },
                onFailure = { snack("生成失败: ${it.message}") }
            )
        }
    }

    private fun confirmGenerateDiary() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_generate_diary)
            .setMessage("将根据今日已整理的素材生成正式日记并可下载 Word，可能需要一些时间。是否继续？")
            .setNegativeButton("取消", null)
            .setPositiveButton("生成") { _, _ -> generateDiary() }
            .show()
    }

    private fun generateDiary() {
        val client = requireClient() ?: return
        addChatMessage(ChatMessage.Assistant(nextChatId(), nowTimeString(), "正在生成正式日记…"))
        setBusy(true)
        lifecycleScope.launch {
            val result = client.generateDiary(currentDate)
            setBusy(false)
            result.fold(
                onSuccess = { body ->
                    addChatMessage(ChatMessage.DiaryDraft(nextChatId(), nowTimeString(), extractDraft(body)))
                    refreshStatus()
                },
                onFailure = { snack("生成失败: ${it.message}") }
            )
        }
    }

    private fun extractDraft(body: String): String {
        return try {
            val json = JSONObject(body)
            val draft = json.optString("draft")
            if (draft.isNullOrBlank()) "日记已生成。" else draft
        } catch (e: Exception) {
            body
        }
    }

    private fun downloadDocx() {
        val client = requireClient() ?: return
        setBusy(true)
        lifecycleScope.launch {
            val result = client.downloadDocx(currentDate)
            setBusy(false)
            result.fold(
                onSuccess = { bytes ->
                    val savedPath = saveDocxToDownloads(bytes, "diary_final_$currentDate.docx")
                    if (savedPath != null) {
                        addChatMessage(ChatMessage.Assistant(nextChatId(), nowTimeString(), "已保存 Word 文件到：$savedPath"))
                    } else {
                        snack("保存文件失败")
                    }
                },
                onFailure = { snack("下载失败: ${it.message}") }
            )
        }
    }

    private fun confirmGenerateWeeklyReport() {
        val (start, end) = weekRangeFor(currentDate)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_generate_report)
            .setMessage("将生成 $start 至 $end 的周报并下载 Word，可能需要一些时间。是否继续？")
            .setNegativeButton("取消", null)
            .setPositiveButton("生成") { _, _ -> generateWeeklyReport(start, end) }
            .show()
    }

    private fun generateWeeklyReport(startDate: String, endDate: String) {
        val client = requireClient() ?: return
        addChatMessage(ChatMessage.Assistant(nextChatId(), nowTimeString(), "正在生成周报（$startDate 至 $endDate）…"))
        setBusy(true)
        lifecycleScope.launch {
            val report = client.generateReport("weekly", startDate, endDate)
            if (report.isFailure) {
                setBusy(false)
                snack("周报生成失败: ${report.exceptionOrNull()?.message}")
                return@launch
            }
            val value = report.getOrThrow()
            if (value.markdown.isNotBlank()) {
                addChatMessage(ChatMessage.DiaryDraft(nextChatId(), nowTimeString(), value.markdown))
            }
            val docx = client.downloadReportDocx(value.reportId)
            setBusy(false)
            docx.fold(
                onSuccess = { bytes ->
                    val savedPath = saveDocxToDownloads(bytes, "weekly_report_${startDate}_$endDate.docx")
                    if (savedPath != null) {
                        addChatMessage(ChatMessage.Assistant(nextChatId(), nowTimeString(), "已保存周报 Word 文件到：$savedPath"))
                    } else {
                        snack("保存文件失败")
                    }
                },
                onFailure = { snack("周报下载失败: ${it.message}") },
            )
        }
    }

    private fun saveDocxToDownloads(bytes: ByteArray, filename: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(
                        MediaStore.Downloads.MIME_TYPE,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return null
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                "Downloads/$filename"
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
                dir.mkdirs()
                val file = File(dir, filename)
                FileOutputStream(file).use { it.write(bytes) }
                file.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(this) {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    // endregion
}
