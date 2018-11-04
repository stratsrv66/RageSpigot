package me.joeleoli.ragespigot.util;

public class BooleanUtil {

    public static boolean isBoolean(String s) {
        return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false");
    }

}
