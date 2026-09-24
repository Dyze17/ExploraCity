package co.edu.uniquindio.exploracity.ui.components

import androidx.annotation.StringRes
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.PriceRange

// Rango de precio del paso 4 (18): «$ · Hasta 25.000». El lienzo 13.a dibuja «$ 10.000 – 25.000», un rango que el
// paso 4 no captura; se muestran los rangos que sí existen.

/** «$», «$$», «$$$»; null con entrada libre (se dice con palabras). */
val PriceRange.symbol: String?
    get() = when (this) {
        PriceRange.FREE -> null
        PriceRange.LOW -> "$"
        PriceRange.MEDIUM -> "$$"
        PriceRange.HIGH -> "$$$"
    }

/** «Hasta 25.000», «Entrada libre»… */
@get:StringRes
val PriceRange.labelRes: Int
    get() = when (this) {
        PriceRange.FREE -> R.string.price_free
        PriceRange.LOW -> R.string.price_low
        PriceRange.MEDIUM -> R.string.price_medium
        PriceRange.HIGH -> R.string.price_high
    }

/** Lo que oye el lector en lugar de los símbolos: «hasta 25.000 pesos». */
@get:StringRes
val PriceRange.spokenRes: Int
    get() = when (this) {
        PriceRange.FREE -> R.string.price_free_spoken
        PriceRange.LOW -> R.string.price_low_spoken
        PriceRange.MEDIUM -> R.string.price_medium_spoken
        PriceRange.HIGH -> R.string.price_high_spoken
    }
