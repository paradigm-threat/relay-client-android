package com.relay.client.hookapp

import org.junit.Test
import org.junit.Before
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Unit tests for HookContext (JavaScript execution environment)
 */
class HookContextTest {
    
    private lateinit var context: HookContext
    
    @Before
    fun setUp() {
        context = HookContext(
            filename = "/hooks/test.jsx",
            imports = mapOf("helpers" to "export const h = () => {}")
        )
    }
    
    @Test
    fun createHookContext() {
        assertEquals("/hooks/test.jsx", context.filename)
        assertEquals(1, context.imports.size)
    }
    
    @Test
    fun executeHookWithValidCode() {
        val code = """
            const App = () => {
                return { type: 'div', props: { className: 'app' } }
            }
            export default App
        """.trimIndent()
        
        val result = context.executeHook(code)
        
        assertTrue(result.isSuccess)
        val element = result.getOrNull()
        assertNotNull(element)
        assertEquals("div", element.type)
    }
    
    @Test
    fun executeHookCallsRegisterCallback() {
        var callbackCalled = false
        var receivedElement: JsxElement? = null
        
        val context = HookContext(
            filename = "/hooks/test.jsx",
            registerElement = { element ->
                callbackCalled = true
                receivedElement = element
            }
        )
        
        context.executeHook("const App = () => <div/>")
        
        assertTrue(callbackCalled)
        assertNotNull(receivedElement)
    }
    
    @Test
    fun executeHookWithImports() {
        val context = HookContext(
            filename = "/hooks/test.jsx",
            imports = mapOf(
                "helpers" to "export const h = (x) => x",
                "react" to "export const createElement = () => {}"
            )
        )
        
        // Should handle multiple imports without error
        val result = context.executeHook("const App = () => <div/>")
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun executeHookWithoutImports() {
        val context = HookContext(
            filename = "/hooks/test.jsx",
            imports = emptyMap()
        )
        
        val result = context.executeHook("const App = () => <div/>")
        assertTrue(result.isSuccess)
    }
}

/**
 * Unit tests for JsxElement data class
 */
class JsxElementTest {
    
    @Test
    fun createSimpleElement() {
        val element = JsxElement(type = "div")
        
        assertEquals("div", element.type)
        assertTrue(element.props.isEmpty())
        assertTrue(element.children.isEmpty())
        assertEquals(null, element.text)
    }
    
    @Test
    fun createElementWithProps() {
        val element = JsxElement(
            type = "Button",
            props = mapOf(
                "className" to "primary",
                "disabled" to false,
                "onClick" to { }
            )
        )
        
        assertEquals("Button", element.type)
        assertEquals("primary", element.props["className"])
        assertEquals(false, element.props["disabled"])
    }
    
    @Test
    fun createElementWithChildren() {
        val child = JsxElement(type = "Text", text = "Hello")
        val parent = JsxElement(
            type = "View",
            children = listOf(child)
        )
        
        assertEquals("View", parent.type)
        assertEquals(1, parent.children.size)
        assertEquals("Text", parent.children[0].type)
    }
    
    @Test
    fun createTextElement() {
        val element = JsxElement(
            type = "Text",
            text = "Hello, World!"
        )
        
        assertEquals("Text", element.type)
        assertEquals("Hello, World!", element.text)
    }
    
    @Test
    fun nestedElements() {
        val innerChild = JsxElement(type = "Text", text = "Child")
        val child = JsxElement(type = "View", children = listOf(innerChild))
        val parent = JsxElement(type = "View", children = listOf(child))
        
        assertEquals(1, parent.children.size)
        assertEquals(1, parent.children[0].children.size)
        assertEquals("Child", parent.children[0].children[0].text)
    }
}
