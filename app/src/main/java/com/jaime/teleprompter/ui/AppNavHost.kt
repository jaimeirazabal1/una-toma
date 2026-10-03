package com.jaime.teleprompter.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jaime.teleprompter.AppContainer
import com.jaime.teleprompter.ui.export.ExportScreen
import com.jaime.teleprompter.ui.export.ExportViewModel
import com.jaime.teleprompter.ui.record.RecordScreen
import com.jaime.teleprompter.ui.record.RecordViewModel
import com.jaime.teleprompter.ui.script.ScriptScreen
import com.jaime.teleprompter.ui.script.ScriptViewModel

private object Routes {
    const val SCRIPT = "script"
    const val RECORD = "record"
    const val EXPORT = "export"
}

@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.SCRIPT) {
        composable(Routes.SCRIPT) {
            ScriptScreen(
                viewModel = viewModel(factory = ScriptViewModel.factory(container)),
                onRecord = { nav.navigate(Routes.RECORD) },
            )
        }
        composable(Routes.RECORD) {
            RecordScreen(
                viewModel = viewModel(factory = RecordViewModel.factory(container)),
                onClose = { nav.popBackStack() },
                onRecordingFinished = {
                    nav.navigate(Routes.EXPORT) { popUpTo(Routes.SCRIPT) }
                },
            )
        }
        composable(Routes.EXPORT) {
            ExportScreen(
                viewModel = viewModel(factory = ExportViewModel.factory(container)),
                onFinished = { nav.popBackStack(Routes.SCRIPT, inclusive = false) },
            )
        }
    }
}
