import re

with open('app/src/main/java/com/aeropad/remote/viewmodel/MacrosViewModel.kt', 'r') as f:
    text = f.read()

funcs = """    private val _message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val message: kotlinx.coroutines.flow.StateFlow<String?> = _message.asStateFlow()

    private val _exportPayload = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, String>?>(null)
    val exportPayload: kotlinx.coroutines.flow.StateFlow<Pair<String, String>?> = _exportPayload.asStateFlow()

    fun requestExport(id: Long) {
        viewModelScope.launch {
            val macro = repository.byId(id)
            val json = repository.exportJson(id)
            if (macro != null && json != null) {
                val fileName = macro.spec.name
                    .replace(Regex("[^A-Za-z0-9 _-]"), "")
                    .ifBlank { "macro" }
                    .replace(' ', '_') + ".bpmacro.json"
                _exportPayload.value = fileName to json
            } else {
                _message.value = "Export failed - macro not found."
            }
        }
    }

    fun consumeExport() { _exportPayload.value = null }

    fun importFromJson(raw: String?) {
        viewModelScope.launch {
            if (raw.isNullOrBlank()) {
                _message.value = "Import failed - file was empty."
                return@launch
            }
            val newId = repository.importJson(raw)
            _message.value = if (newId != null) "Macro imported ✓"
            else "Import failed - invalid macro file."
        }
    }

    fun consumeMessage() { _message.value = null }"""

text = text.replace("    fun delete(id: Long) {\n        viewModelScope.launch { repository.delete(id) }\n    }", "    fun delete(id: Long) {\n        viewModelScope.launch { repository.delete(id) }\n    }\n\n" + funcs)

with open('app/src/main/java/com/aeropad/remote/viewmodel/MacrosViewModel.kt', 'w') as f:
    f.write(text)
