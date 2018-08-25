package de.markusressel.kodeeditor

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super
                .onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        codeEditorView
                .setText("# Topic 1\n" + "\n" + "This is a *simple* demo text written in **Markdown**")

    }
}
