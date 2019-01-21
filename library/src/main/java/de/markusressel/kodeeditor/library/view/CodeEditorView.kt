package de.markusressel.kodeeditor.library.view

import android.content.Context
import android.support.annotation.StringRes
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.otaliastudios.zoom.ZoomLayout
import de.markusressel.kodeeditor.library.R
import de.markusressel.kodeeditor.library.extensions.getColor
import de.markusressel.kodeeditor.library.extensions.setViewBackgroundWithoutResettingPadding
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighter

/**
 * Code Editor that allows pinch-to-zoom
 */
open class CodeEditorView
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : ZoomLayout(context, attrs, defStyleAttr) {

    /**
     * The unique (zoomable) child element of this ZoomLayout
     */
    lateinit var contentLayout: LinearLayout

    /**
     * The actual text editor content
     */
    lateinit var codeEditText: CodeEditText

    /**
     * A text view for the non-editable state
     */
    lateinit var codeTextView: CodeTextView

    /**
     * Set to true to force the width of the CodeEditorView to it's parents width
     */
    private var forceParentWidth = false

    init {
        setHasClickableChildren(true)
        isFocusableInTouchMode = true

        inflateViews(LayoutInflater.from(context))
        readParameters(attrs, defStyleAttr)

        setListeners()
    }

    private fun readParameters(attrs: AttributeSet?, defStyleAttr: Int) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.CodeEditorView, defStyleAttr, 0)

        val editTextBackgroundColor = a.getColor(context, R.styleable.CodeEditorView_cev_editor_backgroundColor, R.attr.cev_editor_backgroundColor, android.R.attr.windowBackground)
        codeEditText.setBackgroundColor(editTextBackgroundColor)

        a.recycle()
    }

    private fun inflateViews(inflater: LayoutInflater) {
        contentLayout = inflater.inflate(R.layout.view_code_editor__inner_layout, this).findViewById(R.id.cev_editor_contentLayout)

        codeEditText = contentLayout.findViewById(R.id.cev_editor_codeEditText) as CodeEditText
        codeEditText.setViewBackgroundWithoutResettingPadding(null)
        codeEditText.post {
            codeEditText.setSelection(0)
        }

        codeTextView = contentLayout.findViewById(R.id.cev_editor_codeTextView) as CodeTextView
        codeTextView.setViewBackgroundWithoutResettingPadding(null)
    }

    private var firstInit = true

    private fun setListeners() {
        addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (firstInit) {
                firstInit = false

                setMinimumDimensions()
                if (forceParentWidth) {
                    // force exact width
                    val params = contentLayout.layoutParams
                    params.width = (parent as View).height
                    contentLayout.layoutParams = params
                }
            }
        }
    }

    private fun setMinimumDimensions() {
        val parentView = (parent as View)
        contentLayout.minimumHeight = parentView.height
        contentLayout.minimumWidth = parentView.width
    }

    /**
     * Controls whether the text is editable
     *
     * @param editable true = user can type, false otherwise
     */
    fun setEditable(editable: Boolean) {
        if (editable) {
            codeEditText.visibility = View.VISIBLE
            codeTextView.visibility = View.GONE
        } else {
            codeEditText.visibility = View.GONE
            codeTextView.visibility = View.VISIBLE
        }
//        codeEditText.isEnabled = editable
//        codeEditText.isClickable = editable
//        codeEditText.isFocusableInTouchMode = editable
//        if (!editable) codeEditText.clearFocus()
    }

    /**
     * Set the text in the editor
     *
     * @param text the new text to set
     */
    fun setText(text: CharSequence) {
        codeEditText.setText(text)
        codeTextView.text = text
    }

    /**
     * Set the text in the editor
     *
     * @param text string resource of the new text to set
     */
    @Suppress("unused")
    fun setText(@StringRes text: Int) {
        setText(context.getString(text))
    }

    /**
     * Set the syntax highlighter to use
     *
     * @param syntaxHighlighter the highlighter to set
     */
    fun setSyntaxHighlighter(syntaxHighlighter: SyntaxHighlighter?) {
        codeEditText.syntaxHighlighter = syntaxHighlighter
        codeTextView.syntaxHighlighter = syntaxHighlighter
    }

    companion object {
        const val TAG = "CodeEditorView"
    }

}
