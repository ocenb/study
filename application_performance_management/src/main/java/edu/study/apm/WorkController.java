package edu.study.apm;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

@RestController
class WorkController {
    private static final long REQUEST_TIMEOUT_MS = 30_000;
    private static final long WORK_DURATION_MS = 1_000;
    private final AsyncTaskExecutor platformExecutor;
    private final AsyncTaskExecutor virtualExecutor;

    WorkController(
            @Qualifier("platformExecutor") AsyncTaskExecutor platformExecutor,
            @Qualifier("virtualExecutor") AsyncTaskExecutor virtualExecutor) {
        this.platformExecutor = platformExecutor;
        this.virtualExecutor = virtualExecutor;
    }

    @GetMapping("/work/platform")
    WebAsyncTask<WorkResult> platform() {
        return new WebAsyncTask<>(REQUEST_TIMEOUT_MS, platformExecutor, this::process);
    }

    @GetMapping("/work/virtual")
    WebAsyncTask<WorkResult> virtual() {
        return new WebAsyncTask<>(REQUEST_TIMEOUT_MS, virtualExecutor, this::process);
    }

    private WorkResult process() throws InterruptedException {
        Thread.sleep(WORK_DURATION_MS);
        return new WorkResult(Thread.currentThread().isVirtual());
    }

    record WorkResult(boolean virtualThread) {}
}
