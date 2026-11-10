package com.wusper.zeyao

import android.app.Application
import com.wusper.zeyao.data.RecordRepository
import com.wusper.zeyao.data.SecretStore
import com.wusper.zeyao.data.SettingsRepository
import com.wusper.zeyao.intercept.InterceptController

class ZeyaoApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var secrets: SecretStore
        private set
    lateinit var records: RecordRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        secrets = SecretStore(this)
        records = RecordRepository(this)
        InterceptController.attach(this)
    }

    companion object {
        lateinit var instance: ZeyaoApp
            private set
    }
}
