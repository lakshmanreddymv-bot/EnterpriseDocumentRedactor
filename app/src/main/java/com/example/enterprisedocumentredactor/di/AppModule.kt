package com.example.enterprisedocumentredactor.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.enterprisedocumentredactor.data.local.DocumentDao
import com.example.enterprisedocumentredactor.data.local.DocumentDatabase
import com.example.enterprisedocumentredactor.data.pdf.PdfRedactor
import com.example.enterprisedocumentredactor.data.repository.DocumentRepositoryImpl
import com.example.enterprisedocumentredactor.domain.repository.DocumentRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    companion object {

        // Adds redactedPath column (version 1 → 2)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE documents ADD COLUMN redactedPath TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        @Provides
        @Singleton
        fun provideDocumentDatabase(@ApplicationContext context: Context): DocumentDatabase =
            Room.databaseBuilder(context, DocumentDatabase::class.java, "document_db")
                .addMigrations(MIGRATION_1_2)
                .build()

        @Provides
        @Singleton
        fun provideDocumentDao(db: DocumentDatabase): DocumentDao = db.documentDao()

        // PiiDetector, ModelDownloadHelper, and DocumentScanner are auto-provided
        // via their @Singleton @Inject constructors — no explicit @Provides needed.

        @Provides
        fun providePdfRedactor(@ApplicationContext context: Context): PdfRedactor =
            PdfRedactor(context)
    }
}
