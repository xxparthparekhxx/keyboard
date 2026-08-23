package com.example.composekeyboard.data

enum class KeyboardMode {
    LOWERCASE,
    UPPERCASE,
    CAPS_LOCKED,
    SYMBOLS,
    SYMBOLS_MORE,
    EMOJI,
    CLIPBOARD,
    THEMES,
    SETTINGS
}

sealed class KeyType {
    data class Character(val primary: String, val popup: List<String> = emptyList()) : KeyType()
    object Shift : KeyType()
    object Backspace : KeyType()
    object SymbolToggle : KeyType()
    object SymbolMoreToggle : KeyType()
    object AlphabetToggle : KeyType()
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

    val qwertyRow1: List<KeyModel> = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").map {
        KeyModel(KeyType.Character(it))
    }

    val qwertyRow2: List<KeyModel> = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l").map {
        KeyModel(KeyType.Character(it))
    }

    val qwertyRow3: List<KeyModel> = listOf(
        KeyModel(KeyType.Shift, weight = 1.35f, isAccent = true),
        KeyModel(KeyType.Character("z")),
        KeyModel(KeyType.Character("x")),
        KeyModel(KeyType.Character("c")),
        KeyModel(KeyType.Character("v")),
        KeyModel(KeyType.Character("b")),
        KeyModel(KeyType.Character("n")),
        KeyModel(KeyType.Character("m")),
        KeyModel(KeyType.Backspace, weight = 1.35f, isAccent = true)
    )

    val qwertyBottomRow: List<KeyModel> = listOf(
        KeyModel(KeyType.SymbolToggle, weight = 1.3f, isAccent = true),
        KeyModel(KeyType.EmojiToggle, weight = 1.0f, isAccent = true),
        KeyModel(KeyType.Character(","), weight = 0.9f),
        KeyModel(KeyType.Space, weight = 4.0f),
        KeyModel(KeyType.Character("."), weight = 0.9f),
        KeyModel(KeyType.Enter, weight = 1.5f, isAccent = true)
    )

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
        KeyModel(KeyType.AlphabetToggle, weight = 1.3f, isAccent = true),
        KeyModel(KeyType.EmojiToggle, weight = 1.0f, isAccent = true),
        KeyModel(KeyType.Character(","), weight = 0.9f),
        KeyModel(KeyType.Space, weight = 4.0f),
        KeyModel(KeyType.Character("."), weight = 0.9f),
        KeyModel(KeyType.Enter, weight = 1.5f, isAccent = true)
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
        KeyModel(KeyType.AlphabetToggle, weight = 1.3f, isAccent = true),
        KeyModel(KeyType.Character("<"), weight = 1.0f),
        KeyModel(KeyType.Character(">"), weight = 1.0f),
        KeyModel(KeyType.Space, weight = 3.5f),
        KeyModel(KeyType.Character("="), weight = 1.0f),
        KeyModel(KeyType.Enter, weight = 1.5f, isAccent = true)
    )
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
