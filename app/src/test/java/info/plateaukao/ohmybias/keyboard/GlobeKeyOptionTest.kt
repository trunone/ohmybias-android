package info.plateaukao.ohmybias.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobeKeyOptionTest {

    private fun createRow3LeadingKeySpec(
        needsInputModeSwitchKey: Boolean,
        globeAsLanguageKey: Boolean,
        isEnglishMode: Boolean,
        isSameSoundMode: Boolean = false,
        isShifted: Boolean = false
    ): KeyboardView.KeySpec {
        return if (isEnglishMode) {
            KeyboardView.KeySpec(if (isShifted) "⬆" else "⇧", KeyAction.Shift, isSpecial = true)
        } else {
            if (globeAsLanguageKey && needsInputModeSwitchKey) {
                val label = if (isSameSoundMode) "組" else "同"
                KeyboardView.KeySpec(label, KeyAction.EnterHomophone, isSpecial = true)
            } else {
                KeyboardView.KeySpec("英", KeyAction.ToggleLanguage, isSpecial = true)
            }
        }
    }

    private fun createRow4SwitchKeySpec(
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
    fun defaultRow3AndRow4KeyWhenOptionDisabled() {
        val row3Spec = createRow3LeadingKeySpec(
            needsInputModeSwitchKey = true,
            globeAsLanguageKey = false,
            isEnglishMode = false
        )
        assertEquals("英", row3Spec.label)
        assertEquals(KeyAction.ToggleLanguage, row3Spec.action)

        val row4Spec = createRow4SwitchKeySpec(
            needsInputModeSwitchKey = true,
            globeAsLanguageKey = false,
            isEnglishMode = false
        )
        requireNotNull(row4Spec)
        assertEquals("🌐", row4Spec.label)
        assertEquals(KeyAction.Globe, row4Spec.action)
        assertTrue(row4Spec.isGlobe)
    }

    @Test
    fun row3IsHomophoneKeyAndRow4IsLanguageToggleKeyWhenOptionEnabledInChineseMode() {
        val row3Spec = createRow3LeadingKeySpec(
            needsInputModeSwitchKey = true,
            globeAsLanguageKey = true,
            isEnglishMode = false,
            isSameSoundMode = false
        )
        assertEquals("同", row3Spec.label)
        assertEquals(KeyAction.EnterHomophone, row3Spec.action)

        val row4Spec = createRow4SwitchKeySpec(
            needsInputModeSwitchKey = true,
            globeAsLanguageKey = true,
            isEnglishMode = false
        )
        requireNotNull(row4Spec)
        assertEquals("英", row4Spec.label)
        assertEquals(KeyAction.ToggleLanguage, row4Spec.action)
        assertFalse(row4Spec.isGlobe)
    }

    @Test
    fun row3DisplaysZuWhenHomophoneModeActive() {
        val row3Spec = createRow3LeadingKeySpec(
            needsInputModeSwitchKey = true,
            globeAsLanguageKey = true,
            isEnglishMode = false,
            isSameSoundMode = true
        )
        assertEquals("組", row3Spec.label)
        assertEquals(KeyAction.EnterHomophone, row3Spec.action)
    }

    @Test
    fun row3IsShiftAndRow4IsLanguageToggleKeyWhenOptionEnabledInEnglishMode() {
        val row3Spec = createRow3LeadingKeySpec(
            needsInputModeSwitchKey = true,
            globeAsLanguageKey = true,
            isEnglishMode = true
        )
        assertEquals("⇧", row3Spec.label)
        assertEquals(KeyAction.Shift, row3Spec.action)

        val row4Spec = createRow4SwitchKeySpec(
            needsInputModeSwitchKey = true,
            globeAsLanguageKey = true,
            isEnglishMode = true
        )
        requireNotNull(row4Spec)
        assertEquals("米", row4Spec.label)
        assertEquals(KeyAction.ToggleLanguage, row4Spec.action)
        assertFalse(row4Spec.isGlobe)
    }
}
