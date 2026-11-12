package com.personal.passvault

import android.app.Application
import com.personal.passvault.core.session.VaultSession
import com.personal.passvault.work.ReminderWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PassVaultApp : Application() {

    @Inject
    lateinit var session: VaultSession

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
        session.attach()
        ReminderWorker.schedule(this)
    }
}
