package com.activaplan.app.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.activaplan.app.designsystem.ActivaDimens
import com.activaplan.app.designsystem.ActivaPlanTheme
import com.activaplan.app.designsystem.ActivaShapes
import com.activaplan.app.designsystem.components.ActivaDialog
import com.activaplan.app.designsystem.components.ActivaIcon
import com.activaplan.app.designsystem.components.ActivaIconKind
import com.activaplan.app.designsystem.components.ActivaLogoTile
import com.activaplan.app.designsystem.components.ActivaSegmentedControl
import com.activaplan.app.designsystem.components.ActivaSnackbarHost
import com.activaplan.app.designsystem.components.ActivaStepper
import com.activaplan.app.designsystem.components.AlertType
import com.activaplan.app.designsystem.components.showActiva
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.i18n.StringKey
import com.activaplan.app.domain.info.AppInfo
import com.activaplan.app.domain.model.AppLanguage
import com.activaplan.app.domain.model.Settings
import com.activaplan.app.domain.model.ThemeMode
import com.activaplan.app.domain.cardio.HeartRateZones
import com.activaplan.app.domain.reminder.NotificationPermission
import com.activaplan.app.domain.reminder.PermissionState
import com.activaplan.app.domain.reminder.ReminderSynchronizer
import com.activaplan.app.domain.tracking.LocationTracker
import com.activaplan.app.domain.time.formatClock
import com.activaplan.app.domain.usecase.ClearAllDataUseCase
import com.activaplan.app.domain.usecase.ClearSessionHistoryUseCase
import com.activaplan.app.domain.usecase.ObserveSettingsUseCase
import com.activaplan.app.domain.usecase.SetAgeUseCase
import com.activaplan.app.domain.usecase.SetDefaultTimeUseCase
import com.activaplan.app.domain.usecase.SetGpsTrackingUseCase
import com.activaplan.app.domain.usecase.SetKeepHistoryUseCase
import com.activaplan.app.domain.usecase.SetLanguageUseCase
import com.activaplan.app.domain.usecase.SetRemindersEnabledUseCase
import com.activaplan.app.domain.usecase.SetThemeUseCase
import com.activaplan.app.presentation.rememberLocationPermissionRequester
import com.activaplan.app.presentation.rememberNotificationPermissionRequester
import com.activaplan.app.presentation.schedule.TimePickerDialog
import com.activaplan.app.presentation.schedule.reminderSwitchColors
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

/** Aksi layar Setelan, dipisah dari tampilan agar pratinjau tidak perlu ViewModel. */
class SettingsActions(
    val onTheme: (ThemeMode) -> Unit,
    val onLanguage: (AppLanguage) -> Unit,
    val onRemindersEnabled: (Boolean) -> Unit,
    val onPickDefaultTime: () -> Unit,
    val onPermissionClick: () -> Unit,
    val onKeepHistory: (Boolean) -> Unit,
    val onClearHistory: () -> Unit,
    val onClearAll: () -> Unit,
    val onReplayIntro: () -> Unit,
    val onGpsTracking: (Boolean) -> Unit = {},
    val onAge: (Int) -> Unit = {},
)

/** Dialog konfirmasi hapus yang sedang terbuka. */
private enum class DeleteTarget { HISTORY, ALL }

/**
 * Layar Setelan: tema, pengingat, data, dan tentang. [onDataCleared] dipanggil setelah semua data terhapus, agar navigasi kembali ke Beranda.
 * [onReplayIntro] membuka Perkenalan 1-3 lagi.
 */
