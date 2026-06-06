package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BillMenuItem
import com.example.formatRupiah
import com.example.ParticipantState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MenuItemInputSection(
    menuItems: MutableList<BillMenuItem>,
    participants: List<ParticipantState>,
    onSyncSplits: (Double, List<Pair<String, Double>>) -> Unit
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(false) }
    
    // MenuItem draft input states
    var itemName by remember { mutableStateOf("") }
    var itemPriceStr by remember { mutableStateOf("") }
    var itemQty by remember { mutableStateOf(1) }
    var selectedOrderByName by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.RestaurantMenu,
                        contentDescription = "Menu Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "DETAIL MENU PESANAN (OPSIONAL)",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { isEnabled = it }
                )
            }

            if (isEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Akuratkan pembagian tagihan dengan mencatat rincian makanan/minuman yang dipesan teman.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Input field: Item Name
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("Nama Menu Makanan/Minuman") },
                    placeholder = { Text("misal: Mie Gacoan lvl 3, Es Teh manis") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Input field: Price
                    OutlinedTextField(
                        value = itemPriceStr,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() }) itemPriceStr = it
                        },
                        label = { Text("Harga Satuan (Rp)") },
                        placeholder = { Text("15000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    // Quantity controls
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { if (itemQty > 1) itemQty-- },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Kurang")
                        }
                        
                        Text(
                            text = "$itemQty",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )

                        IconButton(
                            onClick = { itemQty++ },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Tambah")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Select who ordered it
                Text(
                    text = "Dipesan Oleh:",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                if (participants.isEmpty()) {
                    Text(
                        text = "Harap tambahkan teman terlebih dahulu di bagian Metode Bagi Bill di bawah.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    // Select person tags
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Option: Split Equally/Everyone
                        TextButton(
                            onClick = { selectedOrderByName = "" },
                            shape = RoundedCornerShape(30.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (selectedOrderByName.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                contentColor = if (selectedOrderByName.isEmpty()) Color.White else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            Text("Bagi Semua Teman", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        participants.forEach { p ->
                            TextButton(
                                onClick = { selectedOrderByName = p.name },
                                shape = RoundedCornerShape(30.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (selectedOrderByName == p.name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                    contentColor = if (selectedOrderByName == p.name) Color.White else MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Text(p.name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Add to draft action button
                Button(
                    onClick = {
                        if (itemName.isBlank()) {
                            Toast.makeText(context, "Ketik nama menu terlebih dahulu!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val price = itemPriceStr.toDoubleOrNull() ?: 0.0
                        if (price <= 0.0) {
                            Toast.makeText(context, "Masukkan harga satuan menu!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        // Append
                        menuItems.add(
                            BillMenuItem(
                                name = itemName.trim(),
                                price = price,
                                qty = itemQty,
                                orderedBy = selectedOrderByName
                            )
                        )

                        // Reset fields
                        itemName = ""
                        itemPriceStr = ""
                        itemQty = 1
                        selectedOrderByName = ""
                        Toast.makeText(context, "Menu berhasil dimasukkan list detail!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = "Add Item")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tambahkan Menu ke List", fontWeight = FontWeight.Bold)
                }

                // If items list is not empty, display them
                if (menuItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Text(
                        text = "Rincian Menu Saat Ini (${menuItems.size} Item):",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    var totalDraftSum = 0.0
                    menuItems.forEachIndexed { idx, item ->
                        val itemTotal = item.price * item.qty
                        totalDraftSum += itemTotal
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.name} (${item.qty}x)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Bagian: " + if (item.orderedBy.isEmpty()) "Dibagi Rata Semua" else item.orderedBy,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatRupiah(itemTotal),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(onClick = { menuItems.removeAt(idx) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Hapus",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Rincian: " + formatRupiah(totalDraftSum),
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // SYNC BUTTON: Auto calculate splits based on items!
                        Button(
                            onClick = {
                                if (participants.isEmpty()) {
                                    Toast.makeText(context, "Tambahkan teman di bawah untuk sinkronisasi!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                
                                // Calculate individual shares
                                val mappedShares = mutableMapOf<String, Double>()
                                participants.forEach { mappedShares[it.name] = 0.0 }
                                
                                var sharedEquallyTotal = 0.0
                                menuItems.forEach { item ->
                                    val cost = item.price * item.qty
                                    if (item.orderedBy.isEmpty()) {
                                        sharedEquallyTotal += cost
                                    } else {
                                        mappedShares[item.orderedBy] = (mappedShares[item.orderedBy] ?: 0.0) + cost
                                    }
                                }

                                // Distribute shared equally items to all
                                val equalPortion = if (participants.isNotEmpty()) sharedEquallyTotal / participants.size else 0.0
                                val finalSplitsList = participants.map { p ->
                                    val individualSum = mappedShares[p.name] ?: 0.0
                                    p.name to (individualSum + equalPortion)
                                }

                                onSyncSplits(totalDraftSum, finalSplitsList)
                                Toast.makeText(context, "Selesai! Tagihan teman tersinkron otomatis.", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.SyncAlt, contentDescription = "Sync splits", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sinkron Rincian Ke Roster", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
