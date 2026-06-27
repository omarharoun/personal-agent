package com.personalagent.android.embedding

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * On-device test for [AndroidEmbedder]. Runs only on a real device/emulator
 * (it executes the native ONNX Runtime). It **self-skips** when the model asset
 * has not been provisioned, so it never fails a clone that lacks the ~90 MB
 * weights — provision with `./gradlew :androidApp:downloadEmbeddingModel`.
 */
@RunWith(AndroidJUnit4::class)
class AndroidEmbedderTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun embeds_normalized_384d_vectors_with_meaningful_similarity() = runTest {
        assumeTrue(
            "Embedding model asset not installed — skipping (run :androidApp:downloadEmbeddingModel)",
            EmbedderFactory.isModelInstalled(context),
        )

        val embedder = AndroidEmbedder(context)

        val a = embedder.embed("The cat sat on the mat.")
        val b = embedder.embed("A kitten rested on the rug.")
        val c = embedder.embed("Quarterly tax filing deadlines for small businesses.")

        // Correct dimensionality.
        assertEquals(384L, embedder.dimension.toLong())
        assertEquals(384L, a.size.toLong())

        // Output is L2-normalized (unit length).
        assertTrue("vector should be unit length, was ${norm(a)}", abs(norm(a) - 1f) < 1e-3f)

        // Related sentences should be more similar than an unrelated one.
        val simRelated = dot(a, b)
        val simUnrelated = dot(a, c)
        assertTrue(
            "related ($simRelated) should exceed unrelated ($simUnrelated)",
            simRelated > simUnrelated,
        )

        embedder.close()
    }

    private fun norm(v: FloatArray): Float {
        var s = 0f; for (x in v) s += x * x; return sqrt(s)
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f; for (i in a.indices) s += a[i] * b[i]; return s
    }
}
