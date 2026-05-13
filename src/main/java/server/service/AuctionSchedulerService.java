package server.service;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * AuctionSchedulerService — tự động chuyển trạng thái phiên theo thời gian.
 *
 * Trách nhiệm:
 *  - Khi Admin approve phiên → scheduleAuction(auctionId, startTime, endTime)
 *  - Đến startTime → gọi lifecycleService.startAuction()  (OPEN → RUNNING)
 *  - Đến endTime   → gọi lifecycleService.finishAuction() (RUNNING → FINISHED/CANCELED)
 *
 * Đổi từ newScheduledThreadPool(4) → newSingleThreadScheduledExecutor():
 *  - Phiên đấu giá không có task CPU-heavy — 1 thread là đủ.
 *  - Single thread tránh race condition giữa các scheduled task.
 *  - Dùng daemon thread để không chặn JVM shutdown.
 *
 * Scheduler KHÔNG:
 *  - Đọc/ghi DB trực tiếp.
 *  - Biết về bid hay payment.
 *  - Giữ bất kỳ state business nào.
 */
public class AuctionSchedulerService {

    /**
     * Single-thread executor — đủ cho việc trigger start/end phiên.
     * Daemon thread để JVM có thể shutdown sạch khi không còn user thread.
     */
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "AuctionScheduler");
                t.setDaemon(true);
                return t;
            });

    /**
     * Lưu ScheduledFuture để có thể cancel nếu cần.
     * Key: auctionId + ":start" hoặc auctionId + ":end"
     */
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    /**
     * Inject sau khi khởi tạo (hoặc dùng setter injection) để tránh circular dependency.
     * AuctionLifecycleService → AuctionSchedulerService → AuctionLifecycleService
     */
    private AuctionLifeCycleService lifecycleService;

    public void setLifecycleService(AuctionLifeCycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    // ──────────────────────────────────────────────
    // SCHEDULE
    // ──────────────────────────────────────────────

    /**
     * Đặt lịch start và end cho một phiên đấu giá đã được approve.
     * Gọi bởi AuctionLifecycleService.approveAuction() ngay sau khi update DB.
     *
     * @param auctionId ID phiên
     * @param startTime Thời điểm bắt đầu
     * @param endTime   Thời điểm kết thúc
     */
    public void scheduleAuction(String auctionId,
                                LocalDateTime startTime,
                                LocalDateTime endTime) {
        long startDelayMs = delayMs(startTime);
        long endDelayMs   = delayMs(endTime);

        if (startDelayMs < 0) {
            System.err.printf("[Scheduler] startTime đã qua cho phiên %s — bỏ qua schedule start.%n",
                    auctionId);
        } else {
            ScheduledFuture<?> startFuture = scheduler.schedule(
                    () -> safeStart(auctionId),
                    startDelayMs,
                    TimeUnit.MILLISECONDS);
            futures.put(auctionId + ":start", startFuture);
            System.out.printf("[Scheduler] Đã schedule start phiên %s sau %.1f phút%n",
                    auctionId, startDelayMs / 60000.0);
        }

        if (endDelayMs < 0) {
            System.err.printf("[Scheduler] endTime đã qua cho phiên %s — bỏ qua schedule end.%n",
                    auctionId);
        } else {
            ScheduledFuture<?> endFuture = scheduler.schedule(
                    () -> safeFinish(auctionId),
                    endDelayMs,
                    TimeUnit.MILLISECONDS);
            futures.put(auctionId + ":end", endFuture);
            System.out.printf("[Scheduler] Đã schedule end phiên %s sau %.1f phút%n",
                    auctionId, endDelayMs / 60000.0);
        }
    }

    /**
     * Huỷ scheduled task của một phiên (nếu cần — hiện tại spec không dùng).
     * Giữ lại để dễ mở rộng sau này.
     */
    public void cancelSchedule(String auctionId) {
        cancelFuture(auctionId + ":start");
        cancelFuture(auctionId + ":end");
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    // ──────────────────────────────────────────────
    // SAFE WRAPPERS
    // ──────────────────────────────────────────────

    /**
     * Wrapper để exception trong scheduled task không kill executor.
     * ScheduledExecutorService sẽ ngừng chạy task nếu Runnable throw exception.
     */
    private void safeStart(String auctionId) {
        try {
            lifecycleService.startAuction(auctionId);
        } catch (Exception e) {
            System.err.printf("[Scheduler] Lỗi khi startAuction %s: %s%n",
                    auctionId, e.getMessage());
            e.printStackTrace();
        } finally {
            futures.remove(auctionId + ":start");
        }
    }

    private void safeFinish(String auctionId) {
        try {
            lifecycleService.finishAuction(auctionId);
        } catch (Exception e) {
            System.err.printf("[Scheduler] Lỗi khi finishAuction %s: %s%n",
                    auctionId, e.getMessage());
            e.printStackTrace();
        } finally {
            futures.remove(auctionId + ":end");
        }
    }

    // ──────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────

    /** Tính milliseconds từ bây giờ đến targetTime. Âm = đã qua. */
    private long delayMs(LocalDateTime targetTime) {
        long targetEpoch = targetTime.atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        return targetEpoch - System.currentTimeMillis();
    }

    private void cancelFuture(String key) {
        ScheduledFuture<?> f = futures.remove(key);
        if (f != null && !f.isDone()) {
            f.cancel(false); // không interrupt nếu đang chạy
        }
    }
}