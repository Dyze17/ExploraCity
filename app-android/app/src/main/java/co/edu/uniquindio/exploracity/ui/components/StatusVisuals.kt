package co.edu.uniquindio.exploracity.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.ui.theme.ContainerColors
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

// El estado siempre se comunica con color + icono + texto, nunca solo con color.

@get:DrawableRes
val PublicationStatus.iconRes: Int
    get() = when (this) {
        PublicationStatus.PENDING -> R.drawable.ic_schedule
        PublicationStatus.VERIFIED -> R.drawable.ic_verified
        PublicationStatus.REJECTED -> R.drawable.ic_cancel
        PublicationStatus.FINALIZED -> R.drawable.ic_task_alt
    }

@get:StringRes
val PublicationStatus.labelRes: Int
    get() = when (this) {
        PublicationStatus.PENDING -> R.string.status_pending
        PublicationStatus.VERIFIED -> R.string.status_verified
        PublicationStatus.REJECTED -> R.string.status_rejected
        PublicationStatus.FINALIZED -> R.string.status_finalized
    }

val PublicationStatus.colors: ContainerColors
    @Composable
    @ReadOnlyComposable
    get() {
        val status = MaterialTheme.exploraColors.status
        return when (this) {
            PublicationStatus.PENDING -> status.pending
            PublicationStatus.VERIFIED -> status.verified
            PublicationStatus.REJECTED -> status.rejected
            PublicationStatus.FINALIZED -> status.finalized
        }
    }
