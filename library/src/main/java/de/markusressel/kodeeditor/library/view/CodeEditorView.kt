package de.markusressel.kodeeditor.library.view

import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.support.annotation.AttrRes
import android.support.annotation.ColorInt
import android.support.annotation.StringRes
import android.support.annotation.StyleableRes
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.otaliastudios.zoom.ZoomLayout
import de.markusressel.kodeeditor.library.R
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighter

/**
 * Code Editor that allows pinch-to-zoom, line numbers etc.
 */
open class CodeEditorView : ZoomLayout {

    /**
     * The unique (zoomable) child element of this ZoomLayout
     */
    lateinit var contentLayout: LinearLayout

    /**
     * The actual EditText
     */
    lateinit var editTextView: CodeEditText

    /**
     * Set to true to force the width of the CodeEditorView to it's parents width
     */
    private var forceParentWidth = false

    var mMoveWithCursorEnabled = false
    private var internalMoveWithCursorEnabled = false

    private var currentLineCount = -1

    constructor(context: Context) : super(context) {
        initialize(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(attrs, defStyleAttr)
    }

    private fun initialize(attrs: AttributeSet?, defStyleAttr: Int) {
        setHasClickableChildren(true)
        isFocusableInTouchMode = true

        inflateViews(LayoutInflater.from(context))
        readParameters(attrs, defStyleAttr)

        addView(contentLayout)
        setListeners()
    }

    private fun View.setViewBackgroundWithoutResettingPadding(background: Drawable?) {
        val paddingBottom = this
                .paddingBottom
        val paddingStart = ViewCompat
                .getPaddingStart(this)
        val paddingEnd = ViewCompat
                .getPaddingEnd(this)
        val paddingTop = this
                .paddingTop
        ViewCompat
                .setBackground(this, background)
        ViewCompat
                .setPaddingRelative(this, paddingStart, paddingTop, paddingEnd, paddingBottom)
    }


    private fun readParameters(attrs: AttributeSet?, defStyleAttr: Int) {
        val a = context
                .obtainStyledAttributes(attrs, R.styleable.CodeEditorView, defStyleAttr, 0)

        val editTextBackgroundColor = a
                .getColor(R.styleable.CodeEditorView_cev_editor_backgroundColor, R.attr.cev_editor_backgroundColor, android.R.attr.windowBackground)
        editTextView
                .setBackgroundColor(editTextBackgroundColor)

        a
                .recycle()
    }

    private fun inflateViews(inflater: LayoutInflater) {
        contentLayout = inflater.inflate(R.layout.view_code_editor__inner_layout, null) as LinearLayout

        editTextView = contentLayout.findViewById(R.id.codeEditText) as CodeEditText
        editTextView
                .setViewBackgroundWithoutResettingPadding(null)
        editTextView
                .post {
                    editTextView
                            .setSelection(0)
                }
    }

    /**
     * Get Color from Theme attribute
     *
     * @param context Activity context
     * @param attr    Attribute ressource ID
     *
     * @return Color as Int
     */
    @ColorInt
    private fun getThemeAttrColor(context: Context, @AttrRes attr: Int): Int? {
        val typedValue = TypedValue()
        if (context.theme.resolveAttribute(attr, typedValue, true)) {
            if (typedValue.type >= TypedValue.TYPE_FIRST_INT && typedValue.type <= TypedValue.TYPE_LAST_INT) {
                return typedValue
                        .data
            } else if (typedValue.type == TypedValue.TYPE_STRING) {
                return ContextCompat
                        .getColor(context, typedValue.resourceId)
            }
        }

        return null
    }

    /**
     * Get a color from this TypedArray or use the first default that is found
     */
    @ColorInt
    private fun TypedArray.getColor(@StyleableRes styleableRes: Int, @AttrRes vararg attr: Int): Int {
        return this
                .getColor(styleableRes, attr.find { getThemeAttrColor(context, it) != null }
                        ?: getThemeAttrColor(context, attr.last())!!)
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

        val parentWidth = parentView
                .width
        val parentHeight = parentView
                .height

        contentLayout
                .minimumHeight = parentHeight
        contentLayout
                .minimumWidth = parentWidth
    }

    /**
     * @param editable true = user can type, false otherwise
     */
    fun setEditable(editable: Boolean) {
        editTextView
                .isEnabled = editable
    }

    /**
     * Set the text in the editor
     */
    fun setText(text: CharSequence) {
        editTextView
                .setText(text, TextView.BufferType.EDITABLE)
        editTextView
                .refreshSyntaxHighlighting()
    }

    /**
     * Set the text in the editor
     */
    @Suppress("unused")
    fun setText(@StringRes text: Int) {
        setText(context.getString(text))
    }

    /**
     * Set the syntax highlighter to use for this CodeEditor
     */
    @Suppress("unused")
    fun setSyntaxHighlighter(syntaxHighlighter: SyntaxHighlighter) {
        editTextView.syntaxHighlighter = syntaxHighlighter
    }

    companion object {
        const val TAG = "CodeEditorView"
        const val MIN_LINES = 1
    }

}
