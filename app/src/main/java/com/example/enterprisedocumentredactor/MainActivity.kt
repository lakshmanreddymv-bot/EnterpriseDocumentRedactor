package com.example.enterprisedocumentredactor

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.enterprisedocumentredactor.ui.history.HistoryScreen
import com.example.enterprisedocumentredactor.ui.home.HomeScreen
import com.example.enterprisedocumentredactor.ui.result.ResultScreen
import com.example.enterprisedocumentredactor.ui.review.ReviewScreen
import com.example.enterprisedocumentredactor.ui.theme.EnterpriseDocumentRedactorTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EnterpriseDocumentRedactorTheme {
                val navController = rememberNavController()
                AppNavHost(navController)
            }
        }
    }

    private fun setSecureWindow(secure: Boolean) {
        if (secure) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    @Composable
    fun AppNavHost(navController: NavHostController) {
        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                setSecureWindow(false)
                HomeScreen(
                    onNavigateToReview = { navController.navigate("review") },
                    onNavigateToHistory = { navController.navigate("history") }
                )
            }
            composable("review") {
                setSecureWindow(true)
                ReviewScreen(
                    onNavigateToResult = {
                        navController.navigate("result") {
                            popUpTo("review") { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("result") {
                setSecureWindow(true)
                ResultScreen(
                    onDone = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            }
            composable("history") {
                setSecureWindow(false)
                HistoryScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
