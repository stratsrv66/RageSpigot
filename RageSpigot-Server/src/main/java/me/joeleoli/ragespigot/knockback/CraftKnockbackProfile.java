package me.joeleoli.ragespigot.knockback;

import lombok.Getter;
import lombok.Setter;
import me.joeleoli.ragespigot.RageSpigot;

@Getter
@Setter
public class CraftKnockbackProfile implements KnockbackProfile {

    private String name;
    private double friction = 2.0D;
    private double horizontal = 0.35D;
    private double vertical = 0.35D;
    private double verticalLimit = 0.4D;
    private double extraHorizontal = 0.425D;
    private double extraVertical = 0.085D;

    public CraftKnockbackProfile(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String[] getValues() {
        return new String[]{
                "Friction: " + this.friction,
                "Horizontal: " + this.horizontal,
                "Vertical: " + this.vertical,
                "Vertical Limit: " + this.verticalLimit,
                "Extra Horizontal: " + this.extraHorizontal,
                "Extra Vertical: " + this.extraVertical,
        };
    }

    public void save() {
        final String path = "knockback.profiles." + this.name;

        RageSpigot.INSTANCE.getConfig().set(path + ".friction", this.friction);
        RageSpigot.INSTANCE.getConfig().set(path + ".horizontal", this.horizontal);
        RageSpigot.INSTANCE.getConfig().set(path + ".vertical", this.vertical);
        RageSpigot.INSTANCE.getConfig().set(path + ".vertical-limit", this.verticalLimit);
        RageSpigot.INSTANCE.getConfig().set(path + ".extra-horizontal", this.extraHorizontal);
        RageSpigot.INSTANCE.getConfig().set(path + ".extra-vertical", this.extraVertical);
        RageSpigot.INSTANCE.getConfig().save();
    }

}
