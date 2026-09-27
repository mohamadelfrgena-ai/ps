package com.addonstation.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.addonstation.app.AppContainer
import com.addonstation.app.LocalApp
import com.addonstation.app.R
import com.addonstation.app.appViewModel
import com.addonstation.app.core.ContentItem
import com.addonstation.app.ui.components.ContentCard
import com.addonstation.app.ui.components.EmptyBox
import com.addonstation.app.ui.components.ErrorBox
import com.addonstation.app.ui.components.LoadingBox
import com.addonstation.app.ui.components.TopBar
import com.addonstation.app.ui.navigation.AppViewModel
import com.addonstation.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExclusivesViewModel(private val app: AppContainer) : ViewModel() {
    data class Ui(val loading: Boolean = true, val items: List<ContentItem> = emptyList(), val error: String? = null)
    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                _ui.value = Ui(false, app.content.exclusives(100))
            } catch (e: Exception) {
                _ui.value = Ui(false, emptyList(), e.message)
            }
        }
    }
}

@Composable
fun ExclusivesScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val evm: ExclusivesViewModel = appViewModel { ExclusivesViewModel(app) }
    val ui by evm.ui.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.exclusives_title), titleIcon = R.drawable.ic_store,
            vm = vm, showBack = true, onBack = { nav.popBackStack() },
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        when {
            ui.loading -> LoadingBox(Modifier.weight(1f))
            ui.error != null -> ErrorBox(ui.error!!, { evm.refresh() }, Modifier.weight(1f))
            ui.items.isEmpty() -> EmptyBox(stringResource(R.string.store_no_results), Modifier.weight(1f))
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(ui.items) { item ->
                    ContentCard(item, { nav.navigate(Routes.item(item.id)) })
                }
            }
        }
    }
}
