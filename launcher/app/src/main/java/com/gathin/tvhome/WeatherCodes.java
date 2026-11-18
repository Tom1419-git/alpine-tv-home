package com.gathin.tvhome;

/** Codes meteo WMO d'Open-Meteo vers la condition visuelle de l'accueil. Un code inconnu donne {@code OVERCAST}. */
final class WeatherCodes {
    enum Condition { CLEAR, PARTLY, OVERCAST, FOG, DRIZZLE, RAIN, SHOWERS, SNOW, STORM }

    private WeatherCodes() {
    }

    static Condition map(int code) {
        if (code == 0 || code == 1) return Condition.CLEAR;
        if (code == 2) return Condition.PARTLY;
        if (code == 3) return Condition.OVERCAST;
        if (code == 45 || code == 48) return Condition.FOG;
        if (code >= 51 && code <= 57) return Condition.DRIZZLE;
        if (code >= 61 && code <= 67) return Condition.RAIN;
        if (code >= 80 && code <= 82) return Condition.SHOWERS;   // averses : l'intensite monte et descend par vagues
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Condition.SNOW;
        if (code >= 95 && code <= 99) return Condition.STORM;
        return Condition.OVERCAST;   // neutre : un code inconnu ne casse rien
    }

    static Condition parse(String name) {
        if (name != null) {
            for (Condition c : Condition.values()) {
                if (c.name().equalsIgnoreCase(name.trim())) return c;
            }
        }
        return null;
    }
}
