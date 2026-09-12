package cz.loplex.reflog.ui

import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import cz.loplex.reflog.GitReflogBundle
import cz.loplex.reflog.GitReflogEntry
import org.jetbrains.annotations.Nls
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * Table model of the reflog tab. Owns its columns, including the width each one asks for.
 */
internal class GitReflogTableModel : ListTableModel<GitReflogEntry>(*COLUMNS) {

    /**
     * Applies the columns' preferred widths. Only preferred widths are set, so that the columns stay resizable
     * and the description column keeps absorbing the remaining space.
     */
    fun applyColumnWidths(table: JTable) {
        COLUMNS.forEachIndexed { index, column ->
            table.columnModel.getColumn(index).preferredWidth = JBUIScale.scale(column.preferredWidth)
        }
    }
}

private class ReflogColumn(
    name: @Nls String,
    val preferredWidth: Int,
    private val cellRenderer: TableCellRenderer? = null,
    private val value: (GitReflogEntry) -> String,
) : ColumnInfo<GitReflogEntry, String>(name) {

    override fun valueOf(item: GitReflogEntry): String = value(item)

    override fun getRenderer(item: GitReflogEntry?): TableCellRenderer? = cellRenderer
}

/** Renders a value in the muted style the platform uses for secondary information such as hashes. */
private val grayedRenderer = object : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(
        table: JTable,
        value: Any?,
        selected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ) {
        append(value as? String ?: "", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
}

private val COLUMNS: Array<ReflogColumn> = arrayOf(
    ReflogColumn(GitReflogBundle.message("reflog.column.selector"), preferredWidth = 90) { it.selector },
    ReflogColumn(GitReflogBundle.message("reflog.column.date"), preferredWidth = 130) {
        DateFormatUtil.formatPrettyDateTime(it.timestamp)
    },
    ReflogColumn(GitReflogBundle.message("reflog.column.action"), preferredWidth = 120) { it.action },
    ReflogColumn(GitReflogBundle.message("reflog.column.description"), preferredWidth = 400) { it.description },
    ReflogColumn(GitReflogBundle.message("reflog.column.commit"), preferredWidth = 90, grayedRenderer) { it.shortHash },
)
