/*
 * SPDX-FileCopyrightText: 2026 Tanish Ranjan <tanishranjan4@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.screen.about

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.ui.about.AboutData
import org.kde.kdeconnect.ui.about.AboutPerson
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.components.KdeThemePreviews
import org.kde.kdeconnect.ui.components.card
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect_tp.R

@Composable
fun AboutScreen(
    aboutData: AboutData,
    onReportBugClicked: () -> Unit,
    onDonateClicked: () -> Unit,
    onSourceCodeClicked: () -> Unit,
    onLicensesClicked: () -> Unit,
    onWebsiteClicked: () -> Unit,
    navigator: Navigator,
) {
    HazeScaffold(
        title = stringResource(id = R.string.about),
        scrollState = null,
        backAction = BackAction.Normal(navigator)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = paddingValues
        ) {
            item {
                AppInfoCard(
                    aboutData = aboutData,
                )
            }

            item {
                ActionButtons(
                    onReportBugClicked = onReportBugClicked,
                    onDonateClicked = onDonateClicked,
                    onSourceCodeClicked = onSourceCodeClicked,
                    onLicensesClicked = onLicensesClicked,
                    onWebsiteClicked = onWebsiteClicked
                )
            }

            item {
                AuthorsCard(aboutData = aboutData)
            }
        }
    }
}

@Composable
private fun AppInfoCard(
    aboutData: AboutData,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .card()
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Image(
            painter = painterResource(id = aboutData.icon),
            contentDescription = null,
            modifier = Modifier.size(52.dp)
        )

        Column(
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            Text(
                text = stringResource(aboutData.name),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(id = R.string.version, aboutData.versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionButtons(
    onReportBugClicked: () -> Unit,
    onDonateClicked: () -> Unit,
    onSourceCodeClicked: () -> Unit,
    onLicensesClicked: () -> Unit,
    onWebsiteClicked: () -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .card()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        maxItemsInEachRow = 3
    ) {
        ActionIconTextButton(
            textRes = R.string.report_bug,
            iconRes = R.drawable.bug_report,
            onClick = onReportBugClicked
        )
        ActionIconTextButton(
            textRes = R.string.donate,
            iconRes = R.drawable.attach_money,
            onClick = onDonateClicked
        )
        ActionIconTextButton(
            textRes = R.string.source_code,
            iconRes = R.drawable.code,
            onClick = onSourceCodeClicked
        )
        ActionIconTextButton(
            textRes = R.string.licenses,
            iconRes = R.drawable.gavel,
            onClick = onLicensesClicked
        )
        ActionIconTextButton(
            textRes = R.string.website,
            iconRes = R.drawable.web,
            onClick = onWebsiteClicked
        )
    }
}

@Composable
private fun ActionIconTextButton(
    @StringRes textRes: Int,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .size(84.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = textRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AuthorsCard(aboutData: AboutData) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CategoryTitleTextSmall(stringResource(id = R.string.redesign))
        Column(
            modifier = Modifier
                .card()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            AuthorItemRow(
                author = AboutPerson(
                    "Vytautas Butenas",
                    task = R.string.ui_design_implementation_backend_refactoring
                )
            )
        }

        CategoryTitleTextSmall(stringResource(id = R.string.authors))
        Column(
            modifier = Modifier
                .card()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            aboutData.authors.forEach { author ->
                AuthorItemRow(author = author)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(aboutData.authorsFooterText),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun AuthorItemRow(author: AboutPerson) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = author.name,
            style = MaterialTheme.typography.bodyLarge
        )
        if (author.task != null) {
            Text(
                text = stringResource(id = author.task),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@KdeThemePreviews
@Composable
private fun AboutScreenPreview() {
    Surface {
        AboutScreen(
            aboutData = AboutData(),
            onReportBugClicked = {},
            onDonateClicked = {},
            onSourceCodeClicked = {},
            onLicensesClicked = {},
            onWebsiteClicked = {},
            navigator = Navigator()
        )
    }
}
