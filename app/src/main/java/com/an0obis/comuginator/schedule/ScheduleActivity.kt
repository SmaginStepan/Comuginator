package com.an0obis.comuginator.ui.schedule

import android.os.Bundle
import com.an0obis.comuginator.R
import com.an0obis.comuginator.ui.base.BaseActivity

class ScheduleActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (redirectedByRoleGuard) return

        setContentView(R.layout.activity_schedule)

        ensureInitialized()
    }

}