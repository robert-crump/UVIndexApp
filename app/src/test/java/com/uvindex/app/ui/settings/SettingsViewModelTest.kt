package com.uvindex.app.ui.settings

import com.uvindex.app.data.local.SkinTypeStore
import com.uvindex.app.notification.NotificationDecision
import com.uvindex.app.notification.NotificationHistory
import com.uvindex.app.notification.NotificationHistoryStore
import com.uvindex.app.permission.LocationPermissionState
import com.uvindex.app.uv.SkinType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private class FakeSkinTypeStore(initial: SkinType? = null) : SkinTypeStore {
        val current = MutableStateFlow(initial)
        override fun getSkinType() = current
        override suspend fun saveSkinType(skinType: SkinType) {
            current.value = skinType
        }
    }

    private class FakeHistoryStore : NotificationHistoryStore {
        var history = NotificationHistory(true, true, null, null, null, null, null, null)
        val dailyWrites = mutableListOf<Boolean>()
        val uvWarningWrites = mutableListOf<Boolean>()
        override suspend fun snapshot() = history
        override suspend fun record(decision: NotificationDecision, now: Instant) = Unit
        override suspend fun markUvWarningDisabledToday() = Unit
        override suspend fun setDailyEnabled(enabled: Boolean) {
            dailyWrites += enabled
            history = history.copy(dailyEnabled = enabled)
        }
        override suspend fun setUvWarningEnabled(enabled: Boolean) {
            uvWarningWrites += enabled
            history = history.copy(uvWarningEnabled = enabled)
        }
    }

    private class FakePermissions(
        var foreground: Boolean = false,
        var background: Boolean = false,
    ) : LocationPermissionState {
        override fun hasForegroundLocation() = foreground
        override fun hasBackgroundLocation() = background
    }

    private val skinStore = FakeSkinTypeStore()
    private val historyStore = FakeHistoryStore()
    private val permissions = FakePermissions()
    private var widgetRefreshes = 0

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(sdkInt: Int = 34) =
        SettingsViewModel(skinStore, historyStore, permissions, { widgetRefreshes++ }, sdkInt)

    @Test
    fun `initial state reflects stored skin type and channel flags`() {
        skinStore.current.value = SkinType.entries.first()
        historyStore.history = historyStore.history.copy(dailyEnabled = false, uvWarningEnabled = true)

        val state = viewModel().state.value

        assertEquals(SkinType.entries.first(), state.skinType)
        assertFalse(state.dailyEnabled)
        assertTrue(state.uvWarningEnabled)
    }

    @Test
    fun `setting skin type persists it and requests one widget refresh`() {
        val vm = viewModel()
        val type = SkinType.entries.last()

        vm.setSkinType(type)

        assertEquals(type, skinStore.current.value)
        assertEquals(type, vm.state.value.skinType)
        assertEquals(1, widgetRefreshes)
    }

    @Test
    fun `toggling daily off writes the flag once and needs no schedule work`() {
        val vm = viewModel()

        vm.setDailyEnabled(false)

        assertEquals(listOf(false), historyStore.dailyWrites)
        assertFalse(vm.state.value.dailyEnabled)
        assertEquals(0, widgetRefreshes)
    }

    @Test
    fun `toggling UV warning writes the flag`() {
        val vm = viewModel()

        vm.setUvWarningEnabled(false)
        vm.setUvWarningEnabled(true)

        assertEquals(listOf(false, true), historyStore.uvWarningWrites)
        assertTrue(vm.state.value.uvWarningEnabled)
        assertTrue(historyStore.dailyWrites.isEmpty())
    }

    @Test
    fun `refreshPermissions picks up a grant made in system settings`() {
        val vm = viewModel()
        assertFalse(vm.state.value.backgroundLocationGranted)

        permissions.background = true
        vm.refreshPermissions()

        assertTrue(vm.state.value.backgroundLocationGranted)
    }

    @Test
    fun `request background location asks for foreground first when missing`() = runTest {
        val vm = viewModel()

        vm.requestBackgroundLocation()

        assertEquals(PermissionAction.RequestForegroundLocation, vm.permissionActions.first())
    }

    @Test
    fun `request background location goes to system settings on API 30 and up`() = runTest {
        permissions.foreground = true
        val vm = viewModel(sdkInt = 34)

        vm.requestBackgroundLocation()

        assertEquals(PermissionAction.OpenAppSettings, vm.permissionActions.first())
    }

    @Test
    fun `request background location prompts inline on API 29`() = runTest {
        permissions.foreground = true
        val vm = viewModel(sdkInt = 29)

        vm.requestBackgroundLocation()

        assertEquals(PermissionAction.RequestBackgroundLocation, vm.permissionActions.first())
    }

    @Test
    fun `foreground grant continues to the background request`() = runTest {
        val vm = viewModel()

        vm.onForegroundLocationResult(granted = true)

        assertEquals(PermissionAction.RequestBackgroundLocation, vm.permissionActions.first())
    }

    @Test
    fun `background result updates the granted state`() {
        val vm = viewModel()

        vm.onBackgroundLocationResult(true)

        assertTrue(vm.state.value.backgroundLocationGranted)
    }
}
