package dev.zmdld11.guitardojo.data.training

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import dev.zmdld11.guitardojo.core.plan.AttemptKind
import dev.zmdld11.guitardojo.core.plan.AttemptRecord
import dev.zmdld11.guitardojo.core.plan.Mastery
import dev.zmdld11.guitardojo.core.plan.PhraseProgress
import dev.zmdld11.guitardojo.core.plan.SelfRating

/**
 * 训练记录表。表结构与 docs/M4-每日训练流设计.md §5.1 对应；M5 接入后只加列/加表，不改现有字段语义。
 * 卡片进度不在这里：数据量小且需要同步读，继续用 SharedPreferences（见 CardProgressStore）。
 */
@Entity(tableName = "training_day")
data class TrainingDayEntity(
    @PrimaryKey val epochDay: Long,
    val kind: String,
    val planJson: String,
    val currentStep: Int,
    val completedSteps: String,
    val practiceSeconds: Long,
    /** 当日调音是否通过；跳过记 false。无琴日恒为 true。 */
    val tuningPassed: Boolean,
    /** 无琴日「下次要注意什么」的一句记录。 */
    val note: String,
    val startedAtMillis: Long?,
    val finishedAtMillis: Long?,
)

@Entity(tableName = "phrase_progress")
data class PhraseProgressEntity(
    @PrimaryKey val phraseId: String,
    val currentBpm: Double,
    val targetBpm: Double,
    val cleanStreak: Int,
    val consecutiveFails: Int,
    val passDays: Int,
    val failDays: Int,
    val lastPassEpochDay: Long?,
    val lastFailEpochDay: Long?,
    val speedUpMarker: Int,
    val lastPracticedEpochDay: Long?,
    val nextReviewEpochDay: Long?,
    val reviewStage: Int,
    val inMaintenance: Boolean,
    val mastery: String,
)

/** 一次考核/复习的记录；scoreAccuracy / scoreRhythm 留给 M5 自动评分。 */
@Entity(tableName = "attempt")
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phraseId: String,
    val epochDay: Long,
    val kind: String,
    val bpm: Double,
    val clean: Boolean,
    val selfRating: String,
    val reachedTargetSpeed: Boolean,
    val tuningPassed: Boolean,
    val scoreAccuracy: Double? = null,
    val scoreRhythm: Double? = null,
)

fun PhraseProgressEntity.toModel(): PhraseProgress =
    PhraseProgress(
        phraseId = phraseId,
        currentBpm = currentBpm,
        targetBpm = targetBpm,
        cleanStreak = cleanStreak,
        consecutiveFails = consecutiveFails,
        passDays = passDays,
        failDays = failDays,
        lastPassEpochDay = lastPassEpochDay,
        lastFailEpochDay = lastFailEpochDay,
        speedUpMarker = speedUpMarker,
        lastPracticedEpochDay = lastPracticedEpochDay,
        nextReviewEpochDay = nextReviewEpochDay,
        reviewStage = reviewStage,
        inMaintenance = inMaintenance,
        mastery = Mastery.valueOf(mastery),
    )

fun PhraseProgress.toEntity(): PhraseProgressEntity =
    PhraseProgressEntity(
        phraseId = phraseId,
        currentBpm = currentBpm,
        targetBpm = targetBpm,
        cleanStreak = cleanStreak,
        consecutiveFails = consecutiveFails,
        passDays = passDays,
        failDays = failDays,
        lastPassEpochDay = lastPassEpochDay,
        lastFailEpochDay = lastFailEpochDay,
        speedUpMarker = speedUpMarker,
        lastPracticedEpochDay = lastPracticedEpochDay,
        nextReviewEpochDay = nextReviewEpochDay,
        reviewStage = reviewStage,
        inMaintenance = inMaintenance,
        mastery = mastery.name,
    )

fun AttemptEntity.toModel(): AttemptRecord =
    AttemptRecord(
        phraseId = phraseId,
        epochDay = epochDay,
        kind = AttemptKind.valueOf(kind),
        bpm = bpm,
        clean = clean,
        selfRating = SelfRating.valueOf(selfRating),
        reachedTargetSpeed = reachedTargetSpeed,
        tuningPassed = tuningPassed,
    )

fun AttemptRecord.toEntity(tuningPassed: Boolean): AttemptEntity =
    AttemptEntity(
        phraseId = phraseId,
        epochDay = epochDay,
        kind = kind.name,
        bpm = bpm,
        clean = clean,
        selfRating = selfRating.name,
        reachedTargetSpeed = reachedTargetSpeed,
        tuningPassed = tuningPassed,
    )

@Dao
interface TrainingDayDao {
    @Query("SELECT * FROM training_day WHERE epochDay = :epochDay")
    suspend fun get(epochDay: Long): TrainingDayEntity?

    @Query("SELECT * FROM training_day WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    suspend fun range(from: Long, to: Long): List<TrainingDayEntity>

    @Query("SELECT * FROM training_day WHERE finishedAtMillis IS NOT NULL ORDER BY epochDay")
    suspend fun finished(): List<TrainingDayEntity>

    @Upsert
    suspend fun upsert(entity: TrainingDayEntity)
}

@Dao
interface PhraseProgressDao {
    @Query("SELECT * FROM phrase_progress")
    suspend fun all(): List<PhraseProgressEntity>

    @Query("SELECT * FROM phrase_progress WHERE phraseId = :phraseId")
    suspend fun get(phraseId: String): PhraseProgressEntity?

    @Upsert
    suspend fun upsert(entity: PhraseProgressEntity)
}

@Dao
interface AttemptDao {
    @Insert
    suspend fun insert(entity: AttemptEntity)

    @Query("SELECT * FROM attempt WHERE epochDay >= :from ORDER BY epochDay, id")
    suspend fun since(from: Long): List<AttemptEntity>

    @Query("SELECT * FROM attempt WHERE epochDay = :epochDay ORDER BY id")
    suspend fun byDay(epochDay: Long): List<AttemptEntity>

    @Query("SELECT * FROM attempt ORDER BY epochDay, id")
    suspend fun all(): List<AttemptEntity>
}

@Database(
    entities = [TrainingDayEntity::class, PhraseProgressEntity::class, AttemptEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TrainingDatabase : RoomDatabase() {
    abstract fun trainingDayDao(): TrainingDayDao

    abstract fun phraseProgressDao(): PhraseProgressDao

    abstract fun attemptDao(): AttemptDao

    companion object {
        @Volatile
        private var instance: TrainingDatabase? = null

        fun get(context: Context): TrainingDatabase =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }

        private fun create(context: Context): TrainingDatabase =
            Room
                .databaseBuilder(context, TrainingDatabase::class.java, "guitar-dojo.db")
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
