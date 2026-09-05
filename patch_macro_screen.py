import re

with open('app/src/main/java/com/aeropad/remote/ui/screens/macros/MacrosScreen.kt', 'r') as f:
    text = f.read()

import_launchers = """    val message by viewModel.message.collectAsState()
    val exportPayload by viewModel.exportPayload.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()
        viewModel.importFromJson(json)
    }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri: android.net.Uri? ->
        if (uri == null) { viewModel.consumeExport(); return@rememberLauncherForActivityResult }
        val payload = exportPayload?.second ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray()) }
        }
        viewModel.consumeExport()
    }

    androidx.compose.runtime.LaunchedEffect(exportPayload) {
        exportPayload?.let { (name, _) -> exportLauncher.launch(name) }
    }

    androidx.compose.runtime.LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }"""

text = text.replace("    val draft by viewModel.draft.collectAsState()", "    val draft by viewModel.draft.collectAsState()\n" + import_launchers)

# We need to add the import/export buttons to the top bar and rows.
old_top_bar = """        topBar = {
            TopAppBar(
                title = { Text(if (spec.monoFont) "MACROS" else "Macros") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        }"""

new_top_bar = """        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (spec.monoFont) "MACROS" else "Macros") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.FileUpload, contentDescription = "Import macro")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        }"""

text = text.replace(old_top_bar, new_top_bar)

old_row_actions = """                        IconButton(onClick = { viewModel.delete(macro.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }"""

new_row_actions = """                        IconButton(onClick = { viewModel.requestExport(macro.id) }) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.FileDownload, contentDescription = "Export")
                        }
                        IconButton(onClick = { viewModel.delete(macro.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }"""

text = text.replace(old_row_actions, new_row_actions)

with open('app/src/main/java/com/aeropad/remote/ui/screens/macros/MacrosScreen.kt', 'w') as f:
    f.write(text)
