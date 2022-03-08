package de.markusressel.kodeeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import de.markusressel.kodeeditor.library.KodeEditor
import de.markusressel.kodeeditor.ui.theme.KodeEditorTheme
import de.markusressel.kodehighlighter.language.markdown.MarkdownRuleBook

class ComposeMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KodeEditorTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {

                    val sampleText = readResourceFileAsText(R.raw.sample_text)
                    KodeEditor(
                            languageRuleBook = MarkdownRuleBook(),
                            initialText = sampleText
                    )
                }
            }
        }
    }

    private fun readResourceFileAsText(@RawRes resourceId: Int): String {
        return resources.openRawResource(resourceId).bufferedReader().readText()
    }

}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    KodeEditorTheme {
        KodeEditor(
                languageRuleBook = MarkdownRuleBook(),
                initialText = """
                    # Heading
                    
                    Heading Test
                    
                    ## Subheading
                    
                    Subheading Text
                    """.trimIndent()
        )
    }
}