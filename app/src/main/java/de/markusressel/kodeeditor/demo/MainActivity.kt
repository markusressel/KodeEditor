package de.markusressel.kodeeditor.demo

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import androidx.annotation.RawRes
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel
import de.markusressel.kodeeditor.demo.databinding.ActivityMainBinding
import de.markusressel.kodeeditor.library.extensions.dpToPx
import de.markusressel.kodehighlighter.language.markdown.MarkdownRuleBook
import de.markusressel.kodehighlighter.language.markdown.colorscheme.DarkBackgroundColorScheme

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.codeEditorLayout.apply {
            languageRuleBook = MarkdownRuleBook()
            colorScheme = DarkBackgroundColorScheme()
            lineNumberGenerator = { lines ->
                (1..lines).map { " $it " }
            }
            editable = false
            showDivider = true
            showMinimap = true
            minimapBorderWidth = 1.dpToPx(context)
            minimapBorderColor = Color.BLACK
            minimapIndicatorColor = Color.GREEN
            minimapMaxDimension = 150.dpToPx(context)
            minimapGravity = Gravity.BOTTOM or Gravity.END
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
                        binding.codeEditorLayout.text = sampleText
                    } else {
                        binding.codeEditorLayout.text = text
                    }
                    binding.codeEditorLayout.editable = true
                }
    }

    private fun readResourceFileAsText(@RawRes resourceId: Int): String {
        return resources.openRawResource(resourceId).bufferedReader().readText()
    }

}
