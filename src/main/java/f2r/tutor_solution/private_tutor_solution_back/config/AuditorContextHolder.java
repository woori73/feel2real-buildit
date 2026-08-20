package f2r.tutor_solution.private_tutor_solution_back.config;

public class AuditorContextHolder {

    private static final ThreadLocal<String> CURRENT_AUDITOR = new ThreadLocal<>();

    public static void set(String loginId) {
        CURRENT_AUDITOR.set(loginId);
    }

    public static String get() {
        return CURRENT_AUDITOR.get();
    }

    public static void clear() {
        CURRENT_AUDITOR.remove();
    }
}
