package com.iclonud.bridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.iclonud.bridge.ui.HomeScreen
import com.iclonud.bridge.ui.LoginScreen
import com.iclonud.bridge.ui.theme.IclonudTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IclonudTheme {
                var loggedIn by remember { mutableStateOf(App.instance.credentials.isLoggedIn) }
                if (loggedIn) {
                    HomeScreen(onLogout = {
                        App.instance.logout()
                        loggedIn = false
                    })
                } else {
                    LoginScreen(onLoggedIn = { loggedIn = true })
                }
            }
        }
    }
}
