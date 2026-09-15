package com.example.composekeyboard.data

enum class KeyboardMode {
    LOWERCASE,
    UPPERCASE,
    CAPS_LOCKED,
    SYMBOLS,
    SYMBOLS_MORE,
    NUMPAD,
    EMOJI,
    CLIPBOARD,
    THEMES,
    SETTINGS,
    VOICE
}

sealed class KeyType {
    data class Character(val primary: String, val popup: List<String> = emptyList()) : KeyType()
    object Shift : KeyType()
    object Backspace : KeyType()
    object SymbolToggle : KeyType()
    object SymbolMoreToggle : KeyType()
    object AlphabetToggle : KeyType()
    object NumpadToggle : KeyType()
    object EmojiToggle : KeyType()
    object Space : KeyType()
    object Enter : KeyType()
    object LanguageSwitch : KeyType()
}

data class KeyModel(
    val type: KeyType,
    val weight: Float = 1.0f,
    val isAccent: Boolean = false
)

object KeyboardLayouts {

    val numberRow: List<KeyModel> = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map {
        KeyModel(KeyType.Character(it))
    }

    val qwertyRow1: List<KeyModel> = listOf(
        KeyModel(KeyType.Character("q")),
        KeyModel(KeyType.Character("w")),
        KeyModel(KeyType.Character("e", listOf("é", "è", "ê", "ë", "ē"))),
        KeyModel(KeyType.Character("r")),
        KeyModel(KeyType.Character("t", listOf("þ"))),
        KeyModel(KeyType.Character("y", listOf("ÿ"))),
        KeyModel(KeyType.Character("u", listOf("ú", "ù", "û", "ü", "ū"))),
        KeyModel(KeyType.Character("i", listOf("í", "ì", "î", "ï", "ī"))),
        KeyModel(KeyType.Character("o", listOf("ó", "ò", "ô", "ö", "õ", "ø", "œ", "ō"))),
        KeyModel(KeyType.Character("p"))
    )

    val qwertyRow1WithNumbers: List<KeyModel> = listOf(
        KeyModel(KeyType.Character("q", listOf("1"))),
        KeyModel(KeyType.Character("w", listOf("2"))),
        KeyModel(KeyType.Character("e", listOf("3", "é", "è", "ê", "ë", "ē"))),
        KeyModel(KeyType.Character("r", listOf("4"))),
        KeyModel(KeyType.Character("t", listOf("5", "þ"))),
        KeyModel(KeyType.Character("y", listOf("6", "ÿ"))),
        KeyModel(KeyType.Character("u", listOf("7", "ú", "ù", "û", "ü", "ū"))),
        KeyModel(KeyType.Character("i", listOf("8", "í", "ì", "î", "ï", "ī"))),
        KeyModel(KeyType.Character("o", listOf("9", "ó", "ò", "ô", "ö", "õ", "ø", "œ", "ō"))),
        KeyModel(KeyType.Character("p", listOf("0")))
    )

    fun getQwertyRow1(showNumberRow: Boolean): List<KeyModel> =
        if (showNumberRow) qwertyRow1 else qwertyRow1WithNumbers

    val qwertyRow2: List<KeyModel> = listOf(
        KeyModel(KeyType.Character("a", listOf("á", "à", "â", "ä", "ã", "å", "æ", "ā"))),
        KeyModel(KeyType.Character("s", listOf("ß", "ś", "š", "$"))),
        KeyModel(KeyType.Character("d", listOf("ð"))),
        KeyModel(KeyType.Character("f")),
        KeyModel(KeyType.Character("g")),
        KeyModel(KeyType.Character("h")),
        KeyModel(KeyType.Character("j")),
        KeyModel(KeyType.Character("k")),
        KeyModel(KeyType.Character("l", listOf("£")))
    )

    val qwertyRow3: List<KeyModel> = listOf(
        KeyModel(KeyType.Shift, weight = 1.35f, isAccent = true),
        KeyModel(KeyType.Character("z", listOf("ź", "ž", "ż"))),
        KeyModel(KeyType.Character("x")),
        KeyModel(KeyType.Character("c", listOf("ç", "ć", "č"))),
        KeyModel(KeyType.Character("v")),
        KeyModel(KeyType.Character("b")),
        KeyModel(KeyType.Character("n", listOf("ñ", "ń"))),
        KeyModel(KeyType.Character("m")),
        KeyModel(KeyType.Backspace, weight = 1.35f, isAccent = true)
    )

