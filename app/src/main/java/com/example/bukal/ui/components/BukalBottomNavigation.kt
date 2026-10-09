package com.example.bukal.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History as FilledHistory
import androidx.compose.material.icons.filled.Home as FilledHome
import androidx.compose.material.icons.filled.Person as FilledPerson
import androidx.compose.material.icons.outlined.History as OutlinedHistory
import androidx.compose.material.icons.outlined.Home as OutlinedHome
import androidx.compose.material.icons.outlined.Person as OutlinedPerson
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bukal.R
import com.example.bukal.ui.theme.BukalTheme
import com.example.bukal.ui.theme.BukalMutedText
import com.example.bukal.ui.theme.BukalOutline
import com.example.bukal.ui.theme.BukalPrimary
import com.example.bukal.ui.theme.BukalPrimaryContainer
import com.example.bukal.ui.theme.BukalSurface

enum class MainDestination {
    HOME,
    HISTORY,
    PROFILE,
}

private data class NavigationItem(
    val destination: MainDestination,
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val mainNavigationItems = listOf(
    NavigationItem(
        destination = MainDestination.HOME,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.FilledHome,
        unselectedIcon = Icons.Outlined.OutlinedHome,
    ),
    NavigationItem(
        destination = MainDestination.HISTORY,
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Filled.FilledHistory,
        unselectedIcon = Icons.Outlined.OutlinedHistory,
    ),
    NavigationItem(
        destination = MainDestination.PROFILE,
        labelRes = R.string.nav_profile,
        selectedIcon = Icons.Filled.FilledPerson,
        unselectedIcon = Icons.Outlined.OutlinedPerson,
    ),
)

@Composable
fun BukalBottomNavigation(
    selectedDestination: MainDestination,
    onDestinationSelected: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalDivider(color = BukalOutline)
        NavigationBar(
            modifier = Modifier.height(64.dp),
            containerColor = BukalSurface,
            tonalElevation = 0.dp,
        ) {
            mainNavigationItems.forEach { item ->
                val selected = item.destination == selectedDestination
                NavigationBarItem(
                    selected = selected,
                    onClick = { onDestinationSelected(item.destination) },
                    icon = {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(item.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BukalPrimary,
                        selectedTextColor = BukalPrimary,
                        indicatorColor = BukalPrimaryContainer,
                        unselectedIconColor = BukalMutedText,
                        unselectedTextColor = BukalMutedText,
                    ),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BukalBottomNavigationPreview() {
    BukalTheme {
        BukalBottomNavigation(
            selectedDestination = MainDestination.HOME,
            onDestinationSelected = {},
        )
    }
}
