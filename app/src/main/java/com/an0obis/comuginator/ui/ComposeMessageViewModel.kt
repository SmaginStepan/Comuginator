package com.an0obis.comuginator.ui

import androidx.lifecycle.ViewModel
import com.an0obis.comuginator.api.AacCardDto

class ComposeMessageViewModel : ViewModel() {
    var initialized: Boolean = false

    var targetUserId: String = ""
    var targetUserName: String = ""

    var currentAddMode: AddMode = AddMode.MESSAGE

    val selectedCards: MutableList<AacCardDto> = mutableListOf()
    val replyCards: MutableList<AacCardDto> = mutableListOf()

    var lastSearchQuery: String = ""

    enum class AddMode {
        MESSAGE,
        REPLY
    }
}