    val qwertyBottomRow: List<KeyModel> = listOf(
        KeyModel(KeyType.SymbolToggle, weight = 1.3f, isAccent = true),
        KeyModel(KeyType.EmojiToggle, weight = 1.0f, isAccent = true),
        KeyModel(KeyType.Character(",", listOf("?", "!", "'", "\"", ";", ":")), weight = 0.9f),
        KeyModel(KeyType.Space, weight = 4.0f),
        KeyModel(KeyType.Character(".", listOf("!", "?", ",", "@", "#", "/", "-", "_", ":", ";")), weight = 0.9f),
        KeyModel(KeyType.Enter, weight = 1.5f, isAccent = true)
    )

    val emojiSearchBottomRow: List<KeyModel> = listOf(
        KeyModel(KeyType.AlphabetToggle, weight = 1.3f, isAccent = true),
        KeyModel(KeyType.EmojiToggle, weight = 1.0f, isAccent = true),
        KeyModel(KeyType.Character(",", listOf("?", "!", "'", "\"", ";", ":")), weight = 0.9f),
        KeyModel(KeyType.Space, weight = 4.0f),
        KeyModel(KeyType.Character(".", listOf("!", "?", ",", "@", "#", "/", "-", "_", ":", ";")), weight = 0.9f),
        KeyModel(KeyType.Enter, weight = 1.5f, isAccent = true)
    )

    val emailBottomRow: List<KeyModel> = listOf(
        KeyModel(KeyType.SymbolToggle, weight = 1.2f, isAccent = true),
        KeyModel(KeyType.EmojiToggle, weight = 0.9f, isAccent = true),
        KeyModel(KeyType.Character("@"), weight = 1.15f),
        KeyModel(KeyType.Space, weight = 3.2f),
        KeyModel(KeyType.Character(".", listOf(".com", ".org", ".net", "_", "-")), weight = 1.1f),
        KeyModel(KeyType.Enter, weight = 1.4f, isAccent = true)
    )

    val uriBottomRow: List<KeyModel> = listOf(
        KeyModel(KeyType.SymbolToggle, weight = 1.2f, isAccent = true),
        KeyModel(KeyType.EmojiToggle, weight = 0.9f, isAccent = true),
        KeyModel(KeyType.Character("/", listOf("://", "www.", "https://")), weight = 1.15f),
        KeyModel(KeyType.Space, weight = 3.2f),
        KeyModel(KeyType.Character(".", listOf(".com", ".org", "-", "_")), weight = 1.1f),
        KeyModel(KeyType.Enter, weight = 1.4f, isAccent = true)
    )

    fun qwertyBottomRowFor(kind: FieldInputKind): List<KeyModel> = when (kind) {
        FieldInputKind.EMAIL -> emailBottomRow
        FieldInputKind.URI -> uriBottomRow
        else -> qwertyBottomRow
    }

    // Symbols Page 1
    val symbolsRow1: List<KeyModel> = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map {
        KeyModel(KeyType.Character(it))
    }

