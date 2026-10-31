package com.example.stefanykhub.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.ui.theme.PrimaryIndigo
import com.example.stefanykhub.ui.theme.TextPrimary

@Composable
fun PaginationAndActionsBar(
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
    onAddMaterialClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            OutlinedButton(
                onClick = { if (currentPage > 1) onPageChange(currentPage - 1) },
                enabled = currentPage > 1,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "‹ Попередня",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary
                )
            }


            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                (1..totalPages).forEach { page ->
                    val isSelected = page == currentPage
                    Button(
                        onClick = { onPageChange(page) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) PrimaryIndigo else Color.White,
                            contentColor = if (isSelected) Color.White else TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                    ) {
                        Text(
                            text = page.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }


            OutlinedButton(
                onClick = { if (currentPage < totalPages) onPageChange(currentPage + 1) },
                enabled = currentPage < totalPages,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Наступна ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary
                )
            }
        }


        if (onAddMaterialClick != null) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Button(
                    onClick = onAddMaterialClick,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryIndigo,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Додати матеріал",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
