package com.an0obis.comuginator.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.scale
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.AacMessageListItemDto
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.ScheduleItemDto
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.ui.childhome.ChildHomeActivity
import com.an0obis.comuginator.ui.messaging.IncomingMessageActivity
import java.util.Calendar

class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private var authHeader: String? = null

    override suspend fun doWork(): Result {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val widgetIds = manager.getAppWidgetIds(
            ComponentName(applicationContext, ComuginatorWidgetProvider::class.java)
        )
        if (widgetIds.isEmpty()) return Result.success()

        val store = SessionStore(applicationContext)
        val auth = store.authHeader() ?: return Result.success()
        authHeader = auth

        return try {
            val views = buildViews(store, auth)
            widgetIds.forEach { manager.updateAppWidget(it, views) }
            Result.success()
        } catch (e: Exception) {
            Log.w("WidgetUpdateWorker", "widget update failed", e)
            Result.retry()
        }
    }

    private suspend fun buildViews(store: SessionStore, auth: String): RemoteViews {
        val views = RemoteViews(applicationContext.packageName, R.layout.widget_comuginator)

        val unread = try {
            ApiClient.api.getAacMessages(auth = auth, scope = "all").items
                .filter { isAwaitingReply(it, store.userId) }
                .maxByOrNull { it.createdAt }
        } catch (e: Exception) {
            Log.w("WidgetUpdateWorker", "failed to load messages", e)
            null
        }

        if (unread != null) {
            bindMessage(views, unread)
        } else {
            bindSchedule(views, auth)
        }
        return views
    }

    // Mirrors BaseActivity.shouldOpenIncoming: a message still waiting for
    // this user's (next) reply.
    private fun isAwaitingReply(msg: AacMessageListItemDto, userId: String?): Boolean {
        if (msg.toUserId != userId) return false
        if (msg.suggestedReplies.isEmpty()) return false

        if (msg.mode != "SEQUENCE") {
            return msg.reply == null
        }

        val currentReplyId = msg.reply?.reply?.lastOrNull()?.id ?: return true
        if (currentReplyId == "SEQUENCE_COMPLETED") return false

        val currentIndex = msg.suggestedReplies.indexOfFirst { it.id == currentReplyId }
        if (currentIndex < 0) return true

        return currentIndex < msg.suggestedReplies.lastIndex
    }

    private fun bindMessage(views: RemoteViews, msg: AacMessageListItemDto) {
        views.setViewVisibility(R.id.widgetMessageContainer, View.VISIBLE)
        views.setViewVisibility(R.id.widgetScheduleContainer, View.GONE)

        val senderName = msg.fromUser?.name
            ?: applicationContext.getString(R.string.app_name)
        views.setTextViewText(
            R.id.widgetMessageTitle,
            applicationContext.getString(R.string.widget_message_from, senderName)
        )
        views.setTextViewText(
            R.id.widgetMessageHint,
            applicationContext.getString(R.string.widget_tap_to_answer)
        )

        bindMessageCards(views, msg)

        val intent = Intent(applicationContext, IncomingMessageActivity::class.java).apply {
            putExtra(IncomingMessageActivity.EXTRA_MESSAGE_ID, msg.id)
            putExtra(IncomingMessageActivity.EXTRA_MODE, IncomingMessageActivity.MODE_MESSAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        views.setOnClickPendingIntent(R.id.widgetRoot, activityPendingIntent(intent))
    }

    private data class CardDisplay(
        val label: String,
        val imageUrl: String?,
        val isWait: Boolean
    )

    private fun bindMessageCards(views: RemoteViews, msg: AacMessageListItemDto) {
        val messageSlots = listOf(
            Triple(R.id.widgetCard1, R.id.widgetCard1Image, R.id.widgetCard1Label),
            Triple(R.id.widgetCard2, R.id.widgetCard2Image, R.id.widgetCard2Label),
            Triple(R.id.widgetCard3, R.id.widgetCard3Image, R.id.widgetCard3Label),
            Triple(R.id.widgetCard4, R.id.widgetCard4Image, R.id.widgetCard4Label),
            Triple(R.id.widgetCard5, R.id.widgetCard5Image, R.id.widgetCard5Label)
        )
        val replySlots = listOf(
            Triple(R.id.widgetReply1, R.id.widgetReply1Image, R.id.widgetReply1Label),
            Triple(R.id.widgetReply2, R.id.widgetReply2Image, R.id.widgetReply2Label),
            Triple(R.id.widgetReply3, R.id.widgetReply3Image, R.id.widgetReply3Label),
            Triple(R.id.widgetReply4, R.id.widgetReply4Image, R.id.widgetReply4Label),
            Triple(R.id.widgetReply5, R.id.widgetReply5Image, R.id.widgetReply5Label)
        )

        // Same rule as IncomingMessageActivity.renderMessage: the question
        // cards are shown only when there are no suggested replies; otherwise
        // the replies (sequence steps / reply options) take the whole space.
        val hasSuggestedReplies = msg.suggestedReplies.isNotEmpty()

        if (hasSuggestedReplies) {
            views.setViewVisibility(R.id.widgetCardsRow, View.GONE)
            views.setViewVisibility(R.id.widgetRepliesRow, View.VISIBLE)
            bindCardRow(views, replySlots, msg.suggestedReplies.map {
                val isWait = it.type == "WAIT" || it.source == "WAIT"
                val label = when {
                    isWait && it.seconds != null -> "⏱ ${it.seconds}s"
                    else -> it.label.orEmpty()
                }
                CardDisplay(label, it.imageUrl, isWait)
            })
        } else {
            views.setViewVisibility(R.id.widgetRepliesRow, View.GONE)
            views.setViewVisibility(R.id.widgetCardsRow, View.VISIBLE)
            bindCardRow(views, messageSlots, msg.message.map {
                CardDisplay(it.label, it.imageUrl, it.source == "WAIT")
            })
        }
    }

    private fun bindCardRow(
        views: RemoteViews,
        slots: List<Triple<Int, Int, Int>>,
        items: List<CardDisplay>
    ) {
        slots.forEachIndexed { index, (containerId, imageId, labelId) ->
            val card = items.getOrNull(index)
            if (card == null) {
                views.setViewVisibility(containerId, View.GONE)
                return@forEachIndexed
            }

            views.setViewVisibility(containerId, View.VISIBLE)

            // The last visible slot hints at how many cards didn't fit.
            val hiddenCount = items.size - slots.size
            val label = if (index == slots.lastIndex && hiddenCount > 0) {
                "${card.label} +$hiddenCount"
            } else {
                card.label
            }
            views.setTextViewText(labelId, label)

            if (card.isWait) {
                views.setImageViewResource(imageId, R.drawable.ic_timer_large)
                return@forEachIndexed
            }

            val bitmap = ApiClient.loadBitmap(card.imageUrl, authHeader)?.let { scaleForWidget(it) }
            if (bitmap != null) {
                views.setImageViewBitmap(imageId, bitmap)
            } else {
                views.setImageViewResource(imageId, android.R.drawable.ic_menu_gallery)
            }
        }
    }

    // RemoteViews bitmaps cross IPC; oversized ones fail the whole update.
    private fun scaleForWidget(bitmap: Bitmap): Bitmap {
        val maxSize = 256
        if (bitmap.width <= maxSize && bitmap.height <= maxSize) return bitmap
        val factor = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        return bitmap.scale(
            (bitmap.width * factor).toInt().coerceAtLeast(1),
            (bitmap.height * factor).toInt().coerceAtLeast(1)
        )
    }

    private suspend fun bindSchedule(views: RemoteViews, auth: String) {
        views.setViewVisibility(R.id.widgetMessageContainer, View.GONE)
        views.setViewVisibility(R.id.widgetScheduleContainer, View.VISIBLE)

        views.setTextViewText(
            R.id.widgetScheduleTitle,
            applicationContext.getString(R.string.widget_today)
        )

        val allItems = try {
            ApiClient.api.getScheduleItems(auth).items
        } catch (e: Exception) {
            Log.w("WidgetUpdateWorker", "failed to load schedule", e)
            emptyList()
        }
        val todays = itemsForDay(allItems, Calendar.getInstance())
        val tomorrows = itemsForDay(
            allItems,
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        )

        if (todays.isEmpty()) {
            views.setViewVisibility(R.id.widgetScheduleNowContainer, View.GONE)
            views.setViewVisibility(R.id.widgetScheduleNext1Container, View.GONE)
            views.setViewVisibility(R.id.widgetScheduleNext2Container, View.GONE)
            views.setViewVisibility(R.id.widgetScheduleEmpty, View.VISIBLE)
            views.setTextViewText(
                R.id.widgetScheduleEmpty,
                applicationContext.getString(R.string.widget_no_schedule)
            )
        } else {
            val now = "%02d:%02d".format(
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                Calendar.getInstance().get(Calendar.MINUTE)
            )
            val currentIndex = todays.indexOfLast { it.time <= now }
            // If nothing has started yet, promote the first upcoming item to
            // the large slot so the child always sees what's next.
            val bigIndex = if (currentIndex >= 0) currentIndex else 0
            val big = todays[bigIndex]

            views.setViewVisibility(R.id.widgetScheduleEmpty, View.GONE)
            views.setViewVisibility(R.id.widgetScheduleNowContainer, View.VISIBLE)
            views.setTextViewText(
                R.id.widgetScheduleNow,
                applicationContext.getString(
                    if (currentIndex >= 0) R.string.widget_schedule_now_row
                    else R.string.widget_schedule_row,
                    big.time,
                    itemName(big)
                )
            )
            setScheduleImage(views, R.id.widgetScheduleNowImage, big)

            bindUpcomingRow(
                views, todays.getOrNull(bigIndex + 1),
                R.id.widgetScheduleNext1Container, R.id.widgetScheduleNext1Image, R.id.widgetScheduleNext1
            )
            bindUpcomingRow(
                views, todays.getOrNull(bigIndex + 2),
                R.id.widgetScheduleNext2Container, R.id.widgetScheduleNext2Image, R.id.widgetScheduleNext2
            )
        }

        bindTomorrowRow(views, tomorrows)

        val intent = Intent(applicationContext, ChildHomeActivity::class.java).apply {
            putExtra(ChildHomeActivity.EXTRA_EDITOR_MODE, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        views.setOnClickPendingIntent(R.id.widgetRoot, activityPendingIntent(intent))
    }

    private fun itemsForDay(items: List<ScheduleItemDto>, cal: Calendar): List<ScheduleItemDto> {
        // Calendar: Sun=1..Sat=7 → app convention: Mon=1..Sun=7
        val dayIso = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        val dayDate = "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )

        return items.filter {
            (it.mode == "WEEKDAY" && dayIso in it.weekdays) ||
                    (it.mode == "DATE" && it.date?.take(10) == dayDate)
        }.sortedBy { it.time }
    }

    private fun bindTomorrowRow(views: RemoteViews, tomorrows: List<ScheduleItemDto>) {
        if (tomorrows.isEmpty()) {
            views.setViewVisibility(R.id.widgetTomorrowTitle, View.GONE)
            views.setViewVisibility(R.id.widgetTomorrowRow, View.GONE)
            return
        }

        views.setViewVisibility(R.id.widgetTomorrowTitle, View.VISIBLE)
        views.setTextViewText(
            R.id.widgetTomorrowTitle,
            applicationContext.getString(R.string.widget_tomorrow)
        )
        views.setViewVisibility(R.id.widgetTomorrowRow, View.VISIBLE)

        val slots = listOf(
            Triple(R.id.widgetTomorrow1, R.id.widgetTomorrow1Image, R.id.widgetTomorrow1Label),
            Triple(R.id.widgetTomorrow2, R.id.widgetTomorrow2Image, R.id.widgetTomorrow2Label),
            Triple(R.id.widgetTomorrow3, R.id.widgetTomorrow3Image, R.id.widgetTomorrow3Label),
            Triple(R.id.widgetTomorrow4, R.id.widgetTomorrow4Image, R.id.widgetTomorrow4Label),
            Triple(R.id.widgetTomorrow5, R.id.widgetTomorrow5Image, R.id.widgetTomorrow5Label)
        )

        slots.forEachIndexed { index, (containerId, imageId, labelId) ->
            val item = tomorrows.getOrNull(index)
            if (item == null) {
                views.setViewVisibility(containerId, View.GONE)
                return@forEachIndexed
            }

            views.setViewVisibility(containerId, View.VISIBLE)

            val hiddenCount = tomorrows.size - slots.size
            val label = if (index == slots.lastIndex && hiddenCount > 0) {
                "${item.time} +$hiddenCount"
            } else {
                item.time
            }
            views.setTextViewText(labelId, label)
            setScheduleImage(views, imageId, item)
        }
    }

    private fun itemName(item: ScheduleItemDto): String =
        item.name?.takeIf { it.isNotEmpty() }
            ?: item.cards.firstOrNull()?.label
            ?: ""

    private fun bindUpcomingRow(
        views: RemoteViews,
        item: ScheduleItemDto?,
        containerId: Int,
        imageId: Int,
        textId: Int
    ) {
        if (item == null) {
            views.setViewVisibility(containerId, View.GONE)
            return
        }
        views.setViewVisibility(containerId, View.VISIBLE)
        views.setTextViewText(
            textId,
            applicationContext.getString(R.string.widget_schedule_row, item.time, itemName(item))
        )
        setScheduleImage(views, imageId, item)
    }

    private fun setScheduleImage(views: RemoteViews, imageId: Int, item: ScheduleItemDto) {
        val imageUrl = item.cards.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl
        val bitmap = ApiClient.loadBitmap(imageUrl, authHeader)?.let { scaleForWidget(it) }
        if (bitmap != null) {
            views.setImageViewBitmap(imageId, bitmap)
        } else {
            views.setImageViewResource(imageId, android.R.drawable.ic_menu_gallery)
        }
    }

    private fun activityPendingIntent(intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
