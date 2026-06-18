package com.smouldering_durtles.wk.views;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import static com.smouldering_durtles.wk.util.ObjectSupport.isEmpty;

/** Parses KanjiVG SVG path strings into {@link KanjiStroke} objects. */
final class KanjiStrokeParser {
    private static final Pattern SVG_INSTRUCTION = Pattern.compile("([a-zA-Z])([^a-zA-Z]+)");
    private static final Pattern SVG_COORDINATES = Pattern.compile("-?\\d*\\.?\\d*");

    private KanjiStrokeParser() {}

    /**
     * Parse one KanjiVG path string into a {@link KanjiStroke} in the raw 109×109 coordinate space.
     * The 'T' command encodes stroke number and label position; the first 'M' gives the start dot.
     */
    static KanjiStroke parse(final CharSequence pathData) {
        final KanjiStroke ks = new KanjiStroke();
        final Matcher matcher = SVG_INSTRUCTION.matcher(pathData);

        float lastX = 0, lastY = 0, lastX1 = 0, lastY1 = 0;
        float subStartX = 0, subStartY = 0;
        boolean firstMove = true;
        boolean curve = false;

        while (matcher.find()) {
            final char cmd = matcher.group(1).charAt(0);
            final List<Float> coords = splitCoordinates(matcher.group(2));

            switch (cmd) {
                case 'T': {
                    if (coords.size() >= 3) {
                        ks.number = coords.get(0).intValue();
                        ks.labelX = coords.get(1);
                        ks.labelY = coords.get(2);
                    }
                    break;
                }
                case 'M': {
                    subStartX = coords.get(0); subStartY = coords.get(1);
                    ks.path.moveTo(subStartX, subStartY);
                    lastX = subStartX; lastY = subStartY;
                    if (firstMove) { ks.startX = lastX; ks.startY = lastY; firstMove = false; }
                    break;
                }
                case 'm': {
                    subStartX += coords.get(0); subStartY += coords.get(1);
                    ks.path.rMoveTo(coords.get(0), coords.get(1));
                    lastX += coords.get(0); lastY += coords.get(1);
                    if (firstMove) { ks.startX = lastX; ks.startY = lastY; firstMove = false; }
                    break;
                }
                case 'L': {
                    ks.path.lineTo(coords.get(0), coords.get(1));
                    lastX = coords.get(0); lastY = coords.get(1);
                    break;
                }
                case 'l': {
                    ks.path.rLineTo(coords.get(0), coords.get(1));
                    lastX += coords.get(0); lastY += coords.get(1);
                    break;
                }
                case 'H': {
                    for (final float x : coords) { ks.path.lineTo(x, lastY); lastX = x; }
                    break;
                }
                case 'h': {
                    for (final float x : coords) { ks.path.rLineTo(x, 0); lastX += x; }
                    break;
                }
                case 'V': {
                    for (final float y : coords) { ks.path.lineTo(lastX, y); lastY = y; }
                    break;
                }
                case 'v': {
                    for (final float y : coords) { ks.path.rLineTo(0, y); lastY += y; }
                    break;
                }
                case 'C': case 'c': {
                    curve = true;
                    int i = 0;
                    while (i + 6 <= coords.size()) {
                        float x1 = coords.get(i), y1 = coords.get(i+1);
                        float x2 = coords.get(i+2), y2 = coords.get(i+3);
                        float x  = coords.get(i+4), y  = coords.get(i+5);
                        if (cmd == 'c') { x1+=lastX; y1+=lastY; x2+=lastX; y2+=lastY; x+=lastX; y+=lastY; }
                        ks.path.cubicTo(x1, y1, x2, y2, x, y);
                        lastX1=x2; lastY1=y2; lastX=x; lastY=y;
                        i += 6;
                    }
                    break;
                }
                case 'S': case 's': {
                    curve = true;
                    int i = 0;
                    while (i + 4 <= coords.size()) {
                        float x2 = coords.get(i), y2 = coords.get(i+1);
                        float x  = coords.get(i+2), y  = coords.get(i+3);
                        if (cmd == 's') { x2+=lastX; y2+=lastY; x+=lastX; y+=lastY; }
                        final float x1 = 2*lastX - lastX1, y1 = 2*lastY - lastY1;
                        ks.path.cubicTo(x1, y1, x2, y2, x, y);
                        lastX1=x2; lastY1=y2; lastX=x; lastY=y;
                        i += 4;
                    }
                    break;
                }
                case 'Z': case 'z': {
                    ks.path.close();
                    ks.path.moveTo(subStartX, subStartY);
                    lastX=subStartX; lastY=subStartY; lastX1=subStartX; lastY1=subStartY;
                    curve = true;
                    break;
                }
                default:
                    break;
            }
            if (!curve) { lastX1 = lastX; lastY1 = lastY; }
            curve = false;
        }
        return ks;
    }

    private static List<Float> splitCoordinates(final CharSequence str) {
        final Matcher matcher = SVG_COORDINATES.matcher(str);
        final List<Float> result = new ArrayList<>();
        while (matcher.find()) {
            final @Nullable String s = matcher.group();
            if (!isEmpty(s)) {
                result.add(Float.parseFloat(s));
            }
        }
        return result;
    }
}
