package th.ac.tu.cs364.wordward;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;

/** Owns one exit dialog. Cancelling or confirming never resets game data. */
final class ExitConfirmation {
    private final Activity activity;
    private AlertDialog dialog;
    private Runnable unregister;

    ExitConfirmation(Activity activity) {
        this.activity = activity;
        if (Build.VERSION.SDK_INT >= 33) unregister = Api33.register(activity, this::show);
    }

    void show() {
        if (activity.isFinishing() || activity.isDestroyed()
                || (dialog != null && dialog.isShowing())) return;
        dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.exit_confirm_title)
                .setMessage(R.string.exit_confirm_message)
                .setNegativeButton(R.string.exit_confirm_cancel, (d, which) -> d.dismiss())
                .setPositiveButton(R.string.exit_confirm_positive, (d, which) -> activity.finish())
                .create();
        dialog.setOnDismissListener(d -> dialog = null);
        dialog.show();
    }

    void dismiss() { if (dialog != null) dialog.dismiss(); }

    void close() {
        dismiss();
        if (unregister != null) {
            unregister.run();
            unregister = null;
        }
    }

    /** API 24–32 never load the API 33 callback types. */
    @android.annotation.TargetApi(33)
    private static final class Api33 {
        static Runnable register(Activity activity, Runnable show) {
            android.window.OnBackInvokedCallback callback = show::run;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return () -> activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(callback);
        }
    }
}