    val symbolsRow2: List<KeyModel> = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/").map {
        KeyModel(KeyType.Character(it))
    }

    val symbolsRow3: List<KeyModel> = listOf(
        KeyModel(KeyType.SymbolMoreToggle, weight = 1.35f, isAccent = true),
        KeyModel(KeyType.Character("*")),
        KeyModel(KeyType.Character("\"")),
        KeyModel(KeyType.Character("'")),
        KeyModel(KeyType.Character(":")),
        KeyModel(KeyType.Character(";")),
        KeyModel(KeyType.Character("!")),
        KeyModel(KeyType.Character("?")),
        KeyModel(KeyType.Backspace, weight = 1.35f, isAccent = true)
    )

    val symbolsBottomRow: List<KeyModel> = listOf(
        KeyModel(KeyType.AlphabetToggle, weight = 1.2f, isAccent = true),
        KeyModel(KeyType.NumpadToggle, weight = 1.1f, isAccent = true),
        KeyModel(KeyType.EmojiToggle, weight = 0.9f, isAccent = true),
        KeyModel(KeyType.Space, weight = 3.6f),
        KeyModel(KeyType.Character("."), weight = 0.8f),
        KeyModel(KeyType.Enter, weight = 1.4f, isAccent = true)
    )

    // Symbols Page 2 (More symbols)
    val moreSymbolsRow1: List<KeyModel> = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆").map {
        KeyModel(KeyType.Character(it))
    }

    val moreSymbolsRow2: List<KeyModel> = listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\").map {
        KeyModel(KeyType.Character(it))
    }

    val moreSymbolsRow3: List<KeyModel> = listOf(
        KeyModel(KeyType.SymbolToggle, weight = 1.35f, isAccent = true),
        KeyModel(KeyType.Character("%")),
        KeyModel(KeyType.Character("©")),
        KeyModel(KeyType.Character("®")),
        KeyModel(KeyType.Character("™")),
        KeyModel(KeyType.Character("✓")),
        KeyModel(KeyType.Character("[")),
        KeyModel(KeyType.Character("]")),
        KeyModel(KeyType.Backspace, weight = 1.35f, isAccent = true)
    )

    val moreSymbolsBottomRow: List<KeyModel> = listOf(
        KeyModel(KeyType.AlphabetToggle, weight = 1.2f, isAccent = true),
        KeyModel(KeyType.NumpadToggle, weight = 1.1f, isAccent = true),
        KeyModel(KeyType.Character("<"), weight = 0.9f),
        KeyModel(KeyType.Space, weight = 3.2f),
        KeyModel(KeyType.Character(">"), weight = 0.9f),
        KeyModel(KeyType.Enter, weight = 1.4f, isAccent = true)
    )

    // Phone-style 4×4 pad: digits stay on a 3-column grid so 0 sits under 8,
    // with a right-hand action column for backspace, operators, and enter.
    val numpadRow1: List<KeyModel> = listOf(
        KeyModel(KeyType.Character("1")),
        KeyModel(KeyType.Character("2")),
        KeyModel(KeyType.Character("3")),
        KeyModel(KeyType.Backspace, isAccent = true)
    )

    val numpadRow2: List<KeyModel> = listOf(
        KeyModel(KeyType.Character("4")),
        KeyModel(KeyType.Character("5")),
        KeyModel(KeyType.Character("6")),
        KeyModel(KeyType.Character("-", listOf("+", "/", "×", "÷", "(", ")")), isAccent = true)
    )

    val numpadRow3: List<KeyModel> = listOf(
        KeyModel(KeyType.Character("7")),
        KeyModel(KeyType.Character("8")),
        KeyModel(KeyType.Character("9")),
        KeyModel(KeyType.Character(".", listOf("%", "=", ":", ";")), isAccent = true)
    )

    val numpadBottomRow: List<KeyModel> = listOf(
        KeyModel(KeyType.AlphabetToggle, isAccent = true),
        KeyModel(KeyType.Character("0", listOf("+", "*", "#"))),
        KeyModel(KeyType.Character(",", listOf("'", "\"", ";", ":"))),
        KeyModel(KeyType.Enter, isAccent = true)
    )

    val phoneRow1: List<KeyModel> = listOf(
        KeyModel(KeyType.Character("1")),
        KeyModel(KeyType.Character("2")),
        KeyModel(KeyType.Character("3")),
        KeyModel(KeyType.Backspace, isAccent = true)
    )
    val phoneRow2: List<KeyModel> = listOf(
        KeyModel(KeyType.Character("4")),
        KeyModel(KeyType.Character("5")),
        KeyModel(KeyType.Character("6")),
        KeyModel(KeyType.Character("+", listOf("-")), isAccent = true)
    )
    val phoneRow3: List<KeyModel> = listOf(
        KeyModel(KeyType.Character("7")),
        KeyModel(KeyType.Character("8")),
        KeyModel(KeyType.Character("9")),
        KeyModel(KeyType.Character("*"), isAccent = true)
    )
    val phoneBottomRow: List<KeyModel> = listOf(
        KeyModel(KeyType.AlphabetToggle, isAccent = true),
        KeyModel(KeyType.Character("0", listOf("+"))),
        KeyModel(KeyType.Character("#")),
        KeyModel(KeyType.Enter, isAccent = true)
    )

    val datetimeRow1: List<KeyModel> = listOf(
        KeyModel(KeyType.Character("1")),
        KeyModel(KeyType.Character("2")),
        KeyModel(KeyType.Character("3")),
        KeyModel(KeyType.Backspace, isAccent = true)
    )
    val datetimeRow2: List<KeyModel> = listOf(
        KeyModel(KeyType.Character("4")),
        KeyModel(KeyType.Character("5")),
        KeyModel(KeyType.Character("6")),
        KeyModel(KeyType.Character("/", listOf("-")), isAccent = true)
    )
    val datetimeRow3: List<KeyModel> = listOf(
        KeyModel(KeyType.Character("7")),
        KeyModel(KeyType.Character("8")),
        KeyModel(KeyType.Character("9")),
        KeyModel(KeyType.Character(":", listOf(".")), isAccent = true)
    )
    val datetimeBottomRow: List<KeyModel> = listOf(
        KeyModel(KeyType.AlphabetToggle, isAccent = true),
        KeyModel(KeyType.Character("0")),
        KeyModel(KeyType.Space),
        KeyModel(KeyType.Enter, isAccent = true)
    )

    fun numpadRowsFor(kind: FieldInputKind): List<List<KeyModel>> = when (kind) {
        FieldInputKind.PHONE -> listOf(phoneRow1, phoneRow2, phoneRow3, phoneBottomRow)
        FieldInputKind.DATETIME -> listOf(datetimeRow1, datetimeRow2, datetimeRow3, datetimeBottomRow)
        else -> listOf(numpadRow1, numpadRow2, numpadRow3, numpadBottomRow)
    }
}

