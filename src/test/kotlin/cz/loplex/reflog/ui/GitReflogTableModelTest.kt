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
