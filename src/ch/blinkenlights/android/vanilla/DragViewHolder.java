package ch.blinkenlights.android.vanilla;

import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.h6ah4i.android.widget.advrecyclerview.swipeable.SwipeableItemViewHolder;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableSwipeableItemViewHolder;

public class DragViewHolder extends AbstractDraggableSwipeableItemViewHolder {
    private View mContainer;
    private View mDraggable;
    public DragViewHolder(final View itemView) {
        super(itemView);
        mContainer = itemView.findViewById(R.id.container);
        mDraggable = itemView.findViewById(R.id.dragger);
    }

    @Override
    public View getSwipeableContainerView() {
        return mContainer;
    }

    public View getClickableView() {
        return getSwipeableContainerView();
    }

    public View getDragHandle() {
        return mDraggable;
    }

    public boolean canSwipeToRemove() {
        return true;
    }
}
