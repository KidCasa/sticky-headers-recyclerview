package com.timehop.stickyheadersrecyclerview;

import android.graphics.Rect;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import com.timehop.stickyheadersrecyclerview.caching.HeaderProvider;
import com.timehop.stickyheadersrecyclerview.calculation.DimensionCalculator;
import com.timehop.stickyheadersrecyclerview.util.OrientationProvider;

/**
 * Calculates the position and location of header views
 */
public class HeaderPositionCalculator {

    private static final String TAG = HeaderPositionCalculator.class.getName();
    private final StickyRecyclerHeadersAdapter mAdapter;
    private final OrientationProvider mOrientationProvider;
    private final HeaderProvider mHeaderProvider;
    private final DimensionCalculator mDimensionCalculator;
    private int stickyTopOffset = 250;

    public HeaderPositionCalculator(StickyRecyclerHeadersAdapter adapter, HeaderProvider headerProvider,
                                    OrientationProvider orientationProvider, DimensionCalculator dimensionCalculator) {
        mAdapter = adapter;
        mHeaderProvider = headerProvider;
        mOrientationProvider = orientationProvider;
        mDimensionCalculator = dimensionCalculator;
    }

    /**
     * Determines if a view should have a sticky header.
     * The view has a sticky header if:
     * 1. It is the first element in the recycler view
     * 2. It has a valid ID associated to its position
     *
     * @param itemView    given by the RecyclerView
     * @param orientation of the Recyclerview
     * @param position    of the list item in question
     * @return True if the view should have a sticky header
     */
    public boolean hasStickyHeader(View itemView, int orientation, int position) {
        int offset, margin;
        if (orientation == LinearLayout.VERTICAL) {
            offset = itemView.getTop();
            margin = mDimensionCalculator.getMargins(itemView).top + stickyTopOffset;
        } else {
            offset = itemView.getLeft();
            margin = mDimensionCalculator.getMargins(itemView).left;
        }

        return offset <= margin && itemView.getBottom() >= margin - 1 && mAdapter.getHeaderId(position) >= 0;
    }

    /**
     * Determines if an item in the list should have a header that is different than the item in the
     * list that immediately precedes it. Items with no headers will always return false.
     *
     * @param position of the list item in questions
     * @return true if this item has a different header than the previous item in the list
     * @see {@link StickyRecyclerHeadersAdapter#getHeaderId(int)}
     */
    public boolean hasNewHeader(int position) {
        if (indexOutOfBounds(position)) {
            return false;
        }

        long headerId = mAdapter.getHeaderId(position);

        if (headerId < 0) {
            return false;
        }

        return position == 0 || headerId != mAdapter.getHeaderId(position - 1);
    }

    private boolean indexOutOfBounds(int position) {
        return position < 0 || position >= mAdapter.getItemCount();
    }

    public Rect getHeaderBounds(RecyclerView recyclerView, View header, View firstView, boolean firstHeader) {
        int orientation = mOrientationProvider.getOrientation(recyclerView);
        Rect bounds = getDefaultHeaderOffset(recyclerView, header, firstView, orientation);

        if (firstHeader && isStickyHeaderBeingPushedOffscreen(recyclerView, header)) {
            View viewAfterNextHeader = getFirstViewUnobscuredByHeader(recyclerView, header);
            int firstViewUnderHeaderPosition = recyclerView.getChildAdapterPosition(viewAfterNextHeader);
            View secondHeader = mHeaderProvider.getHeader(recyclerView, firstViewUnderHeaderPosition);
            translateHeaderWithNextHeader(recyclerView, mOrientationProvider.getOrientation(recyclerView), bounds,
                    header, viewAfterNextHeader, secondHeader);
        }

        return bounds;
    }

    private Rect getDefaultHeaderOffset(RecyclerView recyclerView, View header, View firstView, int orientation) {
        int translationX, translationY;
        Rect headerMargins = mDimensionCalculator.getMargins(header);
        if (orientation == LinearLayoutManager.VERTICAL) {
            translationX = firstView.getLeft() + headerMargins.left;
            translationY = Math.max(
                    firstView.getTop() - header.getHeight() - headerMargins.bottom,
                    getListTop(recyclerView) + headerMargins.top);
        } else {
            translationY = firstView.getTop() + headerMargins.top;
            translationX = Math.max(
                    firstView.getLeft() - header.getWidth() - headerMargins.right,
                    getListLeft(recyclerView) + headerMargins.left);
        }

        return new Rect(translationX, translationY, translationX + header.getWidth(),
                translationY + header.getHeight());
    }

