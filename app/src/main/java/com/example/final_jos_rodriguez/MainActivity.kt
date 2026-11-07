package com.example.final_jos_rodriguez

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val EXTRA_TASK_ID = "task_id"
private const val TASKS_PREFS = "tasks_store"
private const val TASKS_KEY = "tasks"

private data class Task(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    var completed: Boolean
)

private object TaskStore {
    fun all(context: Context): MutableList<Task> {
        val json = context.getSharedPreferences(TASKS_PREFS, Context.MODE_PRIVATE).getString(TASKS_KEY, "[]")
        val result = mutableListOf<Task>()
        val array = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result += Task(
                id = item.optString("id"),
                title = item.optString("title"),
                description = item.optString("description"),
                createdAt = item.optLong("createdAt"),
                completed = item.optBoolean("completed")
            )
        }
        return result
    }

    fun save(context: Context, tasks: List<Task>) {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("description", task.description)
                put("createdAt", task.createdAt)
                put("completed", task.completed)
            })
        }
        context.getSharedPreferences(TASKS_PREFS, Context.MODE_PRIVATE)
            .edit().putString(TASKS_KEY, array.toString()).apply()
    }

    fun add(context: Context, title: String, description: String) {
        val tasks = all(context)
        tasks.add(0, Task(UUID.randomUUID().toString(), title.trim(), description.trim(), System.currentTimeMillis(), false))
        save(context, tasks)
    }

    fun find(context: Context, id: String): Task? = all(context).firstOrNull { it.id == id }

    fun update(context: Context, id: String, completed: Boolean) {
        val tasks = all(context)
        tasks.firstOrNull { it.id == id }?.completed = completed
        save(context, tasks)
    }

    fun delete(context: Context, id: String) = save(context, all(context).filterNot { it.id == id })
}

private fun Activity.column(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(24, 20, 24, 20)
    setBackgroundColor(Color.rgb(247, 248, 250))
}

private fun Activity.heading(text: String): TextView = TextView(this).apply {
    this.text = text
    textSize = 26f
    setTextColor(Color.rgb(25, 43, 68))
    setTypeface(typeface, Typeface.BOLD)
    setPadding(0, 8, 0, 16)
}

private fun LinearLayout.actionButton(label: String, onClick: () -> Unit) {
    addView(Button(context).apply {
        text = label
        setOnClickListener { onClick() }
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = 8
    })
}

class MainActivity : ComponentActivity() {
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private var selectedFilter = "Todas"
    private val filterButtons = mutableMapOf<String, Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = column()
        root.addView(heading("Mis tareas"))
        root.actionButton("+ Agregar tarea") {
            startActivity(Intent(this, AddTaskActivity::class.java))
        }
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("Todas", "Pendientes", "Completadas").forEach { filter ->
            val button = Button(this).apply {
                text = filter
                setOnClickListener {
                    selectedFilter = filter
                    refreshTasks()
                }
            }
            filterButtons[filter] = button
            filterRow.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(filterRow)
        empty = TextView(this).apply {
            text = "Aún no tienes tareas. Agrega una para comenzar."
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(12, 36, 12, 36)
        }
        root.addView(empty)
        list = ListView(this).apply {
            dividerHeight = 1
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshTasks()
    }

    private fun refreshTasks() {
        val allTasks = TaskStore.all(this)
        val tasks = when (selectedFilter) {
            "Pendientes" -> allTasks.filterNot { it.completed }
            "Completadas" -> allTasks.filter { it.completed }
            else -> allTasks
        }
        filterButtons.forEach { (filter, button) ->
            button.text = if (filter == selectedFilter) "✓ $filter" else filter
        }
        empty.text = if (allTasks.isEmpty()) {
            "Aún no tienes tareas. Agrega una para comenzar."
        } else {
            "No hay tareas en esta categoría."
        }
        empty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (tasks.isEmpty()) View.GONE else View.VISIBLE
        list.adapter = TaskAdapter(tasks,
            onToggle = { task, checked ->
                TaskStore.update(this, task.id, checked)
                refreshTasks()
            },
            onOpen = { task ->
                startActivity(Intent(this, TaskDetailActivity::class.java).putExtra(EXTRA_TASK_ID, task.id))
            }
        )
    }

    private inner class TaskAdapter(
        private val tasks: List<Task>,
        private val onToggle: (Task, Boolean) -> Unit,
        private val onOpen: (Task) -> Unit
    ) : BaseAdapter() {
        override fun getCount() = tasks.size
        override fun getItem(position: Int) = tasks[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val task = getItem(position)
            val row = convertView as? LinearLayout ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 12, 8, 12)
            }
            row.removeAllViews()
            val check = CheckBox(this@MainActivity).apply {
                isChecked = task.completed
                setOnCheckedChangeListener { _, checked -> onToggle(task, checked) }
            }
            row.addView(check)
            val text = TextView(this@MainActivity).apply {
                this.text = "${task.title}\n${if (task.completed) "Completada" else "Pendiente"} · Toca para ver detalles"
                textSize = 16f
                setTextColor(if (task.completed) Color.GRAY else Color.rgb(35, 45, 60))
                if (task.completed) paintFlags = paintFlags or 0x10
                setPadding(8, 4, 8, 4)
                setOnClickListener { onOpen(task) }
            }
            row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.setOnClickListener { onOpen(task) }
            return row
        }
    }
}

class AddTaskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = column()
        root.addView(heading("Nueva tarea"))
        val titleLabel = TextView(this).apply { text = "Título"; textSize = 16f }
        root.addView(titleLabel)
        val title = android.widget.EditText(this).apply {
            hint = "Ej. Preparar informe"
            singleLine = true
        }
        root.addView(title)
        val descriptionLabel = TextView(this).apply {
            text = "Descripción"
            textSize = 16f
            setPadding(0, 16, 0, 0)
        }
        root.addView(descriptionLabel)
        val description = android.widget.EditText(this).apply {
            hint = "Agrega detalles (opcional)"
            minLines = 4
            gravity = Gravity.TOP
        }
        root.addView(description)
        root.actionButton("Guardar tarea") {
            val value = title.text.toString().trim()
            if (value.isEmpty()) {
                title.error = "Escribe un título"
                title.requestFocus()
            } else {
                TaskStore.add(this, value, description.text.toString())
                finish()
            }
        }
        root.actionButton("Cancelar") { finish() }
        setContentView(root)
    }
}

class TaskDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        val task = TaskStore.find(this, id)
        val root = column()
        if (task == null) {
            root.addView(heading("Tarea no encontrada"))
            root.actionButton("Volver") { finish() }
            setContentView(root)
            return
        }
        root.addView(heading(task.title))
        root.addView(TextView(this).apply {
            text = if (task.completed) "Estado: Completada" else "Estado: Pendiente"
            textSize = 18f
            setTextColor(Color.rgb(35, 60, 85))
            setPadding(0, 0, 0, 12)
        })
        root.addView(TextView(this).apply {
            text = "Descripción\n${task.description.ifBlank { "Sin descripción" }}"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            setPadding(0, 4, 0, 18)
        })
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(task.createdAt))
        root.addView(TextView(this).apply {
            text = "Creada: $date"
            textSize = 14f
            setTextColor(Color.GRAY)
        })
        root.actionButton(if (task.completed) "Marcar como pendiente" else "Marcar como completada") {
            TaskStore.update(this, task.id, !task.completed)
            finish()
        }
        root.actionButton("Eliminar tarea") {
            TaskStore.delete(this, task.id)
            finish()
        }
        root.actionButton("Volver a la lista") { finish() }
        setContentView(root)
    }
}

