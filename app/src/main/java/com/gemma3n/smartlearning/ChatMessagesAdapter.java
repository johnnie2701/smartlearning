package com.gemma3n.smartlearning;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import io.noties.markwon.Markwon;
import com.airbnb.lottie.LottieAnimationView;
import java.util.List;


public class ChatMessagesAdapter extends RecyclerView.Adapter<ChatMessagesAdapter.MessageViewHolder> {

    private static final int VIEW_TYPE_USER_MESSAGE = 1;
    private static final int VIEW_TYPE_AI_MESSAGE = 2;
    private static final int VIEW_TYPE_LOADING_MESSAGE = 3;

    private List<ChatMessagePojo> messages;
    private Markwon markwon;

    public ChatMessagesAdapter(List<ChatMessagePojo> messages) {
        this.messages = messages;
    }
    
    public void setMarkwon(Markwon markwon) {
        this.markwon = markwon;
    }

    public void setMessages(List<ChatMessagePojo> newMessages) {
        this.messages = newMessages;
        notifyDataSetChanged(); // For simplicity. For better performance, use DiffUtil.
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessagePojo message = messages.get(position);
        if (message.isLoading) {
            return VIEW_TYPE_LOADING_MESSAGE;
        } else if (message.isUser) {
            return VIEW_TYPE_USER_MESSAGE;
        } else {
            return VIEW_TYPE_AI_MESSAGE;
        }
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view;
        if (viewType == VIEW_TYPE_USER_MESSAGE) {
            view = inflater.inflate(R.layout.item_chat_message_user, parent, false);
        } else if (viewType == VIEW_TYPE_LOADING_MESSAGE) {
            view = inflater.inflate(R.layout.item_chat_message_loading, parent, false);
        } else { // VIEW_TYPE_AI_MESSAGE
            view = inflater.inflate(R.layout.item_chat_message_ai, parent, false);
        }
        MessageViewHolder holder = new MessageViewHolder(view, viewType);
        if (markwon != null) {
            holder.setMarkwon(markwon);
        }
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessagePojo message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messages == null ? 0 : messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView;
        LottieAnimationView loadingAnimation;
        Markwon markwon;
        int viewType;

        public MessageViewHolder(@NonNull View itemView, int viewType) {
            super(itemView);
            this.viewType = viewType;
            
            if (viewType == VIEW_TYPE_LOADING_MESSAGE) {
                loadingAnimation = itemView.findViewById(R.id.loadingAnimation);
            } else {
                // The TextView ID is the same in both item_chat_message_user.xml and item_chat_message_ai.xml
                messageTextView = itemView.findViewById(R.id.messageTextView);
            }
        }
        
        public void setMarkwon(Markwon markwon) {
            this.markwon = markwon;
        }

        void bind(ChatMessagePojo message) {
            if (viewType == VIEW_TYPE_LOADING_MESSAGE) {
                if (loadingAnimation != null) {
                    loadingAnimation.playAnimation();
                }
            } else if (messageTextView != null) {
                if (markwon != null && !message.isUser) {
                    // Use Markwon for AI messages (which may contain markdown)
                    markwon.setMarkdown(messageTextView, message.text);
                } else {
                    // Use plain text for user messages
                    messageTextView.setText(message.text);
                }
            }
        }
    }
}