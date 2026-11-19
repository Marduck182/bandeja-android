package com.bandeja.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bandeja.app.BandejaApp
import com.bandeja.app.di.AppContainer

val Context.appContainer: AppContainer
    get() = (applicationContext as BandejaApp).container

inline fun <reified VM : ViewModel> appViewModelFactory(
    container: AppContainer,
    crossinline create: (AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory { initializer { create(container) } }

/** Creates a ViewModel wired with the app's [AppContainer]. [key] separates instances that take arguments. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContext.current.appContainer
    return viewModel(key = key, factory = appViewModelFactory(container, create))
}
