package ch.blinkenlights.android.vanilla;

import android.support.v7.widget.RecyclerView;
import android.util.Log;

import com.h6ah4i.android.widget.advrecyclerview.swipeable.RecyclerViewSwipeManager;
import com.h6ah4i.android.widget.advrecyclerview.swipeable.SwipeableItemAdapter;

public class RemoveSwipeAdapter<T extends DragViewHolder> implements SwipeableItemAdapter<T> {
    private RemoveItemListener mRemoveItemListener;

    @Override
    public int onGetSwipeReactionType(final DragViewHolder holder, final int position, final int x, final int y) {
        log("onGetSwipeReactionType");
        if(!holder.canSwipeToRemove()){
            return RecyclerViewSwipeManager.REACTION_CAN_NOT_SWIPE_BOTH;
        }
        return RecyclerViewSwipeManager.REACTION_CAN_SWIPE_LEFT | RecyclerViewSwipeManager.REACTION_CAN_SWIPE_RIGHT;
    }

    @Override
    public void onSetSwipeBackground(final DragViewHolder holder, final int position, final int type) {
        logv("onSetSwipeBackground");
    }

    @Override
    public int onSwipeItem(final DragViewHolder holder, final int position, final int result) {
        log("onSwipeItem");
        switch (result) {
            // swipe right or left
            case RecyclerViewSwipeManager.RESULT_SWIPED_RIGHT:
            case RecyclerViewSwipeManager.RESULT_SWIPED_LEFT:
                return RecyclerViewSwipeManager.AFTER_SWIPE_REACTION_REMOVE_ITEM;
            default:
                return RecyclerViewSwipeManager.AFTER_SWIPE_REACTION_DEFAULT;
        }
    }

    @Override
    public void onPerformAfterSwipeReaction(final DragViewHolder holder, final int position, final int result, final int reaction) {
        log("onPerformAfterSwipeReaction");
        if (reaction == RecyclerViewSwipeManager.AFTER_SWIPE_REACTION_REMOVE_ITEM) {
            mRemoveItemListener.onRemoveItem(position);
        }
    }

    public void setRemoveItemListener(RemoveItemListener removeItemListener) {
        mRemoveItemListener = removeItemListener;
    }

    public interface RemoveItemListener {
        void onRemoveItem(int position);
    }

    private void log(final String message) {
        Log.d(RemoveSwipeAdapter.class.getSimpleName(), message);
    }
    private void logv(final String message) {
        Log.v(RemoveSwipeAdapter.class.getSimpleName(), message);
    }
}