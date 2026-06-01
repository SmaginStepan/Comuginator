package com.an0obis.comuginator.ui.messaging

import androidx.lifecycle.ViewModel
import com.an0obis.comuginator.api.AacCardDto

class ComposeMessageViewModel : ViewModel() {
    var initialized: Boolean = false

    var targetUserId: String = ""
    var targetUserName: String = ""

    var mode: String = "NORMAL"

    var requiredReplyCount: Int = 1

    val replyCards = mutableListOf<AacCardDto>()
}