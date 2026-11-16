package com.stepnote.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepnote.app.ui.theme.LocalStepnoteColors

@Composable
fun StateNoticeCard(
    title: String,
    description: String,
    primaryLabel: String? = null,
    onPrimaryClick: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalStepnoteColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.hairline)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                color = colors.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            if (primaryLabel != null && onPrimaryClick != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onPrimaryClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = primaryLabel, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            if (secondaryLabel != null && onSecondaryClick != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSecondaryClick,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = secondaryLabel,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
