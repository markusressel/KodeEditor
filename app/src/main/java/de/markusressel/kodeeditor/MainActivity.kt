package de.markusressel.kodeeditor

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel
import de.markusressel.kodehighlighter.language.markdown.MarkdownSyntaxHighlighter
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        codeEditorLayout.syntaxHighlighter = MarkdownSyntaxHighlighter()
        codeEditorLayout.editable = false
        codeEditorLayout.showDivider = true
        codeEditorLayout.showMinimap = true

        initEditorText()
    }

    private fun initEditorText() {
        Fuel.get("https://raw.githubusercontent.com/markusressel/KodeEditor/master/README.md")
                .responseString { request, response, result ->
                    val (text, error) = result

                    if (error != null || text == null) {
                        // fallback if no network is available
                        codeEditorLayout.setText(R.string.demo_text)
                    } else {
                        codeEditorLayout.text = text
                    }
                    codeEditorLayout.editable = true
                }
    }

}
