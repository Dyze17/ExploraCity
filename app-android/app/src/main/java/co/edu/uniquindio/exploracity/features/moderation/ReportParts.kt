package co.edu.uniquindio.exploracity.features.moderation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.core.ui.components.labelRes
import co.edu.uniquindio.exploracity.core.ui.components.relativeTimeText
import co.edu.uniquindio.exploracity.core.ui.components.rememberNow
import co.edu.uniquindio.exploracity.core.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.core.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.domain.model.PlaceReport

/**
 * v1.4.0 · Los reportes de un lugar, del más reciente al más antiguo: el motivo, lo que escribió quien reportó (si
 * escribió) y cuándo. Nunca quién: el reporte es anónimo. El lector oye cada uno de una vez.
 */
@Composable
internal fun ReportLines(reports: List<PlaceReport>, modifier: Modifier = Modifier) {
    val now by rememberNow()
    val explora = MaterialTheme.exploraColors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        reports.forEach { report ->
            val reason = stringResource(report.reason.labelRes)
            val time = relativeTimeText(report.createdAt, now)
            val spoken = listOfNotNull(stringResource(R.string.reported_report_description, reason, time), report.detail).joinToString(". ")
            Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(reason, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W600), color = MaterialTheme.colorScheme.onSurface)
                report.detail?.let { detail ->
                    Text("«$detail»", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp), color = MaterialTheme.colorScheme.onSurface)
                }
                Text(time, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = explora.textSecondary)
            }
        }
    }
}

/**
 * 33.b · La publicación volvió a pendiente (o la editaron) con reportes abiertos: cuántas personas, qué dijeron y que
 * se cierran al decidir. En tono de error y con la bandera, como lo dibuja el diseño.
 */
@Composable
internal fun ReportsCard(reports: List<PlaceReport>) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(scheme.errorContainer.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
            .border(1.dp, scheme.error, MaterialTheme.shapes.medium)
            .padding(14.dp)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_flag), null, tint = scheme.error, modifier = Modifier.size(20.dp.scaledWithFont()))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                pluralStringResource(R.plurals.review_reports_title, reports.size, reports.size),
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
            )
            ReportLines(reports)
            Text(
                stringResource(R.string.review_reports_hint),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = MaterialTheme.exploraColors.textSecondary,
            )
        }
    }
}
