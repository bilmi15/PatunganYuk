package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.BillEntity
import com.example.data.local.Participant
import com.example.data.local.BillMenuItem
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.BillViewModel
import com.example.ui.LoginRegisterScreen
import com.example.ui.StatistikScreen
import com.example.ui.HistoryScreen
import com.example.ui.ProfilScreen
import com.example.ui.PermissionOnboardingDialog
import com.example.ui.ImagePreviewDialog
import com.example.ui.MenuItemInputSection
import com.example.utils.LocationHelper
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

enum class ActiveScreen {
    Dashboard, AddEditBill
}

enum class DashboardTab {
    Beranda, Statistik, History, Profil
}

enum class SplitMode {
    EQUAL, INDIVIDUAL
}

class MainActivity : ComponentActivity() {
    private val viewModel: BillViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

// Format Currency to Indonesian Rupiah
fun formatRupiah(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    return formatter.format(amount).replace("Rp", "Rp ").replace(",00", "")
}

// Format Date from timestamp
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("in", "ID"))
    return sdf.format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: BillViewModel) {
    val context = LocalContext.current
    
    // Auth Session Check State
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    
    var activeScreen by remember { mutableStateOf(ActiveScreen.Dashboard) }
    var currentDashboardTab by remember { mutableStateOf(DashboardTab.Beranda) }
    var editingBill by remember { mutableStateOf<BillEntity?>(null) }

    // Intercept back actions to prevent accidental exit and return to Main Beranda screen first
    val isNotAtBeranda = currentUser != null && (activeScreen != ActiveScreen.Dashboard || currentDashboardTab != DashboardTab.Beranda)
    BackHandler(enabled = isNotAtBeranda) {
        if (activeScreen == ActiveScreen.AddEditBill) {
            activeScreen = ActiveScreen.Dashboard
            editingBill = null
        } else if (currentDashboardTab != DashboardTab.Beranda) {
            currentDashboardTab = DashboardTab.Beranda
        }
    }
    
    // Dialog State for "Nagih Tanpa Canggung"
    var shareBillPendingForParticipant by remember { mutableStateOf<Participant?>(null) }
    var shareBillPendingForEvent by remember { mutableStateOf<BillEntity?>(null) }
    var showShareTemplateDialog by remember { mutableStateOf(false) }

    // Dialog state for permission onboarding and full size image preview
    var showPermissionOnboarding by remember { mutableStateOf(false) }
    var viewImageUriStr by remember { mutableStateOf<String?>(null) }

    // Launcher permissions contract
    val startupPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val camera = permissions[Manifest.permission.CAMERA] ?: false
        if (fineLocation && camera) {
            Toast.makeText(context, "Selesai! Seluruh izin utama berhasil disetujui.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Beberapa izin ditolak, Anda tetap bisa mengoperasikan manual.", Toast.LENGTH_LONG).show()
        }
    }

    if (currentUser == null) {
        LoginRegisterScreen(
            viewModel = viewModel,
            onLoginSuccess = {
                val fineLocationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (!fineLocationGranted || !cameraGranted) {
                    showPermissionOnboarding = true
                }
            }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (activeScreen == ActiveScreen.Dashboard) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NavigationBarItem(
                            selected = currentDashboardTab == DashboardTab.Beranda,
                            onClick = { currentDashboardTab = DashboardTab.Beranda },
                            icon = {
                                Icon(
                                    imageVector = if (currentDashboardTab == DashboardTab.Beranda) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = "Beranda"
                                )
                            },
                            label = {
                                Text(
                                    "Beranda",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        )

                        NavigationBarItem(
                            selected = currentDashboardTab == DashboardTab.Statistik,
                            onClick = { currentDashboardTab = DashboardTab.Statistik },
                            icon = {
                                Icon(
                                    imageVector = if (currentDashboardTab == DashboardTab.Statistik) Icons.Filled.BarChart else Icons.Outlined.BarChart,
                                    contentDescription = "Statistik"
                                )
                            },
                            label = {
                                Text(
                                    "Statistik",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        )

                        NavigationBarItem(
                            selected = currentDashboardTab == DashboardTab.History,
                            onClick = { currentDashboardTab = DashboardTab.History },
                            icon = {
                                Icon(
                                    imageVector = if (currentDashboardTab == DashboardTab.History) Icons.Filled.History else Icons.Outlined.History,
                                    contentDescription = "History"
                                )
                            },
                            label = {
                                Text(
                                    "History",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        )

                        NavigationBarItem(
                            selected = currentDashboardTab == DashboardTab.Profil,
                            onClick = { currentDashboardTab = DashboardTab.Profil },
                            icon = {
                                Icon(
                                    imageVector = if (currentDashboardTab == DashboardTab.Profil) Icons.Filled.Person else Icons.Outlined.Person,
                                    contentDescription = "Profil"
                                )
                            },
                            label = {
                                Text(
                                    "Profil",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (activeScreen) {
                    ActiveScreen.Dashboard -> {
                        when (currentDashboardTab) {
                            DashboardTab.Beranda -> {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToAddBill = {
                                        editingBill = null
                                        activeScreen = ActiveScreen.AddEditBill
                                    },
                                    onNavigateToEditBill = { bill ->
                                        editingBill = bill
                                        activeScreen = ActiveScreen.AddEditBill
                                    },
                                    onSettleParticipant = { bill, participant ->
                                        shareBillPendingForEvent = bill
                                        shareBillPendingForParticipant = participant
                                        showShareTemplateDialog = true
                                    },
                                    onViewImage = { uri ->
                                        viewImageUriStr = uri
                                    }
                                )
                            }
                            DashboardTab.Statistik -> {
                                StatistikScreen(viewModel = viewModel)
                            }
                            DashboardTab.History -> {
                                HistoryScreen(
                                    viewModel = viewModel,
                                    onNavigateToEditBill = { bill ->
                                        editingBill = bill
                                        activeScreen = ActiveScreen.AddEditBill
                                    },
                                    onSettleParticipant = { bill, participant ->
                                        shareBillPendingForEvent = bill
                                        shareBillPendingForParticipant = participant
                                        showShareTemplateDialog = true
                                    },
                                    onViewImage = { uri ->
                                        viewImageUriStr = uri
                                    }
                                )
                            }
                            DashboardTab.Profil -> {
                                ProfilScreen(
                                    viewModel = viewModel,
                                    onLogout = {
                                        viewModel.logout()
                                        currentDashboardTab = DashboardTab.Beranda
                                    }
                                )
                            }
                        }
                    }
                    ActiveScreen.AddEditBill -> {
                        AddEditBillScreen(
                            viewModel = viewModel,
                            billToEdit = editingBill,
                            onBack = {
                                activeScreen = ActiveScreen.Dashboard
                            }
                        )
                    }
                }

                // First run application permissions confirmation dialog modal
                if (showPermissionOnboarding) {
                    PermissionOnboardingDialog(
                        onDismiss = { showPermissionOnboarding = false },
                        onRequestPermissions = {
                            startupPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )
                }

                // Full screen high resolution scrollable zoom receipt image viewer preview
                if (!viewImageUriStr.isNullOrEmpty()) {
                    ImagePreviewDialog(
                        imageUri = viewImageUriStr!!,
                        onDismiss = { viewImageUriStr = null }
                    )
                }

                // WhatsApp Template Quick Dialog
                if (showShareTemplateDialog && shareBillPendingForParticipant != null && shareBillPendingForEvent != null) {
                    WhatsAppShareDialog(
                        participant = shareBillPendingForParticipant!!,
                        bill = shareBillPendingForEvent!!,
                        onDismiss = {
                            showShareTemplateDialog = false
                        },
                        onShareText = { msg ->
                            showShareTemplateDialog = false
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, msg)
                                setPackage("com.whatsapp")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val chooserIntent = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, msg)
                                }, "Kirim Tagihan Patungan")
                                context.startActivity(chooserIntent)
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: BillViewModel,
    onNavigateToAddBill: () -> Unit,
    onNavigateToEditBill: (BillEntity) -> Unit,
    onSettleParticipant: (BillEntity, Participant) -> Unit,
    onViewImage: (String) -> Unit = {}
) {
    val bills by viewModel.bills.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // App Title Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.patunganyuk_logo_1780481231743),
                            contentDescription = "PatunganYuk Emblem",
                            modifier = Modifier
                                .size(66.dp)
                                .offset(y = (-4).dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Column {
                    Text(
                        text = "Hai, Selamat Datang ${currentUser?.username ?: "User"}! 👋",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "PatunganYuk!",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Stats Dashboard Card (Professional Polish Style 2-Column Grid)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Total Piutang (Light Blue container)
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(132.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Piutang",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Piutang",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Belum Bayar",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                        Text(
                            text = formatRupiah(stats.totalPending),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Card 2: Total Utang / Tertagih (Deep Blue container)
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(132.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Terkumpul",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Sudah Lunas",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Sudah Lunas",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = formatRupiah(stats.totalCollected),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Real-time Pending Debts List & Alarm Reminder System (Fitur Pengingat)
        val unpaidParticipants = remember(bills) {
            bills.flatMap { b ->
                b.participants.filter { !it.isPaid }.map { p ->
                    Pair(b, p)
                }
            }
        }

        if (unpaidParticipants.isNotEmpty()) {
            var showRemindersSection by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Alarm Pengingat",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pengingat: Ada ${unpaidParticipants.size} Utang Belum Bayar",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        IconButton(
                            onClick = { showRemindersSection = !showRemindersSection },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (showRemindersSection) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand/Collapse",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    if (showRemindersSection) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            unpaidParticipants.take(5).forEach { (b, p) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = p.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = b.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = formatRupiah(p.amountToPay),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Button(
                                            onClick = { onSettleParticipant(b, p) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Send,
                                                    contentDescription = "Tagih",
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Text("Ingatkan", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                            if (unpaidParticipants.size > 5) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "... dan ${unpaidParticipants.size - 5} tagihan lainnya belum dibayar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Real-time Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Cari berdasar nama acara atau lokasi...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("search_input"),
            prefix = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    modifier = Modifier.padding(end = 8.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
            )
        )

        // Historical Bill Transactions List
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (bills.isEmpty()) {
                // High-fidelity Empty State layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SentimentSatisfiedAlt,
                        contentDescription = "Empty icon",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Dompet aman! Belum ada utang kelompok hari ini.",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tekan tombol + di pojok kanan bawah untuk mulai mencatat patungan bareng teman kamu secara instan.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(bills, key = { it.id }) { bill ->
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

            // Floating Action Button
            LargeFloatingActionButton(
                onClick = onNavigateToAddBill,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 4.dp)
                    .testTag("add_bill_button"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add bill", modifier = Modifier.size(36.dp))
            }
        }
    }
}

fun getEmojiForTitle(title: String): String {
    val t = title.lowercase()
    return when {
        t.contains("makan") || t.contains("mie") || t.contains("gacoan") || t.contains("bakso") || t.contains("nasgor") || t.contains("ayam") || t.contains("rasa") || t.contains("bubur") || t.contains("sate") || t.contains("seblak") -> "🍜"
        t.contains("kopi") || t.contains("coffee") || t.contains("teh") || t.contains("nugas") || t.contains("cafe") || t.contains("starbucks") || t.contains("drink") || t.contains("boba") -> "☕"
        t.contains("bensin") || t.contains("grab") || t.contains("gojek") || t.contains("motor") || t.contains("mobil") || t.contains("trans") || t.contains("parkir") -> "🚗"
        t.contains("nonton") || t.contains("bioskop") || t.contains("cinema") || t.contains("tiket") || t.contains("film") || t.contains("karaoke") -> "🎬"
        t.contains("kado") || t.contains("ultah") || t.contains("hadiah") || t.contains("gift") -> "🎁"
        t.contains("belanja") || t.contains("foto") || t.contains("struk") || t.contains("nota") || t.contains("focal") || t.contains("print") -> "📸"
        else -> "💰"
    }
}

fun getEmojiBgForTitle(title: String): Color {
    val t = title.lowercase().trim()
    return when {
        t.contains("makan") || t.contains("mie") || t.contains("gacoan") || t.contains("bakso") || t.contains("nasgor") || t.contains("ayam") || t.contains("rasa") || t.contains("bubur") || t.contains("sate") || t.contains("seblak") -> Color(0xFFFFE0B2) // Warm Orange
        t.contains("kopi") || t.contains("coffee") || t.contains("teh") || t.contains("nugas") || t.contains("cafe") || t.contains("starbucks") || t.contains("drink") || t.contains("boba") -> Color(0xFFD1C4E9) // Pastel Purple
        t.contains("bensin") || t.contains("grab") || t.contains("gojek") || t.contains("motor") || t.contains("mobil") || t.contains("trans") || t.contains("parkir") -> Color(0xFFC8E6C9) // Pastel Green
        t.contains("nonton") || t.contains("bioskop") || t.contains("cinema") || t.contains("tiket") || t.contains("film") || t.contains("karaoke") -> Color(0xFFF8BBD0) // Pastel Pink
        t.contains("kado") || t.contains("ultah") || t.contains("hadiah") || t.contains("gift") -> Color(0xFFE1BEE7) // Light Violet
        t.contains("belanja") || t.contains("foto") || t.contains("struk") || t.contains("nota") || t.contains("focal") || t.contains("print") -> Color(0xFFB3E5FC) // Baby Blue
        else -> Color(0xFFCFD8DC) // Slate Grey
    }
}

@Composable
fun BillItemCard(
    bill: BillEntity,
    onEdit: (BillEntity) -> Unit,
    onDelete: () -> Unit,
    onTogglePaid: (String, Boolean) -> Unit,
    onNagih: (Participant) -> Unit,
    onViewImage: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("bill_item_card_${bill.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Highly Polished Row Layout Matching Design HTML
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Dynamic colorful circular icon
                val emoji = getEmojiForTitle(bill.title)
                val emojiBg = getEmojiBgForTitle(bill.title)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(emojiBg, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 22.sp)
                }

                // Title and Location/Date Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bill.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!bill.locationName.isNullOrEmpty()) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Location",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = bill.locationName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 100.dp)
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                        Text(
                            text = formatDate(bill.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                // Balance & Status Badge Column
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatRupiah(bill.totalAmount),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val allPaid = bill.participants.isNotEmpty() && bill.participants.all { it.isPaid }
                    val statusText = if (allPaid) "Lunas" else "Belum Lunas"
                    val statusBg = if (allPaid) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    val statusColor = if (allPaid) Color(0xFF2E7D32) else Color(0xFFC62828)
                    
                    Box(
                        modifier = Modifier
                            .background(statusBg, RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusText.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            // Thumbnail indicator of receipt image (if exists)
            if (!bill.imageUri.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .clickable { onViewImage(bill.imageUri) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = "Nota",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ada Bukti Struk Nota (Lihat)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer of list item: Summary of participants paid status
            val totalFriendCount = bill.participants.size
            val paidCount = bill.participants.count { it.isPaid }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$paidCount dari $totalFriendCount teman telah bayar",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (paidCount == totalFriendCount && totalFriendCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Animated expansion section
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    
                    // Display detailed menu items if available
                    if (bill.menuItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Rincian Menu Item:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                bill.menuItems.forEachIndexed { i, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "${item.name} (x${item.qty})",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                            if (!item.orderedBy.isNullOrEmpty()) {
                                                Text(
                                                    text = "Dipesan Oleh: ${item.orderedBy}",
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        Text(
                                            text = formatRupiah(item.price * item.qty),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold)
                                        )
                                    }
                                    if (i < bill.menuItems.size - 1) {
                                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), modifier = Modifier.padding(vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Daftar Anggota Patungan:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Individual participants ledger
                    bill.participants.forEach { participant ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (participant.isPaid) MaterialTheme.colorScheme.primary.copy(alpha = 0.03f)
                                    else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = participant.isPaid,
                                    onCheckedChange = { isChecked ->
                                        onTogglePaid(participant.name, isChecked)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Column {
                                    Text(
                                        text = participant.name,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = if (participant.isPaid) FontWeight.Normal else FontWeight.Bold
                                        ),
                                        color = if (participant.isPaid) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Share: ${formatRupiah(participant.amountToPay)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (participant.isPaid) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            // Send WhatsApp reminder button for unpaid participant
                            if (!participant.isPaid) {
                                Button(
                                    onClick = { onNagih(participant) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Nagih icon",
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Nagih", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selesai",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "LUNAS",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }



                    Spacer(modifier = Modifier.height(16.dp))

                    // Action items: EDIT and DELETE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { onEdit(bill) },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onDelete,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hapus", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

data class ParticipantState(
    val name: String,
    val amount: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBillScreen(
    viewModel: BillViewModel,
    billToEdit: BillEntity?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isEditMode = billToEdit != null

    // Form inputs state
    var title by remember { mutableStateOf(billToEdit?.title ?: "") }
    var totalAmountStr by remember { mutableStateOf(billToEdit?.totalAmount?.toInt()?.toString() ?: "") }
    var locationName by remember { mutableStateOf(billToEdit?.locationName ?: "") }
    var latitude by remember { mutableStateOf(billToEdit?.latitude) }
    var longitude by remember { mutableStateOf(billToEdit?.longitude) }
    
    // Receipt path state
    var imageUriStr by remember { mutableStateOf(billToEdit?.imageUri) }

    // Split Engine Parameters
    var splitMode by remember { mutableStateOf(if (billToEdit != null && billToEdit.participants.any { it.amountToPay != billToEdit.totalAmount / billToEdit.participants.size }) SplitMode.INDIVIDUAL else SplitMode.EQUAL) }
    
    // Loaded participants list
    val participantsList = remember { mutableStateListOf<ParticipantState>() }
    val menuItemsStateList = remember { mutableStateListOf<BillMenuItem>() }

    // Populate checklist on entry
    LaunchedEffect(billToEdit) {
        if (billToEdit != null) {
            if (participantsList.isEmpty()) {
                participantsList.clear()
                billToEdit.participants.forEach {
                    participantsList.add(ParticipantState(it.name, it.amountToPay.toInt().toString()))
                }
            }
            if (menuItemsStateList.isEmpty()) {
                menuItemsStateList.clear()
                billToEdit.menuItems.forEach {
                    menuItemsStateList.add(it)
                }
            }
        }
    }

    // Interactive new teammate entry drawer
    var currentTeammateName by remember { mutableStateOf("") }
    var currentTeammateAmount by remember { mutableStateOf("") }
    var participantToEditIndex by remember { mutableStateOf<Int?>(null) }

    // Form validation warnings status
    var titleError by remember { mutableStateOf<String?>(null) }
    var totalAmountError by remember { mutableStateOf<String?>(null) }
    var participantError by remember { mutableStateOf<String?>(null) }

    // Location Tracking Flow
    val gpsLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val isTrackingGps by viewModel.isTrackingLocation.collectAsStateWithLifecycle()
    val locationHelper = remember { LocationHelper(context) }

    // File setup for Camera triggers
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Listen to GPS callback
    LaunchedEffect(gpsLocation) {
        if (gpsLocation != null) {
            locationName = gpsLocation!!.addressName
            latitude = gpsLocation!!.latitude
            longitude = gpsLocation!!.longitude
            viewModel.clearTrackedLocation()
        }
    }

    // Modern Launcher Contracts
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.fetchLocation(locationHelper)
        } else {
            Toast.makeText(context, "Izin lokasi ditolak, gunakan input manual.", Toast.LENGTH_LONG).show()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            val permanentPath = com.example.utils.FileStorageHelper.copyUriToInternalStorage(context, tempCameraUri!!, "receipt_images")
            if (permanentPath != null) {
                imageUriStr = permanentPath
                Toast.makeText(context, "Foto struk berhasil disimpan dan dimuat secara permanen!", Toast.LENGTH_SHORT).show()
            } else {
                imageUriStr = tempCameraUri.toString()
                Toast.makeText(context, "Foto struk berhasil dimuat!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val permanentPath = com.example.utils.FileStorageHelper.copyUriToInternalStorage(context, uri, "receipt_images")
            if (permanentPath != null) {
                imageUriStr = permanentPath
                Toast.makeText(context, "Nota disimpan & dimuat secara permanen!", Toast.LENGTH_SHORT).show()
            } else {
                imageUriStr = uri.toString()
                Toast.makeText(context, "Nota dipilih dari Galeri!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Helper calculate individual weights
    val totalInvoice = totalAmountStr.toDoubleOrNull() ?: 0.0
    val totalAllocated = participantsList.sumOf { item -> item.amount.toDoubleOrNull() ?: 0.0 }
    val remainingToAllocate = totalInvoice - totalAllocated

    // Real-time distribution triggered anytime totalInvoice, splitMode, or participantsCount shifts
    val processedParticipantsList = remember(totalInvoice, splitMode, participantsList.size, participantsList.toList()) {
        if (splitMode == SplitMode.EQUAL && participantsList.isNotEmpty() && totalInvoice > 0) {
            val share = totalInvoice / participantsList.size
            participantsList.map { item -> item.copy(amount = share.toInt().toString()) }
        } else {
            participantsList.toList()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // App header form
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEditMode) "Edit Catatan Patungan" else "Buat Patungan Baru",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Section 1: Bill parameters
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "INFORMASI NOTA",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            titleError = null
                        },
                        label = { Text("Nama Acara / Pembelian") },
                        isError = titleError != null,
                        supportingText = titleError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        placeholder = { Text("misal: Mie Gacoan, Kopi Nugas") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("title_field"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    // Total Invoice Billing
                    OutlinedTextField(
                        value = totalAmountStr,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() }) {
                                totalAmountStr = it
                                totalAmountError = null
                            }
                        },
                        label = { Text("Total Tagihan Nota (Rp)") },
                        isError = totalAmountError != null,
                        supportingText = totalAmountError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        placeholder = { Text("misal: 60000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("total_amount_field"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    // GPS integration module
                    Text(
                        text = "Lokasi Pembelian (GPS Automatic)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = locationName,
                            onValueChange = { locationName = it },
                            placeholder = { Text("Ketik nama toko / deteksi GPS") },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("location_field"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            trailingIcon = {
                                if (locationName.isNotEmpty()) {
                                    IconButton(onClick = {
                                        locationName = ""
                                        latitude = null
                                        longitude = null
                                    }) {
                                        Icon(imageVector = Icons.Default.Cancel, contentDescription = "Hapus")
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Trigger coordinates button
                        Button(
                            onClick = {
                                val permissionsNeeded = arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                                if (permissionsNeeded.any {
                                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                    }) {
                                    permissionLauncher.launch(permissionsNeeded)
                                } else {
                                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                                    val isGpsEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true
                                    val isNetworkEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
                                    if (!isGpsEnabled && !isNetworkEnabled) {
                                        Toast.makeText(context, "⚠️ Peringatan: Layanan Lokasi (GPS) Anda dinonaktifkan. Silakan aktifkan GPS HP Anda terlebih dahulu!", Toast.LENGTH_LONG).show()
                                    } else {
                                        viewModel.fetchLocation(locationHelper)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            if (isTrackingGps) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "Find Location Tracker",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Section 2: Invoice File scan
                    Text(
                        text = "Bukti Struk Pembelian (Kamera / Galeri)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    if (imageUriStr.isNullOrEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Button(
                                onClick = {
                                    val tempFile = File(context.cacheDir, "receipt_temp_${System.currentTimeMillis()}.jpg")
                                    tempCameraUri = FileProvider.getUriForFile(
                                        context,
                                        "com.patunganyuk.tysxvb.fileprovider",
                                        tempFile
                                    )
                                    cameraLauncher.launch(tempCameraUri!!)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Take Photo", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kamera", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Choose Gallery", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Galeri", fontSize = 12.sp)
                            }
                        }
                    } else {
                        // Display attached thumbnail file
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            AsyncImage(
                                model = com.example.utils.FileStorageHelper.parseImageUri(imageUriStr),
                                contentDescription = "Receipt Image Invoice",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { imageUriStr = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Hapus Struk",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Optional Menu Items Details
        item {
            MenuItemInputSection(
                menuItems = menuItemsStateList,
                participants = participantsList,
                onSyncSplits = { totalDraftSum, finalSplits ->
                    totalAmountStr = totalDraftSum.toInt().toString()
                    participantsList.clear()
                    finalSplits.forEach { (name, amount) ->
                        participantsList.add(ParticipantState(name, amount.toInt().toString()))
                    }
                    splitMode = SplitMode.INDIVIDUAL
                }
            )
        }

        // Section 2: Split configuration Engine
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "METODE BAGI BILL",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Segmented selection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Button(
                            onClick = { splitMode = SplitMode.EQUAL },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (splitMode == SplitMode.EQUAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                contentColor = if (splitMode == SplitMode.EQUAL) Color.White else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Bagi Rata (Equal)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { splitMode = SplitMode.INDIVIDUAL },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (splitMode == SplitMode.INDIVIDUAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                contentColor = if (splitMode == SplitMode.INDIVIDUAL) Color.White else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Manual (Itemized)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Helper breakdown calculation tracker for Individual allocation
                    if (splitMode == SplitMode.INDIVIDUAL) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (remainingToAllocate == 0.0) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Status Pembagian Manual", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    text = "Terdistribusi: ${formatRupiah(totalAllocated)} (dari ${formatRupiah(totalInvoice)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                            }
                            Text(
                                text = when {
                                    remainingToAllocate == 0.0 -> "Sesuai! (Cocok)"
                                    remainingToAllocate > 0.0 -> "Belum Terbagi: ${formatRupiah(remainingToAllocate)}"
                                    else -> "Kelebihan: ${formatRupiah(Math.abs(remainingToAllocate))}"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                color = if (remainingToAllocate == 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Teammate input box details
                    Text(
                        text = "Tambah Anggota Patungan",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        OutlinedTextField(
                            value = currentTeammateName,
                            onValueChange = { currentTeammateName = it },
                            placeholder = { Text("Nama Teman") },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("friend_name_field"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        if (splitMode == SplitMode.INDIVIDUAL) {
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedTextField(
                                value = currentTeammateAmount,
                                onValueChange = {
                                    if (it.all { char -> char.isDigit() }) {
                                        currentTeammateAmount = it
                                    }
                                },
                                placeholder = { Text("Nominal (Rp)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("friend_amount_field"),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (currentTeammateName.isBlank()) {
                                    Toast.makeText(context, "Ketik nama teman terlebih dulu!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (splitMode == SplitMode.INDIVIDUAL && currentTeammateAmount.isBlank()) {
                                    Toast.makeText(context, "Ketik nominal bagian teman!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val amountValue = if (splitMode == SplitMode.EQUAL) "0" else currentTeammateAmount
                                participantsList.add(
                                    ParticipantState(
                                        name = currentTeammateName.trim(),
                                        amount = amountValue
                                    )
                                )
                                currentTeammateName = ""
                                currentTeammateAmount = ""
                                participantError = null
                            },
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Add Person")
                        }
                    }

                    // Friends Checklist error
                    participantError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (processedParticipantsList.isEmpty()) {
                        Text(
                            text = "Belum ada teman ditambahkan. Silahkan input nama teman di atas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    } else {
                        Text(
                            text = "Rincian Tagihan Anggota (${processedParticipantsList.size} Orang):",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Dynamic roster details
                        processedParticipantsList.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.02f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(text = item.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                        val actualShare = item.amount.toDoubleOrNull() ?: 0.0
                                        Text(text = "Patungan: ${formatRupiah(actualShare)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        participantToEditIndex = index
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit teman",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(onClick = {
                                        participantsList.removeAt(index)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.RemoveCircleOutline,
                                            contentDescription = "Hapus teman",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (participantToEditIndex != null) {
                        val idx = participantToEditIndex!!
                        if (idx in participantsList.indices) {
                            val participant = participantsList[idx]
                            var editName by remember(idx) { mutableStateOf(participant.name) }
                            var editAmount by remember(idx) { mutableStateOf(participant.amount) }

                            AlertDialog(
                                onDismissRequest = { participantToEditIndex = null },
                                title = { Text("Edit Anggota Patungan", fontWeight = FontWeight.Bold) },
                                text = {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = editName,
                                            onValueChange = { editName = it },
                                            label = { Text("Nama Teman") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            singleLine = true
                                        )
                                        if (splitMode == SplitMode.INDIVIDUAL) {
                                            OutlinedTextField(
                                                value = editAmount,
                                                onValueChange = {
                                                    if (it.all { char -> char.isDigit() }) {
                                                        editAmount = it
                                                    }
                                                },
                                                label = { Text("Nominal Bagian (Rp)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp),
                                                singleLine = true
                                            )
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            if (editName.isBlank()) {
                                                Toast.makeText(context, "Nama harus terisi!", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (splitMode == SplitMode.INDIVIDUAL && editAmount.isBlank()) {
                                                Toast.makeText(context, "Nominal harus terisi!", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            participantsList[idx] = ParticipantState(
                                                name = editName.trim(),
                                                amount = if (splitMode == SplitMode.EQUAL) "0" else editAmount
                                            )
                                            participantToEditIndex = null
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Simpan")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { participantToEditIndex = null }) {
                                        Text("Batal")
                                    }
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        } else {
                            participantToEditIndex = null
                        }
                    }
                }
            }
        }

        // Section 3: Save button trigger form
        item {
            Button(
                onClick = {
                    var hasError = false
                    if (title.isBlank()) {
                        titleError = "Nama acara harus terisi!"
                        hasError = true
                    }
                    val price = totalAmountStr.toDoubleOrNull() ?: 0.0
                    if (price <= 0.0) {
                        totalAmountError = "Masukkan total nota belanja!"
                        hasError = true
                    }
                    if (processedParticipantsList.isEmpty()) {
                        participantError = "Setidaknya tambahkan 1 teman patungan!"
                        hasError = true
                    }

                    if (splitMode == SplitMode.INDIVIDUAL) {
                        val diff = totalInvoice - processedParticipantsList.sumOf { item -> item.amount.toDoubleOrNull() ?: 0.0 }
                        if (Math.abs(diff) > 1.0) { // Keep safety tolerance 1 rupiah
                            participantError = "Total pembagian manual harus cocok dengan total tagihan nota!"
                            hasError = true
                        }
                    }

                    if (hasError) return@Button

                    val participantEntities = processedParticipantsList.map { item ->
                        val isCheckedPaid = billToEdit?.participants?.find { p -> p.name == item.name }?.isPaid ?: false
                        Participant(
                            name = item.name,
                            amountToPay = item.amount.toDoubleOrNull() ?: 0.0,
                            isPaid = isCheckedPaid
                        )
                    }

                    viewModel.saveBill(
                        id = billToEdit?.id ?: 0,
                        title = title.trim(),
                        totalAmount = price,
                        locationName = locationName.trim().ifBlank { null },
                        latitude = latitude,
                        longitude = longitude,
                        imageUri = imageUriStr,
                        participants = participantEntities,
                        menuItems = menuItemsStateList.toList(),
                        onSuccess = {
                            Toast.makeText(context, "Patungan disimpan successfully!", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("submit_form_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = "Simpan")
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isEditMode) "Update Catatan Patungan" else "Simpan Patungan", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WhatsAppShareDialog(
    participant: Participant,
    bill: BillEntity,
    onDismiss: () -> Unit,
    onShareText: (String) -> Unit
) {
    var selectedTone by remember { mutableStateOf(0) } // 0: Santai, 1: Formal, 2: Galak

    val santaiMessage = "Woi ${participant.name}! Inget patungan *${bill.title}* kemarin? Bagian lu *${formatRupiah(participant.amountToPay)}* nih. Transfer ke rekening gw ya. Thank you! 🙏😊"
    
    val formalMessage = "Selamat pagi/siang. Mengingat kembali untuk pengisian tagihan kegiatan *${bill.title}* sebesar *${formatRupiah(participant.amountToPay)}*. Mohon kesediaannya untuk segera ditransfer ke rekening. Terima kasih atas kerja samanya."
    
    val galakMessage = "⚡ *Pemberitahuan Dompet Krisis* ⚡\nTagihan makan *${bill.title}* kemarin belum lunas nih Bos! Bagian lu sebesar *${formatRupiah(participant.amountToPay)}*! Tolong segera ditransfer secepatnya ya sebelum pertemanan kita digugat di pengadilan mahasiswa! 😂💸"

    var customMessage by remember(selectedTone, participant.amountToPay, bill.title) {
        val initialMsg = when (selectedTone) {
            0 -> santaiMessage
            1 -> formalMessage
            else -> galakMessage
        }
        mutableStateOf(initialMsg)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Nagih Tanpa Canggung",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Pilih gaya bahasa yang pas buat nagih ke ${participant.name}:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Selection row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tones = listOf("Bestie (Santai)", "Formal", "Humoris (Galak)")
                    tones.forEachIndexed { index, toneName ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selectedTone == index) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                )
                                .clickable { selectedTone = index }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = toneName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTone == index) Color.White else MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Text Area Preview & Custom Edit Input
                OutlinedTextField(
                    value = customMessage,
                    onValueChange = { customMessage = it },
                    label = { Text("Ubah / Sesuaikan Pesan Nagih", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(12.dp),
                    minLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onShareText(customMessage) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kirim WhatsApp", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
