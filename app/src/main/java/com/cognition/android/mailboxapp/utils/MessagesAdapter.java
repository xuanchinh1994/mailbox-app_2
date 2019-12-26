package com.cognition.android.mailboxapp.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.LinearLayoutCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;

import com.cognition.android.mailboxapp.DecoderWrapper;
import com.cognition.android.mailboxapp.R;
import com.cognition.android.mailboxapp.activities.EmailActivity;
import com.cognition.android.mailboxapp.models.Message;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.MessageViewHolder> implements Filterable {

    private Context mContext;
    private Utils mUtils;
    private List<Message> messageList;
    private List<Message> messageListFiltered;

    private ViewGroup parent;

    // Load library
    static {
        System.loadLibrary("bpg_decoder");
    };

    public static byte[] toByteArray(InputStream input) throws IOException
    {
        byte[] buffer = new byte[1024];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while ((bytesRead = input.read(buffer)) != -1)
        {
            output.write(buffer, 0, bytesRead);
        }
        return output.toByteArray();
    }

    public Bitmap getDecodedBitmap(String resourceId){
        Bitmap bm = null;
//        InputStream is = getResources().openRawResource(resourceId);
        Base64 base64Url = new Base64(true);
//        byte[] fileByteArray = base64Url.decode(resourceId);
        byte[] byteArray = base64Url.decode(resourceId);
//            byte[] byteArray = toByteArray(is);
        byte[] decBuffer = null;
        int decBufferSize = 0;
        decBuffer = DecoderWrapper.decodeBuffer(byteArray, byteArray.length);
        decBufferSize = decBuffer.length;
        if(decBuffer != null){
            bm = BitmapFactory.decodeByteArray(decBuffer, 0, decBufferSize);
        }
        return bm;
    }

    class MessageViewHolder extends RecyclerView.ViewHolder {

        LinearLayoutCompat lytItemParent;
        ConstraintLayout lytFromPreviewParent;
        AppCompatTextView txtFromPreview, txtFrom, txtDate, txtSubject, txtSnippet;
        ImageView imageViewList;

        public MessageViewHolder(View itemView) {
            super(itemView);

            lytItemParent = itemView.findViewById(R.id.lytItemParent);
            lytFromPreviewParent = itemView.findViewById(R.id.lytFromPreviewParent);
            txtFromPreview = itemView.findViewById(R.id.txtFromPreview);
//            txtFrom = itemView.findViewById(R.id.txtFrom);
            txtDate = itemView.findViewById(R.id.txtDate);
//            txtSubject = itemView.findViewById(R.id.txtSubject);
//            txtSnippet = itemView.findViewById(R.id.txtSnippet);
            imageViewList = itemView.findViewById(R.id.imageInList);
        }
    }

    public MessagesAdapter(Context context, List<Message> messageList) {
        this.mContext = context;
        this.mUtils = new Utils(context);
        this.messageList = messageList;
        this.messageListFiltered = messageList;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        this.parent = parent;
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);

        return new MessageViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        final Message message = this.messageListFiltered.get(position);

        holder.lytItemParent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mUtils.isDeviceOnline()) {
                    Intent intent = new Intent(mContext, EmailActivity.class);
                    String test = message.getMessageGmailId();
                    intent.putExtra("messageId", message.getId());
                    intent.putExtra("messageGmailId", message.getMessageGmailId());
                    intent.putExtra("attachmentData", message.getAttachmentData());
                    mContext.startActivity(intent);
                } else
                    mUtils.showSnackbar(parent, mContext.getString(R.string.device_is_offline));
            }
        });

        android.graphics.drawable.GradientDrawable gradientDrawable = (android.graphics.drawable.GradientDrawable) holder.lytFromPreviewParent.getBackground();
        gradientDrawable.setColor(message.getColor());

        holder.txtFromPreview.setText(message.getFrom().substring(0, 1).toUpperCase(Locale.ENGLISH));
//        holder.txtFrom.setText(message.getFrom());
        holder.txtDate.setText(mUtils.timestampToDate(message.getTimestamp()));
//        holder.txtSubject.setText(message.getSubject());
//        holder.txtSnippet.setText(message.getSnippet());
        if (message.getAttachmentData()!= null){
            Bitmap bm = getDecodedBitmap(message.getAttachmentData());
            if(bm != null) {
                holder.imageViewList.setImageBitmap(bm);
            }
        }
    }

    @Override
    public int getItemCount() {
        return this.messageListFiltered.size();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                String searchText = constraint.toString().trim().toLowerCase();

                if (searchText.isEmpty()) {
                    MessagesAdapter.this.messageListFiltered = MessagesAdapter.this.messageList;
                } else {
                    List<Message> newMessageList = new ArrayList<>();

                    for (Message message : MessagesAdapter.this.messageList) {
                        if (message.getFrom().toLowerCase().contains(searchText)
                                || message.getSubject().toLowerCase().contains(searchText)
                                || message.getSnippet().toLowerCase().contains(searchText)
                                || mUtils.timestampToDate(message.getTimestamp()).toLowerCase().contains(searchText)
                                )
                            newMessageList.add(message);
                    }

                    MessagesAdapter.this.messageListFiltered = newMessageList;
                }

                FilterResults filterResults = new FilterResults();
                filterResults.values = MessagesAdapter.this.messageListFiltered;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                MessagesAdapter.this.messageListFiltered = (ArrayList<Message>) results.values;
                notifyDataSetChanged();
            }
        };
    }

}
