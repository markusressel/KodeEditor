package de.markusressel.kodeeditor

import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import de.markusressel.kodeeditor.library.markdown.MarkdownSyntaxHighlighter
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super
                .onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        codeEditorView
                .setSyntaxHighlighter(MarkdownSyntaxHighlighter())
        codeEditorView
                .setText("# Topic 1\n" + "\n" + "This is a *simple* demo text written in **Markdown**")

        doAsync {
            Thread
                    .sleep(5000)
            runOnUiThread {
                codeEditorView
                        .setText("# Topic 1\n" + "\n" + "This is a *simple* demo text written in **Markdown**\n\n\nTEST\n" + "\n" + "\n" + "TEST\n" + "\n" + "\n" + "TEST")
            }
        }

    }

    fun Any.doAsync(handler: () -> Unit) {
        object : AsyncTask<Void, Void, Void?>() {
            override fun doInBackground(vararg p0: Void?): Void? {
                handler()
                return null
            }
        }
                .execute()
    }
}
