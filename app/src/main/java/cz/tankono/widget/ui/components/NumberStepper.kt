package cz.tankono.widget.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.tankono.widget.ui.OnoRed

@Composable
fun TwoSteppersRow(
    value1: String, value2: String,
    onMinus1: () -> Unit, onPlus1: () -> Unit,
    onMinus2: () -> Unit, onPlus2: () -> Unit,
    canMinus1: Boolean = true, canPlus1: Boolean = true,
    canMinus2: Boolean = true, canPlus2: Boolean = true,
    onMin1: (() -> Unit)? = null, onMax1: (() -> Unit)? = null,
    onMin2: (() -> Unit)? = null, onMax2: (() -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f).padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
            NumberStepper(value1, onMinus1, onPlus1, canMinus1, canPlus1, onMin1, onMax1)
        }
        Box(modifier = Modifier.weight(1f).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
            NumberStepper(value2, onMinus2, onPlus2, canMinus2, canPlus2, onMin2, onMax2)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NumberStepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    canMinus: Boolean = true,
    canPlus: Boolean = true,
    onMin: (() -> Unit)? = null,
    onMax: (() -> Unit)? = null
) {
    val buttonSize = 40.dp
    BoxWithConstraints {
        val availableWidth = maxWidth
        val buttonsWidth = buttonSize * 2
        val remaining = availableWidth - buttonsWidth
        val gap = when {
            remaining >= 120.dp -> 4.dp
            remaining >= 100.dp -> 3.dp
            remaining >= 80.dp -> 2.dp
            else -> 1.dp
        }
        val valueWidth = (remaining - (gap * 2)).coerceAtLeast(40.dp)
        val valueFontSize = when {
            valueWidth >= 70.dp -> 18.sp
            valueWidth >= 60.dp -> 16.sp
            valueWidth >= 50.dp -> 14.sp
            else -> 12.sp
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
                    .then(
                        if (canMinus && onMin != null) {
                            Modifier.combinedClickable(onClick = onMinus, onLongClick = onMin)
                        } else if (canMinus) {
                            Modifier.clickable { onMinus() }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (canMinus) Text("−", color = OnoRed, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Box(modifier = Modifier.width(valueWidth).height(buttonSize), contentAlignment = Alignment.Center) {
                Text(
                    text = value,
                    color = OnoRed,
                    fontSize = valueFontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
                    .then(
                        if (canPlus && onMax != null) {
                            Modifier.combinedClickable(onClick = onPlus, onLongClick = onMax)
                        } else if (canPlus) {
                            Modifier.clickable { onPlus() }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (canPlus) Text("+", color = OnoRed, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}