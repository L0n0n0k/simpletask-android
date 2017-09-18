/**
 * This file is part of Todo.txt Touch, an Android app for managing your todo.txt file (http://todotxt.com).
 *
 *
 * Copyright (c) 2009-2012 Todo.txt contributors (http://todotxt.com)
 *
 *
 * LICENSE:
 *
 *
 * Todo.txt Touch is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 2 of the License, or (at your option) any
 * later version.
 *
 *
 * Todo.txt Touch is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 *
 * You should have received a copy of the GNU General Public License along with Todo.txt Touch.  If not, see
 * //www.gnu.org/licenses/>.

 * @author Todo.txt contributors @yahoogroups.com>
 * *
 * @license http://www.gnu.org/licenses/gpl.html
 * *
 * @copyright 2009-2012 Todo.txt contributors (http://todotxt.com)
 */
package nl.mpcjanssen.simpletask.task

import android.app.Activity
import android.content.Intent
import nl.mpcjanssen.simpletask.*

import nl.mpcjanssen.simpletask.remote.BackupInterface
import nl.mpcjanssen.simpletask.sort.MultiComparator
import nl.mpcjanssen.simpletask.util.*
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Implementation of the in memory representation of the Todo list
 * uses an ActionQueue to ensure modifications and access of the underlying todo list are
 * sequential. If this is not done properly the result is a likely ConcurrentModificationException.

 * @author Mark Janssen
 */
object TodoList {
    private val log: Logger = Logger

    private var mLists: ArrayList<String>? = null
    private var mTags: ArrayList<String>? = null
    val todoItems = CopyOnWriteArrayList<Task>()
    val selectedItems = CopyOnWriteArraySet<Task>()
    val pendingEdits = LinkedHashSet<Task>()
    internal val TAG = TodoList::class.java.simpleName

    fun hasPendingAction(): Boolean {
        return ActionQueue.hasPending()
    }

    // Wait until there are no more pending actions
    @Suppress("unused") // Used in test suite
    fun settle() {
        while (hasPendingAction()) {
            Thread.sleep(10)
        }
    }

    fun queue(description: String, body: () -> Unit) {
        val r = Runnable(body)
        ActionQueue.add(description, r)
    }

    fun add(items: List<Task>, atEnd: Boolean) {
        queue("Add task ${items.size} atEnd: $atEnd") {
            if (atEnd) {
                todoItems.addAll(items)
            } else {
                todoItems.addAll(0, items)
            }
        }
    }

    fun add(t: Task, atEnd: Boolean) {
        add(listOf(t), atEnd)
    }

    fun removeAll(tasks: List<Task>) {
        queue("Remove") {
            todoItems.removeAll(tasks)
            selectedItems.removeAll(tasks)
            pendingEdits.removeAll(tasks)
        }
    }

    fun size(): Int {
        return todoItems.size
    }

    val priorities: ArrayList<Priority>
        get() {
            val res = HashSet<Priority>()
            todoItems.forEach {
                res.add(it.priority)
            }
            val ret = ArrayList(res)
            Collections.sort(ret)
            return ret
        }

    val contexts: ArrayList<String>
        get() {
            val lists = mLists
            if (lists != null) {
                return lists
            }
            val res = HashSet<String>()
            todoItems.toMutableList().forEach {
                res.addAll(it.lists)
            }
            val newLists = ArrayList<String>()
            newLists.addAll(res)
            mLists = newLists
            return newLists
        }

    val projects: ArrayList<String>
        get() {
            val tags = mTags
            if (tags != null) {
                return tags
            }
            val res = HashSet<String>()
            todoItems.toMutableList().forEach {
                res.addAll(it.tags)
            }
            val newTags = ArrayList<String>()
            newTags.addAll(res)
            mTags = newTags
            return newTags
        }

    fun uncomplete(items: List<Task>) {
        queue("Uncomplete") {
            items.forEach {
                it.markIncomplete()
            }
        }
    }

