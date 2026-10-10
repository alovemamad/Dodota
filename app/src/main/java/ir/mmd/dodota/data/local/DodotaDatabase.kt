package ir.mmd.dodota.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import ir.mmd.dodota.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        DebtEntity::class,
        DebtPaymentEntity::class,
        InstallmentPlanEntity::class,
        InstallmentEntity::class,
        RecurringTransactionEntity::class,
        ReminderEntity::class,
        BudgetEntity::class,
        FinancialGoalEntity::class,
        AssetEntity::class,
        ChecklistEntity::class,
        ChecklistItemEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class DodotaDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun debtDao(): DebtDao
    abstract fun installmentDao(): InstallmentDao
    abstract fun recurringDao(): RecurringDao
    abstract fun reminderDao(): ReminderDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun assetDao(): AssetDao
    abstract fun checklistDao(): ChecklistDao

    companion object {
        @Volatile
        private var INSTANCE: DodotaDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create checklists table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `checklists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `colorHex` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `isPinned` INTEGER NOT NULL,
                        `isArchived` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                // Create checklist_items table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `checklist_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `checklistId` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `estimatedAmount` INTEGER NOT NULL,
                        `isChecked` INTEGER NOT NULL,
                        `isExpenseConverted` INTEGER NOT NULL,
                        `transactionId` INTEGER,
                        `createdAtMillis` INTEGER NOT NULL,
                        FOREIGN KEY(`checklistId`) REFERENCES `checklists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_items_checklistId` ON `checklist_items` (`checklistId`)")
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `debts` ADD COLUMN `isCheck` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `debts` ADD COLUMN `checkNumber` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `cardNumber` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `shebaNumber` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `accountNumber` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `cvv2` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `expiryDate` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `cardHolderName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `installment_plans` ADD COLUMN `totalLoanAmount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("INSERT INTO `categories` (`name`, `type`, `iconName`, `colorHex`, `isDefault`, `parentCategoryId`) VALUES ('انتقال به شخص دیگر', 'EXPENSE', 'swap_horiz', '#0EA5E9', 1, NULL)")
            }
        }

        fun getDatabase(context: Context): DodotaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DodotaDatabase::class.java,
                    "dodota_finance.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(true)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .addCallback(DatabaseCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val context: Context
        ) : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val database = INSTANCE ?: getDatabase(context)
                        populateDefaultCategories(database.categoryDao())
                    } catch (_: Exception) {
                    }
                }
            }

            private suspend fun populateDefaultCategories(categoryDao: CategoryDao) {
                val defaultCategories = listOf(
                    // Expense categories
                    CategoryEntity(name = "خوراک و رستوران", type = TransactionType.EXPENSE, iconName = "restaurant", colorHex = "#EF4444", isDefault = true),
                    CategoryEntity(name = "مسکن و اجاره", type = TransactionType.EXPENSE, iconName = "home", colorHex = "#F59E0B", isDefault = true),
                    CategoryEntity(name = "حمل‌ونقل و بنزین", type = TransactionType.EXPENSE, iconName = "directions_car", colorHex = "#3B82F6", isDefault = true),
                    CategoryEntity(name = "خرید روزمره", type = TransactionType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981", isDefault = true),
                    CategoryEntity(name = "درمان و دارو", type = TransactionType.EXPENSE, iconName = "local_hospital", colorHex = "#EC4899", isDefault = true),
                    CategoryEntity(name = "آموزش و کتاب", type = TransactionType.EXPENSE, iconName = "school", colorHex = "#8B5CF6", isDefault = true),
                    CategoryEntity(name = "تفریح و سرگرمی", type = TransactionType.EXPENSE, iconName = "sports_esports", colorHex = "#06B6D4", isDefault = true),
                    CategoryEntity(name = "قبوض خدماتی", type = TransactionType.EXPENSE, iconName = "receipt", colorHex = "#64748B", isDefault = true),
                    CategoryEntity(name = "اینترنت و بسته", type = TransactionType.EXPENSE, iconName = "wifi", colorHex = "#0284C7", isDefault = true),
                    CategoryEntity(name = "تلفن و شارژ", type = TransactionType.EXPENSE, iconName = "phone", colorHex = "#14B8A6", isDefault = true),
                    CategoryEntity(name = "اشتراک‌ها", type = TransactionType.EXPENSE, iconName = "subscriptions", colorHex = "#A855F7", isDefault = true),
                    CategoryEntity(name = "سفر و اقامت", type = TransactionType.EXPENSE, iconName = "flight", colorHex = "#E11D48", isDefault = true),
                    CategoryEntity(name = "پوشاک و زیبایی", type = TransactionType.EXPENSE, iconName = "checkroom", colorHex = "#D946EF", isDefault = true),
                    CategoryEntity(name = "تعمیرات خودرو", type = TransactionType.EXPENSE, iconName = "build", colorHex = "#78716C", isDefault = true),
                    CategoryEntity(name = "انتقال به شخص دیگر", type = TransactionType.EXPENSE, iconName = "swap_horiz", colorHex = "#0EA5E9", isDefault = true),
                    CategoryEntity(name = "سایر هزینه‌ها", type = TransactionType.EXPENSE, iconName = "more_horiz", colorHex = "#94A3B8", isDefault = true),

                    // Income categories
                    CategoryEntity(name = "حقوق ماهانه", type = TransactionType.INCOME, iconName = "payments", colorHex = "#10B981", isDefault = true),
                    CategoryEntity(name = "پاداش و اضافه کار", type = TransactionType.INCOME, iconName = "card_giftcard", colorHex = "#059669", isDefault = true),
                    CategoryEntity(name = "فروش و بیزنس", type = TransactionType.INCOME, iconName = "store", colorHex = "#3B82F6", isDefault = true),
                    CategoryEntity(name = "سود سرمایه‌گذاری", type = TransactionType.INCOME, iconName = "trending_up", colorHex = "#8B5CF6", isDefault = true),
                    CategoryEntity(name = "یارانه و کمک", type = TransactionType.INCOME, iconName = "volunteer_activism", colorHex = "#F59E0B", isDefault = true),
                    CategoryEntity(name = "سایر درآمدها", type = TransactionType.INCOME, iconName = "savings", colorHex = "#0D9488", isDefault = true)
                )
                categoryDao.insertAll(defaultCategories)
            }
        }
    }
}
