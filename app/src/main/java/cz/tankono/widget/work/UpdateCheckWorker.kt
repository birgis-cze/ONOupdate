package cz.tankono.widget.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.notify.Notifier
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.util.UpdateChecker
import java.util.Calendar

/**
 * Worker pro denní kontrolu nových verzí aplikace na GitHubu.
 * 
 * Logika:
 * - Spustí se 1× denně kolem 15:00.
 * - NOCNÍ POJISTKA: Pokud se z nějakého důvodu probudí v noci (mezi 21:00 a 08:00),
 *   notifikaci NEPOŠLE a raději si naplánuje nový běh na 15:00.
 * - Upozornění posílá až po X (proměnná MIN_DAYS_BEFORE_NOTIFY) dnech ignorování a jen jednou pro danou verzi.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        /** Po kolika dnech čekání poslat upozornění */
        private const val MIN_DAYS_BEFORE_NOTIFY = 3
        
        /** Kolik ms je jeden den */
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }

    override suspend fun doWork(): Result {
        AppLogger.i("=== UpdateCheckWorker START (id=$id) ===")

        try {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            
            // NOCNÍ POJISTKA: Pokud je noc (mezi 21:00 a 08:00), notifikaci neposíláme!
            if (currentHour < 8 || currentHour >= 21) {
                AppLogger.w("UpdateCheckWorker: Je noc ($currentHour:00) – přesouvám notifikaci na zítřejší 15:00.")
                // Sám si naplánuje nový běh na zítřejší 15:00
                WorkScheduler.scheduleUpdateCheck(applicationContext)
                return Result.success()
            }

            val info = UpdateChecker.checkForUpdate()
            
            if (info == null) {
                AppLogger.d("UpdateCheckWorker: máš nejnovější verzi, uklízím stale data")
                SettingsStore(applicationContext).clearPendingUpdateInfo()
                // Naplánujeme zítřejší běh
                WorkScheduler.scheduleUpdateCheck(applicationContext)
                return Result.success()
            }

            val pending = SettingsStore(applicationContext).getPendingUpdateInfo()
            val now = System.currentTimeMillis()

            if (pending == null || pending.first != info.version) {
                AppLogger.i("UpdateCheckWorker: nová verze ${info.version} detekována, začínám počítat $MIN_DAYS_BEFORE_NOTIFY dny.")
                SettingsStore(applicationContext).savePendingUpdateInfo(info.version, now)
            } else {
                val elapsedMs = now - pending.second
                val elapsedDays = elapsedMs / DAY_MS
                val lastNotified = SettingsStore(applicationContext).getLastNotifiedVersion()

                AppLogger.d("UpdateCheckWorker: verze ${info.version} čeká $elapsedDays dní, min=$MIN_DAYS_BEFORE_NOTIFY, notified=$lastNotified")

                if (elapsedDays >= MIN_DAYS_BEFORE_NOTIFY && lastNotified != info.version) {
                    AppLogger.i("UpdateCheckWorker: posílám tiché upozornění na verzi ${info.version}")
                    Notifier.notifyUpdateAvailable(applicationContext, info.version)
                    SettingsStore(applicationContext).saveLastNotifiedVersion(info.version)
                } else {
                    AppLogger.d("UpdateCheckWorker: podmínky pro notifikaci nesplněny.")
                }
            }

            AppLogger.i("=== UpdateCheckWorker DONE ===")
            
            // Naplánujeme další běh na zítřejší 15:00
            WorkScheduler.scheduleUpdateCheck(applicationContext)
            
            return Result.success()
        } catch (t: Throwable) {
            AppLogger.e("UpdateCheckWorker: chyba", t)
            // Zkusíme to znovu za hodinu (i když to není přesně 15:00)
            return Result.retry()
        }
    }
}