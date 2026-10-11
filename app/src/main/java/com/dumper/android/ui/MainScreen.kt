package com.dumper.android.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dumper.android.R
import com.dumper.android.ui.console.ConsoleScreen
import com.dumper.android.ui.console.ConsoleViewModel
import com.dumper.android.ui.memory.MemoryScreen
import com.dumper.android.ui.memory.MemoryViewModel
import com.dumper.android.ui.navigation.BottomNavBar
import com.dumper.android.utils.openGithubPage

/** Supported in-app language options. Labels are always shown in their own locale. */
private data class LanguageOption(val label: String, val tag: String)

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun MainScreen(memory: MemoryViewModel = viewModel(), console: ConsoleViewModel = viewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val followSystemLabel = stringResource(R.string.follow_system)

    val languageOptions = remember(followSystemLabel) {
        listOf(
            LanguageOption(followSystemLabel, ""),
            LanguageOption("English", "en"),
            LanguageOption("简体中文", "zh-CN"),
        )
    }

    var showLanguageDialog by remember { mutableStateOf(false) }

    if (showLanguageDialog) {
        LanguageDialog(
            options = languageOptions,
            onDismiss = { showLanguageDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = stringResource(R.string.app_name)
                    )
                },
                actions = {
                    IconButton(onClick = { showLanguageDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = stringResource(R.string.language)
                        )
                    }
                    IconButton(
                        modifier = Modifier.heightIn(min = 24.dp, max = 32.dp),
                        onClick = { context.openGithubPage() }) {
                        Icon(
                            painter = painterResource(R.drawable.github_mark),
                            contentDescription = stringResource(R.string.github_repository)
                        )
                    }
                }
            )
        },
        bottomBar = { BottomNavBar(navController) }
    ) {
        Surface(modifier = Modifier.padding(it)) {
            NavHost(navController = navController, startDestination = "memory") {
                composable("memory") { MemoryScreen(navController, memory) }
                composable("console") { ConsoleScreen(navController, console) }
            }
        }
    }
}

@Composable
private fun LanguageDialog(
    options: List<LanguageOption>,
    onDismiss: () -> Unit
) {
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                options.forEach { option ->
                    val selected = if (option.tag.isEmpty()) {
                        currentTag.isEmpty()
                    } else {
                        currentTag.split(',')
                            .any { it.trim() == option.tag || it.trim().startsWith("${option.tag}-") }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppCompatDelegate.setApplicationLocales(
                                    if (option.tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                    else LocaleListCompat.forLanguageTags(option.tag)
                                )
                                onDismiss()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            text = option.label,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}
