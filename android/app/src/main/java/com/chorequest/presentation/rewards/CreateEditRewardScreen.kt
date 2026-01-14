package com.chorequest.presentation.rewards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chorequest.presentation.components.ChoreQuestTopAppBar

/**
 * Screen for creating or editing a reward
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditRewardScreen(
    rewardId: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: RewardViewModel = hiltViewModel()
) {
    val createEditState by viewModel.createEditState.collectAsState()
    
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var pointsCost by remember { mutableStateOf("50") }
    var stock by remember { mutableStateOf("") }
    var hasLimitedStock by remember { mutableStateOf(false) }
    
    val isEditing = rewardId != null

    // Handle success/error states
    LaunchedEffect(createEditState) {
        when (createEditState) {
            is CreateEditState.Success -> {
                onNavigateBack()
                viewModel.resetCreateEditState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            ChoreQuestTopAppBar(
                title = if (isEditing) "Edit Reward" else "Create Reward",
                onNavigateBack = onNavigateBack,
                actions = {
                    TextButton(
                        onClick = {
                            if (title.isNotBlank() && pointsCost.toIntOrNull() != null) {
                                viewModel.createReward(
                                    title = title,
                                    description = description,
                                    pointsCost = pointsCost.toInt(),
                                    icon = null, // TODO: Add icon picker
                                    stock = if (hasLimitedStock && stock.toIntOrNull() != null) stock.toInt() else null
                                )
                            }
                        },
                        enabled = title.isNotBlank() && pointsCost.toIntOrNull() != null
                    ) {
                        Text(
                            text = if (isEditing) "Save" else "Create",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Reward Title *") },
                placeholder = { Text("e.g., 30 minutes of screen time") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Title, null)
                }
            )

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                placeholder = { Text("Describe this reward...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5,
                leadingIcon = {
                    Icon(Icons.Default.Description, null)
                }
            )

            // Points cost
            OutlinedTextField(
                value = pointsCost,
                onValueChange = { if (it.all { char -> char.isDigit() }) pointsCost = it },
                label = { Text("Points Cost *") },
                placeholder = { Text("50") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                leadingIcon = {
                    Icon(Icons.Default.Stars, null)
                }
            )

            // Limited stock toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Limited Stock",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = hasLimitedStock,
                    onCheckedChange = { hasLimitedStock = it }
                )
            }

            // Stock quantity (if limited)
            if (hasLimitedStock) {
                OutlinedTextField(
                    value = stock,
                    onValueChange = { if (it.all { char -> char.isDigit() }) stock = it },
                    label = { Text("Stock Quantity") },
                    placeholder = { Text("e.g., 5") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = {
                        Icon(Icons.Default.Inventory, null)
                    },
                    supportingText = {
                        Text("How many times this reward can be redeemed")
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Kids will be able to redeem this reward with their earned points!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Loading indicator
            if (createEditState is CreateEditState.Loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Error message
            if (createEditState is CreateEditState.Error) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = (createEditState as CreateEditState.Error).message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
