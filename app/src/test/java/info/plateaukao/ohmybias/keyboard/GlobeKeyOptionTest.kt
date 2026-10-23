package info.plateaukao.ohmybias.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobeKeyOptionTest {

    private fun createSwitchKeySpec(
        needsInputModeSwitchKey: Boolean,
        globeAsLanguageKey: Boolean,
        isEnglishMode: Boolean
    ): KeyboardView.KeySpec? {
        if (!needsInputModeSwitchKey) return null
        return if (globeAsLanguageKey) {
            KeyboardView.KeySpec(if (isEnglishMode) "米" else "英", KeyAction.ToggleLanguage, widthMultiplier = 1.0f, isSpecial = true)
        } else {
            KeyboardView.KeySpec("🌐", KeyAction.Globe, widthMultiplier = 1.0f, isSpecial = true, isGlobe = true)
        }
    }

    @Test
    fun defaultGlobeKeyWhenOptionDisabled() {
        val spec = createSwitchKeySpec(
            needsInputModeSwitchKey = true,
            globeAsLanguageKey = false,
            isEnglishMode = false
        )
        requireNotNull(spec)
        assertEquals("🌐", spec.label)
        assertEquals(KeyAction.Globe, spec.action)
        assertTrue(spec.isGlobe)
    }

    @Test
    fun languageToggleKeyWhenOptionEnabledInChineseMode() {
        val spec = createSwitchKeySpec(
            needsInputModeSwitchKey = true,
            globeAsLanguageKey = true,
            isEnglishMode = false
        )
        requireNotNull(spec)
        assertEquals("英", spec.label)
        assertEquals(KeyAction.ToggleLanguage, spec.action)
        assertFalse(spec.isGlobe)
    }

    @Test
    fun languageToggleKeyWhenOptionEnabledInEnglishMode() {
        val spec = createSwitchKeySpec(
            needsInputModeSwitchKey = true,
            globeAsLanguageKey = true,
            isEnglishMode = true
        )
        requireNotNull(spec)
        assertEquals("米", spec.label)
        assertEquals(KeyAction.ToggleLanguage, spec.action)
        assertFalse(spec.isGlobe)
    }
}
