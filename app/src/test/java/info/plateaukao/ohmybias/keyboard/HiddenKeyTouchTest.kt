package info.plateaukao.ohmybias.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.max

class HiddenKeyTouchTest {

    data class TestKey(
        val name: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        var isVisible: Boolean = true
    )

    private fun nearestKey(keys: List<TestKey>, x: Float, y: Float): TestKey? {
        var best: TestKey? = null
        var bestDist = Float.MAX_VALUE
        for (b1 in keys) {
            val dx = max(max(b1.left - x, 0f), x - b1.right)
            val dy = max(max(b1.top - y, 0f), y - b1.bottom)
            val d = dx * dx + dy * dy
            if (d < bestDist) {
                bestDist = d
                best = b1
            }
        }
        return best
    }

    private fun handleTouchActionDown(keys: List<TestKey>, x: Float, y: Float): TestKey? {
        val target = nearestKey(keys, x, y) ?: return null
        if (!target.isVisible) {
            return null // Do not trigger any target key
        }
        return target
    }

    @Test
    fun whenKeyIsHidden_touchingNearestToHiddenKeyDoesNotTriggerAdjacentKey() {
        // Layout simulating Row 3: [m (visible)] [l (hidden/impossible)] [Backspace (visible)]
        val keyM = TestKey("m", 100f, 100f, 150f, 150f, isVisible = true)
        val keyL = TestKey("l", 150f, 100f, 200f, 150f, isVisible = false)
        val keyBackspace = TestKey("backspace", 200f, 100f, 270f, 150f, isVisible = true)

        val keys = listOf(keyM, keyL, keyBackspace)

        // Touch at x=175, y=125 (directly on the hidden 'l' key area)
        val targetOnL = handleTouchActionDown(keys, 175f, 125f)
        assertNull("Touching hidden key area should not trigger any key (e.g. backspace)", targetOnL)

        // Touch near 'm'
        val targetOnM = handleTouchActionDown(keys, 110f, 125f)
        assertEquals("m", targetOnM?.name)

        // Touch near 'backspace'
        val targetOnBackspace = handleTouchActionDown(keys, 250f, 125f)
        assertEquals("backspace", targetOnBackspace?.name)
    }
}
