package de.markusressel.kodeeditor

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import de.markusressel.kodeeditor.library.markdown.MarkdownSyntaxHighlighter
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        codeEditorView.setSyntaxHighlighter(MarkdownSyntaxHighlighter())
        codeEditorView.setText(getString(R.string.demo_text))
        codeEditorView.zoomTo(codeEditorView.realZoom, false)
    }

}
