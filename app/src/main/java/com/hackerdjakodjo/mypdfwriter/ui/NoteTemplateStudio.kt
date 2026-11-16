package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.CoverStyle
import com.hackerdjakodjo.mypdfwriter.data.CustomTemplatePaths
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.NotePaper
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PageOrientation
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.PaperTint

/**
 * One pending Template Studio edit. Applying it is a single [Notebook] change,
 * so the editor can commit the whole studio result as one undo step.
 *
 * [customBackground] is a portable asset file name below `files/templates/`; null
 * removes the custom background and reveals the paper template/tint underneath.
 */
data class NoteTemplateStudioChoice(
    val template: PaperTemplate,
    val tint: PaperTint,
    val orientation: PageOrientation,
    val applyToAllPages: Boolean,
    val coverColor: Int,
    val coverStyle: CoverStyle,
    val customBackground: String? = null,
    val customBackgroundName: String = "",
)

/**
 * Pure Template Studio application and warning math.
 *
 * Template, tint and orientation are per page; the cover is per notebook. When the
 * orientation changes, [NotePaper.reorient] re-cuts the page without stretching
 * its ink. [cropRiskCount] tells the sheet how many target pages would lose ink.
 */
object NoteTemplateStudio {

    fun apply(
        notebook: Notebook,
        currentIndex: Int,
        choice: NoteTemplateStudioChoice,
    ): Notebook {
        val pages = notebook.pages.ifEmpty { listOf(NotePage()) }
        val targets = targetIndices(pages, currentIndex, choice.applyToAllPages).toSet()
        val updatedPages = pages.mapIndexed { index, page ->
            if (index !in targets) {
                page
            } else {
                styledPage(page, choice)
            }
        }
        return notebook.copy(
            pages = updatedPages,
            coverColor = choice.coverColor,
            coverStyle = choice.coverStyle,
        )
    }

    /** Pages that would receive this edit, in page order. */
    fun targetIndices(
        pages: List<NotePage>,
        currentIndex: Int,
        applyToAllPages: Boolean,
    ): List<Int> {
        if (pages.isEmpty()) return emptyList()
        return if (applyToAllPages) {
            pages.indices.toList()
        } else {
            listOf(currentIndex.coerceIn(0, pages.lastIndex))
        }
    }

    /** How many target pages would have ink outside the target orientation. */
    fun cropRiskCount(
        notebook: Notebook,
        currentIndex: Int,
        choice: NoteTemplateStudioChoice,
    ): Int {
        val pages = notebook.pages
        return targetIndices(pages, currentIndex, choice.applyToAllPages).count { index ->
            val page = pages[index]
            page.orientation != choice.orientation &&
                NotePaper.wouldCrop(page, choice.orientation)
        }
    }

    /** True when at least one target page with ink actually changes orientation. */
    fun reorientsExistingInk(
        notebook: Notebook,
        currentIndex: Int,
        choice: NoteTemplateStudioChoice,
    ): Boolean {
        val pages = notebook.pages
        return targetIndices(pages, currentIndex, choice.applyToAllPages).any { index ->
            val page = pages[index]
            page.orientation != choice.orientation && NotePaper.hasInk(page)
        }
    }

    fun styledPage(page: NotePage, choice: NoteTemplateStudioChoice): NotePage {
        val safeBackground = CustomTemplatePaths.sanitizeKey(choice.customBackground)
        return NotePaper.reorient(page, choice.orientation).copy(
            template = choice.template,
            tint = choice.tint,
            customBackground = safeBackground,
            customBackgroundName = if (safeBackground == null) {
                ""
            } else {
                CustomTemplatePaths.normalizeName(choice.customBackgroundName)
            },
        )
    }
}