@Composable
fun SettingsScreen(onDataCleared: () -> Unit, onReplayIntro: () -> Unit, modifier: Modifier = Modifier) {
    val observe = koinInject<ObserveSettingsUseCase>()
    val setTheme = koinInject<SetThemeUseCase>()
    val setLanguage = koinInject<SetLanguageUseCase>()
    val setReminders = koinInject<SetRemindersEnabledUseCase>()
    val setDefaultTime = koinInject<SetDefaultTimeUseCase>()
    val setKeepHistory = koinInject<SetKeepHistoryUseCase>()
    val clearHistory = koinInject<ClearSessionHistoryUseCase>()
    val clearAll = koinInject<ClearAllDataUseCase>()
    val permission = koinInject<NotificationPermission>()
    val synchronizer = koinInject<ReminderSynchronizer>()
    val tracker = koinInject<LocationTracker>()
    val setGps = koinInject<SetGpsTrackingUseCase>()
    val setAge = koinInject<SetAgeUseCase>()
    val appInfo = koinInject<AppInfo>()
    val strings = ActivaPlanTheme.strings
    val viewModel = viewModel(key = "settings") {
        SettingsViewModel(observe, setTheme, setLanguage, setReminders, setDefaultTime, setKeepHistory, clearHistory, clearAll, permission, synchronizer, tracker, setGps, setAge)
    }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissionState by viewModel.permissionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pickingTime by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }
    val currentOnDataCleared = rememberUpdatedState(onDataCleared)

    val requestPermission = rememberNotificationPermissionRequester(viewModel::onPermissionResult)
    val requestLocation = rememberLocationPermissionRequester(viewModel::onLocationPermissionResult)
    // Pengguna bisa mengubah izin di pengaturan sistem lalu kembali ke aplikasi.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermission() }

    LaunchedEffect(viewModel, strings) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.HistoryDeleted ->
                    launch { snackbarHostState.showActiva(strings[StringKey.ALERT_HISTORY_DELETED]) }
                SettingsEvent.AllDataDeleted -> currentOnDataCleared.value()
                SettingsEvent.RequestPermission -> requestPermission()
                SettingsEvent.RequestLocationPermission -> requestLocation()
                SettingsEvent.LocationDenied -> launch {
                    val open = snackbarHostState.showActiva(
                        strings[StringKey.SETTINGS_GPS_DENIED], AlertType.WARNING, strings[StringKey.SETTINGS_NOTIFICATION_PERMISSION],
                    )
                    if (open) viewModel.onOpenLocationSettings()
                }
                is SettingsEvent.Failed -> launch {
                    val retry = snackbarHostState.showActiva(
                        strings[StringKey.ALERT_SAVE_FAILED], AlertType.ERROR, strings[StringKey.ACTION_RETRY],
                    )
                    if (retry) event.retry()
                }
            }
        }
    }

    val actions = SettingsActions(
        onTheme = viewModel::onTheme,
        onLanguage = viewModel::onLanguage,
        onRemindersEnabled = viewModel::onRemindersEnabled,
        onPickDefaultTime = { pickingTime = true },
        onPermissionClick = viewModel::onPermissionClick,
        onKeepHistory = viewModel::onKeepHistory,
        onClearHistory = { deleteTarget = DeleteTarget.HISTORY },
        onClearAll = { deleteTarget = DeleteTarget.ALL },
        onReplayIntro = onReplayIntro,
        onGpsTracking = viewModel::onGpsTracking,
        onAge = viewModel::onAge,
    )
    settings?.let { SettingsContent(it, permissionState, appInfo.versionName, actions, snackbarHostState, modifier) }

    if (pickingTime) {
        TimePickerDialog(
            initialMinutes = settings?.defaultTimeMinutes ?: Settings.DEFAULT.defaultTimeMinutes,
            onConfirm = { viewModel.onDefaultTime(it); pickingTime = false },
            onDismiss = { pickingTime = false },
        )
    }

    when (deleteTarget) {
        DeleteTarget.HISTORY -> ActivaDialog(
            title = strings[StringKey.ALERT_CLEAR_HISTORY_TITLE],
            message = strings[StringKey.ALERT_CLEAR_HISTORY_BODY],
            confirmLabel = strings[StringKey.ACTION_DELETE],
            onConfirm = { deleteTarget = null; viewModel.onClearHistory() },
            dismissLabel = strings[StringKey.ACTION_CANCEL],
            onDismiss = { deleteTarget = null },
            confirmIcon = ActivaIconKind.TRASH,
        )
        DeleteTarget.ALL -> ActivaDialog(
            title = strings[StringKey.ALERT_CLEAR_ALL_TITLE],
            message = strings[StringKey.ALERT_CLEAR_ALL_BODY],
            confirmLabel = strings[StringKey.ACTION_DELETE],
            onConfirm = { deleteTarget = null; viewModel.onClearAll() },
            dismissLabel = strings[StringKey.ACTION_CANCEL],
            onDismiss = { deleteTarget = null },
            confirmIcon = ActivaIconKind.TRASH,
        )
        null -> Unit
    }
}

