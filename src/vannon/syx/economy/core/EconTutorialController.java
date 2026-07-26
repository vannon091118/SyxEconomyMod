package vannon.syx.economy.core;

public final class EconTutorialController {
    public enum Stage {
        NONE,
        WELCOME_WAGES,
        WAREHOUSE_MODE,
        TAXES_FISCAL,
        EMERGENCY_ACTIONS,
        COMPLETED
    }

    private Stage currentStage = Stage.NONE;
    private double timer = 0.0;
    private boolean active = true;

    public void update(double ds) {
        if (!active || currentStage == Stage.COMPLETED) return;
        timer += ds;

        if (currentStage == Stage.NONE && timer >= 10.0) {
            currentStage = Stage.WELCOME_WAGES;
        } else if (currentStage == Stage.WELCOME_WAGES && timer >= 120.0) {
            currentStage = Stage.WAREHOUSE_MODE;
        } else if (currentStage == Stage.WAREHOUSE_MODE && timer >= 300.0) {
            currentStage = Stage.TAXES_FISCAL;
        } else if (currentStage == Stage.TAXES_FISCAL && timer >= 600.0) {
            currentStage = Stage.EMERGENCY_ACTIONS;
        }
    }

    public Stage currentStage() { return currentStage; }
    public void dismissCurrent() {
        if (currentStage == Stage.EMERGENCY_ACTIONS) {
            currentStage = Stage.COMPLETED;
        } else {
            timer += 100.0;
        }
    }
    public void disableTutorial() { active = false; }
    public boolean isActive() { return active; }
}
