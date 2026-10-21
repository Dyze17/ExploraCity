package co.edu.uniquindio.exploracity.core.util

import java.util.Locale

/** Los formatos de values-es/ y values/ (strings.xml), para probar sin Android. */
internal val spanishFormats = LocaleFormats(Locale.forLanguageTag("es-CO"), "d 'de' MMMM", "d 'de' MMMM 'de' yyyy", "MMMM 'de' yyyy", "H:mm")
internal val englishFormats = LocaleFormats(Locale.forLanguageTag("en-US"), "MMMM d", "MMMM d, yyyy", "MMMM yyyy", "h:mm a")
