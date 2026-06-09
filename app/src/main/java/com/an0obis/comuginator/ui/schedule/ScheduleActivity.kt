package com.an0obis.comuginator.ui.schedule

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.ScheduleItemDto
import com.an0obis.comuginator.ui.base.BaseActivity
import com.an0obis.comuginator.ui.library.LibraryItemPickerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ScheduleActivity : BaseActivity() {

    private lateinit var rvSchedule: RecyclerView
    private lateinit var adapter: ScheduleAdapter
    private lateinit var tvCounter: android.widget.TextView

    // Launch library picker → then open ScheduleItemActivity for creation
    private val pickItemLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val itemId = LibraryItemPickerActivity.parseResultItemId(result.data)
                ?: return@registerForActivityResult

            lifecycleScope.launch {
                try {
                    val card = withContext(Dispatchers.IO) {
                        ApiClient.api.getLibraryItems(auth = store.authHeaderOrThrow())
                    }.items.firstOrNull { it.id == itemId } ?: return@launch

                    scheduleItemLauncher.launch(ScheduleItemActivity.createIntent(this@ScheduleActivity, card))
                } catch (e: Exception) {
                    Toast.makeText(this@ScheduleActivity, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }

    // Launch ScheduleItemActivity for create or edit → reload on OK
    private val scheduleItemLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) loadItems()
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
            onDelete = { item -> deleteItem(item) },
            onEdit = { item -> scheduleItemLauncher.launch(ScheduleItemActivity.editIntent(this, item)) }
        )

        rvSchedule.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.schedule_span_count))
        rvSchedule.adapter = adapter

        findViewById<View>(R.id.btnAdd).setOnClickListener {
            pickItemLauncher.launch(
                LibraryItemPickerActivity.createIntent(this, LibraryItemPickerActivity.TargetMode.PICK_SINGLE)
            )
        }

        loadItems()
    }

    private fun loadItems() {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.getScheduleItems(auth = store.authHeaderOrThrow())
                }
                val sorted = response.items.sortedBy { nextSortKey(it) }
                adapter.submitItems(sorted)
                tvCounter.text = resources.getQuantityString(
                    R.plurals.schedule_items_count, sorted.size, sorted.size
                )
            } catch (e: Exception) {
                Toast.makeText(this@ScheduleActivity, "Failed to load schedule: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun deleteItem(item: ScheduleItemDto) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.deleteScheduleItem(auth = store.authHeaderOrThrow(), id = item.id)
                }
                loadItems()
            } catch (e: Exception) {
                Toast.makeText(this@ScheduleActivity, "Failed to delete: ${e.message}", Toast.LENGTH_LONG).show()
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
                target.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        }

        if (item.mode == "WEEKDAY" && item.weekdays.isNotEmpty()) {
            val calWeekdays = item.weekdays.map {
                when (it) {
                    1 -> Calendar.MONDAY; 2 -> Calendar.TUESDAY; 3 -> Calendar.WEDNESDAY
                    4 -> Calendar.THURSDAY; 5 -> Calendar.FRIDAY; 6 -> Calendar.SATURDAY
                    7 -> Calendar.SUNDAY; else -> now.get(Calendar.DAY_OF_WEEK)
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
