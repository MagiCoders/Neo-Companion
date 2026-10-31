package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AppDocumentationPdfGenerator
import com.example.ui.character.CharacterSpec
import com.example.ui.character.GlbLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentationExportTest {

    @Test
    fun verifyGlbCharacterAssetsAndGenerateRedesignedScreenshots() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val expectedAnims = listOf("idle", "idle_look", "listen", "think", "talk_a", "talk_b", "wave", "happy")
        val expectedMorphs = listOf("jawOpen", "mouthO", "mouthE", "mouthSmile", "eyeBlinkLeft", "eyeBlinkRight")

        // Verify test.glb + all 3 launch characters (elena.glb, kaito.glb, marco.glb)
        val testModel = GlbLoader.verifyStartupTestAsset(context)
        assertNotNull("characters/test.glb must load cleanly", testModel)

        CharacterSpec.entries.forEach { spec ->
            val model = GlbLoader.loadGlbAsset(context, spec.assetFile)
            assertNotNull("${spec.assetFile} must load cleanly", model)
            requireNotNull(model)
            assertEquals("Animation clips for ${spec.id}", expectedAnims, model.animationNames)
            assertEquals("Morph targets for ${spec.id}", expectedMorphs, model.morphTargetNames)
            assertTrue("Triangle count for ${spec.id} > 5000", model.triangleCount > 5000)
        }

        val docsDir = File("/app/applet/docs")
        val rootDir = File("/app/applet")
        val artifactDir = File("/.aistudio/artifacts/brain/e16b4b67-fc57-47b9-9d90-a2192052d3ba")

        val bundle = AppDocumentationPdfGenerator.generateCompleteDocsAndScreenshots(docsDir, context)

        assertTrue("PDF should exist and be non-empty", bundle.pdfFile.exists() && bundle.pdfFile.length() > 10_000L)
        bundle.screenshotFiles.forEach { shot ->
            assertTrue("Screenshot ${shot.name} should exist", shot.exists() && shot.length() > 5_000L)
        }

        bundle.pdfFile.copyTo(File(rootDir, "MOCHILIFE_COMPLETE_DOCUMENTATION.pdf"), overwrite = true)

        if (artifactDir.exists() || artifactDir.mkdirs()) {
            bundle.pdfFile.copyTo(File(artifactDir, "MOCHILIFE_COMPLETE_DOCUMENTATION.pdf"), overwrite = true)
            bundle.screenshotFiles.forEach { shot ->
                shot.copyTo(File(artifactDir, shot.name), overwrite = true)
            }
        }
    }
}
