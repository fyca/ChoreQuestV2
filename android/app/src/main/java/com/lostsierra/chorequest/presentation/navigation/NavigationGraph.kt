package com.lostsierra.chorequest.presentation.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lostsierra.chorequest.presentation.auth.LoginScreen
import com.lostsierra.chorequest.presentation.auth.QRScannerScreen
import com.lostsierra.chorequest.presentation.dashboard.ParentDashboardScreen
import com.lostsierra.chorequest.presentation.dashboard.ChildDashboardScreen
import com.lostsierra.chorequest.presentation.chores.*
import com.lostsierra.chorequest.presentation.chores.RecurringChoreEditorScreen
import com.lostsierra.chorequest.presentation.rewards.*
import com.lostsierra.chorequest.presentation.users.*
import com.lostsierra.chorequest.presentation.activity.ActivityLogScreen
import com.lostsierra.chorequest.presentation.settings.SettingsScreen
import com.lostsierra.chorequest.presentation.profile.ProfileScreen
import com.lostsierra.chorequest.presentation.games.GamesScreen
import com.lostsierra.chorequest.presentation.games.TicTacToeScreen
import com.lostsierra.chorequest.presentation.games.ChoreQuizScreen
import com.lostsierra.chorequest.presentation.games.MemoryMatchScreen
import com.lostsierra.chorequest.presentation.games.RockPaperScissorsScreen
import com.lostsierra.chorequest.presentation.games.JigsawPuzzleScreen
import com.lostsierra.chorequest.presentation.games.SnakeGameScreen
import com.lostsierra.chorequest.presentation.games.BreakoutGameScreen
import com.lostsierra.chorequest.presentation.games.MathGameScreen
import com.lostsierra.chorequest.data.local.SessionManager
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Main navigation graph for the entire app
 */
