package com.hackerdjakodjo.mypdfwriter.ui

/**
 * Pure, Android-free accessibility policy shared by every screen.
 *
 * Keeping target sizing and content descriptions here means a composable can
 * ask for the same minimum hit target or the same spoken state label without
 * duplicating string building inside UI code. Nothing in this file imports the
 * framework, so all of it is unit-testable on the JVM.
 */

/** Minimum recommended touch target, in dp. */
const val MIN_TARGET_DP = 48

/** Never shrinks a control below [MIN_TARGET_DP]; larger values pass through. */
fun accessibleTarget(sizeDp: Int): Int = maxOf(sizeDp, MIN_TARGET_DP)

/** True when [sizeDp] already meets the minimum touch target. */
fun isAccessibleTarget(sizeDp: Int): Boolean = sizeDp >= MIN_TARGET_DP

/**
 * Content description for a selectable item. Selected items append
 * ", <suffix>" so screen readers announce the state; blank labels or suffixes
 * are dropped instead of leaving a dangling separator.
 */
fun stateLabel(
    label: String,
    selected: Boolean,
    selectedSuffix: String = "선택됨",
): String {
    val base = label.trim()
    if (!selected) return base
    val suffix = selectedSuffix.trim()
    return when {
        base.isEmpty() -> suffix
        suffix.isEmpty() -> base
        else -> "$base, $suffix"
    }
}

/** Colour swatch description: a plain state label using the default suffix. */
fun colorChoiceLabel(name: String, selected: Boolean): String =
    stateLabel(name, selected)

/** Menu item description. A blank subject falls back to the action alone. */
fun menuLabel(action: String, subject: String): String {
    val verb = action.trim()
    val target = subject.trim()
    return if (target.isEmpty()) verb else "$verb: $target"
}

/** Slider description. Missing halves are omitted so no stray space remains. */
fun sliderLabel(label: String, valueLabel: String): String {
    val name = label.trim()
    val value = valueLabel.trim()
    return when {
        name.isEmpty() -> value
        value.isEmpty() -> name
        else -> "$name $value"
    }
}

/** Description for a disabled control, explaining why it cannot be used. */
fun disabledReasonLabel(action: String, reason: String): String {
    val verb = action.trim()
    val why = reason.trim()
    return when {
        verb.isEmpty() -> why
        why.isEmpty() -> verb
        else -> "$verb ($why)"
    }
}

/** Spoken state of an expandable sheet header ("펼쳐짐"/"접힘"). */
internal fun expandStateDescription(expanded: Boolean): String =
    if (expanded) "펼쳐짐" else "접힘"

/**
 * How many lines a label may use. A label that fits on one line stays on one
 * line; anything wider wraps to two. Widths below one dp are clamped to one so
 * the result is always a valid Compose `maxLines` value.
 */
fun labelMaxLines(
    availableWidthDp: Int,
    estimatedTextWidthDp: Int,
): Int = if (estimatedTextWidthDp <= maxOf(1, availableWidthDp)) 1 else 2
