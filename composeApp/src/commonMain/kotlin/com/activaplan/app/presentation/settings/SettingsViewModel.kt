package com.activaplan.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.activaplan.app.domain.model.AppLanguage
import com.activaplan.app.domain.model.Settings
import com.activaplan.app.domain.model.ThemeMode
import com.activaplan.app.domain.reminder.NotificationPermission
import com.activaplan.app.domain.reminder.PermissionState
import com.activaplan.app.domain.reminder.ReminderSynchronizer
import com.activaplan.app.domain.tracking.LocationTracker
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Hasil aksi di layar Setelan; [Failed.retry] mengulang aksi yang sama. */
sealed interface SettingsEvent {
    data object HistoryDeleted : SettingsEvent

    /** Semua data dihapus; layar pemanggil kembali ke Beranda dan menampilkan snackbar di sana. */
    data object AllDataDeleted : SettingsEvent

    /** Izin notifikasi belum pernah ditanyakan; layar menampilkan dialog sistem. */
    data object RequestPermission : SettingsEvent

    /** Izin lokasi belum pernah ditanyakan; layar menampilkan dialog sistem. */
    data object RequestLocationPermission : SettingsEvent

    /** Izin lokasi ditolak, jadi GPS tetap mati; layar menjelaskan dan menawarkan membuka pengaturan. */
    data object LocationDenied : SettingsEvent

    data class Failed(val retry: () -> Unit) : SettingsEvent
}

class SettingsViewModel(
    observeSettings: ObserveSettingsUseCase,
    private val setTheme: SetThemeUseCase,
    private val setLanguage: SetLanguageUseCase,
    private val setRemindersEnabled: SetRemindersEnabledUseCase,
    private val setDefaultTime: SetDefaultTimeUseCase,
    private val setKeepHistory: SetKeepHistoryUseCase,
    private val clearHistory: ClearSessionHistoryUseCase,
    private val clearAll: ClearAllDataUseCase,
    private val permission: NotificationPermission,
    private val synchronizer: ReminderSynchronizer,
    private val tracker: LocationTracker,
    private val setGpsTracking: SetGpsTrackingUseCase,
    private val setAge: SetAgeUseCase,
) : ViewModel() {

    /** Null sampai setelan pertama terbaca. */
    val settings: StateFlow<Settings?> = observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _permission = MutableStateFlow(PermissionState.UNDETERMINED)
    val permissionState: StateFlow<PermissionState> = _permission.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    init {
        refreshPermission()
    }

    /** Membaca ulang izin, mis. setelah pengguna kembali dari pengaturan sistem. */
    fun refreshPermission() {
        viewModelScope.launch { _permission.value = permission.status() }
    }

    /** Dipanggil setelah dialog izin dijawab: perbarui status dan jadwalkan ulang alarm sesuai izin baru. */
    fun onPermissionResult() {
        viewModelScope.launch {
            _permission.value = permission.status()
            try {
                synchronizer.sync()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Penyelarasan berikutnya (saat aplikasi dibuka atau data berubah) mencoba lagi.
            }
        }
    }

    /** Baris izin: bila belum pernah ditanyakan tampilkan dialog sistem, selain itu buka pengaturan sistem. */
    fun onPermissionClick() {
        viewModelScope.launch {
            if (permission.status() == PermissionState.UNDETERMINED) _events.send(SettingsEvent.RequestPermission) else permission.openSettings()
        }
    }

    /** Menyalakan GPS hanya bila izin lokasi ada; bila belum ditanyakan, minta dulu, bila ditolak jelaskan. */
    fun onGpsTracking(enabled: Boolean): Unit = perform({ onGpsTracking(enabled) }) {
        if (!enabled) {
            setGpsTracking(false)
            return@perform
        }
        when (tracker.permission()) {
            PermissionState.GRANTED -> setGpsTracking(true)
            PermissionState.UNDETERMINED -> _events.send(SettingsEvent.RequestLocationPermission)
            PermissionState.DENIED -> _events.send(SettingsEvent.LocationDenied)
        }
    }

    /** Dipanggil setelah dialog izin lokasi dijawab. */
    fun onLocationPermissionResult(): Unit = perform({ onLocationPermissionResult() }) {
        if (tracker.permission() == PermissionState.GRANTED) setGpsTracking(true) else _events.send(SettingsEvent.LocationDenied)
    }

    fun onOpenLocationSettings() = tracker.openSettings()

    fun onAge(age: Int?): Unit = perform({ onAge(age) }) { setAge(age) }

    fun onTheme(theme: ThemeMode): Unit = perform({ onTheme(theme) }) { setTheme(theme) }

    fun onLanguage(language: AppLanguage): Unit = perform({ onLanguage(language) }) { setLanguage(language) }

    fun onRemindersEnabled(enabled: Boolean): Unit = perform({ onRemindersEnabled(enabled) }) {
        setRemindersEnabled(enabled)
        if (enabled && permission.status() == PermissionState.UNDETERMINED) _events.send(SettingsEvent.RequestPermission)
    }

    fun onDefaultTime(timeMinutes: Int): Unit = perform({ onDefaultTime(timeMinutes) }) { setDefaultTime(timeMinutes) }

    fun onKeepHistory(keep: Boolean): Unit = perform({ onKeepHistory(keep) }) { setKeepHistory(keep) }

    fun onClearHistory(): Unit = perform({ onClearHistory() }) {
        clearHistory()
        _events.send(SettingsEvent.HistoryDeleted)
    }

    fun onClearAll(): Unit = perform({ onClearAll() }) {
        clearAll()
        _events.send(SettingsEvent.AllDataDeleted)
    }

    private fun perform(retry: () -> Unit, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _events.send(SettingsEvent.Failed(retry))
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
