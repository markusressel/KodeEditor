package de.markusressel.kodeeditor.library.view

import android.content.Context
import android.os.Build
import android.support.v7.widget.AppCompatEditText
import android.text.Layout
import android.util.AttributeSet
import android.util.Log
import com.jakewharton.rxbinding2.widget.RxTextView
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import de.markusressel.kodehighlighter.core.StatefulSyntaxHighlighter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

/**
 * EditText modified for longer texts and support for syntax highlighting
 */
class CodeEditText
@JvmOverloads
constructor(context: Context,
            attrs: AttributeSet? = null,
            defStyleAttr: Int = 0)
    : AppCompatEditText(context, attrs, defStyleAttr) {

    /**
     * The current syntax highlighter
     */
    var syntaxHighlighter: StatefulSyntaxHighlighter? = null
        set(value) {
            // clear any old style
            text?.let {
                field?.clearAppliedStyles(it)
            }

            // set new highlighter
            field = value

            // and initialize it
            initSyntaxHighlighter()
        }

    private var highlightingTimeout = 50L to TimeUnit.MILLISECONDS
    private var highlightingDisposable: Disposable? = null

    init {
        reInit()
    }

    private fun reInit() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }

        initSyntaxHighlighter()
        isClickable = true
        isFocusableInTouchMode = true
    }

    private fun initSyntaxHighlighter() {
        highlightingDisposable?.dispose()

        if (syntaxHighlighter != null) {
            refreshSyntaxHighlighting()

            highlightingDisposable = RxTextView
                    .afterTextChangeEvents(this)
                    .debounce(highlightingTimeout.first, highlightingTimeout.second)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .bindToLifecycle(this)
                    .subscribeBy(onNext = {
                        // syntax highlighting
                        refreshSyntaxHighlighting()
                    }, onError = {
                        Log.e(TAG, "Error while refreshing syntax highlighting", it)
                    })
        }
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(text, type)
        refreshSyntaxHighlighting()
    }

    /**
     * Set the timeout before new text is highlighted after the user has stopped typing.
     *
     * @param timeout arbitrary value
     * @param timeUnit the time unit to use
     */
    @Suppress("unused")
    fun setHighlightingTimeout(timeout: Long, timeUnit: TimeUnit) {
        highlightingTimeout = timeout to timeUnit
        reInit()
    }

    /**
     * Get the current syntax highlighter timeout in milliseconds.
     *
     * @return timeout in milliseconds
     */
    @Suppress("unused")
    fun getHighlightingTimeout(): Long {
        return highlightingTimeout.second.toMillis(highlightingTimeout.first)
    }

    /**
     * Force a refresh of the syntax highlighting
     */
    @Synchronized
    fun refreshSyntaxHighlighting() {
        if (syntaxHighlighter == null) {
            Log.w(TAG, "No syntax highlighter is set!")
        }

        syntaxHighlighter?.apply { highlight(text!!) }
    }

    companion object {
        const val TAG = "CodeEditText"
    }

}