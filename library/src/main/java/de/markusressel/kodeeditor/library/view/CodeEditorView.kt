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
    lateinit var editTextView: CodeEditText

    /**
     * Set to true to force the width of the CodeEditorView to it's parents width
     */
    private var forceParentWidth = false

    init {
        initialize(attrs, defStyleAttr)
    }

    private fun initialize(attrs: AttributeSet?, defStyleAttr: Int) {
        setHasClickableChildren(true)
        isFocusableInTouchMode = true

        inflateViews(LayoutInflater.from(context))
        readParameters(attrs, defStyleAttr)

        setListeners()
    }

    private fun readParameters(attrs: AttributeSet?, defStyleAttr: Int) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.CodeEditorView, defStyleAttr, 0)

        val editTextBackgroundColor = a.getColor(context, R.styleable.CodeEditorView_cev_editor_backgroundColor, R.attr.cev_editor_backgroundColor, android.R.attr.windowBackground)
        editTextView.setBackgroundColor(editTextBackgroundColor)

        a.recycle()
    }

    private fun inflateViews(inflater: LayoutInflater) {
        contentLayout = inflater.inflate(R.layout.view_code_editor__inner_layout, this).findViewById(R.id.cev_editor_contentLayout)

        editTextView = contentLayout.findViewById(R.id.cev_editor_codeEditText) as CodeEditText
        editTextView.setViewBackgroundWithoutResettingPadding(null)
        editTextView.post {
            editTextView.setSelection(0)
        }
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
        editTextView.isEnabled = editable
    }

    /**
     * Set the text in the editor
     *
     * @param text the new text to set
     */
    fun setText(text: CharSequence) {
        editTextView.setText(text)
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
    fun setSyntaxHighlighter(syntaxHighlighter: SyntaxHighlighter) {
        editTextView.syntaxHighlighter = syntaxHighlighter
    }

    companion object {
        const val TAG = "CodeEditorView"
    }

}
