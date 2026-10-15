package com.strata.tv.wizard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.strata.tv.data.settings.SettingsRepository
import com.strata.tv.data.settings.XtreamApi
import com.strata.tv.ui.setup.SetupViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SetupViewModelTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var xtreamApi: XtreamApi

    private lateinit var viewModel: SetupViewModel

    @Before
    fun setUp() {
        hiltRule.inject()
        viewModel = SetupViewModel(settings, xtreamApi)
    }

    @Test
    fun connect_withEmptyFields_setsErrorMessage() = runTest {
        viewModel.connect()
        val state = viewModel.state.first { it.errorMessage != null }
        assertThat(state.errorMessage).isNotEmpty()
        assertThat(state.finished).isFalse()
    }

    @Test
    fun connect_withMissingPassword_setsErrorMessage() = runTest {
        viewModel.updateField(SetupViewModel.Field.Host, "http://example.invalid:8080")
        viewModel.updateField(SetupViewModel.Field.Username, "user")
        viewModel.connect()
        val state = viewModel.state.first { it.errorMessage != null }
        assertThat(state.errorMessage).contains("Password")
        assertThat(state.finished).isFalse()
    }
}
