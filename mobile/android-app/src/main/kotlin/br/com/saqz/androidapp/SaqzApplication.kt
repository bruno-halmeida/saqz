package br.com.saqz.androidapp

import android.app.Application
import br.com.saqz.composeapp.di.startSaqzKoin
import io.branch.referral.Branch
import org.koin.core.context.loadKoinModules
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class SaqzApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Branch.getAutoInstance(this)
        startSaqzKoin()
        loadKoinModules(androidAppModule(this))
    }
}

private fun androidAppModule(application: Application) = module {
    viewModel {
        MainActivityModel(
            context = application,
            factory = MainActivityComposition.factory(),
        )
    }
}