    private boolean isStickyHeaderBeingPushedOffscreen(RecyclerView recyclerView, View stickyHeader) {
        View viewAfterHeader = getFirstViewUnobscuredByHeader(recyclerView, stickyHeader);
        int firstViewUnderHeaderPosition = recyclerView.getChildAdapterPosition(viewAfterHeader);
        if (firstViewUnderHeaderPosition == RecyclerView.NO_POSITION) {
            return false;
        }

        if (firstViewUnderHeaderPosition > 0 && hasNewHeader(firstViewUnderHeaderPosition)) {
            View nextHeader = mHeaderProvider.getHeader(recyclerView, firstViewUnderHeaderPosition);
            Rect nextHeaderMargins = mDimensionCalculator.getMargins(nextHeader);
            Rect headerMargins = mDimensionCalculator.getMargins(stickyHeader);

            if (mOrientationProvider.getOrientation(recyclerView) == LinearLayoutManager.VERTICAL) {
                int topOfNextHeader = viewAfterHeader.getTop() - nextHeaderMargins.bottom - nextHeader.getHeight() - nextHeaderMargins.top;
                int bottomOfThisHeader = getListTop(recyclerView) + stickyHeader.getBottom() + headerMargins.top + headerMargins.bottom;
                if (topOfNextHeader >= getListTop(recyclerView) && topOfNextHeader < bottomOfThisHeader) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "isStickyHeaderBeingPushedOffscreen true");
                    }
                    return true;
                }
            } else {
                int leftOfNextHeader = viewAfterHeader.getLeft() - nextHeaderMargins.right - nextHeader.getWidth() - nextHeaderMargins.left;
                int rightOfThisHeader = recyclerView.getPaddingLeft() + stickyHeader.getRight() + headerMargins.left + headerMargins.right;
                if (leftOfNextHeader < rightOfThisHeader) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "isStickyHeaderBeingPushedOffscreen true");
                    }
                    return true;
                }
            }
        }

        return false;
    }

    private void translateHeaderWithNextHeader(RecyclerView recyclerView, int orientation, Rect translation,
                                               View currentHeader, View viewAfterNextHeader, View nextHeader) {
        Rect nextHeaderMargins = mDimensionCalculator.getMargins(nextHeader);
        Rect stickyHeaderMargins = mDimensionCalculator.getMargins(currentHeader);
        if (orientation == LinearLayoutManager.VERTICAL) {
            int topOfStickyHeader = getListTop(recyclerView) + stickyHeaderMargins.top + stickyHeaderMargins.bottom;
            int shiftFromNextHeader = viewAfterNextHeader.getTop() - nextHeader.getHeight() - nextHeaderMargins.bottom - nextHeaderMargins.top - currentHeader.getHeight() - topOfStickyHeader;
            if (shiftFromNextHeader < topOfStickyHeader) {
                translation.top += shiftFromNextHeader;
            }
        } else {
            int leftOfStickyHeader = getListLeft(recyclerView) + stickyHeaderMargins.left + stickyHeaderMargins.right;
            int shiftFromNextHeader = viewAfterNextHeader.getLeft() - nextHeader.getWidth() - nextHeaderMargins.right - nextHeaderMargins.left - currentHeader.getWidth() - leftOfStickyHeader;
            if (shiftFromNextHeader < leftOfStickyHeader) {
                translation.left += shiftFromNextHeader;
            }
        }
    }

    /**
     * Returns the first item currently in the RecyclerView that is not obscured by a header.
     *
     * @param parent Recyclerview containing all the list items
     * @return first item that is fully beneath a header
     */
    private View getFirstViewUnobscuredByHeader(RecyclerView parent, View firstHeader) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (!itemIsObscuredByHeader(parent, child, firstHeader, mOrientationProvider.getOrientation(parent))) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "First unobscured view " + i);
                }
                return parent.getChildAt(i);
            }
        }
        return null;
    }

    /**
     * Determines if an item is obscured by a header
     *
     * @param parent
     * @param item        to determine if obscured by header
     * @param header      that might be obscuring the item
     * @param orientation of the {@link RecyclerView}
     * @return true if the item view is obscured by the header view
     */
    private boolean itemIsObscuredByHeader(RecyclerView parent, View item, View header, int orientation) {
        RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) item.getLayoutParams();
        Rect headerMargins = mDimensionCalculator.getMargins(header);

        int adapterPosition = parent.getChildAdapterPosition(item);
        if (adapterPosition == RecyclerView.NO_POSITION/* || mHeaderProvider.getHeader(parent, adapterPosition) != header*/) {
            // Resolves https://github.com/timehop/sticky-headers-recyclerview/issues/36
            // Handles an edge case where a trailing header is smaller than the current sticky header.
            return false;
        }

        int itemTop = item.getTop() - layoutParams.topMargin;
        int headerBottom = getListTop(parent) + header.getBottom() + headerMargins.bottom + headerMargins.top;
        if (orientation == LinearLayoutManager.VERTICAL) {
            if (itemTop > headerBottom) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "headerBottom " + headerBottom);
                    Log.d(TAG, "itemTop " + itemTop);
                }
                return false;
            }
        } else {
            int itemLeft = item.getLeft() - layoutParams.leftMargin;
            int headerRight = header.getRight() + headerMargins.right + headerMargins.left;
            if (itemLeft > headerRight) {
                return false;
            }
        }
        return true;
    }

    private int getListTop(RecyclerView view) {
        if (view.getLayoutManager().getClipToPadding()) {
            return view.getPaddingTop() + stickyTopOffset;
        } else {
            return stickyTopOffset;
        }
    }

    private int getListLeft(RecyclerView view) {
        if (view.getLayoutManager().getClipToPadding()) {
            return view.getPaddingLeft();
        } else {
            return 0;
        }
    }

    public void setStickyTopOffset(int topOffset) {
        stickyTopOffset = topOffset;
    }
}
