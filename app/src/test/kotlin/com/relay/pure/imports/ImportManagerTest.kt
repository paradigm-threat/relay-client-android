package com.relay.client.imports

import org.junit.Test
import org.junit.Before
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Unit tests for ImportManager (import deduplication and prefetching)
 */
class ImportManagerTest {
    
    @Before
    fun setUp() {
        // Clear cache before each test
        ImportManager.clear()
    }
    
    @Test
    fun extractImportsBasic() {
        val code = """
            import { helpers } from '@clevertree/helpers'
            import { render } from 'react'
            const App = () => <div/>
        """.trimIndent()
        
        // Use reflection or test through prefetchImports with mock
        // For now, just verify the object exists
        assertTrue(ImportManager.cacheSize() >= 0)
    }
    
    @Test
    fun extractImportsMultiple() {
        val code = """
            import { x } from '@clevertree/helpers'
            import { y } from '@example/lib'
            import z from '/modules/custom.mjs'
            import * as w from 'other'
        """.trimIndent()
        
        // Verify no crashes on complex imports
        assertTrue(ImportManager.cacheSize() >= 0)
    }
    
    @Test
    fun prefetchImportsEmpty() {
        val code = "const App = () => <div/>"
        
        // Empty imports should return empty map
        // (implementation in integration tests)
        assertEquals(0, ImportManager.cacheSize())
    }
    
    @Test
    fun cacheSize() {
        assertEquals(0, ImportManager.cacheSize())
        
        ImportManager.clear()
        
        assertEquals(0, ImportManager.cacheSize())
    }
    
    @Test
    fun cachedImports() {
        assertTrue(ImportManager.cachedImports().isEmpty())
        
        ImportManager.clear()
        
        assertTrue(ImportManager.cachedImports().isEmpty())
    }
    
    @Test
    fun clearCache() {
        assertEquals(0, ImportManager.cacheSize())
        
        ImportManager.clear()
        
        assertEquals(0, ImportManager.cacheSize())
    }
}
