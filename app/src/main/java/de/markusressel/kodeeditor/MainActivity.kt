package de.markusressel.kodeeditor

import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import de.markusressel.kodeeditor.library.java.JavaSyntaxHighlighter
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
                .setText("# Topic 1\n" + "\n" + "This is a *simple* demo ~~text~~ written in **Markdown**")

        doAsync {
            Thread
                    .sleep(5000)

            runOnUiThread {
                codeEditorView
                        .setSyntaxHighlighter(JavaSyntaxHighlighter())
                codeEditorView
                        .setText("package de.markusressel.kodeeditor.libary.java;\n" + "\n" + "import android.content.Context;\n" + "import android.support.test.InstrumentationRegistry;\n" + "import android.support.test.runner.AndroidJUnit4;\n" + "\n" + "import org.junit.Test;\n" + "import org.junit.runner.RunWith;\n" + "\n" + "import static org.junit.Assert.*;\n" + "\n" + "/**\n" + " * Instrumented test, which will execute on an Android device.\n" + " *\n" + " * @see <a href=\"http://d.android.com/tools/testing\">Testing documentation</a>\n" + " */\n" + "@RunWith(AndroidJUnit4.class)\n" + "public class ExampleInstrumentedTest {\n" + "    @Test\n" + "    public void useAppContext() {\n" + "        // Context of the app under test.\n" + "        Context appContext = InstrumentationRegistry.getTargetContext();\n" + "\n" + "        assertEquals(\"de.markusressel.kodeeditor.libarry.java.test\", appContext.getPackageName());\n" + "    }\n" + "}\n")
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
