package com.pochipay

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MyAccessibilityServiceTest {

    private lateinit var service: MyAccessibilityService

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        service = MyAccessibilityService()
    }

    @Test
    fun `findNameNode returns null if node is null`() {
        val result = service.findNameNode(null)
        assertEquals(null, result)
    }

    @Test
    fun `findNameNode returns text if it matches name pattern`() {
        val mockNode = mockk<AccessibilityNodeInfo>()
        every { mockNode.text } returns "John Doe"
        every { mockNode.contentDescription } returns null
        every { mockNode.childCount } returns 0

        val result = service.findNameNode(mockNode)
        assertEquals("John Doe", result)
    }

    @Test
    fun `findNameNode returns contentDescription if it matches name pattern`() {
        val mockNode = mockk<AccessibilityNodeInfo>()
        every { mockNode.text } returns "some random text"
        every { mockNode.contentDescription } returns "Jane Smith"
        every { mockNode.childCount } returns 0

        val result = service.findNameNode(mockNode)
        assertEquals("Jane Smith", result)
    }

    @Test
    fun `findNameNode returns null if no name pattern found`() {
        val mockNode = mockk<AccessibilityNodeInfo>()
        every { mockNode.text } returns "singleword"
        every { mockNode.contentDescription } returns "another singleword"
        every { mockNode.childCount } returns 0

        val result = service.findNameNode(mockNode)
        assertEquals(null, result)
    }

    @Test
    fun `findNameNode returns name from a single valid node`() {
        val mockNode = mockk<AccessibilityNodeInfo>()
        every { mockNode.text } returns "Valid Name"
        every { mockNode.contentDescription } returns null
        every { mockNode.childCount } returns 0

        val result = service.findNameNode(mockNode)
        assertEquals("Valid Name", result)
    }

    @Test
    fun `findNameNode returns name from child node`() {
        val childNode = mockk<AccessibilityNodeInfo>()
        every { childNode.text } returns "Child Name"
        every { childNode.contentDescription } returns null
        every { childNode.childCount } returns 0

        // First, assert that the child node itself returns the name
        val childResult = service.findNameNode(childNode)
        assertEquals("Child Name", childResult) // This assertion passes

        clearMocks(childNode) // Clear mocks on childNode
        every { childNode.text } returns "Child Name"
        every { childNode.contentDescription } returns null
        every { childNode.childCount } returns 0

        val parentNode = mockk<AccessibilityNodeInfo>()
        every { parentNode.text } returns null // Simplified
        every { parentNode.contentDescription } returns null // Simplified
        every { parentNode.childCount } returns 1
        every { parentNode.getChild(any()) } answers { if (call.invocation.args[0] == 0) childNode else null }

        val result = service.findNameNode(parentNode)
        assertEquals("Child Name", result) // This assertion fails
    }

    @Test
    fun `findNameNode handles mixed case names`() {
        val mockNode = mockk<AccessibilityNodeInfo>()
        every { mockNode.text } returns "john DOE"
        every { mockNode.contentDescription } returns null
        every { mockNode.childCount } returns 0

        val result = service.findNameNode(mockNode)
        assertEquals(null, result)
    }

    @Test
    fun `findNameNode handles names with more than two words`() {
        val mockNode = mockk<AccessibilityNodeInfo>()
        every { mockNode.text } returns "Maria Guadalupe Rodriguez"
        every { mockNode.contentDescription } returns null
        every { mockNode.childCount } returns 0

        val result = service.findNameNode(mockNode)
        assertEquals("Maria Guadalupe Rodriguez", result)
    }

    @Test
    fun `findNameNode ignores non-capitalized multi-word text`() {
        val mockNode = mockk<AccessibilityNodeInfo>()
        every { mockNode.text } returns "this is not a name"
        every { mockNode.contentDescription } returns null
        every { mockNode.childCount } returns 0

        val result = service.findNameNode(mockNode)
        assertEquals(null, result)
    }
}
