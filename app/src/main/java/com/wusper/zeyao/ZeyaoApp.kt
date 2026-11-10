package com.wusper.zeyao

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.wusper.zeyao.data.RecordRepository
import com.wusper.zeyao.data.SecretStore
import com.wusper.zeyao.data.SettingsRepository
import com.wusper.zeyao.intercept.InterceptController
import com.wusper.zeyao.mitm.LocalCa

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
        runCatching { LocalCa.ensure(this) }
        InterceptController.attach(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                InterceptController.appUiVisible = activity is MainActivity
            }
            override fun onActivityPaused(activity: Activity) {
                if (activity is MainActivity) InterceptController.appUiVisible = false
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    companion object {
        lateinit var instance: ZeyaoApp
            private set
    }
}
