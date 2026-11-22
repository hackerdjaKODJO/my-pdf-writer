package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType

/** Replaces the text of one annotation without losing its identity, layer or timing. */
internal fun noteReplaceAnnotationText(
    annotations: List<Annotation>,
    id: String,
    text: String,
    fontSize: Float? = null,
): List<Annotation> = annotations.map { annotation ->
    if (annotation.id != id) {
        annotation
    } else {
        annotation.copy(
            text = text,
            fontSize = fontSize?.takeIf { it.isFinite() && it > 0f } ?: annotation.fontSize,
        )
    }
}

/** True only when exactly one selected annotation is a text box. */
internal fun noteCanEditText(selected: List<Annotation>): Boolean =
    selected.size == 1 && selected[0].type == AnnotationType.TEXT
