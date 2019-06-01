package cfig

import de.vandermeer.asciitable.AsciiTable

object InfoTable {
    val instance = AsciiTable()
    val missingParts = mutableListOf<String>()
}