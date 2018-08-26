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
//        codeEditorView
//                .setText("# Topic 1\n" + "\n" + "This is a *simple* demo ~~text~~ written in **Markdown**")
        codeEditorView
                .setText(getString(R.string.huge_text))
        codeEditorView.zoomTo(codeEditorView.realZoom, false)

//        doAsync {
//            Thread
//                    .sleep(5000)
//
//            runOnUiThread {
//                codeEditorView
//                        .setSyntaxHighlighter(JavaSyntaxHighlighter())
//                codeEditorView
//                        .setText("package de.markusressel.kodeeditor.libary.java;\n" + "\n" + "import android.content.Context;\n" + "import android.support.test.InstrumentationRegistry;\n" + "import android.support.test.runner.AndroidJUnit4;\n" + "\n" + "import org.junit.Test;\n" + "import org.junit.runner.RunWith;\n" + "\n" + "import static org.junit.Assert.*;\n" + "\n" + "/**\n" + " * Instrumented test, which will execute on an Android device.\n" + " *\n" + " * @see <a href=\"http://d.android.com/tools/testing\">Testing documentation</a>\n" + " */\n" + "@RunWith(AndroidJUnit4.class)\n" + "public class ExampleInstrumentedTest {\n" + "    @Test\n" + "    public void useAppContext() {\n" + "        // Context of the app under test.\n" + "        Context appContext = InstrumentationRegistry.getTargetContext();\n" + "\n" + "        assertEquals(\"de.markusressel.kodeeditor.libarry.java.test\", appContext.getPackageName());\n" + "    }\n" + "}\n")
//            }
//
//            Thread
//                    .sleep(5000)
//            runOnUiThread {
//                codeEditorView
//                        .setSyntaxHighlighter(KotlinSyntaxHighlighter())
//                codeEditorView
//                        .setText("package de.markusressel.mkdocseditor.data.persistence.entity\n" + "\n" + "import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem\n" + "import de.markusressel.mkdocsrestclient.document.DocumentModel\n" + "import io.objectbox.annotation.Entity\n" + "import io.objectbox.annotation.Id\n" + "import io.objectbox.relation.ToOne\n" + "import java.util.*\n" + "\n" + "/**\n" + " * Created by Markus on 04.06.2018.\n" + " */\n" + "@Entity\n" + "data class DocumentEntity(@Id var entityId: Long = 0, val type: String = \"Document\", val id: String = \"\", val name: String = \"\", val filesize: Long = -1L, val modtime: Date = Date(), val url: String = \"\") : IdentifiableListItem {\n" + "    override fun getItemId(): String = id\n" + "\n" + "    lateinit var parentSection: ToOne<SectionEntity>\n" + "\n" + "}\n" + "\n" + "fun DocumentModel.asEntity(parentSection: SectionEntity): DocumentEntity {\n" + "    val d = DocumentEntity(0, this.type, this.id, this.name, this.filesize, this.modtime, this.url)\n" + "    d\n" + "            .parentSection\n" + "            .target = parentSection\n" + "    return d\n" + "}")
//            }
//        }

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
