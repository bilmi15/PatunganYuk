package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.BillEntity
import com.example.data.local.Participant
import com.example.data.local.BillMenuItem
import com.example.data.local.UserDao
import com.example.data.local.UserEntity
import com.example.data.repository.BillRepository
import com.example.utils.LocationHelper
import com.example.utils.LocationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BillStats(
    val totalAmount: Double = 0.0,
    val totalCollected: Double = 0.0,
    val totalPending: Double = 0.0 // Piutang
)

@OptIn(ExperimentalCoroutinesApi::class)
class BillViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BillRepository
    private val userDao: UserDao

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Real-time location states representing current GPS tracking progress
    private val _currentLocation = MutableStateFlow<LocationResult?>(null)
    val currentLocation: StateFlow<LocationResult?> = _currentLocation.asStateFlow()

    private val _isTrackingLocation = MutableStateFlow(false)
    val isTrackingLocation: StateFlow<Boolean> = _isTrackingLocation.asStateFlow()

    // Dynamic user account session flow
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("patungan_prefs", Context.MODE_PRIVATE)

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BillRepository(database.billDao())
        userDao = database.userDao()
        
        // Auto-populate a default student account for testing convenience if none exists
        viewModelScope.launch {
            if (userDao.getUserByUsername("bahrul") == null) {
                userDao.insertUser(
                    UserEntity(
                        username = "bahrul",
                        email = "bahrulmuhammad13@gmail.com",
                        passwordPlain = "123456"
                    )
                )
            }
            
            // Restore persistent session
            val savedUserId = sharedPrefs.getInt("logged_in_user_id", -1)
            if (savedUserId != -1) {
                val user = userDao.getUserById(savedUserId)
                if (user != null) {
                    _currentUser.value = user
                }
            }
        }
    }

    // Login action helper
    fun login(usernameOrEmail: String, passwordPlain: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val user = if (usernameOrEmail.contains("@")) {
                userDao.getUserByEmail(usernameOrEmail)
            } else {
                userDao.getUserByUsername(usernameOrEmail)
            }
            if (user == null) {
                onResult(false, "Username atau Email tidak ditemukan!")
            } else if (user.passwordPlain != passwordPlain) {
                onResult(false, "Password Anda salah!")
            } else {
                sharedPrefs.edit().putInt("logged_in_user_id", user.id).apply()
                _currentUser.value = user
                onResult(true, "Selamat datang, ${user.username}!")
            }
        }
    }

    // Register action helper
    fun register(username: String, email: String, passwordPlain: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (username.isBlank() || email.isBlank() || passwordPlain.isBlank()) {
                onResult(false, "Semua data wajib diisi!")
                return@launch
            }
            val existingName = userDao.getUserByUsername(username)
            if (existingName != null) {
                onResult(false, "Username '${username}' sudah digunakan!")
                return@launch
            }
            val existingEmail = userDao.getUserByEmail(email)
            if (existingEmail != null) {
                onResult(false, "Email '${email}' sudah digunakan!")
                return@launch
            }
            val user = UserEntity(username = username, email = email, passwordPlain = passwordPlain)
            val newId = userDao.insertUser(user)
            val registeredUser = user.copy(id = newId.toInt())
            sharedPrefs.edit().putInt("logged_in_user_id", registeredUser.id).apply()
            _currentUser.value = registeredUser
            onResult(true, "Registrasi Akun Mahasiswa berhasil!")
        }
    }

    fun logout() {
        sharedPrefs.edit().remove("logged_in_user_id").apply()
        _currentUser.value = null
    }

    fun updateUserProfile(
        username: String,
        email: String,
        status: String,
        phone: String,
        address: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val current = _currentUser.value
            if (current != null) {
                if (username.isBlank() || email.isBlank()) {
                    onResult(false, "Username dan email tidak boleh kosong!")
                    return@launch
                }
                if (username != current.username) {
                    val existing = userDao.getUserByUsername(username)
                    if (existing != null) {
                        onResult(false, "Username '${username}' sudah digunakan!")
                        return@launch
                    }
                }
                if (email != current.email) {
                    val existing = userDao.getUserByEmail(email)
                    if (existing != null) {
                        onResult(false, "Email '${email}' sudah digunakan!")
                        return@launch
                    }
                }
                val updated = current.copy(
                    username = username,
                    email = email,
                    status = if (status.isBlank()) "User Terverifikasi" else status,
                    phone = phone,
                    address = address
                )
                userDao.insertUser(updated)
                _currentUser.value = updated
                onResult(true, "Profil berhasil diperbarui!")
            } else {
                onResult(false, "Sesi pengguna tidak ditemukan!")
            }
        }
    }

    fun updateUserProfileImage(imageUriStr: String) {
        viewModelScope.launch {
            val current = _currentUser.value
            if (current != null) {
                val updated = current.copy(profileImageUri = imageUriStr)
                userDao.insertUser(updated)
                _currentUser.value = updated
            }
        }
    }

    // Real-time synced list based on search filters and active user session
    val bills: StateFlow<List<BillEntity>> = combine(_currentUser, _searchQuery) { user, query ->
        Pair(user, query)
    }
        .flatMapLatest { (user, query) ->
            val userId = user?.id ?: -1
            if (userId == -1) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                if (query.isBlank()) {
                    repository.getAllBills(userId)
                } else {
                    repository.searchBills(userId, query)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Real-time stats calculated automatically whenever bills flow triggers update
    val stats: StateFlow<BillStats> = bills
        .map { list ->
            var totalAmount = 0.0
            var totalCollected = 0.0
            var totalPending = 0.0
            for (bill in list) {
                totalAmount += bill.totalAmount
                for (p in bill.participants) {
                    if (p.isPaid) {
                        totalCollected += p.amountToPay
                    } else {
                        totalPending += p.amountToPay
                    }
                }
            }
            BillStats(
                totalAmount = totalAmount,
                totalCollected = totalCollected,
                totalPending = totalPending
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BillStats()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun saveBill(
        id: Int = 0,
        title: String,
        totalAmount: Double,
        locationName: String?,
        latitude: Double?,
        longitude: Double?,
        imageUri: String?,
        participants: List<Participant>,
        menuItems: List<BillMenuItem> = emptyList(),
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val currentUserId = _currentUser.value?.id ?: 0
            val bill = BillEntity(
                id = id,
                title = title,
                totalAmount = totalAmount,
                locationName = locationName,
                latitude = latitude,
                longitude = longitude,
                imageUri = imageUri,
                participants = participants,
                menuItems = menuItems,
                userId = currentUserId
            )
            if (id == 0) {
                repository.insert(bill)
            } else {
                repository.update(bill)
            }
            onSuccess()
        }
    }

    fun deleteBill(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun toggleParticipantPaid(billId: Int, participantName: String, isPaid: Boolean) {
        viewModelScope.launch {
            val bill = repository.getBillById(billId)
            if (bill != null) {
                val updatedParticipants = bill.participants.map {
                    if (it.name == participantName) {
                        it.copy(isPaid = isPaid)
                    } else {
                        it
                    }
                }
                repository.update(bill.copy(participants = updatedParticipants))
            }
        }
    }

    fun fetchLocation(locationHelper: LocationHelper) {
        viewModelScope.launch {
            _isTrackingLocation.value = true
            val result = locationHelper.getLastKnownLocation()
            _currentLocation.value = result
            _isTrackingLocation.value = false
        }
    }

    fun clearTrackedLocation() {
        _currentLocation.value = null
    }
}
