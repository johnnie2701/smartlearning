package com.gemma3n.smartlearning;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

// Assumes ChatMessagePojo exists as defined previously:
// class ChatMessagePojo {
// public final String text;
// public final boolean isUser;
// public ChatMessagePojo(String text, boolean isUser) { this.text = text; this.isUser = isUser; }
// }

public class ChatMessagesAdapter extends RecyclerView.Adapter<ChatMessagesAdapter.MessageViewHolder> {

    private static final int VIEW_TYPE_USER_MESSAGE = 1;
    private static final int VIEW_TYPE_AI_MESSAGE = 2;

    private List<ChatMessagePojo> messages;

    public ChatMessagesAdapter(List<ChatMessagePojo> messages) {
        this.messages = messages;
    }

    public void setMessages(List<ChatMessagePojo> newMessages) {
        this.messages = newMessages;
        notifyDataSetChanged(); // For simplicity. For better performance, use DiffUtil.
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessagePojo message = messages.get(position);
        if (message.isUser) {
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
        } else { // VIEW_TYPE_AI_MESSAGE
            view = inflater.inflate(R.layout.item_chat_message_ai, parent, false);
        }
        return new MessageViewHolder(view);
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

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            // The TextView ID is the same in both item_chat_message_user.xml and item_chat_message_ai.xml
            messageTextView = itemView.findViewById(R.id.messageTextView);
        }

        void bind(ChatMessagePojo message) {
            if (messageTextView != null) {
                messageTextView.setText(message.text);
            }
        }
    }
}