@Composable
fun SettingsContent(
    settings: Settings,
    permission: PermissionState,
    versionName: String,
    actions: SettingsActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Box(modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = ActivaDimens.screenPadding).padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(strings[StringKey.NAV_SETTINGS], style = ActivaPlanTheme.type.screenTitle, color = c.text)

            SettingsGroup(strings[StringKey.SETTINGS_GROUP_APPEARANCE]) {
                ThemeRow(settings.theme, actions.onTheme)
            }

            SettingsGroup(strings[StringKey.SETTINGS_GROUP_LANGUAGE]) {
                LanguageRows(settings.language, actions.onLanguage)
            }

            SettingsGroup(strings[StringKey.SETTINGS_GROUP_REMINDERS]) {
                ToggleRow(strings[StringKey.SETTINGS_REMINDERS_ENABLED], settings.remindersEnabled, actions.onRemindersEnabled)
                RowDivider()
                NavigationRow(strings[StringKey.SETTINGS_DEFAULT_TIME], actions.onPickDefaultTime) {
                    ValueText(formatClock(settings.defaultTimeMinutes))
                }
                RowDivider()
                NavigationRow(strings[StringKey.SETTINGS_NOTIFICATION_PERMISSION], actions.onPermissionClick) {
                    PermissionBadge(permission)
                }
            }

            SettingsGroup(strings[StringKey.SETTINGS_GROUP_CARDIO]) {
                ToggleRow(strings[StringKey.SETTINGS_GPS], settings.gpsTracking, actions.onGpsTracking)
                RowDivider()
                InfoBlock(strings[StringKey.SETTINGS_GPS], strings[StringKey.SETTINGS_GPS_NOTE])
                RowDivider()
                AgeRow(settings.age, actions.onAge)
            }

            SettingsGroup(strings[StringKey.SETTINGS_GROUP_DATA]) {
                ToggleRow(strings[StringKey.SETTINGS_KEEP_HISTORY], settings.keepHistory, actions.onKeepHistory)
                RowDivider()
                DangerRow(strings[StringKey.SETTINGS_CLEAR_HISTORY], actions.onClearHistory)
                RowDivider()
                DangerRow(strings[StringKey.SETTINGS_CLEAR_ALL], actions.onClearAll)
            }

            SettingsGroup(strings[StringKey.SETTINGS_GROUP_ABOUT]) {
                AboutRow(versionName)
                RowDivider()
                NavigationRow(strings[StringKey.SETTINGS_REPLAY_INTRO], actions.onReplayIntro) {}
                RowDivider()
                InfoBlock(strings[StringKey.SETTINGS_ATTRIBUTION_TITLE], strings[StringKey.SETTINGS_ATTRIBUTION_BODY])
                RowDivider()
                InfoBlock(strings[StringKey.SETTINGS_LICENSES_TITLE], strings[StringKey.SETTINGS_LICENSES_BODY])
            }
        }
        ActivaSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp))
    }
}

// region Komponen baris

private val RowMinHeight = 52.dp

/** Judul grup di atas kartu radius 20; baris di dalamnya dipisah [RowDivider]. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = ActivaPlanTheme.colors
    Column {
        Text(
            title,
            style = ActivaPlanTheme.type.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
            color = c.textMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            color = c.surface,
            contentColor = c.text,
            shape = ActivaShapes.card,
            border = BorderStroke(1.dp, c.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(ActivaPlanTheme.colors.border))
}

@Composable
private fun ThemeRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(strings[StringKey.SETTINGS_THEME], style = ActivaPlanTheme.type.cardTitle, color = c.text)
        ActivaSegmentedControl(
            options = modes.map { strings.label(it) },
            selectedIndex = modes.indexOf(selected),
            onSelect = { onSelect(modes[it]) },
        )
    }
}

/** Pilihan bahasa sebagai daftar radio; nama bahasa memakai nama aslinya, jadi tetap terbaca bila UI sedang berbahasa lain. */
@Composable
private fun LanguageRows(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val options = listOf(AppLanguage.SYSTEM, AppLanguage.ID, AppLanguage.EN)
    Column(Modifier.selectableGroup()) {
        options.forEachIndexed { index, language ->
            if (index > 0) RowDivider()
            val isSelected = language == selected
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = RowMinHeight)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(language) })
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    strings.label(language),
                    style = ActivaPlanTheme.type.cardTitle,
                    color = c.text,
                    modifier = Modifier.weight(1f),
                )
                // Tanda centang, bukan hanya warna, menandai pilihan aktif.
                if (isSelected) ActivaIcon(ActivaIconKind.CHECK, c.primary, size = 20.dp)
            }
        }
    }
}

