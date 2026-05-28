package dev.nicospz.pogomap

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dev.nicospz.pogomap.data.CampfireApi
import dev.nicospz.pogomap.data.MapRepository
import dev.nicospz.pogomap.data.TokenStore
import dev.nicospz.pogomap.data.UserPreferences
import dev.nicospz.pogomap.ui.MainScreen
import dev.nicospz.pogomap.ui.MainViewModel
import dev.nicospz.pogomap.ui.MainViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            tokenStore = TokenStore(applicationContext),
            repository = MapRepository(CampfireApi()),
            userPreferences = UserPreferences(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(0xF7, 0xFC, 0xF6)
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setContent {
            MainScreen(viewModel = viewModel)
        }
    }
}