@Composable
fun NavigationGraph(
    navController: NavHostController,
    startDestination: String = NavigationRoutes.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Authentication
        composable(NavigationRoutes.Login.route) {
            LoginScreen(
                onNavigateToParentDashboard = {
                    navController.navigate(NavigationRoutes.ParentDashboard.route) {
                        popUpTo(NavigationRoutes.Login.route) { inclusive = true }
                    }
                },
                onNavigateToChildDashboard = {
                    navController.navigate(NavigationRoutes.ChildDashboard.route) {
                        popUpTo(NavigationRoutes.Login.route) { inclusive = true }
                    }
                },
                onNavigateToQRScanner = {
                    navController.navigate(NavigationRoutes.QRScanner.route)
                }
            )
        }

        composable(NavigationRoutes.QRScanner.route) {
            QRScannerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToParentDashboard = {
                    navController.navigate(NavigationRoutes.ParentDashboard.route) {
                        popUpTo(NavigationRoutes.Login.route) { inclusive = true }
                    }
                },
                onNavigateToChildDashboard = {
                    navController.navigate(NavigationRoutes.ChildDashboard.route) {
                        popUpTo(NavigationRoutes.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // Parent Dashboard
        composable(NavigationRoutes.ParentDashboard.route) {
            ParentDashboardScreen(
                onNavigateToChoreList = {
                    navController.navigate(NavigationRoutes.ChoreList.route)
                },
                onNavigateToRewardList = {
                    navController.navigate(NavigationRoutes.RewardList.route)
                },
                onNavigateToUserList = {
                    navController.navigate(NavigationRoutes.UserList.route)
                },
                onNavigateToActivityLog = {
                    navController.navigate(NavigationRoutes.ActivityLog.route)
                },
                onNavigateToSettings = {
                    navController.navigate(NavigationRoutes.Settings.route)
                },
                onNavigateToLogin = {
                    navController.navigate(NavigationRoutes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToGames = {
                    navController.navigate(NavigationRoutes.Games.route)
                },
                onNavigateToChoreDetail = { choreId ->
                    navController.navigate(NavigationRoutes.ChoreDetail.createRoute(choreId))
                },
                onNavigateToCompleteChore = { choreId ->
                    navController.navigate(NavigationRoutes.CompleteChore.createRoute(choreId))
                },
                onNavigateToCreateChore = {
                    navController.navigate(NavigationRoutes.CreateChore.route)
                }
            )
        }

        // Child Dashboard
        composable(NavigationRoutes.ChildDashboard.route) {
            ChildDashboardScreen(
                onNavigateToMyChores = {
                    navController.navigate(NavigationRoutes.MyChores.route)
                },
                onNavigateToRewardsMarketplace = {
                    navController.navigate(NavigationRoutes.RewardsMarketplace.route)
                },
                onNavigateToGames = {
                    navController.navigate(NavigationRoutes.Games.route)
                },
                onNavigateToProfile = {
                    navController.navigate(NavigationRoutes.Profile.route)
                },
                onNavigateToLogin = {
                    navController.navigate(NavigationRoutes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToCompleteChore = { choreId ->
                    navController.navigate(NavigationRoutes.CompleteChore.createRoute(choreId))
                }
            )
        }

        // Chore Management
        composable(NavigationRoutes.ChoreList.route) {
            ChoreListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCreateChore = {
                    navController.navigate(NavigationRoutes.CreateChore.route)
                },
                onNavigateToChoreDetail = { choreId ->
                    navController.navigate(NavigationRoutes.ChoreDetail.createRoute(choreId))
                },
                onNavigateToRecurringChoreEditor = {
                    navController.navigate(NavigationRoutes.RecurringChoreEditor.route)
                }
            )
        }

        composable(NavigationRoutes.CreateChore.route) {
            CreateEditChoreScreen(
                choreId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavigationRoutes.EditChore.route,
            arguments = listOf(navArgument("choreId") { type = NavType.StringType })
        ) { backStackEntry ->
            val choreId = backStackEntry.arguments?.getString("choreId")
            CreateEditChoreScreen(
                choreId = choreId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavigationRoutes.CompleteChore.route,
            arguments = listOf(navArgument("choreId") { type = NavType.StringType })
        ) { backStackEntry ->
            val choreId = backStackEntry.arguments?.getString("choreId") ?: return@composable
            CompleteChoreScreen(
                choreId = choreId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavigationRoutes.ChoreDetail.route,
            arguments = listOf(navArgument("choreId") { type = NavType.StringType })
        ) { backStackEntry ->
            val choreId = backStackEntry.arguments?.getString("choreId") ?: return@composable
            ChoreDetailScreen(
                choreId = choreId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { choreId ->
                    navController.navigate(NavigationRoutes.EditChore.createRoute(choreId))
                }
            )
        }

        composable(NavigationRoutes.MyChores.route) {
            MyChoresScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCompleteChore = { choreId ->
                    navController.navigate(NavigationRoutes.CompleteChore.createRoute(choreId))
                }
            )
        }

        composable(NavigationRoutes.RecurringChoreEditor.route) {
            RecurringChoreEditorScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEditChore = { choreId ->
                    navController.navigate(NavigationRoutes.EditChore.createRoute(choreId))
                }
            )
        }

        composable(NavigationRoutes.RewardList.route) {
            RewardListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCreateReward = {
                    navController.navigate(NavigationRoutes.CreateReward.route)
                },
                onNavigateToRewardDetail = { rewardId ->
                    // TODO: Implement reward detail screen
                }
            )
        }

        composable(NavigationRoutes.CreateReward.route) {
            CreateEditRewardScreen(
                rewardId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavigationRoutes.RewardsMarketplace.route) {
            RewardsMarketplaceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavigationRoutes.UserList.route) {
            UserListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCreateUser = {
                    navController.navigate(NavigationRoutes.CreateUser.route)
                },
                onNavigateToQRCode = { userId ->
                    navController.navigate(NavigationRoutes.QRCodeDisplay.createRoute(userId))
                }
            )
        }

        composable(NavigationRoutes.CreateUser.route) {
            CreateUserScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToQRCode = { userId ->
                    navController.navigate(NavigationRoutes.QRCodeDisplay.createRoute(userId)) {
                        popUpTo(NavigationRoutes.UserList.route)
                    }
                }
            )
        }

        composable(
            route = NavigationRoutes.QRCodeDisplay.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            QRCodeDisplayScreen(
                userId = userId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavigationRoutes.ActivityLog.route) {
            ActivityLogScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavigationRoutes.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProfile = {
                    navController.navigate(NavigationRoutes.Profile.route)
                },
                onLogout = {
                    // Logout will be handled by LoginScreen with Google Sign-In sign out
                    navController.navigate(NavigationRoutes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(NavigationRoutes.Profile.route) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEditProfile = {
                    navController.navigate(NavigationRoutes.EditProfile.route)
                }
            )
        }
        
        composable(NavigationRoutes.EditProfile.route) {
            com.lostsierra.chorequest.presentation.profile.EditProfileScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavigationRoutes.Games.route) {
            GamesScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTicTacToe = {
                    navController.navigate(NavigationRoutes.TicTacToe.route)
                },
                onNavigateToChoreQuiz = {
                    navController.navigate(NavigationRoutes.ChoreQuiz.route)
                },
                onNavigateToMemoryMatch = {
                    navController.navigate(NavigationRoutes.MemoryMatch.route)
                },
                onNavigateToRockPaperScissors = {
                    navController.navigate(NavigationRoutes.RockPaperScissors.route)
                },
                onNavigateToJigsawPuzzle = {
                    navController.navigate(NavigationRoutes.JigsawPuzzle.route)
                },
                onNavigateToSnakeGame = {
                    navController.navigate(NavigationRoutes.SnakeGame.route)
                },
                onNavigateToBreakoutGame = {
                    navController.navigate(NavigationRoutes.BreakoutGame.route)
                },
                onNavigateToMathGame = {
                    navController.navigate(NavigationRoutes.MathGame.route)
                }
            )
        }

        composable(NavigationRoutes.TicTacToe.route) {
            TicTacToeScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavigationRoutes.ChoreQuiz.route) {
            ChoreQuizScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavigationRoutes.MemoryMatch.route) {
            MemoryMatchScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavigationRoutes.RockPaperScissors.route) {
            RockPaperScissorsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavigationRoutes.JigsawPuzzle.route) {
            JigsawPuzzleScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavigationRoutes.SnakeGame.route) {
            SnakeGameScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavigationRoutes.BreakoutGame.route) {
            BreakoutGameScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavigationRoutes.MathGame.route) {
            MathGameScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // TODO: Add more screens as they are implemented in subsequent phases
    }
}

/**
 * Temporary placeholder screen for unimplemented routes
 */
@Composable
private fun PlaceholderScreen(
    title: String,
    subtitle: String,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onNavigateBack
        ) {
            Text("Go Back")
        }
    }
}
