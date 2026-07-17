package br.com.saqz.androidapp

import android.app.Application
import io.branch.referral.Branch

class SaqzApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Branch.getAutoInstance(this)
    }
}
