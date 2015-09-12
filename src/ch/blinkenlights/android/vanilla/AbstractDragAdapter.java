package ch.blinkenlights.android.vanilla;

import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.h6ah4i.android.widget.advrecyclerview.swipeable.SwipeableItemAdapter;

public abstract class AbstractDragAdapter<T extends DragViewHolder> extends RecyclerView.Adapter<T> implements SwipeableItemAdapter<T> {

    private RemoveSwipeAdapter<T> mRemoveSwipeAdapter = new RemoveSwipeAdapter<T>();
    private View.OnClickListener mOnClickListener;
    private View.OnClickListener mOnClickListenerDelegate = new View.OnClickListener() {
        @Override
        public void onClick(final View v) {
            if(mOnClickListener != null) {
                mOnClickListener.onClick(v);
            }
        }
    };

    protected RemoveSwipeAdapter<T> getRemoveSwipeAdapter() {
        return mRemoveSwipeAdapter;
    }

    public void setOnClickListener(View.OnClickListener onClickListener){
        mOnClickListener = onClickListener;
    }
    @Override
    public void onBindViewHolder(T holder, int position) {
        holder.itemView.setOnClickListener(mOnClickListenerDelegate);
    }

    @Override
    public int onGetSwipeReactionType(final T holder, final int position, final int x, final int y) {
        return mRemoveSwipeAdapter.onGetSwipeReactionType(holder, position, x, y);
    }

    @Override
    public void onSetSwipeBackground(final T holder, final int position, final int type) {
        mRemoveSwipeAdapter.onSetSwipeBackground(holder, position, type);
    }

    @Override
    public int onSwipeItem(final T holder, final int position, final int result) {
        return mRemoveSwipeAdapter.onSwipeItem(holder, position, result);
    }

    @Override
    public void onPerformAfterSwipeReaction(final T holder, final int position, final int result, final int reaction) {
        mRemoveSwipeAdapter.onPerformAfterSwipeReaction(holder, position, result, reaction);
    }

}