package de.markusressel.kodeeditor

import android.graphics.Color
import android.os.Bundle
import androidx.annotation.RawRes
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel
import de.markusressel.kodeeditor.library.extensions.dpToPx
import de.markusressel.kodehighlighter.language.markdown.MarkdownSyntaxHighlighter
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        codeEditorLayout.apply {
            syntaxHighlighter = MarkdownSyntaxHighlighter()
            editable = false
            showDivider = true
            showMinimap = true
            minimapBorderWidth = 1.dpToPx(context)
            minimapBorderColor = Color.BLACK
            minimapIndicatorColor = Color.GREEN
            minimapMaxDimension = 150.dpToPx(context)
        }

        initEditorText()
    }

    private fun initEditorText() {
        Fuel.get("https://raw.githubusercontent.com/markusressel/KodeEditor/master/README.md")
                .timeout(1)
                .responseString { _, _, result ->
                    val (text, error) = result

                    if (error != null || text == null) {
                        // fallback if no network is available
                        val sampleText = readResourceFileAsText(R.raw.sample_text)
                        codeEditorLayout.text = sampleText
                    } else {
                        codeEditorLayout.text = text
                    }
                    codeEditorLayout.editable = true
                }
    }

    private fun readResourceFileAsText(@RawRes resourceId: Int): String {
        return resources.openRawResource(resourceId).bufferedReader().readText()
    }

}
