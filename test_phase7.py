import re

with open('app/src/main/java/com/aeropad/remote/data/macros/MacroRepository.kt', 'r') as f:
    text = f.read()

funcs = """    fun exportJson(id: Long): String? = kotlinx.coroutines.runBlocking {
        val macro = byId(id) ?: return@runBlocking null
        toJson(macro.spec)
    }

    suspend fun importJson(raw: String): Long? {
        val spec = fromJson(raw) ?: return null
        return save(null, spec.copy(name = spec.name + " (imported)"))
    }"""

text = text.replace("    suspend fun delete(id: Long) {\n        dao.byId(id)?.let { dao.delete(it) }\n    }", "    suspend fun delete(id: Long) {\n        dao.byId(id)?.let { dao.delete(it) }\n    }\n\n" + funcs)

with open('app/src/main/java/com/aeropad/remote/data/macros/MacroRepository.kt', 'w') as f:
    f.write(text)
