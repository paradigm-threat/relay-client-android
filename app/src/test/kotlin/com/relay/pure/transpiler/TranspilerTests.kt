package com.relay.client.transpiler

import org.junit.Test
import org.junit.Before
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for TranspileError and error handling
 */
class TranspileErrorTest {
    
    @Test
    fun libraryNotLoadedError() {
        val error = TranspileError.LibraryNotLoaded("test library")
        assertEquals("Hook transpiler library not loaded: test library", error.message)
    }
    
    @Test
    fun jsxParseErrorWithContext() {
        val error = TranspileError.JsxParseError(
            message = "Unexpected token",
            lineNumber = 5,
            columnNumber = 10,
            lineContent = "const x = <div>>>"
        )
        
        assertEquals(5, error.lineNumber)
        assertEquals(10, error.columnNumber)
        assertTrue(error.getErrorContext().contains("Unexpected token"))
        assertTrue(error.getErrorContext().contains("^"))  // Error marker
    }
    
    @Test
    fun invalidInputError() {
        val error = TranspileError.InvalidInput("empty source")
        assertEquals("Invalid input: empty source", error.message)
    }
    
    @Test
    fun jniCallFailedError() {
        val cause = Exception("Native method not found")
        val error = TranspileError.JniCallFailed("transpile failed", cause)
        assertEquals(cause, error.originalError)
    }
    
    @Test
    fun importPrefetchFailedError() {
        val error = TranspileError.ImportPrefetchFailed(
            moduleName = "helpers",
            reason = "HTTP 404"
        )
        assertTrue(error.message!!.contains("helpers"))
        assertTrue(error.message!!.contains("404"))
    }
}

/**
 * Unit tests for TranspileOptions validation
 */
class TranspileOptionsTest {
    
    @Test
    fun validOptions() {
        val options = TranspileOptions(filename = "hook.jsx")
        assertTrue(options.validate().isSuccess)
    }
    
    @Test
    fun invalidFilenameBlank() {
        val options = TranspileOptions(filename = "")
        assertTrue(options.validate().isFailure)
    }
    
    @Test
    fun invalidFilenameExtension() {
        val options = TranspileOptions(filename = "hook.py")
        assertTrue(options.validate().isFailure)
    }
    
    @Test
    fun validTypescriptFile() {
        val options = TranspileOptions(filename = "hook.tsx", isTypescript = true)
        assertTrue(options.validate().isSuccess)
    }
}

/**
 * Unit tests for TranspileErrorMapper
 */
class TranspileErrorMapperTest {
    
    @Test
    fun mapLineColumnError() {
        val error = TranspileErrorMapper.mapJniError("Parse error at line 5 column 10")
        assertTrue(error is TranspileError.JsxParseError)
        val jsxError = error as TranspileError.JsxParseError
        assertEquals(5, jsxError.lineNumber)
        assertEquals(10, jsxError.columnNumber)
    }
    
    @Test
    fun mapLibraryNotFoundError() {
        val error = TranspileErrorMapper.mapJniError("library not loaded")
        assertTrue(error is TranspileError.LibraryNotLoaded)
    }
    
    @Test
    fun mapJniError() {
        val error = TranspileErrorMapper.mapJniError("JNI call failed")
        assertTrue(error is TranspileError.JniCallFailed)
    }
    
    @Test
    fun mapException() {
        val exception = UnsatisfiedLinkError("relay_hook_transpiler.so")
        val error = TranspileErrorMapper.mapException(exception)
        assertTrue(error is TranspileError.LibraryNotLoaded)
    }
    
    @Test
    fun mapGenericException() {
        val exception = Exception("Unknown error")
        val error = TranspileErrorMapper.mapException(exception)
        assertTrue(error is TranspileError.JniCallFailed)
    }
}

/**
 * Unit tests for JsxTranspilerCache
 */
class JsxTranspilerCacheTest {
    
    private lateinit var cache: JsxTranspilerCache
    
    @Before
    fun setUp() {
        cache = JsxTranspilerCache(maxSize = 5, ttlMillis = 5000)  // 5 second TTL for tests
    }
    
    @Test
    fun cacheHit() {
        val source = "const App = () => <div/>"
        val filename = "test.jsx"
        val code = "const App = () => createElement('div')"
        
        cache.put(source, filename, code)
        val result = cache.get(source, filename)
        
        assertEquals(code, result)
    }
    
    @Test
    fun cacheMiss() {
        val result = cache.get("unknown source", "test.jsx")
        assertEquals(null, result)
    }
    
    @Test
    fun cacheSize() {
        cache.put("source1", "file1.jsx", "code1")
        cache.put("source2", "file2.jsx", "code2")
        
        assertEquals(2, cache.size())
    }
    
    @Test
    fun cacheEvictionWhenFull() {
        val cache = JsxTranspilerCache(maxSize = 2, ttlMillis = 10000)
        
        cache.put("s1", "f1.jsx", "c1")
        cache.put("s2", "f2.jsx", "c2")
        cache.put("s3", "f3.jsx", "c3")  // Should evict oldest
        
        assertEquals(2, cache.size())
        // Oldest (s1) should be evicted
        assertEquals(null, cache.get("s1", "f1.jsx"))
    }
    
    @Test
    fun clearCache() {
        cache.put("s1", "f1.jsx", "c1")
        cache.put("s2", "f2.jsx", "c2")
        
        cache.clear()
        assertEquals(0, cache.size())
    }
    
    @Test
    fun differentFilenamesWithSameSource() {
        val source = "const App = () => <div/>"
        val code1 = "const App = () => createElement('div')"
        val code2 = "const App = () => h('div')"  // Different transpilation
        
        cache.put(source, "file1.jsx", code1)
        cache.put(source, "file2.jsx", code2)
        
        // Both should be retrievable
        assertEquals(code1, cache.get(source, "file1.jsx"))
        assertEquals(code2, cache.get(source, "file2.jsx"))
    }
}
