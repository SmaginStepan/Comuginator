package com.an0obis.comuginator.ui.schedule

import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.ui.base.BaseActivity
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.an0obis.comuginator.api.AacCardDto
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.ScheduleItemDto
import com.an0obis.comuginator.ui.library.LibraryItemPickerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.an0obis.comuginator.api.CreateScheduleItemRequest
import java.util.Calendar

class ScheduleActivity : BaseActivity() {

    private lateinit var rvSchedule: RecyclerView
    private lateinit var adapter: ScheduleAdapter
    private lateinit var tvCounter: android.widget.TextView

    private var selectedScheduleCard: AacCardDto? = null

    private val pickItemLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode != RESULT_OK) {
                return@registerForActivityResult
            }

            val itemId =
                LibraryItemPickerActivity.parseResultItemId(result.data)
                    ?: return@registerForActivityResult

            lifecycleScope.launch {
                try {
                    val items = withContext(Dispatchers.IO) {
                        ApiClient.api.getLibraryItems(
                            auth = store.authHeaderOrThrow()
                        )
                    }

                    val card =
                        items.items.firstOrNull { it.id == itemId }
                            ?: return@launch

                    selectedScheduleCard = card

                    showCreateScheduleDialog(card)
                } catch (e: Exception) {
                    Toast.makeText(
                        this@ScheduleActivity,
                        e.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (redirectedByRoleGuard) return

        setContentView(R.layout.activity_schedule)

        ensureInitialized()
    }

    override fun onInitialized() {
        rvSchedule = findViewById(R.id.rvSchedule)
        tvCounter = findViewById(R.id.tvCounter)

        adapter = ScheduleAdapter(
            authToken = store.authHeaderOrThrow().removePrefix("Bearer "),
            onDelete = { item ->
                deleteItem(item)
            },
            onEdit = { item ->
                Toast.makeText(
                    this,
                    "Edit ${item.id}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        rvSchedule.layoutManager = androidx.recyclerview.widget.GridLayoutManager(
            this, resources.getInteger(R.integer.schedule_span_count)
        )
        rvSchedule.adapter = adapter

        findViewById<View>(R.id.btnAdd).setOnClickListener {
            pickItemLauncher.launch(
                LibraryItemPickerActivity.createIntent(
                    this,
                    LibraryItemPickerActivity.TargetMode.PICK_SINGLE
                )
            )
        }

        loadItems()
    }

    private fun showCreateScheduleDialog(card: AacCardDto) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        val modeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }

        val rbDate = RadioButton(this).apply {
            id = View.generateViewId()
            setText(R.string.exact_date)
        }

        val rbWeekday = RadioButton(this).apply {
            id = View.generateViewId()
            setText(R.string.day_of_week)
            isChecked = true
        }

        modeGroup.addView(rbDate)
        modeGroup.addView(rbWeekday)

        val weekdayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val weekdayCheckboxes = weekdayNames.mapIndexed { index, name ->
            android.widget.CheckBox(this).apply {
                id = View.generateViewId()
                text = name
                tag = index + 1
            }
        }
        val weekdaysContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        weekdayCheckboxes.forEach { weekdaysContainer.addView(it) }

        val calendar = Calendar.getInstance()

        var selectedDate = "%04d-%02d-%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        var selectedTime = "%02d:%02d".format(
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )

        val btnDate = android.widget.Button(this).apply {
            text = selectedDate
            visibility = View.GONE
            setOnClickListener {
                DatePickerDialog(
                    this@ScheduleActivity,
                    { _, year, month, day ->
                        selectedDate = "%04d-%02d-%02d".format(year, month + 1, day)
                        text = selectedDate
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }

        val btnTime = android.widget.Button(this).apply {
            text = selectedTime
            setOnClickListener {
                TimePickerDialog(
                    this@ScheduleActivity,
                    { _, hour, minute ->
                        selectedTime = "%02d:%02d".format(hour, minute)
                        text = selectedTime
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            }
        }

        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            val isDate = checkedId == rbDate.id
            weekdaysContainer.visibility = if (isDate) View.GONE else View.VISIBLE
            btnDate.visibility = if (isDate) View.VISIBLE else View.GONE
        }

        container.addView(modeGroup)
        container.addView(weekdaysContainer)
        container.addView(btnDate)
        container.addView(btnTime)

        AlertDialog.Builder(this)
            .setTitle(card.label)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add) { _, _ ->
                val isDate = rbDate.isChecked

                val selectedWeekdays = if (isDate) emptyList() else
                    weekdayCheckboxes.filter { it.isChecked }.map { it.tag as Int }

                val body = CreateScheduleItemRequest(
                    mode = if (isDate) "DATE" else "WEEKDAY",
                    weekdays = selectedWeekdays,
                    date = if (isDate) selectedDate else null,
                    time = selectedTime,
                    cards = listOf(card),
                    isEnabled = true
                )

                createItem(body)
            }
            .show()
    }

    private fun createItem(body: CreateScheduleItemRequest) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.createScheduleItem(
                        auth = store.authHeaderOrThrow(),
                        body = body
                    )
                }

                loadItems()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ScheduleActivity,
                    "Failed to create: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadItems() {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.getScheduleItems(
                        auth = store.authHeaderOrThrow()
                    )
                }

                val sorted = response.items.sortedBy { nextSortKey(it) }
                adapter.submitItems(sorted)
                tvCounter.text = resources.getQuantityString(
                    R.plurals.schedule_items_count, sorted.size, sorted.size
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@ScheduleActivity,
                    "Failed to load schedule: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun deleteItem(item: ScheduleItemDto) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.deleteScheduleItem(
                        auth = store.authHeaderOrThrow(),
                        id = item.id
                    )
                }

                loadItems()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ScheduleActivity,
                    "Failed to delete: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun nextSortKey(item: ScheduleItemDto): Long {
        val now = Calendar.getInstance()

        val hour = item.time.substringBefore(":").toIntOrNull() ?: 0
        val minute = item.time.substringAfter(":").toIntOrNull() ?: 0

        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (item.mode == "DATE" && !item.date.isNullOrBlank()) {
            val parts = item.date.take(10).split("-")
            if (parts.size == 3) {
                target.set(
                    parts[0].toInt(),
                    parts[1].toInt() - 1,
                    parts[2].toInt()
                )
            }
        }

        if (item.mode == "WEEKDAY" && item.weekdays.isNotEmpty()) {
            val calWeekdays = item.weekdays.map {
                when (it) {
                    1 -> Calendar.MONDAY
                    2 -> Calendar.TUESDAY
                    3 -> Calendar.WEDNESDAY
                    4 -> Calendar.THURSDAY
                    5 -> Calendar.FRIDAY
                    6 -> Calendar.SATURDAY
                    7 -> Calendar.SUNDAY
                    else -> now.get(Calendar.DAY_OF_WEEK)
                }
            }
            val nextWeekday = calWeekdays.map { wd ->
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    set(Calendar.DAY_OF_WEEK, wd)
                    if (!after(now)) add(Calendar.DAY_OF_YEAR, 7)
                }
            }.minByOrNull { it.timeInMillis }
            return nextWeekday?.timeInMillis ?: target.timeInMillis
        }

        return target.timeInMillis
    }

}