    fun complete(tasks: List<Task>, keepPrio: Boolean, extraAtEnd: Boolean) {
        queue("Complete") {
            for (task in tasks) {
                val extra = task.markComplete(todayAsString)
                if (extra != null) {
                    if (extraAtEnd) {
                        todoItems.add(extra)
                    } else {
                        todoItems.add(0, extra)
                    }
                }
                if (!keepPrio) {
                    task.priority = Priority.NONE
                }
            }
        }
    }

    fun prioritize(tasks: List<Task>, prio: Priority) {
        queue("Complete") {
            tasks.map { it.priority = prio }
        }

    }

    fun defer(deferString: String, tasks: List<Task>, dateType: DateType) {
        queue("Defer") {
            tasks.forEach {
                when (dateType) {
                    DateType.DUE -> it.deferDueDate(deferString, todayAsString)
                    DateType.THRESHOLD -> it.deferThresholdDate(deferString, todayAsString)
                }
            }
        }
    }

    var selectedTasks: List<Task> = ArrayList()
        get() {
            return selectedItems.toList()
        }

    var completedTasks: List<Task> = ArrayList()
        get() {
            return todoItems.filter { it.isCompleted() }
        }

    fun saveInCache(backup: BackupInterface?) {
        log.info(TAG, "Handler: Queue save in cache")
        queue("Save in cache") {
            updateCache()
            backup?.backup(Config.cachedContents)
            if (!Config.hasKeepSelection) {
                TodoList.clearSelection()
            }
            mLists = null
            mTags = null
            broadcastRefreshUI()
        }
    }

    fun startAddTaskActivity(act: Activity, prefill: String) {
        queue("Start add/edit task activity") {
            log.info(TAG, "Starting addTask activity")
            val intent = Intent(act, AddTask::class.java)
            intent.putExtra(Constants.EXTRA_PREFILL_TEXT, prefill)
            act.startActivity(intent)
        }
    }

    fun getSortedTasks(filter: ActiveFilter, sorts: ArrayList<String>, caseSensitive: Boolean): Sequence<Task> {
        val comp = MultiComparator(sorts, TodoApplication.app.today, caseSensitive, filter.createIsThreshold)
        val itemsToSort = if (comp.fileOrder) {
            todoItems
        } else {
            todoItems.reversed()
        }
        val filteredTasks = filter.apply(itemsToSort.asSequence())
        comp.comparator?.let {
            return filteredTasks.sortedWith(it)
        }
        return filteredTasks
    }

    fun reload(reason: String = "") {
        val logText = "Reload: " + reason
        queue(logText) {
            broadcastSyncStart()
            log.info(TAG, "Todolist loaded from cache")
            val items = todoItems
            items.clear()
            val tasks = Config.cachedContents.split("\n").map { it -> Task(it) }
            items.addAll(tasks)
        }
    }


    fun isSelected(item: Task): Boolean {
        return selectedItems.indexOf(item) > -1
    }

    fun numSelected(): Int {
        return selectedItems.size
    }

    fun selectTasks(items: List<Task>) {
        queue("Select") {
            selectedItems.addAll(items)
            broadcastRefreshSelection()
        }
    }

    fun selectTask(item: Task?) {
        item?.let {
            selectTasks(listOf(item))
        }
    }

    fun unSelectTask(item: Task) {
        unSelectTasks(listOf(item))
    }

    fun unSelectTasks(items: List<Task>) {
        queue("Unselect") {
            selectedItems.removeAll(items)
            broadcastRefreshSelection()
        }
    }

    fun clearSelection() {
        queue("Clear selection") {
            selectedItems.clear()
            broadcastRefreshSelection()
        }
    }

    fun getTaskCount(): Long {
        val items = todoItems
        return items.filter { it.inFileFormat().isNotBlank() }.size.toLong()
    }

    fun updateCache() {
        Config.cachedContents = todoItems.joinToString("\n") {it.inFileFormat()}
    }

    fun editTasks(from: Activity, tasks: List<Task>, prefill: String) {
        queue("Edit tasks") {
            pendingEdits.addAll(tasks)
            startAddTaskActivity(from, prefill)
        }
    }

    fun clearPendingEdits() {
        queue("Clear selection") {
            pendingEdits.clear()
        }
    }
}

