package com.lostsierra.chorequest.presentation.navigation

/**
 * Sealed class defining all navigation routes in the app
 */
sealed class NavigationRoutes(val route: String) {
    
    // Authentication
    object Login : NavigationRoutes("login")
    object QRScanner : NavigationRoutes("qr_scanner")
    
    // Dashboards
    object ParentDashboard : NavigationRoutes("parent_dashboard")
    object ChildDashboard : NavigationRoutes("child_dashboard")
    
    // Chores
    object ChoreList : NavigationRoutes("chore_list")
    object MyChores : NavigationRoutes("my_chores")
    object ChoreDetail : NavigationRoutes("chore_detail/{choreId}") {
        fun createRoute(choreId: String) = "chore_detail/$choreId"
    }
    object CreateChore : NavigationRoutes("create_chore")
    object EditChore : NavigationRoutes("edit_chore/{choreId}") {
        fun createRoute(choreId: String) = "edit_chore/$choreId"
    }
    object CompleteChore : NavigationRoutes("complete_chore/{choreId}") {
        fun createRoute(choreId: String) = "complete_chore/$choreId"
    }
    object VerifyChore : NavigationRoutes("verify_chore/{choreId}") {
        fun createRoute(choreId: String) = "verify_chore/$choreId"
    }
    object RecurringChoreEditor : NavigationRoutes("recurring_chore_editor")
    
    // Rewards
    object RewardList : NavigationRoutes("reward_list")
    object RewardsMarketplace : NavigationRoutes("rewards_marketplace")
    object CreateReward : NavigationRoutes("create_reward")
    object EditReward : NavigationRoutes("edit_reward/{rewardId}") {
        fun createRoute(rewardId: String) = "edit_reward/$rewardId"
    }
    object RedeemReward : NavigationRoutes("redeem_reward/{rewardId}") {
        fun createRoute(rewardId: String) = "redeem_reward/$rewardId"
    }
    
    // Users
    object UserList : NavigationRoutes("user_list")
    object CreateUser : NavigationRoutes("create_user")
    object UserDetail : NavigationRoutes("user_detail/{userId}") {
        fun createRoute(userId: String) = "user_detail/$userId"
    }
    object QRCodeDisplay : NavigationRoutes("qr_code_display/{userId}") {
        fun createRoute(userId: String) = "qr_code_display/$userId"
    }
    
    // Activity & Settings
    object ActivityLog : NavigationRoutes("activity_log")
    object Settings : NavigationRoutes("settings")
    object Profile : NavigationRoutes("profile")
    object EditProfile : NavigationRoutes("edit_profile")
    
    // Games
    object Games : NavigationRoutes("games")
    object TicTacToe : NavigationRoutes("tic_tac_toe")
    object ChoreQuiz : NavigationRoutes("chore_quiz")
    object MemoryMatch : NavigationRoutes("memory_match")
    object RockPaperScissors : NavigationRoutes("rock_paper_scissors")
    object JigsawPuzzle : NavigationRoutes("jigsaw_puzzle")
    object SnakeGame : NavigationRoutes("snake_game")
    object BreakoutGame : NavigationRoutes("breakout_game")
    object MathGame : NavigationRoutes("math_game")
    object Hangman : NavigationRoutes("hangman")
    object WordScramble : NavigationRoutes("word_scramble")
}
