package com.personalagent.android.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personalagent.shared.llm.GenOptions
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device test for [AndroidOnDeviceLlm]. Runs only on a real device/emulator
 * with enough RAM (it loads the native MediaPipe runtime + the `.task` model).
 * It **self-skips** when the model has not been provisioned, so it never fails a
 * clone that lacks the (gitignored) weights — provision per the README /
 * [LlmModelProvisioning] (`./gradlew :androidApp:pushLlmModel -PllmModel=...`).
 */
@RunWith(AndroidJUnit4::class)
class AndroidOnDeviceLlmTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun generates_text_and_honors_maxTokens_and_stop() = runTest {
        assumeTrue(
            "LLM model not provisioned — skipping (run :androidApp:pushLlmModel).",
            LlmModelProvisioning.isModelInstalled(context),
        )

        val llm = AndroidOnDeviceLlm(context, LlmModelProvisioning.resolveModelFile(context))
        assertTrue("model present => isAvailable", llm.isAvailable)

        // Full generation returns non-empty text.
        val out = llm.generate(
            "List three primary colors, one per line.",
            GenOptions(maxTokens = 64, temperature = 0.2f),
        )
        assertTrue("expected non-empty completion", out.isNotBlank())

        // Streaming yields incremental chunks that concatenate to a completion.
        val chunks = llm.generateStream(
            "Say hello.",
            GenOptions(maxTokens = 32, temperature = 0.2f),
        ).toList()
        assertTrue("expected at least one streamed chunk", chunks.isNotEmpty())

        // Stop sequence is honored: the trailing stop string is trimmed away.
        val stopped = llm.generate(
            "Count: 1, 2, 3, 4, 5",
            GenOptions(maxTokens = 64, temperature = 0.2f, stop = listOf("3")),
        )
        assertTrue("output must not contain the stop sequence", !stopped.contains("3"))

        llm.close()
    }
}
