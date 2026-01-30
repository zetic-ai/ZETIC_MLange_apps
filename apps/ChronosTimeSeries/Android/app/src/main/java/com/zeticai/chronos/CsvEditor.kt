package com.zeticai.chronos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CsvEditor(
    headers: List<String>,
    rows: List<List<String>>,
    onCellChange: (Int, Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
    ) {
        // Scrollable Container for the Table
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
        ) {
            Column {
                // Header Row
                Row(
                    modifier = Modifier
                        .background(Color(0xFFF5F5F5))
                        .padding(vertical = 8.dp)
                ) {
                    headers.forEach { header ->
                        Text(
                            text = header,
                            modifier = Modifier
                                .width(100.dp)
                                .padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Black,
                            maxLines = 1
                        )
                    }
                }

                // Data Rows
                rows.forEachIndexed { rowIndex, rowData ->
                    Row {
                        headers.forEachIndexed { colIndex, _ ->
                            val cellValue = rowData.getOrElse(colIndex) { "" }
                            BasicTextField(
                                value = cellValue,
                                onValueChange = { onCellChange(rowIndex, colIndex, it) },
                                modifier = Modifier
                                    .width(100.dp)
                                    .padding(8.dp)
                                    .background(Color.Transparent),
                                textStyle = TextStyle(
                                    color = Color.Black,
                                    fontSize = 14.sp
                                ),
                                singleLine = true
                            )
                        }
                    }
                    Spacer(modifier = Modifier
                        .width((headers.size * 100).dp)
                        .height(1.dp)
                        .background(Color.LightGray.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}
