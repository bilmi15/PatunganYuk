package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BillEntity
import com.example.ui.viewmodel.BillViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BillItemCard
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: BillViewModel,
    onNavigateToEditBill: (BillEntity) -> Unit,
    onSettleParticipant: (BillEntity, com.example.data.local.Participant) -> Unit,
    onViewImage: (String) -> Unit = {}
) {
    val bills by viewModel.bills.collectAsStateWithLifecycle()
    
    // Filters: 0 -> Semua, 1 -> Belum Lunas, 2 -> Lunas
    var currentFilter by remember { mutableStateOf(0) }

    // Filtered bills
    val filteredBills = remember(bills, currentFilter) {
        when (currentFilter) {
            1 -> bills.filter { bill -> bill.participants.any { !it.isPaid } }
            2 -> bills.filter { bill -> bill.participants.all { it.isPaid } }
            else -> bills
        }
    }

    // Group filtered bills by Month & Year
    val groupedBills = remember(filteredBills) {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
        val groups = mutableMapOf<String, MutableList<BillEntity>>()
        for (bill in filteredBills) {
            val key = sdf.format(Date(bill.date))
            if (!groups.containsKey(key)) {
                groups[key] = mutableListOf()
            }
            groups[key]?.add(bill)
        }
        groups
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Timeline Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History Icon",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column {
                Text(
                    text = "Riwayat Transaksi",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Timeline pencatatan pembagian tagihan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }

        // Filter chips bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Semua", "Belum Lunas", "Lunas").forEachIndexed { index, title ->
                FilterChip(
                    selected = currentFilter == index,
                    onClick = { currentFilter = index },
                    label = { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        if (filteredBills.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = "Empty history",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Timeline kosong.",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = "Sesuaikan filter atau catat patungan di Beranda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                groupedBills.forEach { (monthAndYear, monthBills) ->
                    item {
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = monthAndYear.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    items(monthBills, key = { it.id }) { bill ->
                        BillItemCard(
                            bill = bill,
                            onEdit = onNavigateToEditBill,
                            onDelete = { viewModel.deleteBill(bill.id) },
                            onTogglePaid = { name, isPaid ->
                                viewModel.toggleParticipantPaid(bill.id, name, isPaid)
                            },
                            onNagih = { participant ->
                                onSettleParticipant(bill, participant)
                            },
                            onViewImage = onViewImage
                        )
                    }
                }
            }
        }
    }
}