/** Usia untuk perkiraan zona detak jantung; belum diisi menampilkan 30 sebagai titik awal stepper. */
@Composable
private fun AgeRow(age: Int?, onAge: (Int) -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val shown = age ?: HeartRateZones.DEFAULT_AGE
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ActivaStepper(
            label = strings[StringKey.SETTINGS_AGE],
            value = shown,
            onDecrease = { onAge(shown - 1) },
            onIncrease = { onAge(shown + 1) },
            canDecrease = shown > HeartRateZones.AGE.first,
            canIncrease = shown < HeartRateZones.AGE.last,
        )
        Text(
            if (age != null) strings.format(StringKey.SETTINGS_AGE_MAX_HR, HeartRateZones.maxHeartRate(age)) else strings[StringKey.SETTINGS_AGE_NOTE],
            style = ActivaPlanTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = c.textMuted,
        )
    }
}

/** Seluruh baris bisa disentuh; [Switch] hanya penanda sehingga pembaca layar membaca satu kontrol. */
@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = RowMinHeight)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ActivaPlanTheme.type.cardTitle, color = ActivaPlanTheme.colors.text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, colors = reminderSwitchColors())
    }
}

/** Baris yang membuka sesuatu: label di kiri, [trailing] lalu chevron di kanan. */
@Composable
private fun NavigationRow(label: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    val c = ActivaPlanTheme.colors
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = RowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ActivaPlanTheme.type.cardTitle, color = c.text, modifier = Modifier.weight(1f))
        trailing()
        Spacer(Modifier.width(8.dp))
        ActivaIcon(ActivaIconKind.CHEVRON_LEFT, c.textMuted, Modifier.rotate(180f), size = 20.dp)
    }
}

@Composable
private fun ValueText(text: String) {
    Text(text, style = ActivaPlanTheme.type.body, color = ActivaPlanTheme.colors.textMuted)
}

/** Status izin: merah bila ditolak, hijau bila diizinkan, netral bila belum pernah ditanyakan. */
@Composable
private fun PermissionBadge(state: PermissionState) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val (label, container, content) = when (state) {
        PermissionState.GRANTED -> Triple(strings[StringKey.SETTINGS_PERMISSION_GRANTED], c.successSoft, c.success)
        PermissionState.DENIED -> Triple(strings[StringKey.SETTINGS_PERMISSION_DENIED], c.errorSoft, c.error)
        PermissionState.UNDETERMINED -> Triple(strings[StringKey.SETTINGS_PERMISSION_UNDETERMINED], c.surfaceVar, c.textMuted)
    }
    Text(
        label,
        style = ActivaPlanTheme.type.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = content,
        modifier = Modifier.background(container, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun DangerRow(label: String, onClick: () -> Unit) {
    val c = ActivaPlanTheme.colors
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = RowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActivaIcon(ActivaIconKind.TRASH, c.error, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, style = ActivaPlanTheme.type.cardTitle, color = c.error)
    }
}

@Composable
private fun AboutRow(versionName: String) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = RowMinHeight).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActivaLogoTile(size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Text(strings[StringKey.SETTINGS_APP_NAME], style = ActivaPlanTheme.type.cardTitle, color = c.text, modifier = Modifier.weight(1f))
        if (versionName.isNotBlank()) ValueText(strings.format(StringKey.SETTINGS_VERSION, versionName))
    }
}

@Composable
private fun InfoBlock(title: String, body: String) {
    val c = ActivaPlanTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = ActivaPlanTheme.type.cardTitle, color = c.text)
        Text(body, style = ActivaPlanTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = c.textMuted)
    }
}

// endregion

// region Pratinjau

private val previewActions = SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, {})

@Composable
private fun SettingsPreview(darkTheme: Boolean, settings: Settings, permission: PermissionState) = ActivaPlanTheme(darkTheme = darkTheme) {
    SettingsContent(settings, permission, "0.1.0", previewActions, remember { SnackbarHostState() })
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SettingsDeniedLightPreview() = SettingsPreview(false, Settings.DEFAULT, PermissionState.DENIED)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SettingsDeniedDarkPreview() = SettingsPreview(true, Settings.DEFAULT.copy(theme = ThemeMode.DARK), PermissionState.DENIED)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SettingsEnglishLightPreview() = ActivaPlanTheme(darkTheme = false, language = Language.EN) {
    SettingsContent(Settings.DEFAULT.copy(language = AppLanguage.EN), PermissionState.GRANTED, "0.1.0", previewActions, remember { SnackbarHostState() })
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SettingsGrantedLightPreview() =
    SettingsPreview(false, Settings.DEFAULT.copy(remindersEnabled = false, keepHistory = false), PermissionState.GRANTED)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SettingsGrantedDarkPreview() = SettingsPreview(true, Settings.DEFAULT.copy(theme = ThemeMode.DARK), PermissionState.GRANTED)

// endregion
