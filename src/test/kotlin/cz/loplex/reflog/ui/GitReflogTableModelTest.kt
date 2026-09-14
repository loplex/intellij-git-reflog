package cz.loplex.reflog.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.table.TableView
import cz.loplex.reflog.GitReflogEntry

/**
 * Covers which columns the table has and which part of an entry each one shows.
 *
 * The columns are a list of pairs of a name and a lambda, so a column reading the wrong field of the entry is a
 * one-word mistake that compiles, draws, and is only visible to someone who knows what the value should have
 * been. There is no behaviour here to go wrong - which is exactly why it goes unnoticed when it does.
 */
class GitReflogTableModelTest : BasePlatformTestCase() {

    fun `test the columns are those the tab promises, in order`() {
        val model = GitReflogTableModel()

        assertEquals(
            listOf("Selector", "Date", "Action", "Description", "Commit", "Commit Message", "Author"),
            (0 until model.columnCount).map { model.getColumnName(it) },
        )
    }

    fun `test each column shows its own part of the entry`() {
        val model = GitReflogTableModel()
        model.items = listOf(entry)

        // The date column formats a timestamp for the locale, so it is read as "not empty" rather than by value.
        assertEquals("HEAD@{3}", valueAt(model, COLUMN_SELECTOR))
        assertTrue("The date column shows nothing", valueAt(model, COLUMN_DATE).isNotEmpty())
        assertEquals("commit (amend)", valueAt(model, COLUMN_ACTION))
        assertEquals("moving from master to feature", valueAt(model, COLUMN_DESCRIPTION))
        assertEquals("The commit column shows the whole hash", "a1b2c3d4", valueAt(model, COLUMN_COMMIT))
        assertEquals("Parse the separator", valueAt(model, COLUMN_SUBJECT))
        assertEquals("Alex Smith", valueAt(model, COLUMN_AUTHOR))
    }

    /**
     * Widths are asked for, not imposed: only the preferred width is set, which leaves every column resizable.
     * A column that asked for nothing would be drawn at the table's own default and ignore what it holds.
     */
    fun `test every column asks the table for a width`() {
        val table = TableView(GitReflogTableModel())
        GitReflogTableModel().applyColumnWidths(table)

        val widths = (0 until table.columnModel.columnCount).map { table.columnModel.getColumn(it).preferredWidth }
        assertTrue("A column asked for no width of its own: $widths", widths.all { it > 0 })
    }

    /**
     * The two columns drawn in the muted style answer the selection the same way the plain ones do.
     *
     * A renderer of that kind picks its own background, and the one it picks dims when the table loses focus,
     * where the columns the table draws for itself do not - so clicking away turned a selected row two-tone,
     * these two dimming while the other five stayed put. Whether a table ought to dim a selection it has lost
     * the focus for has an answer either way; what it cannot do is answer differently along one row.
     */
    fun `test the muted columns take their background from the table`() {
        val model = GitReflogTableModel()
        model.items = listOf(entry)
        val table = TableView(model)

        for (column in listOf(COLUMN_COMMIT, COLUMN_AUTHOR)) {
            assertEquals(
                "Column $column paints a selected row its own colour",
                table.selectionBackground,
                backgroundAt(table, model, column, selected = true),
            )
            assertEquals(
                "Column $column paints an unselected row its own colour",
                table.background,
                backgroundAt(table, model, column, selected = false),
            )
        }
    }

    private fun backgroundAt(
        table: TableView<GitReflogEntry>,
        model: GitReflogTableModel,
        column: Int,
        selected: Boolean,
    ): java.awt.Color {
        val renderer = model.columnInfos[column].getRenderer(entry)
            ?: throw AssertionError("Column $column has no renderer of its own")
        // hasFocus is the focus of the cell, and false is what a row that is merely selected gets.
        val component = renderer.getTableCellRendererComponent(table, valueAt(model, column), selected, false, 0, column)
        return component.background
    }

    private fun valueAt(model: GitReflogTableModel, column: Int): String = model.getValueAt(0, column) as String

    private companion object {
        const val COLUMN_SELECTOR = 0
        const val COLUMN_DATE = 1
        const val COLUMN_ACTION = 2
        const val COLUMN_DESCRIPTION = 3
        const val COLUMN_COMMIT = 4
        const val COLUMN_SUBJECT = 5
        const val COLUMN_AUTHOR = 6

        val entry = GitReflogEntry(
            selector = "HEAD@{3}",
            hash = "a1b2c3d4e5f6a7b8",
            timestamp = 1_700_000_000_000L,
            action = "commit (amend)",
            description = "moving from master to feature",
            author = "Alex Smith",
            subject = "Parse the separator",
        )
    }
}
