package com.example.data.repository

import com.example.data.local.BillDao
import com.example.data.local.BillEntity
import kotlinx.coroutines.flow.Flow

class BillRepository(private val billDao: BillDao) {

    fun getAllBills(userId: Int): Flow<List<BillEntity>> {
        return billDao.getAllBills(userId)
    }

    fun searchBills(userId: Int, query: String): Flow<List<BillEntity>> {
        return billDao.searchBills(userId, "%$query%")
    }

    suspend fun insert(bill: BillEntity): Long {
        return billDao.insertBill(bill)
    }

    suspend fun update(bill: BillEntity) {
        billDao.updateBill(bill)
    }

    suspend fun deleteById(id: Int) {
        billDao.deleteBillById(id)
    }

    suspend fun getBillById(id: Int): BillEntity? {
        return billDao.getBillById(id)
    }
}
