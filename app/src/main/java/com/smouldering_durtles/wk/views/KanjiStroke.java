package com.smouldering_durtles.wk.views;

import android.graphics.Path;

/** Parsed representation of one KanjiVG SVG stroke in a specific coordinate space. */
final class KanjiStroke {
    final Path path = new Path();
    int number;
    float labelX, labelY;
    float startX, startY;
}
