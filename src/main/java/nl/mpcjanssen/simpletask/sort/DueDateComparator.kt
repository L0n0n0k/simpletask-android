package nl.mpcjanssen.simpletask.sort

import nl.mpcjanssen.simpletask.task.TodoListItem
import java.util.*

class DueDateComparator : Comparator<TodoListItem> {

    override fun compare(a: TodoListItem?, b: TodoListItem?): Int {
        if (a === b) {
            return 0
        } else if (a == null) {
            return -1
        } else if (b == null) {
            return 1
        }
        val aDate = a.task.dueDate
        val bDate =  b.task.dueDate
        if ( aDate == null && bDate == null) {
            return 0
        } else if (aDate == null) {
            return 1
        } else if (bDate == null) {
            return  -1
        }
        return aDate.compareTo(bDate)

    }
}
