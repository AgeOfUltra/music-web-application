package com.music.musicwebapplication.utils;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Component
public class ColorUsageUtil {

    private final Map<String, String> userColors = new HashMap<>();

    private final List<String> colors = List.of(
            "#1a1a1a", "#2d2d2d", "#3d3d3d",
            "#505050", "#636363", "#767676"
    );

    private final Map<String, String> colorMap = Map.of(
            "#1a1a1a", "#0a0a0a",
            "#2d2d2d", "#1a1a1a",
            "#3d3d3d", "#2d2d2d",
            "#505050", "#3d3d3d",
            "#636363", "#505050",
            "#767676", "#636363"
    );

    public String getUserColor(String username) {
        return userColors.computeIfAbsent(username,
                key -> colors.get(userColors.size() % colors.size())
        );
    }

    public String getDarkerShade(String color) {
        return colorMap.getOrDefault(color, "#000000");
    }

    public Map<String, String> getUserColors(String username) {
        String userColor = getUserColor(username);
        String darkerColor = getDarkerShade(userColor);

        return Map.of(
                "userColor", userColor,
                "darkerColor", darkerColor
        );
    }
}
