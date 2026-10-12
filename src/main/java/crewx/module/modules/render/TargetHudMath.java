package crewx.module.modules.render;


final class TargetHudMath {

    static final float DRAW_MARGIN = 1.0F;

    private TargetHudMath() {
    }


    static char verdict(float ownHealth, float targetHealth) {
        float diff = ownHealth - targetHealth;
        if (diff > DRAW_MARGIN) return 'W';
        if (diff < -DRAW_MARGIN) return 'L';
        return 'D';
    }





    static float animateHealth(float shown, float real, float dtSeconds) {
        if (Float.isNaN(shown)) return real;
        float speed = real < shown ? 6.0F : 10.0F;
        float next = shown + (real - shown) * (1.0F - (float) Math.exp(-dtSeconds * speed));
        return Math.abs(next - real) < 0.01F ? real : next;
    }


    static float fraction(float health, float maxHealth) {
        if (maxHealth <= 0.0F) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, health / maxHealth));
    }
}
