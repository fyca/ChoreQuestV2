package com.lostsierra.chorequest.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom navigation item data class
 */
data class NavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

/**
 * Parent dashboard navigation items
 */
val parentNavItems = listOf(
    NavItem("Dashboard", Icons.Default.Home, "parent_dashboard"),
    NavItem("Chores", Icons.Default.List, "chore_list"),
    NavItem("Rewards", Icons.Default.CardGiftcard, "reward_list"),
    NavItem("Family", Icons.Default.People, "user_list"),
    NavItem("Settings", Icons.Default.Settings, "settings")
)

/**
 * Child dashboard navigation items
 */
val childNavItems = listOf(
    NavItem("Home", Icons.Default.Home, "child_dashboard"),
    NavItem("My Chores", Icons.Default.CheckCircle, "my_chores"),
    NavItem("Rewards", Icons.Default.Redeem, "rewards_marketplace"),
    NavItem("Games", Icons.Default.Games, "games"),
    NavItem("Profile", Icons.Default.Person, "profile")
)

/**
 * Reusable bottom navigation bar
 */
@Composable
fun ChoreQuestBottomNavigationBar(
    items: List<NavItem>,
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
