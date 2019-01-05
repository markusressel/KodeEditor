package de.markusressel.kodeeditor.library.view

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.support.annotation.AttrRes
import android.support.annotation.ColorInt
import android.support.annotation.StringRes
import android.support.annotation.StyleableRes
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.text.Layout
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.jakewharton.rxbinding2.widget.RxTextView
import com.otaliastudios.zoom.ZoomLayout
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import de.markusressel.kodeeditor.library.R
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Code Editor that allows pinch-to-zoom, line numbers etc.
 */
open class CodeEditorView : ZoomLayout {

    /**
     * The unique (zoomable) child element of this ZoomLayout
     */
    internal lateinit var contentLayout: LinearLayout

    /**
     * The view displaying line numbers
     */
    internal lateinit var lineNumberView: TextView

    /**
     * The divider between line numbers and text editor
     */
    internal lateinit var dividerView: View

    /**
     * The actual EditText
     */
    internal lateinit var editTextView: CodeEditText

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

        val lineNumberTextColor = a
                .getColor(R.styleable.CodeEditorView_cev_lineNumbers_textColor, R.attr.cev_lineNumbers_textColor, android.R.attr.textColorPrimary)
        lineNumberView
                .setTextColor(lineNumberTextColor)

        val lineNumberBackgroundColor = a
                .getColor(R.styleable.CodeEditorView_cev_lineNumbers_backgroundColor, R.attr.cev_lineNumbers_backgroundColor, android.R.attr.windowBackground)
        lineNumberView
                .setBackgroundColor(lineNumberBackgroundColor)


        val dividerEnabled = a
                .getBoolean(R.styleable.CodeEditorView_cev_divider, true)
        dividerView
                .visibility = when (dividerEnabled) {
            true -> View.VISIBLE
            else -> View.GONE
        }
        val dividerColor = a
                .getColor(R.styleable.CodeEditorView_cev_divider_color, R.attr.cev_divider_color, android.R.attr.textColorPrimary)
        dividerView
                .setBackgroundColor(dividerColor)

        val editTextBackgroundColor = a
                .getColor(R.styleable.CodeEditorView_cev_editor_backgroundColor, R.attr.cev_editor_backgroundColor, android.R.attr.windowBackground)
        editTextView
                .setBackgroundColor(editTextBackgroundColor)

        a
                .recycle()
    }

    private fun inflateViews(inflater: LayoutInflater) {
        contentLayout = inflater.inflate(R.layout.view_code_editor__inner_layout, null) as LinearLayout

        lineNumberView = contentLayout.findViewById(R.id.codeLinesView) as TextView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            lineNumberView.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }

        dividerView = contentLayout.findViewById(R.id.divider) as View

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
                    val params = contentLayout
                            .layoutParams
                    params
                            .width = (parent as View)
                            .height
                    contentLayout
                            .layoutParams = params
                }
                updateLineNumbers(editTextView.lineCount)
            }
        }

        setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_MOVE -> {
                    internalMoveWithCursorEnabled = false
                }
            }
            false
        }

        editTextView
                .setOnClickListener {
                    if (mMoveWithCursorEnabled) {
                        internalMoveWithCursorEnabled = true
                    }
                }

        if (mMoveWithCursorEnabled) {
            Observable
                    .interval(250, TimeUnit.MILLISECONDS)
                    .skip(5, TimeUnit.SECONDS)
                    .filter { internalMoveWithCursorEnabled }
                    .bindToLifecycle(this)
                    .subscribeBy(onNext = {
                        try {
                            moveScreenWithCursorIfNecessary()
                        } catch (e: Throwable) {
                            Log.e(TAG, "Error moving screen with cursor", e)
                        }
                    }, onError = {
                        Log.e(TAG, "Unrecoverable error while moving screen with cursor", it)
                    })
        }

        RxTextView
                .textChanges(editTextView)
                .debounce(50, TimeUnit.MILLISECONDS)
                .filter {
                    if (mMoveWithCursorEnabled) {
                        internalMoveWithCursorEnabled = true
                    }
                    editTextView.lineCount != currentLineCount
                }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .bindToLifecycle(this)
                .subscribeBy(onNext = {
                    try {
                        updateLineNumbers(editTextView.lineCount)
                    } catch (e: Throwable) {
                        Log
                                .e(TAG, "Error updating line numbers", e)
                    }
                }, onError = {
                    Log
                            .e(TAG, "Unrecoverable error while updating line numbers", it)
                })
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

    private fun moveScreenWithCursorIfNecessary() {
        val pos = editTextView
                .selectionStart
        val layout = editTextView
                .layout

        if (layout != null) {
            val line = layout
                    .getLineForOffset(pos)
            val baseline = layout
                    .getLineBaseline(line)
            val ascent = layout
                    .getLineAscent(line)
            val x = layout
                    .getPrimaryHorizontal(pos)
            val y = (baseline + ascent)
                    .toFloat()

            val zoomLayoutRect = Rect()
            getLocalVisibleRect(zoomLayoutRect)

            val transformedX = x * realZoom + panX * realZoom + lineNumberView.width * realZoom
            val transformedY = y * realZoom + panY * realZoom

            if (!zoomLayoutRect.contains(transformedX.roundToInt(), transformedY.roundToInt())) {

                var newX = panX
                var newY = panY

                if (transformedX < zoomLayoutRect.left || transformedX > zoomLayoutRect.right) {
                    newX = -x
                }

                if (transformedY < zoomLayoutRect.top || transformedY > zoomLayoutRect.bottom) {
                    newY = -y
                }

                moveTo(zoom, newX, newY, false)
            }
        }
    }

    private fun updateLineNumbers(lines: Int) {
        currentLineCount = lines

        val linesToDraw = if (lines < MIN_LINES) {
            MIN_LINES
        } else {
            lines
        }

        val sb = StringBuilder()
        for (i in 1..linesToDraw) {
            sb
                    .append("$i:\n")
        }
        lineNumberView
                .text = sb
                .toString()
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
        updateLineNumbers(editTextView.lineCount)
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
        editTextView
                .syntaxHighlighter = syntaxHighlighter
    }

    companion object {
        const val TAG = "CodeEditorView"
        const val MIN_LINES = 1
    }

}
