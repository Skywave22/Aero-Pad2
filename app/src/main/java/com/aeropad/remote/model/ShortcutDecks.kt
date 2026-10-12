package com.aeropad.remote.model

/**
 * Smart Shortcut Deck catalog — ready-made control panels for popular
 * desktop apps. Every entry is a pure HID key+modifier combo, so it works
 * on any paired host with nothing installed (Windows, macOS, Linux).
 */
data class DeckKey(
    val label: String,
    val key: Byte,
    val mods: Byte = HidModifiers.NONE
)

data class ShortcutDeck(
    val id: String,
    val name: String,
    val icon: String,
    val keys: List<DeckKey>
)

object ShortcutCatalog {

    private const val CTRL = HidModifiers.LEFT_CTRL
    private const val SHFT = HidModifiers.LEFT_SHIFT
    private const val ALT = HidModifiers.LEFT_ALT
    private const val GUI = HidModifiers.LEFT_GUI
    // Precomputed combos (Byte.or returns Int, so keep const math explicit).
    private const val C_S: Byte = 0x03   // Ctrl+Shift
    private const val G_C: Byte = 0x09   // Win+Ctrl
    private const val G_S: Byte = 0x0A   // Win+Shift

    val DECK_VSCODE = ShortcutDeck(
        "vscode", "VS Code", "⌨",
        listOf(
            DeckKey("Palette", HidKeys.P, C_S),
            DeckKey("Quick Open", HidKeys.P, CTRL),
            DeckKey("Save", HidKeys.S, CTRL),
            DeckKey("Undo", HidKeys.Z, CTRL),
            DeckKey("Redo", HidKeys.Y, CTRL),
            DeckKey("Find", HidKeys.F, CTRL),
            DeckKey("Replace", HidKeys.H, CTRL),
            DeckKey("Terminal", HidKeys.GRAVE, CTRL),
            DeckKey("Comment", HidKeys.SLASH, CTRL),
            DeckKey("Explorer", HidKeys.E, C_S),
            DeckKey("Go Line", HidKeys.G, CTRL),
            DeckKey("Split", HidKeys.BACKSLASH, CTRL)
        )
    )

    val DECK_PHOTOSHOP = ShortcutDeck(
        "photoshop", "Photoshop", "🎨",
        listOf(
            DeckKey("Undo", HidKeys.Z, CTRL),
            DeckKey("Save", HidKeys.S, CTRL),
            DeckKey("Brush", HidKeys.B),
            DeckKey("Eraser", HidKeys.E),
            DeckKey("Move", HidKeys.V),
            DeckKey("Marquee", HidKeys.M),
            DeckKey("Lasso", HidKeys.L),
            DeckKey("Crop", HidKeys.C),
            DeckKey("Type", HidKeys.T),
            DeckKey("Pipette", HidKeys.I),
            DeckKey("Hand", HidKeys.H),
            DeckKey("Zoom", HidKeys.Z),
            DeckKey("New Layer", HidKeys.N, C_S),
            DeckKey("Deselect", HidKeys.D, CTRL),
            DeckKey("Fill", HidKeys.F5, SHFT),
            DeckKey("[ Size ]", HidKeys.LEFT_BRACKET),
            DeckKey("] Size [", HidKeys.RIGHT_BRACKET)
        )
    )

    val DECK_BLENDER = ShortcutDeck(
        "blender", "Blender", "🧊",
        listOf(
            DeckKey("Edit Mode", HidKeys.TAB),
            DeckKey("Grab", HidKeys.G),
            DeckKey("Rotate", HidKeys.R),
            DeckKey("Scale", HidKeys.S),
            DeckKey("Extrude", HidKeys.E),
            DeckKey("Delete", HidKeys.X),
            DeckKey("Select All", HidKeys.A),
            DeckKey("Add Menu", HidKeys.A, SHFT),
            DeckKey("Duplicate", HidKeys.D, SHFT),
            DeckKey("Pie Menu", HidKeys.Z),
            DeckKey("Undo", HidKeys.Z, CTRL),
            DeckKey("Save", HidKeys.S, CTRL),
            DeckKey("Front", HidKeys.NUM_1),
            DeckKey("Right", HidKeys.NUM_3),
            DeckKey("Top", HidKeys.NUM_7),
            DeckKey("Render", HidKeys.F12)
        )
    )

    val DECK_BROWSER = ShortcutDeck(
        "browser", "Browser", "🌐",
        listOf(
            DeckKey("New Tab", HidKeys.T, CTRL),
            DeckKey("Close Tab", HidKeys.W, CTRL),
            DeckKey("Reopen", HidKeys.T, C_S),
            DeckKey("Reload", HidKeys.F5),
            DeckKey("Address", HidKeys.L, CTRL),
            DeckKey("DevTools", HidKeys.F12),
            DeckKey("Find", HidKeys.F, CTRL),
            DeckKey("Full Scr", HidKeys.F11),
            DeckKey("Next Tab", HidKeys.TAB, CTRL),
            DeckKey("Prev Tab", HidKeys.TAB, C_S),
            DeckKey("Zoom +", HidKeys.EQUAL, CTRL),
            DeckKey("Zoom −", HidKeys.MINUS, CTRL),
            DeckKey("History", HidKeys.H, CTRL),
            DeckKey("Downloads", HidKeys.J, CTRL)
        )
    )

    val DECK_WINDOWS = ShortcutDeck(
        "windows", "Windows", "🪟",
        listOf(
            DeckKey("Start", HidKeys.NONE, GUI),
            DeckKey("App Switch", HidKeys.TAB, ALT),
            DeckKey("Desktop", HidKeys.D, GUI),
            DeckKey("Explorer", HidKeys.E, GUI),
            DeckKey("Run", HidKeys.R, GUI),
            DeckKey("Snip", HidKeys.S, G_S),
            DeckKey("Settings", HidKeys.I, GUI),
            DeckKey("Lock", HidKeys.L, GUI),
            DeckKey("Task Mgr", HidKeys.ESCAPE, C_S),
            DeckKey("Search", HidKeys.S, GUI),
            DeckKey("Desk ←", HidKeys.ARROW_LEFT, G_C),
            DeckKey("Desk →", HidKeys.ARROW_RIGHT, G_C)
        )
    )

    val DECKS: List<ShortcutDeck> =
        listOf(DECK_VSCODE, DECK_PHOTOSHOP, DECK_BLENDER, DECK_BROWSER, DECK_WINDOWS)
}
