package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextWord

/**
 * Pure decision for the transient text-selection drag.
 *
 * A drag produces a new word list on nearly every pointer frame even when the
 * covered words did not change (finger jitter inside one word). Publishing that
 * list to the host recomposes the entire viewer and redraws the whole page, so
 * the canvas consults this helper first. The final pointer-up result is always
 * published when it differs from what the host already has.
 */
internal object TextSelectionPublish {

    fun shouldPublish(previous: List<PdfTextWord>, next: List<PdfTextWord>): Boolean =
        previous != next
}
