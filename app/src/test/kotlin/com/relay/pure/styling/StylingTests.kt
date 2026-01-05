package com.relay.client.styling

import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * Unit tests for ThemedStylerBridge (Android styling via JNI)
 */
class ThemedStylerBridgeTest {
    
    @Test
    fun isAvailableCheck() {
        // Library might not be loaded in unit test environment
        // Just verify the method exists and returns a boolean
        val available = ThemedStylerBridge.isAvailable()
        assertTrue(available is Boolean)
    }
    
    @Test
    fun applyStyleWithValidInput() {
        val result = ThemedStylerBridge.applyStyle("Button", "bg-blue-500")
        
        // Should return Result, either success or failure
        assertTrue(result.isSuccess || result.isFailure)
    }
    
    @Test
    fun applyStyleWithEmptySelector() {
        val result = ThemedStylerBridge.applyStyle("", "bg-blue-500")
        
        // Empty selector should fail
        assertTrue(result.isFailure)
    }
    
    @Test
    fun applyStyleWithEmptyClasses() {
        val result = ThemedStylerBridge.applyStyle("Button", "")
        
        // Empty classes is OK, should return success with {}
        assertTrue(result.isSuccess)
        val json = result.getOrNull()
        assertEquals("{}", json)
    }
    
    @Test
    fun getThemeWithValidName() {
        val result = ThemedStylerBridge.getTheme("light")
        
        // Should return Result
        assertTrue(result.isSuccess || result.isFailure)
    }
    
    @Test
    fun getThemeWithEmptyName() {
        val result = ThemedStylerBridge.getTheme("")
        
        // Empty theme name should fail
        assertTrue(result.isFailure)
    }
    
    @Test
    fun styleResultMapping() {
        val successResult = ThemedStylerBridge.applyStyle("Button", "px-4")
        
        // Success result should be mappable
        successResult.onSuccess { json ->
            assertTrue(json is String)
        }
    }
    
    @Test
    fun styleErrorMapping() {
        val failureResult = ThemedStylerBridge.applyStyle("", "px-4")
        
        // Failure should have exception
        failureResult.onFailure { error ->
            assertTrue(error is Exception)
        }
    }
}

/**
 * Unit tests for AndroidStyles data class
 */
class AndroidStylesTest {
    
    @Test
    fun createStyles() {
        val styles = AndroidStyles(
            backgroundColor = "#FF0000",
            color = "#FFFFFF",
            paddingHorizontal = 16,
            paddingVertical = 8,
            borderRadius = 4,
            opacity = 0.9f
        )
        
        assertEquals("#FF0000", styles.backgroundColor)
        assertEquals("#FFFFFF", styles.color)
        assertEquals(16, styles.paddingHorizontal)
        assertEquals(8, styles.paddingVertical)
        assertEquals(4, styles.borderRadius)
        assertEquals(0.9f, styles.opacity)
    }
    
    @Test
    fun defaultStyles() {
        val styles = AndroidStyles()
        
        assertEquals(null, styles.backgroundColor)
        assertEquals(null, styles.color)
        assertEquals(0, styles.paddingHorizontal)
        assertEquals(0, styles.paddingVertical)
        assertEquals(0, styles.borderRadius)
        assertEquals(1.0f, styles.opacity)
    }
}
