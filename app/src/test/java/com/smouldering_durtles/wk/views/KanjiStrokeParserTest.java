package com.smouldering_durtles.wk.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.RectF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class KanjiStrokeParserTest {

    @Test
    public void parse_simpleStroke_hasNonEmptyPath() {
        final KanjiStroke ks = KanjiStrokeParser.parse("M10,20L80,90T1,10,20");
        final RectF bounds = new RectF();
        ks.path.computeBounds(bounds, true);
        assertTrue("path bounds should have positive area", bounds.width() > 0 || bounds.height() > 0);
    }

    @Test
    public void parse_extractsStrokeNumber() {
        final KanjiStroke ks = KanjiStrokeParser.parse("M5,5L50,50T3,5,5");
        assertEquals(3, ks.number);
    }

    @Test
    public void parse_extractsStartPoint() {
        final KanjiStroke ks = KanjiStrokeParser.parse("M25,40L80,90T1,25,40");
        assertEquals(25f, ks.startX, 0.1f);
        assertEquals(40f, ks.startY, 0.1f);
    }

    @Test
    public void parse_emptyString_returnsEmptyPath() {
        final KanjiStroke ks = KanjiStrokeParser.parse("");
        final RectF bounds = new RectF();
        ks.path.computeBounds(bounds, true);
        assertEquals(0f, bounds.width(), 0.001f);
    }
}
