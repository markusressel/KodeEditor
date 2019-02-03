package de.markusressel.kodeeditor.library.extensions

import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.support.annotation.AttrRes
import android.support.annotation.ColorInt
import android.support.annotation.StyleableRes
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.util.TypedValue
import android.view.View

/**
 * Get a color from this TypedArray or use the first default that is found
 *
 * @param context view context
 * @param styleableRes styleable resource
 * @param attr theme attribute resource
 */
@ColorInt
fun TypedArray.getColor(context: Context, @StyleableRes styleableRes: Int, @AttrRes vararg attr: Int): Int {
    return getColor(styleableRes, attr.find { context.getThemeAttrColor(it) != null }
            ?: context.getThemeAttrColor(attr.last())!!)
}

/**
 * Get Color from Theme attribute
 *
 * @param context Activity context
 * @param attr    Attribute resource ID
 *
 * @return Color as Int
 */
@ColorInt
fun Context.getThemeAttrColor(@AttrRes attr: Int): Int? {
    val typedValue = TypedValue()
    if (theme.resolveAttribute(attr, typedValue, true)) {
        if (typedValue.type >= TypedValue.TYPE_FIRST_INT && typedValue.type <= TypedValue.TYPE_LAST_INT) {
            return typedValue.data
        } else if (typedValue.type == TypedValue.TYPE_STRING) {
            return ContextCompat.getColor(this, typedValue.resourceId)
        }
    }

    return null
}

/**
 * Sets a view background without resetting it's padding
 *
 * @param background the background drawable to use (may be null)
 */
fun View.setViewBackgroundWithoutResettingPadding(background: Drawable?) {
    val paddingBottom = this.paddingBottom
    val paddingStart = ViewCompat.getPaddingStart(this)
    val paddingEnd = ViewCompat.getPaddingEnd(this)
    val paddingTop = this.paddingTop
    ViewCompat.setBackground(this, background)
    ViewCompat.setPaddingRelative(this, paddingStart, paddingTop, paddingEnd, paddingBottom)
}

/**
 * Converts the given number to a px value assuming it is a dp value.
 *
 * @return px value
 */
fun Number.dpToPx(context: Context): Float {
    return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            context.resources.displayMetrics)
}