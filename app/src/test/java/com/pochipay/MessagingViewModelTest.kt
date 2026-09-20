package com.pochipay

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.pochipay.data.Repository
import com.pochipay.viewmodels.MessagingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class MessagingViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var repository: Repository

    @Mock
    private lateinit var application: Application

    private lateinit var viewModel: MessagingViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        viewModel = MessagingViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun createConversation_callsRepository() = runTest {
        // Given
        val topic = "Test Topic"
        val message = "Test Message"
        val isFromUser = true

        // When
        viewModel.createConversation(topic, message, isFromUser)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(repository).createConversation(topic, message, isFromUser)
    }
}
