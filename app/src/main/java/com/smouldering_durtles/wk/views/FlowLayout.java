package com.smouldering_durtles.wk.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * A simple ViewGroup that lays children out horizontally, wrapping to the next
 * row when they overflow the available width. Useful for chip/tag displays.
 */
public final class FlowLayout extends ViewGroup {

    public FlowLayout(final Context context) {
        super(context);
    }

    public FlowLayout(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public FlowLayout(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final int maxWidth = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int totalHeight = getPaddingTop();
        int rowWidth = 0;
        int rowHeight = 0;

        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;

            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            final int childWidth = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            final int childHeight = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;

            if (rowWidth + childWidth > maxWidth && rowWidth > 0) {
                totalHeight += rowHeight;
                rowWidth = 0;
                rowHeight = 0;
            }
            rowWidth += childWidth;
            rowHeight = Math.max(rowHeight, childHeight);
        }
        totalHeight += rowHeight + getPaddingBottom();

        setMeasuredDimension(
                resolveSize(maxWidth + getPaddingLeft() + getPaddingRight(), widthMeasureSpec),
                resolveSize(totalHeight, heightMeasureSpec));
    }

    @Override
    protected void onLayout(final boolean changed, final int l, final int t, final int r, final int b) {
        final int maxWidth = r - l - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int rowHeight = 0;

        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;

            final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            final int childWidth = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            final int childHeight = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;

            if (x + childWidth > maxWidth + getPaddingLeft() && x > getPaddingLeft()) {
                x = getPaddingLeft();
                y += rowHeight;
                rowHeight = 0;
            }

            child.layout(
                    x + lp.leftMargin,
                    y + lp.topMargin,
                    x + lp.leftMargin + child.getMeasuredWidth(),
                    y + lp.topMargin + child.getMeasuredHeight());

            x += childWidth;
            rowHeight = Math.max(rowHeight, childHeight);
        }
    }

    @Override
    public LayoutParams generateLayoutParams(final AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(final LayoutParams p) {
        return new MarginLayoutParams(p);
    }
}
