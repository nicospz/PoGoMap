package dev.nicospz.pogomap.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.nicospz.pogomap.data.MapRepository
import dev.nicospz.pogomap.data.TokenStore
import dev.nicospz.pogomap.data.UserPreferences

class MainViewModelFactory(
    private val tokenStore: TokenStore,
    private val repository: MapRepository,
    private val userPreferences: UserPreferences,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(tokenStore, repository, userPreferences) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}
