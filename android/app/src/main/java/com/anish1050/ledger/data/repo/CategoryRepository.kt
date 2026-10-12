package com.anish1050.ledger.data.repo

import com.anish1050.ledger.data.db.CategoryDao
import com.anish1050.ledger.data.db.CategoryEntity
import kotlinx.coroutines.flow.Flow

class CategoryRepository(private val dao: CategoryDao) {
    val all: Flow<List<CategoryEntity>> = dao.all()
}
