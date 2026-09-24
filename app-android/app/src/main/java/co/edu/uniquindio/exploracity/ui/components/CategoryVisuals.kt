package co.edu.uniquindio.exploracity.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.ui.theme.CategoryColors
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

// Icono, nombre y color de cada categoría: estables en lista, mapa, formulario y filtros.

@get:DrawableRes
val Category.iconRes: Int
    get() = when (this) {
        Category.GASTRONOMY -> R.drawable.ic_restaurant
        Category.CULTURE -> R.drawable.ic_palette
        Category.NATURE -> R.drawable.ic_forest
        Category.ENTERTAINMENT -> R.drawable.ic_celebration
        Category.HISTORY -> R.drawable.ic_account_balance
    }

@get:StringRes
val Category.labelRes: Int
    get() = when (this) {
        Category.GASTRONOMY -> R.string.category_gastronomy
        Category.CULTURE -> R.string.category_culture
        Category.NATURE -> R.string.category_nature
        Category.ENTERTAINMENT -> R.string.category_entertainment
        Category.HISTORY -> R.string.category_history
    }

val Category.colors: CategoryColors
    @Composable
    @ReadOnlyComposable
    get() {
        val palette = MaterialTheme.exploraColors.category
        return when (this) {
            Category.GASTRONOMY -> palette.gastronomy
            Category.CULTURE -> palette.culture
            Category.NATURE -> palette.nature
            Category.ENTERTAINMENT -> palette.entertainment
            Category.HISTORY -> palette.history
        }
    }
