package com.an0obis.comuginator.ui.schedule

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.request.ImageRequest
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.AacCardDto
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.ChildHomeNodeDto
import com.an0obis.comuginator.api.CreateScheduleItemRequest
import com.an0obis.comuginator.api.ScheduleItemDto
import com.an0obis.comuginator.api.UpdateScheduleItemRequest
import com.an0obis.comuginator.ui.base.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ScheduleItemActivity : BaseActivity() {

    companion object {
        private const val EXTRA_CARD = "card_json"
        private const val EXTRA_ITEM = "item_json"

        fun createIntent(context: Context, card: AacCardDto): Intent =
            Intent(context, ScheduleItemActivity::class.java).apply {
                putExtra(EXTRA_CARD, com.google.gson.Gson().toJson(card))
            }

        fun editIntent(context: Context, item: ScheduleItemDto): Intent =
            Intent(context, ScheduleItemActivity::class.java).apply {
                putExtra(EXTRA_ITEM, com.google.gson.Gson().toJson(item))
            }
    }

    // views
    private lateinit var tvBreadcrumbs: TextView
    private lateinit var tvTitle: TextView
    private lateinit var ivCardPreview: ImageView
    private lateinit var tvCardLabel: TextView
    private lateinit var rgMode: RadioGroup
    private lateinit var rbExactDate: RadioButton
    private lateinit var rbWeekday: RadioButton
    private lateinit var etName: EditText
    private lateinit var btnPickDate: Button
    private lateinit var llWeekdays: LinearLayout
    private lateinit var cbDays: List<CheckBox>
    private lateinit var btnPickTime: Button
    private lateinit var llForceShow: LinearLayout
    private lateinit var llForceHide: LinearLayout
    private lateinit var btnSave: Button
    private lateinit var tvStatus: TextView

    // state
    private var editItem: ScheduleItemDto? = null
    private var card: AacCardDto? = null
    private var selectedDate: String = ""
    private var selectedTime: String = ""
    private var allNodes: List<ChildHomeNodeDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (redirectedByRoleGuard) return
        setContentView(R.layout.activity_schedule_item)
        ensureInitialized()
    }

    override fun onInitialized() {
        bindViews()

        val gson = com.google.gson.Gson()
        val itemJson = intent.getStringExtra(EXTRA_ITEM)
        val cardJson = intent.getStringExtra(EXTRA_CARD)

        if (itemJson != null) {
            editItem = gson.fromJson(itemJson, ScheduleItemDto::class.java)
            card = editItem!!.cards.firstOrNull()
        } else if (cardJson != null) {
            card = gson.fromJson(cardJson, AacCardDto::class.java)
        }

        val cal = Calendar.getInstance()
        selectedDate = "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        selectedTime = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))

        setupHeader()
        setupCardPreview()
        setupMode()
        setupTimePicker()
        setupSave()

        loadNodes()
    }

    private fun bindViews() {
        tvBreadcrumbs = findViewById(R.id.tvBreadcrumbs)
        tvTitle = findViewById(R.id.tvTitle)
        ivCardPreview = findViewById(R.id.ivCardPreview)
        tvCardLabel = findViewById(R.id.tvCardLabel)
        rgMode = findViewById(R.id.rgMode)
        rbExactDate = findViewById(R.id.rbExactDate)
        rbWeekday = findViewById(R.id.rbWeekday)
        etName = findViewById(R.id.etName)
        btnPickDate = findViewById(R.id.btnPickDate)
        llWeekdays = findViewById(R.id.llWeekdays)
        cbDays = listOf(
            findViewById(R.id.cbMon), findViewById(R.id.cbTue), findViewById(R.id.cbWed),
            findViewById(R.id.cbThu), findViewById(R.id.cbFri), findViewById(R.id.cbSat),
            findViewById(R.id.cbSun)
        )
        btnPickTime = findViewById(R.id.btnPickTime)
        llForceShow = findViewById(R.id.llForceShow)
        llForceHide = findViewById(R.id.llForceHide)
        btnSave = findViewById(R.id.btnSave)
        tvStatus = findViewById(R.id.tvStatus)
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupHeader() {
        val isEdit = editItem != null
        val screenTitle = getString(if (isEdit) R.string.edit_schedule_item else R.string.new_schedule_item)
        tvTitle.text = screenTitle
        tvBreadcrumbs.text = getString(R.string.breadcrumb_schedule_item, screenTitle)

        if (isEdit) {
            val item = editItem!!
            etName.setText(item.name ?: "")
            // restore mode
            if (item.mode == "DATE") {
                rbExactDate.isChecked = true
                selectedDate = item.date?.take(10) ?: selectedDate
                btnPickDate.text = selectedDate
            } else {
                rbWeekday.isChecked = true
                item.weekdays.forEach { day ->
                    if (day in 1..7) cbDays[day - 1].isChecked = true
                }
            }
            selectedTime = item.time
        }
    }

    private fun setupCardPreview() {
        val c = card ?: return
        tvCardLabel.text = editItem?.name?.takeIf { it.isNotEmpty() } ?: c.label
        val imageUrl = c.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            val request = ImageRequest.Builder(this)
                .data(imageUrl)
                .addHeader("Authorization", store.authHeaderOrThrow())
                .target(ivCardPreview)
                .build()
            Coil.imageLoader(this).enqueue(request)
        }
    }

    private fun setupMode() {
        updateModeVisibility(rbExactDate.isChecked)
        rgMode.setOnCheckedChangeListener { _, checkedId ->
            updateModeVisibility(checkedId == R.id.rbExactDate)
        }

        val cal = Calendar.getInstance()
        btnPickDate.text = selectedDate
        btnPickDate.setOnClickListener {
            val parts = selectedDate.split("-")
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    selectedDate = "%04d-%02d-%02d".format(y, m + 1, d)
                    btnPickDate.text = selectedDate
                },
                parts.getOrNull(0)?.toIntOrNull() ?: cal.get(Calendar.YEAR),
                (parts.getOrNull(1)?.toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)) - 1,
                parts.getOrNull(2)?.toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun updateModeVisibility(isDate: Boolean) {
        btnPickDate.visibility = if (isDate) View.VISIBLE else View.GONE
        llWeekdays.visibility = if (isDate) View.GONE else View.VISIBLE
    }

    private fun setupTimePicker() {
        btnPickTime.text = getString(R.string.time_with_value, editItem?.time ?: selectedTime)
        btnPickTime.setOnClickListener {
            val parts = selectedTime.split(":")
            TimePickerDialog(
                this,
                { _, h, m ->
                    selectedTime = "%02d:%02d".format(h, m)
                    btnPickTime.text = getString(R.string.time_with_value, selectedTime)
                },
                parts.getOrNull(0)?.toIntOrNull() ?: 0,
                parts.getOrNull(1)?.toIntOrNull() ?: 0,
                true
            ).show()
        }
        editItem?.let { selectedTime = it.time }
    }

    private fun setupSave() {
        btnSave.setOnClickListener { save() }
    }

    private fun loadNodes() {
        tvStatus.text = getString(R.string.loading)
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.getChildHomeNodes(auth = store.authHeaderOrThrow())
                }
                allNodes = response.items
                renderNodeLists()
                tvStatus.text = ""
            } catch (e: Exception) {
                tvStatus.text = e.message
            }
        }
    }

    private fun renderNodeLists() {
        val showIds = editItem?.forceShowChildHomeNodeIds ?: emptyList()
        val hideIds = editItem?.forceHideChildHomeNodeIds ?: emptyList()
        buildNodeCheckboxes(llForceShow, allNodes, showIds)
        buildNodeCheckboxes(llForceHide, allNodes, hideIds)
    }

    private fun buildNodeCheckboxes(
        container: LinearLayout,
        nodes: List<ChildHomeNodeDto>,
        checkedIds: List<String>
    ) {
        container.removeAllViews()
        nodes.forEach { node ->
            val label = node.labelOverride ?: node.item?.label ?: node.id
            val cb = CheckBox(this).apply {
                text = label
                tag = node.id
                isChecked = node.id in checkedIds
            }
            container.addView(cb)
        }
    }

    private fun checkedNodeIds(container: LinearLayout): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until container.childCount) {
            val cb = container.getChildAt(i) as? CheckBox ?: continue
            if (cb.isChecked) result.add(cb.tag as String)
        }
        return result
    }

    private fun save() {
        val isDate = rbExactDate.isChecked
        val weekdays = if (isDate) emptyList() else
            cbDays.mapIndexedNotNull { i, cb -> if (cb.isChecked) i + 1 else null }

        if (!isDate && weekdays.isEmpty()) {
            Toast.makeText(this, getString(R.string.day_of_week), Toast.LENGTH_SHORT).show()
            return
        }

        val name = etName.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val forceShow = checkedNodeIds(llForceShow)
        val forceHide = checkedNodeIds(llForceHide)

        tvStatus.text = getString(R.string.saving)
        btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                val existing = editItem
                if (existing != null) {
                    withContext(Dispatchers.IO) {
                        ApiClient.api.updateScheduleItem(
                            auth = store.authHeaderOrThrow(),
                            id = existing.id,
                            body = UpdateScheduleItemRequest(
                                mode = if (isDate) "DATE" else "WEEKDAY",
                                name = name,
                                weekdays = weekdays,
                                date = if (isDate) selectedDate else null,
                                time = selectedTime,
                                forceShowChildHomeNodeIds = forceShow,
                                forceHideChildHomeNodeIds = forceHide
                            )
                        )
                    }
                } else {
                    val c = card ?: return@launch
                    withContext(Dispatchers.IO) {
                        ApiClient.api.createScheduleItem(
                            auth = store.authHeaderOrThrow(),
                            body = CreateScheduleItemRequest(
                                mode = if (isDate) "DATE" else "WEEKDAY",
                                name = name,
                                weekdays = weekdays,
                                date = if (isDate) selectedDate else null,
                                time = selectedTime,
                                cards = listOf(c),
                                forceShowChildHomeNodeIds = forceShow,
                                forceHideChildHomeNodeIds = forceHide
                            )
                        )
                    }
                }
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                tvStatus.text = e.message
                btnSave.isEnabled = true
            }
        }
    }
}
