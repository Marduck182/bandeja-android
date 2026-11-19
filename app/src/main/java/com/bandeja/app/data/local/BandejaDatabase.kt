package com.bandeja.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CategoryEntity::class, ContentEntity::class, TagEntity::class, ContentTagCrossRef::class],
    version = 1,
    exportSchema = true,
)
abstract class BandejaDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun contentDao(): ContentDao
    abstract fun tagDao(): TagDao

    companion object {
        const val NAME = "bandeja.db"

        fun create(context: Context): BandejaDatabase =
            Room.databaseBuilder(context.applicationContext, BandejaDatabase::class.java, NAME)
                .addCallback(SeedCallback)
                .build()
    }
}

/** Starter categories, written once when the database is created. From then on they belong to the user. */
private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        DefaultCategories.all.forEachIndexed { index, seed ->
            db.execSQL(
                "INSERT INTO categories (name, icon, color, sortOrder, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(seed.name, seed.icon, seed.color, index, now, now),
            )
        }
    }
}

data class CategorySeed(val name: String, val icon: String, val color: String)

object DefaultCategories {
    val all = listOf(
        CategorySeed("Comidas", "🍔", "coral"),
        CategorySeed("Ejercicios", "🏋️", "emerald"),
        CategorySeed("Programación", "💻", "indigo"),
        CategorySeed("Aprendizaje", "📚", "amber"),
        CategorySeed("Videojuegos", "🎮", "violet"),
        CategorySeed("Viajes", "✈️", "sky"),
        CategorySeed("Ideas", "💡", "pink"),
    )
}
