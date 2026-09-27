package com.addonstation.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.addonstation.app.AppContainer
import com.addonstation.app.LocalApp
import com.addonstation.app.R
import com.addonstation.app.appViewModel
import com.addonstation.app.core.Constants
import com.addonstation.app.core.ContentItem
import com.addonstation.app.core.SubscriptionPlan
import com.addonstation.app.ui.components.AsImage
import com.addonstation.app.ui.components.ContentCard
import com.addonstation.app.ui.components.EmptyBox
import com.addonstation.app.ui.components.ErrorBox
import com.addonstation.app.ui.components.LoadingBox
import com.addonstation.app.ui.components.SectionHeader
import com.addonstation.app.ui.components.TopBar
import com.addonstation.app.ui.navigation.AppViewModel
import com.addonstation.app.ui.navigation.OnResume
import com.addonstation.app.ui.navigation.Routes
import com.addonstation.app.ui.theme.LocalExtra
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val app: AppContainer) : ViewModel() {
    data class Ui(
        val loading: Boolean = true,
        val error: String? = null,
        val featured: List<ContentItem> = emptyList(),
        val latestAddons: List<ContentItem> = emptyList(),
        val latestWorlds: List<ContentItem> = emptyList(),
        val plans: List<SubscriptionPlan> = emptyList()
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val featured = app.content.featured(8)
                val addons = app.content.byType("addon", "created_at", 6)
                val worlds = app.content.byType("world", "created_at", 6)
                val plans = try {
                    app.payments.plans().filter { it.active }
                } catch (_: Exception) { emptyList() }
                _ui.value = Ui(false, null, featured, addons, worlds, plans)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message ?: "Error")
            }
        }
    }
}

@Composable
fun HomeScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val hvm: HomeViewModel = appViewModel { HomeViewModel(app) }
    val ui by hvm.ui.collectAsState()
    OnResume { hvm.refresh() }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.nav_home),
            titleIcon = R.drawable.ic_home,
            vm = vm,
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        when {
            ui.loading -> LoadingBox(Modifier.weight(1f))
            ui.error != null -> ErrorBox(ui.error!!, { hvm.refresh() }, Modifier.weight(1f))
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item { HeroCard(nav) }
                item {
                    SectionHeader(
                        kicker = stringResource(R.string.home_featured_kicker),
                        title = stringResource(R.string.home_featured_title),
                        action = {
                            Text(
                                stringResource(R.string.home_featured_viewall),
                                color = LocalExtra.current.diamond,
                                modifier = Modifier.clickable { nav.navigate(Routes.STORE) }
                            )
                        }
                    )
                }
                item {
                    if (ui.featured.isEmpty()) EmptyBox(stringResource(R.string.store_no_results))
                    else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(ui.featured) { item ->
                            ContentCard(item, { nav.navigate(Routes.item(item.id)) }, modifier = Modifier.width(220.dp))
                        }
                    }
                }
                item {
                    SectionHeader(
                        kicker = stringResource(R.string.latest_kicker),
                        title = stringResource(R.string.latest_addons_title)
                    )
                }
                item {
                    if (ui.latestAddons.isEmpty()) EmptyBox(stringResource(R.string.store_no_results))
                    else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(ui.latestAddons) { item ->
                            ContentCard(item, { nav.navigate(Routes.item(item.id)) }, modifier = Modifier.width(220.dp))
                        }
                    }
                }
                if (ui.latestWorlds.isNotEmpty()) {
                    item {
                        SectionHeader(
                            kicker = stringResource(R.string.home_worlds_kicker),
                            title = "${stringResource(R.string.home_worlds_title1)} ${stringResource(R.string.home_worlds_title2)}",
                            action = {
                                Text(
                                    stringResource(R.string.home_worlds_btn),
                                    color = LocalExtra.current.diamond,
                                    modifier = Modifier.clickable { nav.navigate(Routes.STORE) }
                                )
                            }
                        )
                    }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(ui.latestWorlds) { item ->
                                ContentCard(item, { nav.navigate(Routes.item(item.id)) }, modifier = Modifier.width(220.dp))
                            }
                        }
                    }
                }
                if (ui.plans.isNotEmpty()) {
                    item {
                        SectionHeader(
                            kicker = stringResource(R.string.home_plans_kicker),
                            title = stringResource(R.string.home_plans_title)
                        )
                    }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(ui.plans) { plan ->
                                PlanMiniCard(plan) { nav.navigate(Routes.checkout(plan.id)) }
                            }
                        }
                    }
                }
                item { SupportTeaser(nav) }
                item { AboutTeaser() }
            }
        }
    }
}

@Composable
private fun HeroCard(nav: NavHostController) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF12283D), Color(0xFF0E1A2B), Color(0xFF123B2C))))
            .padding(20.dp)
    ) {
        Column {
            Text(stringResource(R.string.hero_label), color = Color(0xFF6FB8FF), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.hero_titlewhite), style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(stringResource(R.string.hero_titlegreen), style = MaterialTheme.typography.displaySmall, color = Color(0xFF3DDC84))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { nav.navigate(Routes.STORE) }) { Text(stringResource(R.string.hero_btnaddons)) }
                OutlinedButton(onClick = { nav.navigate(Routes.STORE) }) { Text(stringResource(R.string.hero_btnworlds)) }
            }
        }
    }
}

@Composable
private fun PlanMiniCard(plan: SubscriptionPlan, onClick: () -> Unit) {
    val extra = LocalExtra.current
    Card(
        onClick = onClick,
        modifier = Modifier.width(170.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(plan.name ?: plan.id.uppercase(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("${plan.priceLyd} LYD", color = extra.gold, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.plans_month), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SupportTeaser(nav: NavHostController) {
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.home_support_kicker), style = MaterialTheme.typography.labelLarge, color = LocalExtra.current.diamond, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.home_support_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.home_support_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { nav.navigate(Routes.SUPPORT) }) { Text(stringResource(R.string.home_support_btn)) }
            OutlinedButton(onClick = { nav.navigate(Routes.CREATORS) }) { Text(stringResource(R.string.legacy_becomecreator)) }
        }
    }
}

@Composable
private fun AboutTeaser() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AsImage(model = Constants.APP_ICON, contentDescription = null, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "${stringResource(R.string.home_about_title1)} ${stringResource(R.string.home_about_title2)}",
                    fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.home_about_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.footer_copyright),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
