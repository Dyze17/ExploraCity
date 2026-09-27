package co.edu.uniquindio.exploracity.ui.screens.legal

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.navigation.LegalTab
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

/**
 * 4A · Política de tratamiento de datos y aviso de privacidad (Ley 1581 de 2012), en dos pestañas. Se abre desde el
 * registro (4) y desde Ajustes (29) en la pestaña que se tocó; «Entendido» vuelve atrás. Los textos son de ejemplo y
 * requieren aprobación jurídica (README).
 */
@Composable
fun LegalDocumentsScreen(initialTab: LegalTab, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ExploraTopAppBar(title = stringResource(R.string.legal_title), onBack = onBack)
        Tabs(tab, onSelect = { tab = it }, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        // Cada pestaña empieza arriba: no hereda el desplazamiento de la otra.
        key(tab) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    stringResource(R.string.legal_version),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (tab) {
                    LegalTab.POLICY -> PolicyContent()
                    LegalTab.PRIVACY_NOTICE -> PrivacyNoticeContent()
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.exploraColors.divider))
            ExploraButton(
                stringResource(R.string.legal_understood),
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            )
        }
    }
}

/** «Política | Aviso de privacidad»: pestañas del lienzo, la activa como una píldora clara sobre el contenedor. */
@Composable
private fun Tabs(selected: LegalTab, onSelect: (LegalTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TabItem(stringResource(R.string.legal_tab_policy), selected == LegalTab.POLICY, { onSelect(LegalTab.POLICY) }, Modifier.weight(1f))
        TabItem(stringResource(R.string.legal_tab_notice), selected == LegalTab.PRIVACY_NOTICE, { onSelect(LegalTab.PRIVACY_NOTICE) }, Modifier.weight(1f))
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .then(if (selected) Modifier.shadow(1.dp, shape, ambientColor = MaterialTheme.exploraColors.shadow, spotColor = MaterialTheme.exploraColors.shadow) else Modifier)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = if (selected) FontWeight.W700 else FontWeight.W600),
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.exploraColors.iconSecondary,
        )
    }
}

@Composable
private fun PolicyContent() {
    Block(stringResource(R.string.legal_who_title), stringResource(R.string.legal_who_body))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BlockTitle(stringResource(R.string.legal_what_title))
        DataItem(R.drawable.ic_mail, stringResource(R.string.legal_what_name))
        DataItem(R.drawable.ic_my_location, stringResource(R.string.legal_what_location))
        DataItem(R.drawable.ic_photo_camera, stringResource(R.string.legal_what_photos))
    }
    Block(stringResource(R.string.legal_purpose_title), stringResource(R.string.legal_purpose_body))
    Block(stringResource(R.string.legal_rights_title), stringResource(R.string.legal_rights_body))
}

@Composable
private fun PrivacyNoticeContent() {
    Block(stringResource(R.string.legal_notice_title), stringResource(R.string.legal_notice_body))
    Block(stringResource(R.string.legal_exercise_title), stringResource(R.string.legal_exercise_body))
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_info), null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp.scaledWithFont()))
        Text(
            stringResource(R.string.legal_notice_hint),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Block(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BlockTitle(title)
        Text(body, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp), color = MaterialTheme.exploraColors.textSecondary)
    }
}

@Composable
private fun BlockTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontFamily = Outfit, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.W600),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

/** Cada dato que se trata, con su icono en una pastilla del color de acento. */
@Composable
private fun DataItem(@DrawableRes icon: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp.scaledWithFont()).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = MaterialTheme.exploraColors.onSurfaceAccent, modifier = Modifier.size(18.dp.scaledWithFont()))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.exploraColors.textSecondary, modifier = Modifier.weight(1f))
    }
}

@Preview(name = "4A · política · claro", widthDp = 360, heightDp = 800)
@Composable
private fun PolicyPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { LegalDocumentsScreen(LegalTab.POLICY, onBack = {}) }
}

@Preview(name = "4A · aviso · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun NoticePreview() {
    ExploraCityTheme(ThemeMode.DARK) { LegalDocumentsScreen(LegalTab.PRIVACY_NOTICE, onBack = {}) }
}

@Preview(name = "4A · fuente 200 %", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun LargeFontPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { LegalDocumentsScreen(LegalTab.POLICY, onBack = {}) }
}