data class EmojiCategory(
    val name: String,
    val icon: String,
    val emojis: List<String>
)

object EmojiData {
    val categories = listOf(
        EmojiCategory(
            name = "Smileys & Emotion",
            icon = "😀",
            emojis = listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "🥹",
                "☺️", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘",
                "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐",
                "🤓", "😎", "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟",
                "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭",
                "😮‍💨", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱",
                "😨", "😰", "😥", "😓", "🫣", "🤗", "🫡", "🤔", "🫢", "🤫",
                "🫠", "🤥", "😶", "😶‍🌫️", "😐", "😑", "😬", "🫨", "🙄", "😯",
                "🥱", "😴", "🤤", "😪", "😵", "😵‍💫", "🤐", "🥴", "🤢", "🤮",
                "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👹", "👺",
                "💀", "☠️", "👻", "👽", "👾", "🤖", "💩", "🎃", "😺", "😸"
            )
        ),
        EmojiCategory(
            name = "People & Gestures",
            icon = "👋",
            emojis = listOf(
                "👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "🫳", "🫴", "🫷",
                "🫸", "👌", "🤌", "🤏", "✌️", "🤞", "🫰", "🤟", "🤘", "🤙",
                "👈", "👉", "👆", "🖕", "👇", "☝️", "🫵", "👍", "👎", "✊",
                "👊", "🤛", "🤜", "👏", "🙌", "🫶", "👐", "🤲", "🤝", "🙏",
                "✍️", "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻",
                "👃", "🧠", "🫀", "🫁", "🦷", "🦴", "👀", "👁️", "👅", "👄",
                "🫦", "👶", "🧒", "👦", "👧", "🧑", "👱", "👨", "🧔", "👩",
                "🧓", "👴", "👵", "👲", "👳", "🧕", "👮", "👷", "💂", "🕵️"
            )
        ),
        EmojiCategory(
            name = "Animals & Nature",
            icon = "🐶",
            emojis = listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨",
                "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🐤",
                "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪱",
                "🐛", "🦋", "🐌", "🐞", "🐜", "🪰", "🪲", "🪳", "🪴", "🌲",
                "🌳", "🌴", "🌵", "🌾", "🌿", "☘️", "🍀", "🍁", "🍂", "🍃",
                "🍄", "🌰", "🦀", "🦞", "🦐", "🦑", "🐙", "🦩", "🦚", "🦜",
                "🦢", "🦤", "🦭", "🐬", "🐳", "🐋", "🦈", "🐊", "🐅", "🐆",
                "🦓", "🦍", "🦧", "🦣", "🐘", "🦛", "🦏", "🐪", "🐫", "🦒"
            )
        ),
        EmojiCategory(
            name = "Food & Drink",
            icon = "🍕",
            emojis = listOf(
                "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈",
                "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🥑", "🥦", "🥬",
                "🥒", "🌶️", "🫑", "🌽", "🥕", "🧄", "🧅", "🥔", "🍠", "🥐",
                "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞", "🧇", "🥓",
                "🥩", "🍗", "🍖", "🌭", "🍔", "🍟", "🍕", "🥪", "🥙", "🌮",
                "🌯", "🫔", "🥗", "🥘", "🍝", "🍜", "🍲", "🍛", "🍣", "🍱",
                "🥟", "🍤", "🍙", "🍚", "🍦", "🍧", "🍨", "🍩", "🍪", "🎂",
                "🍰", "🧁", "🍫", "🍿", "☕", "🍵", "🧋", "🥤", "🍺", "🍻",
                "🥂", "🍷", "🍸", "🍹", "🍾"
            )
        ),
        EmojiCategory(
            name = "Activities & Sports",
            icon = "⚽",
            emojis = listOf(
                "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱",
                "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🥊", "🥋", "🥅", "⛳",
                "🏹", "🎣", "🤿", "🎽", "🛹", "🛼", "🛷", "⛸️", "🎿", "🏂",
                "🏋️", "🤼", "🤸", "⛹️", "🤺", "🤾", "🏌️", "🏇", "🧘", "🏄",
                "🏊", "🤽", "🚣", "🧗", "🚵", "🚴", "🏆", "🥇", "🥈", "🥉",
                "🏅", "🎖️", "🎫", "🎟️", "🎪", "🤹", "🎭", "🎨", "🎬", "🎤",
                "🎧", "🎼", "🎹", "🥁", "🎷", "🎺", "🎸", "🎻", "🎲", "🎮"
            )
        ),
        EmojiCategory(
            name = "Travel & Places",
            icon = "🚀",
            emojis = listOf(
                "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐",
                "🛻", "🚚", "🚛", "🚜", "🛴", "🚲", "🛵", "🏍️", "🛺", "🚨",
                "🚠", "🚟", "🚃", "🚄", "🚅", "🚆", "🚇", "🚉", "✈️", "🛫",
                "🛬", "🛩️", "🚀", "🛸", "🚁", "⛵", "🚤", "🛥️", "🛳️", "🚢",
                "⚓", "🚧", "🚦", "🚥", "🗺️", "🗿", "🗽", "🗼", "🏰", "🏟️",
                "🏖️", "🏝️", "🏜️", "🌋", "⛰️", "🏔️", "🏕️", "⛺", "🏠", "🏡",
                "🏢", "🏥", "🏦", "🏨", "🏪", "🏫", "🏭", "🏛️", "⛪", "🕌",
                "🏙️", "🌆", "🌇", "🌃", "🌉"
            )
        ),
        EmojiCategory(
            name = "Objects & Tools",
            icon = "💡",
            emojis = listOf(
                "💡", "🔦", "🏮", "📱", "📲", "💻", "⌨️", "🖥️", "🖨️", "🖱️",
                "🕹️", "💾", "💿", "📀", "📷", "📸", "📹", "🎥", "📽️", "📞",
                "☎️", "📺", "📻", "🎙️", "⏱️", "⏰", "🕰️", "⌛", "⏳", "📡",
                "🔋", "🔌", "💸", "💵", "💶", "💷", "🪙", "💰", "💳", "💎",
                "⚖️", "🧰", "🔧", "🔨", "🛠️", "🪓", "🔩", "⚙️", "🔑", "🗝️",
                "🔒", "🔓", "🚪", "📦", "📫", "📬", "📮", "✉️", "📨", "📩",
                "📊", "📈", "📉", "📅", "📆", "📋", "📁", "📂", "📖", "📚",
                "📌", "📍", "✂️", "🖊️", "✏️", "🔍", "🔎", "🔬", "🔭", "💊"
            )
        ),
        EmojiCategory(
            name = "Symbols & Hearts",
            icon = "❤️",
            emojis = listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
                "❤️‍🔥", "❤️‍🩹", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝",
                "💟", "💌", "💋", "💯", "💢", "💥", "💫", "✨", "🌟", "⭐",
                "🔥", "⚡", "☀️", "🌤️", "⛅", "☁️", "🌧️", "⛈️", "❄️", "☃️",
                "💨", "💧", "💦", "🫧", "☂️", "🌊", "💤", "☮️", "✝️", "☪️",
                "🕉️", "☸️", "✡️", "☯️", "♈", "♉", "♊", "♋", "♌", "♍",
                "♎", "♏", "♐", "♑", "♒", "♓", "🛑", "⛔", "🚫", "⚠️",
                "✅", "❌", "❓", "❗", "‼️", "⁉️", "▶️", "⏸️", "🔄", "🔁",
                "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "⚫", "⚪", "🟥", "🟦"
            )
        ),
        EmojiCategory(
            name = "Flags",
            icon = "🏳️",
            emojis = listOf(
                "🏳️", "🏴", "🏁", "🚩", "🏳️‍🌈", "🏳️‍⚧️", "🏴‍☠️", "🇺🇸", "🇬🇧", "🇨🇦",
                "🇦🇺", "🇩🇪", "🇫🇷", "🇮🇹", "🇪🇸", "🇯🇵", "🇰🇷", "🇨🇳", "🇮🇳", "🇧🇷",
                "🇲🇽", "🇷🇺", "🇿🇦", "🇳🇬", "🇪🇬", "🇸🇦", "🇦🇪", "🇹🇷", "🇮🇩", "🇵🇰",
                "🇧🇩", "🇵🇭", "🇻🇳", "🇹🇭", "🇲🇾", "🇳🇿", "🇸🇬", "🇮🇪", "🇳🇱", "🇸🇪",
                "🇳🇴", "🇩🇰", "🇫🇮", "🇵🇱", "🇺🇦", "🇬🇷", "🇵🇹", "🇨🇭", "🇦🇹", "🇧🇪"
            )
        )
    )
}
